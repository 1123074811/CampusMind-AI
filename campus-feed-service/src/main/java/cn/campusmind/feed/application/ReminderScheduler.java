package cn.campusmind.feed.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String pushWebhook;

    public ReminderScheduler(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                             @Value("${campus.feed.push-webhook-url:}") String pushWebhook) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.pushWebhook = pushWebhook;
    }

    @Scheduled(fixedDelayString = "${campus.feed.reminder-check-interval-ms:60000}",
               initialDelayString = "${campus.feed.reminder-initial-delay-ms:10000}")
    public void deliverDueReminders() {
        withdrawDismissed();
        materializeDeliveries();
        List<Map<String, Object>> pending = jdbcTemplate.queryForList("""
                SELECT d.id, d.reminder_id AS reminderId, d.user_id AS userId, d.channel,
                       d.attempt_count AS attemptCount, u.push_token AS pushToken,
                       a.title, a.original_url AS originalUrl, r.remind_at AS remindAt
                FROM notification_delivery d
                JOIN user_reminder r ON r.id = d.reminder_id
                JOIN user_action_item a ON a.id = r.action_item_id
                LEFT JOIN user_device u ON u.id = d.device_id
                WHERE d.status IN ('PENDING', 'RETRY') AND (d.next_attempt_at IS NULL OR d.next_attempt_at <= NOW())
                ORDER BY d.id LIMIT 100
                """);
        for (Map<String, Object> row : pending) deliver(row);
        jdbcTemplate.update("""
                UPDATE user_reminder r SET r.status = 'DUE', r.sent_at = COALESCE(r.sent_at, CURRENT_TIMESTAMP)
                WHERE r.status = 'PENDING' AND EXISTS (
                  SELECT 1 FROM notification_delivery d WHERE d.reminder_id = r.id
                    AND d.channel = 'IN_APP' AND d.status = 'SENT')
                """);
    }

    /** 保留给运维手动触发和旧版调用方；正式调度使用带投递账本的 deliverDueReminders。 */
    public void markDueReminders() {
        jdbcTemplate.update("""
                UPDATE user_reminder SET status = 'DUE', sent_at = COALESCE(sent_at, CURRENT_TIMESTAMP)
                WHERE status = 'PENDING' AND remind_at <= NOW()
                """);
    }

    private void materializeDeliveries() {
        jdbcTemplate.update("""
                INSERT IGNORE INTO notification_delivery(reminder_id, user_id, channel, dedup_key)
                SELECT r.id, r.user_id, 'IN_APP', CONCAT('reminder:', r.id, ':in-app')
                FROM user_reminder r WHERE r.status = 'PENDING' AND r.remind_at <= NOW()
                """);
        jdbcTemplate.update("""
                INSERT IGNORE INTO notification_delivery(reminder_id, user_id, device_id, channel, dedup_key)
                SELECT r.id, r.user_id, d.id, 'PUSH', CONCAT('reminder:', r.id, ':device:', d.id)
                FROM user_reminder r JOIN user_device d ON d.user_id = r.user_id
                WHERE r.status = 'PENDING' AND r.remind_at <= NOW() AND d.enabled = 1 AND d.push_token IS NOT NULL
                """);
    }

    private void withdrawDismissed() {
        jdbcTemplate.update("""
                UPDATE notification_delivery d JOIN user_reminder r ON r.id = d.reminder_id
                SET d.status = 'WITHDRAWN', d.withdrawn_at = COALESCE(d.withdrawn_at, CURRENT_TIMESTAMP)
                WHERE r.status = 'DISMISSED' AND d.status <> 'WITHDRAWN'
                """);
    }

    private void deliver(Map<String, Object> row) {
        long id = ((Number) row.get("id")).longValue();
        int attempts = ((Number) row.get("attemptCount")).intValue() + 1;
        if (jdbcTemplate.update("UPDATE notification_delivery SET status='SENDING', attempt_count=? WHERE id=? AND status IN ('PENDING','RETRY')", attempts, id) == 0) return;
        try {
            String providerId = null;
            if ("PUSH".equals(row.get("channel"))) providerId = sendPush(row);
            jdbcTemplate.update("""
                    UPDATE notification_delivery SET status='SENT', provider_message_id=?, sent_at=CURRENT_TIMESTAMP,
                      next_attempt_at=NULL, last_error=NULL WHERE id=?
                    """, providerId, id);
        } catch (Exception ex) {
            boolean exhausted = attempts >= 5;
            int delayMinutes = Math.min(60, 1 << Math.min(attempts, 6));
            jdbcTemplate.update("""
                    UPDATE notification_delivery SET status=?, last_error=?,
                      next_attempt_at=IF(?='RETRY', DATE_ADD(NOW(), INTERVAL ? MINUTE), NULL) WHERE id=?
                    """, exhausted ? "FAILED" : "RETRY", abbreviate(ex.getMessage()), exhausted ? "FAILED" : "RETRY", delayMinutes, id);
            log.warn("通知投递失败 deliveryId={}, attempt={}: {}", id, attempts, ex.getMessage());
        }
    }

    private String sendPush(Map<String, Object> row) throws Exception {
        if (pushWebhook == null || pushWebhook.isBlank()) throw new IllegalStateException("未配置推送服务 webhook");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", row.get("pushToken"));
        payload.put("title", "CampusMind 待办提醒");
        payload.put("body", row.get("title"));
        payload.put("url", row.get("originalUrl"));
        payload.put("reminderId", row.get("reminderId"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(pushWebhook)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload))).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("推送服务 HTTP " + response.statusCode());
        return response.headers().firstValue("X-Message-Id").orElse(null);
    }

    private static String abbreviate(String value) {
        if (value == null) return "未知错误";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}

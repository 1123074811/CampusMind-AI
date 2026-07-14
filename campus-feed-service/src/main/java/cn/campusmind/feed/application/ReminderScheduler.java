package cn.campusmind.feed.application;

import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时扫描到期提醒，将 PENDING 状态更新为 DUE 并记录站内送达时间，供前端轮询展示。
 */
@Component
public class ReminderScheduler {

    private final JdbcTemplate jdbcTemplate;

    public ReminderScheduler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelayString = "${campus.feed.reminder-check-interval-ms:300000}",
               initialDelayString = "${campus.feed.reminder-initial-delay-ms:30000}")
    public void markDueReminders() {
        int updated = jdbcTemplate.update("""
                UPDATE user_reminder
                SET status = 'DUE', sent_at = COALESCE(sent_at, CURRENT_TIMESTAMP)
                WHERE status = 'PENDING' AND remind_at <= NOW()
                """);
        if (updated > 0) {
            LoggerFactory.getLogger(ReminderScheduler.class)
                    .info("已将 {} 条到期提醒标记为 DUE", updated);
        }
    }
}

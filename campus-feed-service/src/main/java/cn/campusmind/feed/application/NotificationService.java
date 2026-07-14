package cn.campusmind.feed.application;

import cn.campusmind.common.exception.BusinessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private final JdbcTemplate jdbcTemplate;

    public NotificationService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Transactional
    public Map<String, Object> registerDevice(Long userId, String deviceId, String platform, String pushToken) {
        requireUser(userId);
        jdbcTemplate.update("""
                INSERT INTO user_device(user_id, device_id, platform, push_token, enabled)
                VALUES (?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE platform = VALUES(platform), push_token = VALUES(push_token), enabled = 1
                """, userId, deviceId.trim(), platform.trim().toUpperCase(), blankToNull(pushToken));
        return jdbcTemplate.queryForMap("""
                SELECT id, device_id AS deviceId, platform, enabled, updated_at AS updatedAt
                FROM user_device WHERE user_id = ? AND device_id = ?
                """, userId, deviceId.trim());
    }

    @Transactional
    public void unregisterDevice(Long userId, String deviceId) {
        requireUser(userId);
        jdbcTemplate.update("UPDATE user_device SET enabled = 0, push_token = NULL WHERE user_id = ? AND device_id = ?",
                userId, deviceId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> deliveries(Long userId) {
        requireUser(userId);
        return jdbcTemplate.queryForList("""
                SELECT d.id, d.reminder_id AS reminderId, d.channel, d.status,
                       d.attempt_count AS attemptCount, d.last_error AS lastError,
                       d.sent_at AS sentAt, d.withdrawn_at AS withdrawnAt, d.created_at AS createdAt
                FROM notification_delivery d WHERE d.user_id = ? ORDER BY d.id DESC LIMIT 100
                """, userId);
    }

    @Transactional
    public void withdraw(Long userId, Long reminderId) {
        requireUser(userId);
        int changed = jdbcTemplate.update("""
                UPDATE user_reminder SET status = 'DISMISSED' WHERE id = ? AND user_id = ?
                  AND status IN ('PENDING', 'DUE')
                """, reminderId, userId);
        if (changed == 0) {
            throw new BusinessException("REMINDER_NOT_FOUND", "提醒不存在或已处理", HttpStatus.NOT_FOUND);
        }
        jdbcTemplate.update("""
                UPDATE notification_delivery SET status = 'WITHDRAWN', withdrawn_at = CURRENT_TIMESTAMP
                WHERE reminder_id = ? AND user_id = ? AND status <> 'WITHDRAWN'
                """, reminderId, userId);
    }

    private static void requireUser(Long userId) {
        if (userId == null) throw new BusinessException("USER_REQUIRED", "需要用户身份", HttpStatus.UNAUTHORIZED);
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

package cn.campusmind.user.application;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class PrivacyRetentionScheduler {
    private final JdbcTemplate jdbcTemplate;
    private final int retentionDays;

    public PrivacyRetentionScheduler(JdbcTemplate jdbcTemplate,
                                     @Value("${campus.privacy.retention-days:365}") int retentionDays) {
        this.jdbcTemplate = jdbcTemplate;
        this.retentionDays = Math.max(30, retentionDays);
    }

    @Scheduled(cron = "${campus.privacy.cleanup-cron:0 20 3 * * *}")
    public void purgeExpiredOperationalData() {
        LocalDateTime expiresBefore = LocalDateTime.now().minusDays(retentionDays);
        int deliveries = jdbcTemplate.update(
                "DELETE FROM notification_delivery WHERE created_at < ?", expiresBefore);
        int changes = jdbcTemplate.update(
                "DELETE FROM information_change_log WHERE changed_at < ?", expiresBefore);
        if (deliveries + changes > 0) {
            LoggerFactory.getLogger(getClass()).info("隐私保留期清理完成：通知投递 {}，变更记录 {}", deliveries, changes);
        }
    }
}

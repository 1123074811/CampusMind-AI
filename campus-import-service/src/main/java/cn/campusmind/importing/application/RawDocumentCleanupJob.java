package cn.campusmind.importing.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时清理过期原始文档。作为 MongoDB TTL 索引的兜底机制。
 */
@Component
public class RawDocumentCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RawDocumentCleanupJob.class);

    private final RawDocumentService rawDocumentService;

    public RawDocumentCleanupJob(RawDocumentService rawDocumentService) {
        this.rawDocumentService = rawDocumentService;
    }

    /**
     * 每小时执行一次，清理已过期的原始文档。
     */
    @Scheduled(fixedDelay = 3600000)
    public void cleanupExpired() {
        try {
            int deleted = rawDocumentService.deleteExpired();
            log.debug("定时清理完成，删除 {} 条过期原始文档", deleted);
        } catch (Exception ex) {
            log.warn("定时清理过期原始文档失败: {}", ex.getMessage());
        }
    }
}

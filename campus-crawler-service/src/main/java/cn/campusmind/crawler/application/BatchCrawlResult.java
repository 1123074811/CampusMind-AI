package cn.campusmind.crawler.application;

import java.time.LocalDateTime;
import java.util.List;

public record BatchCrawlResult(
        int sourceCount,
        int successCount,
        int failedCount,
        int persistedCount,
        List<CrawlSourceResult> results,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}

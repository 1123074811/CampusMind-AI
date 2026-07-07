package cn.campusmind.crawler.application;

import java.time.LocalDateTime;
import java.util.List;

public record CrawlSourceResult(
        Long taskId,
        Long sourceId,
        String sourceName,
        String status,
        Integer httpStatus,
        String crawlUrl,
        int discoveredCount,
        List<CrawledLink> links,
        String parserVersion,
        String failReason,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}

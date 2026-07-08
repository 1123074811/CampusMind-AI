package cn.campusmind.crawler.controller;

import java.time.LocalDateTime;

public record CrawlItemResponse(
        Long id,
        Long sourceId,
        String sourceName,
        String sourceUrl,
        String itemUrl,
        String title,
        String detailTitle,
        String dateText,
        String summary,
        String detailContent,
        String parseStatus,
        String parseError,
        Integer detailHttpStatus,
        LocalDateTime fetchedAt,
        LocalDateTime detailFetchedAt
) {
}

package cn.campusmind.feed.controller;

import java.time.LocalDateTime;
import java.util.Map;

public record InformationFeedItemResponse(
        Long id,
        String title,
        String sourceName,
        LocalDateTime publishTime,
        LocalDateTime fetchedAt,
        String readStatus,
        String itemStatus,
        String preview,
        String originalUrl,
        String aiStatus,
        String eventType,
        String aiSummary,
        Boolean aiNeedReview,
        Map<String, Object> aiCard
) {
}

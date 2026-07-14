package cn.campusmind.feed.controller;

import java.time.LocalDateTime;
import java.util.Map;

public record InformationDetailResponse(
        Long id,
        String title,
        String detailContent,
        String sourceName,
        String sourceUrl,
        String originalUrl,
        LocalDateTime publishTime,
        LocalDateTime fetchedAt,
        String contentHash,
        String itemStatus,
        String readStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String aiStatus,
        String eventType,
        String aiSummary,
        Boolean aiNeedReview,
        Map<String, Object> aiCard,
        String submittedBy,
        Long submittedByUserId
) {
}

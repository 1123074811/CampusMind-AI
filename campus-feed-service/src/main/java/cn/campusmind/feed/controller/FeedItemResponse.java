package cn.campusmind.feed.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record FeedItemResponse(
        Long id,
        String title,
        String summary,
        String eventType,
        String status,
        boolean aiPredicted,
        BigDecimal confidence,
        LocalDateTime startTime,
        String location,
        List<String> tags,
        int relevanceScore
) {
}

package cn.campusmind.event.controller;

import java.time.LocalDateTime;
import java.util.List;

public record EventDetailResponse(
        Long id,
        String title,
        String summary,
        String eventType,
        String sourceType,
        String status,
        boolean aiPredicted,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String location,
        String organizer,
        List<String> targetScopes,
        List<String> tags,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<EventSourceRefResponse> sources
) {
}

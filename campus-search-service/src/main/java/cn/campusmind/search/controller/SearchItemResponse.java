package cn.campusmind.search.controller;

import java.time.LocalDateTime;
import java.util.List;

public record SearchItemResponse(
        Long id,
        String title,
        String summary,
        String eventType,
        String sourceType,
        String status,
        boolean aiPredicted,
        double confidence,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String location,
        String organizer,
        List<String> tags
) {
}

package cn.campusmind.feed.controller;

import java.time.LocalDateTime;
import java.util.List;

public record ActionItemResponse(
        Long id,
        Long informationItemId,
        String title,
        LocalDateTime dueAt,
        String originalUrl,
        String status,
        LocalDateTime createdAt,
        String sourceTitle,
        String sourceName,
        List<String> requiredMaterials
) {
}

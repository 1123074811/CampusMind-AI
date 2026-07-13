package cn.campusmind.feed.controller;

import java.time.LocalDateTime;

public record ReminderItemResponse(
        Long id,
        Long actionItemId,
        Long informationItemId,
        String actionTitle,
        String sourceTitle,
        String originalUrl,
        LocalDateTime remindAt,
        LocalDateTime dueAt,
        String status,
        LocalDateTime sentAt
) {
}

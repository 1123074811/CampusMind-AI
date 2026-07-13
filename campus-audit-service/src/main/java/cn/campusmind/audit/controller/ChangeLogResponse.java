package cn.campusmind.audit.controller;

import java.time.LocalDateTime;

public record ChangeLogResponse(
        Long id,
        Long itemId,
        String itemTitle,
        String sourceName,
        String oldContentHash,
        String newContentHash,
        String changedFields,
        LocalDateTime changedAt
) {
}

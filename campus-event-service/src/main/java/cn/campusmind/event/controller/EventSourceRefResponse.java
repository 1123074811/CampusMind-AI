package cn.campusmind.event.controller;

import java.time.LocalDateTime;

public record EventSourceRefResponse(
        Long id,
        Long sourceId,
        String rawDocId,
        String sourceUrl,
        String sourceTitle,
        String contentHash,
        LocalDateTime createdAt
) {
}

package cn.campusmind.feed.controller;

import java.time.LocalDateTime;

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
        LocalDateTime updatedAt
) {
}

package cn.campusmind.feed.controller;

import java.time.LocalDateTime;

public record InformationFeedItemResponse(
        Long id,
        String title,
        String sourceName,
        LocalDateTime publishTime,
        LocalDateTime fetchedAt,
        String readStatus,
        String itemStatus,
        String preview,
        String originalUrl
) {
}

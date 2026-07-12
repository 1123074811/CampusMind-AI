package cn.campusmind.feed.controller;

import java.time.LocalDateTime;

public record SubscriptionResponse(
        Long sourceId,
        String sourceName,
        String sourceType,
        Boolean enabled,
        LocalDateTime subscribedAt
) {
}

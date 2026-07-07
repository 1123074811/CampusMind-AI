package cn.campusmind.feed.controller;

import java.time.LocalDateTime;
import java.util.List;

public record FeedResponse(
        List<FeedItemResponse> items,
        LocalDateTime nextCursor,
        boolean hasMore
) {
}

package cn.campusmind.feed.controller;

import java.time.LocalDateTime;
import java.util.List;

public record InformationFeedResponse(
        List<InformationFeedItemResponse> items,
        LocalDateTime nextCursor,
        boolean hasMore
) {
}

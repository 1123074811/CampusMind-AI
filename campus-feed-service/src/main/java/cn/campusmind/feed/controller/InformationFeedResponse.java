package cn.campusmind.feed.controller;

import java.time.LocalDateTime;
import java.util.List;

public record InformationFeedResponse(
        List<InformationFeedItemResponse> items,
        LocalDateTime nextCursor,
        Long nextCursorId,
        Integer nextSubscriptionMatch,
        boolean hasMore
) {
    public InformationFeedResponse(List<InformationFeedItemResponse> items,
                                   LocalDateTime nextCursor,
                                   boolean hasMore) {
        this(items, nextCursor, null, null, hasMore);
    }
}

package cn.campusmind.event.controller;

import java.util.List;

public record EventSearchResponse(
        List<EventDetailResponse> items,
        long total,
        long page,
        long size,
        boolean hasMore
) {
}

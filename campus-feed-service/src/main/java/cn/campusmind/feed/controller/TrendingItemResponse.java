package cn.campusmind.feed.controller;

public record TrendingItemResponse(
        Long id,
        String rank,
        String title,
        String heatLabel
) {
}

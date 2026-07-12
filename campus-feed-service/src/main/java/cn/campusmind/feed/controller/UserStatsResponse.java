package cn.campusmind.feed.controller;

public record UserStatsResponse(
        long readCount,
        long favoriteCount,
        long subscriptionCount
) {
}

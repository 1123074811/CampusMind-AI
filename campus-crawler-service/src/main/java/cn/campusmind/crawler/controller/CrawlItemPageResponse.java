package cn.campusmind.crawler.controller;

import java.util.List;

public record CrawlItemPageResponse(
        List<CrawlItemResponse> items,
        long total,
        int page,
        int pageSize
) {
}

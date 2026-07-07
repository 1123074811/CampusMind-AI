package cn.campusmind.crawler.controller;

import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.crawler.application.CrawlOptions;
import cn.campusmind.crawler.application.CrawlSourceResult;
import cn.campusmind.crawler.application.CrawlerService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/crawler")
public class CrawlerController {

    private final CrawlerService crawlerService;

    public CrawlerController(CrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    @PostMapping("/sources/{sourceId}/crawl")
    public ApiResponse<CrawlSourceResult> crawlSource(
            @PathVariable Long sourceId,
            @RequestParam(defaultValue = "30") Integer days,
            @RequestParam(defaultValue = "50") Integer maxItems
    ) {
        return ApiResponse.ok(crawlerService.crawlSource(sourceId, new CrawlOptions(days, maxItems)));
    }
}

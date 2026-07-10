package cn.campusmind.crawler.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CrawlerScheduler {

    private final CrawlerService crawlerService;

    public CrawlerScheduler(CrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    @Scheduled(cron = "${campus.crawler.daily-cron}", zone = "Asia/Shanghai")
    public void crawlPublicSourcesDaily() {
        crawlerService.crawlEnabledSources(new CrawlOptions(365, null, "AUTO"));
    }

    @Scheduled(initialDelayString = "${campus.ai.backfill-initial-delay-ms:10000}",
            fixedDelayString = "${campus.ai.backfill-delay-ms:60000}")
    public void processPendingAiCards() {
        crawlerService.processPendingAiCards(20);
    }
}

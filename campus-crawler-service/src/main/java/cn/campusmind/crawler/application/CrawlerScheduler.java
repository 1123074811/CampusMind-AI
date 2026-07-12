package cn.campusmind.crawler.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CrawlerScheduler {

    private final CrawlerService crawlerService;

    public CrawlerScheduler(CrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    @Scheduled(cron = "${campus.crawler.hourly-cron}", zone = "Asia/Shanghai")
    public void crawlPublicSourcesHourly() {
        crawlerService.crawlEnabledSources(new CrawlOptions(365, null, "AUTO"));
    }

    @Scheduled(initialDelayString = "${campus.crawler.startup-delay-ms:15000}",
            fixedDelayString = "${campus.crawler.startup-repeat-delay-ms:315360000000}")
    public void crawlPublicSourcesOnStartup() {
        crawlerService.crawlEnabledSources(new CrawlOptions(365, null, "AUTO_STARTUP"));
    }

    @Scheduled(initialDelayString = "${campus.ai.backfill-initial-delay-ms:10000}",
            fixedDelayString = "${campus.ai.backfill-delay-ms:60000}")
    public void processPendingAiCards() {
        crawlerService.processPendingAiCards(20);
    }
}

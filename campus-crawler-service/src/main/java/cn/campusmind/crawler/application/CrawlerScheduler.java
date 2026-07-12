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

    /**
     * 服务启动后一次性回填向量库：将已有 AI_SUCCESS 事件推送到向量库。
     * fixedDelay 设置极大值避免重复执行。
     */
    @Scheduled(initialDelayString = "${campus.ai.vector-backfill-delay-ms:20000}",
            fixedDelayString = "${campus.ai.vector-backfill-interval-ms:315360000000}")
    public void backfillVectorStore() {
        int pushed = crawlerService.backfillVectorStore(200);
        if (pushed > 0) {
            org.slf4j.LoggerFactory.getLogger(CrawlerScheduler.class)
                    .info("向量库回填完成，推送 {} 条事件", pushed);
        }
    }
}

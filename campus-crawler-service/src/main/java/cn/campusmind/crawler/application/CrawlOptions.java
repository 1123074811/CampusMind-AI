package cn.campusmind.crawler.application;

public record CrawlOptions(
        Integer days,
        Integer maxItems
) {

    public int normalizedDays() {
        if (days == null || days <= 0) {
            return 30;
        }
        return Math.min(days, 365);
    }

    public int normalizedMaxItems() {
        if (maxItems == null || maxItems <= 0) {
            return 50;
        }
        return Math.min(maxItems, 200);
    }
}

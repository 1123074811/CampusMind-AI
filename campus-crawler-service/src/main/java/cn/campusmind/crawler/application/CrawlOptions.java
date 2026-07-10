package cn.campusmind.crawler.application;

public record CrawlOptions(
        Integer days,
        Integer maxItems,
        String trigger
) {

    public int normalizedDays() {
        if (days == null || days <= 0) {
            return 365;
        }
        return Math.min(days, 365);
    }

    public int normalizedMaxItems() {
        return Integer.MAX_VALUE;
    }

    public String normalizedTrigger() {
        return "AUTO".equalsIgnoreCase(trigger) ? "AUTO" : "MANUAL";
    }
}

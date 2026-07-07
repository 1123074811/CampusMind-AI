package cn.campusmind.crawler.application;

public record CrawledLink(
        String title,
        String url,
        String dateText,
        String summary
) {
}

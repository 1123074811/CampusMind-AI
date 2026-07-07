package cn.campusmind.crawler.application;

import java.time.LocalDate;

public record CrawledLink(
        String title,
        String url,
        String dateText,
        LocalDate publishedDate,
        String summary
) {
}

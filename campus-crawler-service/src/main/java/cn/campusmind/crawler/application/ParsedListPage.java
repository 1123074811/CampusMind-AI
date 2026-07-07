package cn.campusmind.crawler.application;

import java.util.List;

public record ParsedListPage(
        String parserVersion,
        List<CrawledLink> links
) {
}

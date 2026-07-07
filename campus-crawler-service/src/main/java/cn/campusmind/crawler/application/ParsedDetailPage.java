package cn.campusmind.crawler.application;

public record ParsedDetailPage(
        String title,
        String content,
        String status,
        String error
) {
}

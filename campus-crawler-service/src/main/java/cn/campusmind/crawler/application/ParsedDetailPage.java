package cn.campusmind.crawler.application;

public record ParsedDetailPage(
        String title,
        String content,
        String publishedAtText,
        String status,
        String error
) {
}

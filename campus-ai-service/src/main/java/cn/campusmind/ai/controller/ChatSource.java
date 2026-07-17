package cn.campusmind.ai.controller;

/**
 * AI 回答引用的校园信息来源。
 */
public record ChatSource(
        Long businessId,
        String docId,
        String title,
        String sourceName,
        String sourceType,
        String publishedAt,
        String originalUrl,
        double score
) {
}

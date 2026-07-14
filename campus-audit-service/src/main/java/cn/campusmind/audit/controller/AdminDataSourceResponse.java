package cn.campusmind.audit.controller;

public record AdminDataSourceResponse(
        Long id,
        String name,
        String sourceUrl,
        String channel,
        String robotsUrl,
        Integer crawlIntervalSeconds,
        String parserType,
        String selectorConfig,
        boolean enabled,
        String status,
        String lastSync,
        int successRate,
        long pending
) {
}

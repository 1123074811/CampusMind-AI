package cn.campusmind.audit.controller;

public record MetricsResponse(
        long reviewCount,
        long urgentCount,
        int sourceSuccessRate,
        long sourcesNeedAuth,
        long vectorPending
) {
}

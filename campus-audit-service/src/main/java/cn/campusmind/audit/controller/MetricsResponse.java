package cn.campusmind.audit.controller;

public record MetricsResponse(
        long reviewCount,
        long urgentCount,
        int sourceSuccessRate,
        long sourcesNeedAuth,
        long vectorPending,
        long aiPendingCount,
        long aiProcessingCount,
        long aiSuccessCount,
        long aiFailedCount
) {
    /** 向后兼容旧构造器 */
    public MetricsResponse(long reviewCount, long urgentCount, int sourceSuccessRate, long sourcesNeedAuth, long vectorPending) {
        this(reviewCount, urgentCount, sourceSuccessRate, sourcesNeedAuth, vectorPending, 0, 0, 0, 0);
    }
}

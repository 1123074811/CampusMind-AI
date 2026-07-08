package cn.campusmind.search.application;

import java.util.List;

/**
 * 决策 Agent 检索计划，与 campus-ai-service 的 SearchPlan 结构对齐。
 */
public record DecisionPlan(
        String intent,
        List<String> eventTypes,
        String timeRange,
        List<String> scopes,
        boolean useVectorSearch,
        boolean usePersonalProfile,
        int topK
) {
}

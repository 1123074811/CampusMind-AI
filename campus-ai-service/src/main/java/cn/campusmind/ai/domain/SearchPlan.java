package cn.campusmind.ai.domain;

import java.util.List;

public record SearchPlan(
        String intent,
        List<String> eventTypes,
        String timeRange,
        List<String> scopes,
        boolean useVectorSearch,
        boolean usePersonalProfile,
        int topK
) {
}

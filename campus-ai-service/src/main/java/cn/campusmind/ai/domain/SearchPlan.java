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
    private static final List<String> INTENTS = List.of(
            "CASUAL_CHAT", "FEED_QUERY", "SEMANTIC_SEARCH", "PERSONAL_SCHEDULE", "QA_EXPLAIN", "IMPORT_HELP");
    private static final List<String> TIME_RANGES = List.of("TODAY", "TOMORROW", "THIS_WEEK", "RECENT", "ANY");

    public SearchPlan {
        intent = INTENTS.contains(intent) ? intent : "SEMANTIC_SEARCH";
        eventTypes = eventTypes == null ? List.of() : List.copyOf(eventTypes);
        timeRange = TIME_RANGES.contains(timeRange) ? timeRange : "ANY";
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        topK = Math.min(Math.max(topK, 1), 20);
    }
}

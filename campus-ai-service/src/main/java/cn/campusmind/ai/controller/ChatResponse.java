package cn.campusmind.ai.controller;

import cn.campusmind.ai.domain.SearchPlan;

import java.util.List;

public record ChatResponse(
        String sessionId,
        String answer,
        SearchPlan plan,
        List<ChatSource> sources,
        boolean grounded,
        String retrievalMode
) {
    public ChatResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}

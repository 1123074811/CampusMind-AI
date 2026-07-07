package cn.campusmind.ai.controller;

import cn.campusmind.ai.domain.SearchPlan;

public record ChatResponse(
        String sessionId,
        String answer,
        SearchPlan plan
) {
}

package cn.campusmind.search.controller;

import cn.campusmind.search.application.DecisionPlan;

import java.util.List;

public record SearchResponse(
        String intent,
        DecisionPlan plan,
        List<SearchItemResponse> items,
        int total,
        String message
) {
}

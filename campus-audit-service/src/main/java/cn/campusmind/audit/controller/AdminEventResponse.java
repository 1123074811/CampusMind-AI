package cn.campusmind.audit.controller;

import java.util.List;

public record AdminEventResponse(
        Long id,
        String title,
        String source,
        String type,
        String status,
        double confidence,
        String location,
        String startTime,
        String scope,
        String summary,
        String risk,
        List<String> tags
) {
}

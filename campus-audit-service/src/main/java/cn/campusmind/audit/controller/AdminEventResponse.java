package cn.campusmind.audit.controller;

import java.util.List;

public record AdminEventResponse(
        Long id,
        String title,
        String source,
        String sourceUrl,
        String type,
        String status,
        double confidence,
        String location,
        String startTime,
        String endTime,
        String organizer,
        String scope,
        String summary,
        String risk,
        List<String> tags
) {
}

package cn.campusmind.audit.controller;

import java.util.List;
import java.util.Map;

public record AdminEventResponse(
        Long id,
        String title,
        String source,
        String sourceUrl,
        String type,
        String status,
        String location,
        String startTime,
        String endTime,
        String organizer,
        String scope,
        String summary,
        String risk,
        List<String> tags,
        String aiStatus,
        Boolean aiNeedReview,
        Map<String, Object> aiCard
) {
}

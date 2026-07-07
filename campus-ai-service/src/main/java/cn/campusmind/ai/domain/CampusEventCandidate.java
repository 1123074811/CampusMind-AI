package cn.campusmind.ai.domain;

import java.util.List;

public record CampusEventCandidate(
        String title,
        String eventType,
        String summary,
        String startTime,
        String endTime,
        String location,
        String organizer,
        List<String> targetScopes,
        List<String> tags,
        double confidence,
        boolean needHumanReview,
        String reason
) {
}

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
        boolean needHumanReview,
        String reason,
        Long originalItemId,
        String originalUrl,
        List<String> keyDates,
        List<String> requiredActions,
        String registrationStartTime,
        String registrationDeadline,
        String eventDuration,
        List<String> requiredMaterials,
        String registrationUrl,
        String participationMethod,
        String teamRequirement,
        List<String> attachments
) {

    public CampusEventCandidate {
        targetScopes = targetScopes == null ? List.of() : List.copyOf(targetScopes);
        tags = tags == null ? List.of() : List.copyOf(tags);
        keyDates = keyDates == null ? List.of() : List.copyOf(keyDates);
        requiredActions = requiredActions == null ? List.of() : List.copyOf(requiredActions);
        requiredMaterials = requiredMaterials == null ? List.of() : List.copyOf(requiredMaterials);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public CampusEventCandidate withOriginal(Long itemId, String url) {
        return new CampusEventCandidate(title, eventType, summary, startTime, endTime, location, organizer,
                targetScopes, tags, needHumanReview, reason, itemId, url, keyDates,
                requiredActions, registrationStartTime, registrationDeadline, eventDuration,
                requiredMaterials, registrationUrl, participationMethod, teamRequirement, attachments);
    }
}

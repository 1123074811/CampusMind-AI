package cn.campusmind.importing.application;

import java.util.List;

/**
 * AI 认知 Agent 抽取结果，与 campus-ai-service 的 CampusEventCandidate 结构对齐。
 */
public record CognitionResult(
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

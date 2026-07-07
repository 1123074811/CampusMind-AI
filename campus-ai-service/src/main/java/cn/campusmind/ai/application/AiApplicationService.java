package cn.campusmind.ai.application;

import cn.campusmind.ai.agent.CognitionAgent;
import cn.campusmind.ai.agent.DecisionAgent;
import cn.campusmind.ai.controller.ChatResponse;
import cn.campusmind.ai.controller.EventVectorTextRequest;
import cn.campusmind.ai.domain.CampusEventCandidate;
import cn.campusmind.ai.domain.SearchPlan;
import cn.campusmind.ai.domain.VectorText;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AiApplicationService {

    private final CognitionAgent cognitionAgent;
    private final DecisionAgent decisionAgent;

    public AiApplicationService(CognitionAgent cognitionAgent, DecisionAgent decisionAgent) {
        this.cognitionAgent = cognitionAgent;
        this.decisionAgent = decisionAgent;
    }

    public CampusEventCandidate extractEvent(String sourceType, String plainText) {
        return cognitionAgent.extract(sourceType, plainText);
    }

    public SearchPlan planSearch(String query, List<String> userScopes, boolean usePersonalProfile) {
        return decisionAgent.plan(query, userScopes, usePersonalProfile);
    }

    public VectorText buildVectorText(EventVectorTextRequest request) {
        String scopes = join(request.targetScopes());
        String tags = join(request.tags());
        String text = Stream.of(
                        "标题：" + request.title(),
                        line("类型", request.eventType()),
                        line("摘要", request.summary()),
                        line("时间", joinTime(request.startTime(), request.endTime())),
                        line("地点", request.location()),
                        line("范围", scopes),
                        line("标签", tags),
                        line("正文", request.content())
                )
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining("\n"));
        return new VectorText(text);
    }

    public ChatResponse chat(String sessionId, String message, boolean usePersonalProfile) {
        SearchPlan plan = planSearch(message, List.of(), usePersonalProfile);
        String answer = switch (plan.intent()) {
            case "IMPORT_HELP" -> "请在导入入口粘贴雨课堂 JSON 或一次性 Cookie，系统会在后台解析并生成待审核事件。";
            case "PERSONAL_SCHEDULE" -> "我已按个人日程意图生成检索计划，后续会结合你的画像、课程和作业事件返回结果。";
            case "QA_EXPLAIN" -> "我已按问答解释意图生成检索计划，后续会结合相似事件与原文进行说明。";
            default -> "我已生成校园事件检索计划，可用于查询信息流、语义检索或个性化推荐。";
        };
        String resolvedSessionId = sessionId == null || sessionId.isBlank() ? UUID.randomUUID().toString() : sessionId;
        return new ChatResponse(resolvedSessionId, answer, plan);
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join("、", values);
    }

    private static String joinTime(String startTime, String endTime) {
        if ((startTime == null || startTime.isBlank()) && (endTime == null || endTime.isBlank())) {
            return null;
        }
        if (endTime == null || endTime.isBlank()) {
            return startTime;
        }
        return startTime + " - " + endTime;
    }

    private static String line(String label, String value) {
        return value == null || value.isBlank() ? null : label + "：" + value;
    }
}

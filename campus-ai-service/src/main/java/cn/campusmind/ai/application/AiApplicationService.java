package cn.campusmind.ai.application;

import cn.campusmind.ai.agent.CognitionAgent;
import cn.campusmind.ai.agent.DecisionAgent;
import cn.campusmind.ai.config.RuntimeAiConfig;
import cn.campusmind.ai.controller.ChatResponse;
import cn.campusmind.ai.controller.EventVectorTextRequest;
import cn.campusmind.ai.controller.VectorStoreRequest;
import cn.campusmind.ai.controller.VectorStoreResponse;
import cn.campusmind.ai.controller.VectorSearchResponse;
import cn.campusmind.ai.domain.CampusEventCandidate;
import cn.campusmind.ai.domain.SearchPlan;
import cn.campusmind.ai.domain.VectorSearchHit;
import cn.campusmind.ai.domain.VectorText;
import cn.campusmind.ai.vector.EventVectorStore;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AiApplicationService {

    private static final int RAG_TOP_K = 5;

    private final RuntimeAiConfig runtimeAiConfig;
    private final EventVectorStore eventVectorStore;

    public AiApplicationService(RuntimeAiConfig runtimeAiConfig,
                                EventVectorStore eventVectorStore) {
        this.runtimeAiConfig = runtimeAiConfig;
        this.eventVectorStore = eventVectorStore;
    }

    public CampusEventCandidate extractEvent(String sourceType, String plainText, Long originalItemId, String originalUrl) {
        return runtimeAiConfig.resolveCognitionAgent().extract(sourceType, plainText).withOriginal(originalItemId, originalUrl);
    }

    public SearchPlan planSearch(String query, List<String> userScopes, boolean usePersonalProfile) {
        return runtimeAiConfig.resolveDecisionAgent().plan(query, userScopes, usePersonalProfile);
    }

    public VectorText buildVectorText(EventVectorTextRequest request) {
        return new VectorText(buildEventText(request));
    }

    public VectorStoreResponse storeVector(VectorStoreRequest request) {
        EventVectorTextRequest event = request.event();
        VectorText vectorText = buildVectorText(event);
        Map<String, Object> metadata = new HashMap<>();
        if (event.eventType() != null) {
            metadata.put("eventType", event.eventType());
        }
        if (event.targetScopes() != null && !event.targetScopes().isEmpty()) {
            metadata.put("targetScopes", String.join(",", event.targetScopes()));
        }
        String docId = eventVectorStore.store(request.docId(), vectorText.text(), metadata);
        return new VectorStoreResponse(docId, vectorText.text());
    }

    public VectorSearchResponse searchVector(String query, int topK) {
        List<VectorSearchHit> hits = eventVectorStore.search(query, topK);
        return new VectorSearchResponse(hits, hits.size());
    }

    public ChatResponse chat(String sessionId, String message, boolean usePersonalProfile) {
        SearchPlan plan = planSearch(message, List.of(), usePersonalProfile);
        String answer = switch (plan.intent()) {
            case "CASUAL_CHAT" -> casualChatReply(message);
            case "IMPORT_HELP" -> "请在导入入口粘贴雨课堂 JSON 或一次性 Cookie，系统会在后台解析并生成待审核事件。";
            case "PERSONAL_SCHEDULE" -> "我已按个人日程意图生成检索计划，后续会结合你的画像、课程和作业事件返回结果。";
            case "QA_EXPLAIN", "SEMANTIC_SEARCH" -> ragAnswer(message, plan);
            default -> "我已生成校园事件检索计划，可用于查询信息流、语义检索或个性化推荐。";
        };
        String resolvedSessionId = sessionId == null || sessionId.isBlank() ? UUID.randomUUID().toString() : sessionId;
        return new ChatResponse(resolvedSessionId, answer, plan);
    }

    /**
     * 检索取向量库，把命中事件作为上下文拼出 RAG 答案。命中为空时回退到固定话术。
     */
    private String ragAnswer(String message, SearchPlan plan) {
        List<VectorSearchHit> hits = eventVectorStore.search(message, RAG_TOP_K);
        if (hits.isEmpty()) {
            // 向量库无数据时，如果有 LLM 可用，直接用 LLM 回答
            ChatModel model = runtimeAiConfig.resolveChatModel();
            if (model != null) {
                try {
                    return callLlm(model, message);
                } catch (Exception ex) {
                    // LLM 调用失败，回退到固定话术
                }
            }
            return "QA_EXPLAIN".equals(plan.intent())
                    ? "我已按问答解释意图生成检索计划，暂未在向量库召回相关事件，后续可结合原文进行说明。"
                    : "我已按语义检索意图生成检索计划，暂未在向量库召回相关事件。";
        }
        String context = hits.stream()
                .map(VectorSearchHit::text)
                .map(text -> "- " + text.replace("\n", " "))
                .collect(Collectors.joining("\n"));
        return "已从向量库召回 " + hits.size() + " 条相关事件作为参考：\n" + context;
    }

    /**
     * 闲聊场景：优先用 LLM 自然回复，LLM 不可用时返回友好的固定回复。
     */
    private String casualChatReply(String message) {
        ChatModel model = runtimeAiConfig.resolveChatModel();
        if (model != null) {
            try {
                return callLlm(model, message);
            } catch (Exception ignored) {
                // 回退到固定回复
            }
        }
        return "你好！我是 CampusMind AI 校园助手 \uD83C\uDF93\n\n"
                + "我可以帮你：\n"
                + "• 查看今天的重要通知和校园事件\n"
                + "• 搜索讲座、竞赛、活动等信息\n"
                + "• 检查课表和作业安排\n"
                + "• 导入雨课堂等外部数据\n\n"
                + "试试问我「今天有什么重要通知」或「帮我找最近的讲座」吧！";
    }

    /**
     * 调用 LLM 生成回复。
     */
    private String callLlm(ChatModel model, String message) {
        Prompt prompt = new Prompt(
                new SystemMessage("你是 CampusMind 校园 AI 助手，基于校园多源信息为学生提供智能问答服务。"
                        + "请用友好、简洁、专业的语气回复。如果用户只是打招呼，请简短回应并介绍你能提供的帮助。"),
                new UserMessage(message)
        );
        return model.call(prompt).getResult().getOutput().getText();
    }

    private static String buildEventText(EventVectorTextRequest request) {
        String scopes = join(request.targetScopes());
        String tags = join(request.tags());
        return Stream.of(
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

package cn.campusmind.ai.agent;

import cn.campusmind.ai.config.AiModeProperties;
import cn.campusmind.ai.domain.SearchPlan;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmDecisionAgentTest {

    private final AiModeProperties properties = new AiModeProperties(AiModeProperties.Mode.LLM, "gpt-4o-mini", "llm-v1");

    @Test
    void shouldParseLlmStructuredPlan() {
        String json = """
                {
                  "intent": "SEMANTIC_SEARCH",
                  "eventTypes": ["LECTURE"],
                  "timeRange": "RECENT",
                  "scopes": ["软件学院"],
                  "useVectorSearch": true,
                  "usePersonalProfile": false,
                  "topK": 10
                }
                """;
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(json));

        LlmDecisionAgent agent = new LlmDecisionAgent(chatModel, properties);
        SearchPlan plan = agent.plan("最近有没有 AI 相关讲座", List.of("软件学院"), false);

        assertEquals("SEMANTIC_SEARCH", plan.intent());
        assertEquals("RECENT", plan.timeRange());
        assertTrue(plan.useVectorSearch());
    }

    @Test
    void shouldFallbackToRulesWhenLlmOutputUnparseable() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("???"));

        LlmDecisionAgent agent = new LlmDecisionAgent(chatModel, properties);
        SearchPlan plan = agent.plan("最近有没有 AI 相关讲座", List.of("软件学院"), false);

        // 降级规则识别：含"AI"+"最近" → SEMANTIC_SEARCH + RECENT
        assertEquals("SEMANTIC_SEARCH", plan.intent());
        assertEquals("RECENT", plan.timeRange());
    }

    @Test
    void shouldFallbackToRulesWhenLlmThrows() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("model unavailable"));

        LlmDecisionAgent agent = new LlmDecisionAgent(chatModel, properties);
        SearchPlan plan = agent.plan("我本周有哪些作业", List.of(), true);

        // 降级规则识别：含"我"+"本周"+"作业" → PERSONAL_SCHEDULE
        assertEquals("PERSONAL_SCHEDULE", plan.intent());
    }

    private static ChatResponse chatResponse(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}

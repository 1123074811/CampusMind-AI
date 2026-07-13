package cn.campusmind.ai.agent;

import cn.campusmind.ai.config.AiModeProperties;
import cn.campusmind.ai.domain.CampusEventCandidate;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmCognitionAgentTest {

    private static final String ISSUER_TEXT = "人工智能主题讲座通知\n时间：2026年7月8日 19:00\n地点：图书馆报告厅\n主办：软件学院";

    private final AiModeProperties properties = new AiModeProperties(AiModeProperties.Mode.LLM, "gpt-4o-mini", "llm-v1");

    @Test
    void shouldParseLlmStructuredOutput() {
        String json = """
                {
                  "title": "人工智能主题讲座通知",
                  "eventType": "LECTURE",
                  "summary": "软件学院AI讲座",
                  "startTime": "2026-07-08T19:00:00+08:00",
                  "endTime": null,
                  "location": "图书馆报告厅",
                  "organizer": "软件学院",
                  "targetScopes": ["软件学院"],
                  "tags": ["LECTURE", "AI"],
                  "needHumanReview": false,
                  "reason": "LLM抽取字段完整"
                }
                """;
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(json));

        LlmCognitionAgent agent = new LlmCognitionAgent(chatModel, properties);
        CampusEventCandidate candidate = agent.extract("USER_TEXT", ISSUER_TEXT);

        assertEquals("人工智能主题讲座通知", candidate.title());
        assertEquals("LECTURE", candidate.eventType());
        assertEquals("图书馆报告厅", candidate.location());
        var prompt = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertTrue(prompt.getValue().getInstructions().get(0).getText().contains("不得截取原文开头"));
    }

    @Test
    void shouldFallbackToRulesWhenLlmOutputUnparseable() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("not a json"));

        LlmCognitionAgent agent = new LlmCognitionAgent(chatModel, properties);
        CampusEventCandidate candidate = agent.extract("USER_TEXT", ISSUER_TEXT);

        // 降级到规则抽取，规则版能识别讲座类型与地点
        assertEquals("LECTURE", candidate.eventType());
        assertEquals("图书馆报告厅", candidate.location());
    }

    @Test
    void shouldFallbackToRulesWhenLlmThrows() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("model unavailable"));

        LlmCognitionAgent agent = new LlmCognitionAgent(chatModel, properties);
        CampusEventCandidate candidate = agent.extract("USER_TEXT", ISSUER_TEXT);

        assertNotNull(candidate);
        assertEquals("LECTURE", candidate.eventType());
    }

    private static ChatResponse chatResponse(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}

package cn.campusmind.ai.application;

import cn.campusmind.ai.config.RuntimeAiConfig;
import cn.campusmind.ai.controller.ChatResponse;
import cn.campusmind.ai.domain.SearchPlan;
import cn.campusmind.ai.domain.VectorSearchHit;
import cn.campusmind.ai.vector.EventVectorStore;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiApplicationServiceTest {

    @Test
    void chatAppliesPlanAndEvidenceGates() {
        RuntimeAiConfig config = mock(RuntimeAiConfig.class);
        EventVectorStore store = mock(EventVectorStore.class);
        SearchPlan plan = new SearchPlan(
                "FEED_QUERY", List.of("LECTURE"), "TODAY", List.of("软件学院"), true, true, 1);
        when(config.resolveDecisionAgent()).thenReturn((query, scopes, personalized) -> plan);
        when(store.search("今天的讲座", 3, 7L)).thenReturn(List.of(
                hit("valid", 0.9, "LECTURE", "ACTIVE", LocalDate.now().toString(), "软件学院"),
                hit("wrong-type", 0.99, "EXAM", "ACTIVE", LocalDate.now().toString(), "软件学院"),
                hit("offline", 0.98, "LECTURE", "OFFLINE", LocalDate.now().toString(), "软件学院"),
                hit("old", 0.97, "LECTURE", "ACTIVE", LocalDate.now().minusDays(1).toString(), "软件学院"),
                hit("low-score", 0.2, "LECTURE", "ACTIVE", LocalDate.now().toString(), "软件学院")));

        ChatResponse response = new AiApplicationService(config, store, 0.5)
                .chat("session", "今天的讲座", List.of("软件学院"), true, 7L);

        assertThat(response.grounded()).isTrue();
        assertThat(response.retrievalMode()).isEqualTo("VECTOR_RULES");
        assertThat(response.sources()).extracting(source -> source.docId()).containsExactly("valid");
        assertThat(response.answer()).contains("有效讲座");
    }

    @Test
    void chatDoesNotCallLlmWhenNoEvidencePassesGate() {
        RuntimeAiConfig config = mock(RuntimeAiConfig.class);
        EventVectorStore store = mock(EventVectorStore.class);
        ChatModel model = mock(ChatModel.class);
        SearchPlan plan = new SearchPlan("QA_EXPLAIN", List.of(), "ANY", List.of(), true, false, 5);
        when(config.resolveDecisionAgent()).thenReturn((query, scopes, personalized) -> plan);
        when(config.resolveChatModel()).thenReturn(model);
        when(store.search("校历是什么", 15, 9L)).thenReturn(List.of());

        ChatResponse response = new AiApplicationService(config, store, 0.5)
                .chat(null, "校历是什么", List.of(), false, 9L);

        assertThat(response.grounded()).isFalse();
        assertThat(response.retrievalMode()).isEqualTo("NONE");
        assertThat(response.sources()).isEmpty();
        verifyNoInteractions(model);
    }

    @Test
    void chatUsesPreviousTurnToResolveFollowUpWithinSameUserSession() {
        RuntimeAiConfig config = mock(RuntimeAiConfig.class);
        EventVectorStore store = mock(EventVectorStore.class);
        when(config.resolveDecisionAgent()).thenReturn((query, scopes, personalized) ->
                query.contains("讲座")
                        ? new SearchPlan("FEED_QUERY", List.of("LECTURE"), "RECENT", List.of(), false, false, 5)
                        : new SearchPlan("SEMANTIC_SEARCH", List.of(), "TOMORROW", List.of(), true, false, 5));
        when(store.search("最近的讲座", 15, 7L)).thenReturn(List.of());
        when(store.search("最近的讲座\n后续问题：那明天呢", 15, 7L)).thenReturn(List.of(
                hit("tomorrow", 0.9, "LECTURE", "ACTIVE", LocalDate.now().plusDays(1).toString(), "")));
        AiApplicationService service = new AiApplicationService(config, store, 0.5);

        service.chat("context-session", "最近的讲座", List.of(), false, 7L);
        ChatResponse followUp = service.chat("context-session", "那明天呢", List.of(), false, 7L);
        ChatResponse otherUser = service.chat("context-session", "那明天呢", List.of(), false, 8L);

        assertThat(followUp.plan().intent()).isEqualTo("FEED_QUERY");
        assertThat(followUp.plan().eventTypes()).containsExactly("LECTURE");
        assertThat(followUp.plan().timeRange()).isEqualTo("TOMORROW");
        assertThat(followUp.sources()).extracting(source -> source.docId()).containsExactly("tomorrow");
        assertThat(otherUser.plan().intent()).isEqualTo("SEMANTIC_SEARCH");
        assertThat(otherUser.plan().eventTypes()).isEmpty();
    }

    private static VectorSearchHit hit(String id, double score, String type, String status,
                                       String startTime, String scopes) {
        return new VectorSearchHit(id, "标题：有效讲座\n类型：" + type,
                score, Map.of(
                "title", "有效讲座",
                "eventType", type,
                "status", status,
                "startTime", startTime,
                "targetScopes", scopes,
                "originalUrl", "https://example.edu/notice/1"));
    }
}

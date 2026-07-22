package cn.campusmind.ai.application;

import cn.campusmind.ai.config.RuntimeAiConfig;
import cn.campusmind.ai.controller.ChatResponse;
import cn.campusmind.ai.domain.SearchPlan;
import cn.campusmind.ai.domain.VectorSearchHit;
import cn.campusmind.ai.feign.UserProfileMemoryClient;
import cn.campusmind.ai.feign.UserProfileMemoryClient.LearnProfileTagsRequest;
import cn.campusmind.ai.feign.UserProfileMemoryClient.ProfileMemory;
import cn.campusmind.ai.vector.EventVectorStore;
import cn.campusmind.ai.tool.WebSearchTool;
import cn.campusmind.ai.tool.WebSearchTool.WebSearchResponse;
import cn.campusmind.ai.tool.WebSearchTool.WebSearchResult;
import cn.campusmind.common.web.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
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

        ChatResponse response = service(config, store, mock(UserProfileMemoryClient.class))
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

        ChatResponse response = service(config, store, mock(UserProfileMemoryClient.class))
                .chat(null, "校历是什么", List.of(), false, 9L);

        assertThat(response.grounded()).isFalse();
        assertThat(response.retrievalMode()).isEqualTo("NONE");
        assertThat(response.sources()).isEmpty();
        verifyNoInteractions(model);
    }

    @Test
    void dailyBriefingFallsBackWhenLlmReturnsFailureText() {
        RuntimeAiConfig config = mock(RuntimeAiConfig.class);
        EventVectorStore store = mock(EventVectorStore.class);
        ChatModel model = mock(ChatModel.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(config.resolveChatModel()).thenReturn(model);
        when(model.call(org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class))
                .getResult().getOutput().getText()).thenReturn("抱歉，根据现有检索内容，我无法生成今日 AI 日报摘要。");

        var response = service(config, store, mock(UserProfileMemoryClient.class)).dailyBriefing(List.of(
                new cn.campusmind.ai.controller.AiController.DailyBriefingItem(
                        "软件工程", "课程", "COURSE", "雨课堂导入", null),
                new cn.campusmind.ai.controller.AiController.DailyBriefingItem(
                        "有效讲座", "今晚举行", "NOTICE", "校园通知", LocalDate.now().toString())));

        assertThat(response.summary()).isEqualTo("今日有 1 条信息值得关注：有效讲座。");
        verifyNoInteractions(store);
    }

    @Test
    void chatUsesWebSearchWhenInternalEvidenceIsMissing() {
        RuntimeAiConfig config = mock(RuntimeAiConfig.class);
        EventVectorStore store = mock(EventVectorStore.class);
        WebSearchTool webSearchTool = mock(WebSearchTool.class);
        SearchPlan plan = new SearchPlan("QA_EXPLAIN", List.of(), "ANY", List.of(), true, false, 5);
        when(config.resolveDecisionAgent()).thenReturn((query, scopes, personalized) -> plan);
        when(store.search("新疆大学历年分数线", 15, 9L)).thenReturn(List.of());
        when(webSearchTool.enabled()).thenReturn(true);
        when(webSearchTool.searchWeb("新疆大学历年分数线")).thenReturn(new WebSearchResponse(
                "新疆大学历年分数线",
                List.of(new WebSearchResult("新疆大学招生网", "https://zsw.xju.edu.cn/score", "历年录取分数", 0.9))));

        ChatResponse response = service(config, store, mock(UserProfileMemoryClient.class), webSearchTool)
                .chat(null, "新疆大学历年分数线", List.of(), false, 9L);

        assertThat(response.grounded()).isTrue();
        assertThat(response.retrievalMode()).isEqualTo("WEB_RULES");
        assertThat(response.sources()).extracting(source -> source.originalUrl())
                .containsExactly("https://zsw.xju.edu.cn/score");
        assertThat(response.answer()).contains("新疆大学招生网");
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
        AiApplicationService service = service(config, store, mock(UserProfileMemoryClient.class));

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

    @Test
    void chatLearnsOnlyExplicitPreferencesIntoLongTermProfile() {
        RuntimeAiConfig config = mock(RuntimeAiConfig.class);
        EventVectorStore store = mock(EventVectorStore.class);
        UserProfileMemoryClient profileClient = mock(UserProfileMemoryClient.class);
        when(config.resolveDecisionAgent()).thenReturn((query, scopes, personalized) ->
                new SearchPlan("CASUAL_CHAT", List.of(), "ANY", scopes, false, personalized, 5));
        when(profileClient.getProfile()).thenReturn(ApiResponse.ok(ProfileMemory.empty()));

        service(config, store, profileClient)
                .chat("profile-session", "记住我喜欢讲座和竞赛", List.of(), true, 7L);
        service(config, store, profileClient)
                .chat("profile-session-2", "记住我不喜欢讲座", List.of(), true, 7L);

        verify(profileClient).learn(new LearnProfileTagsRequest(List.of("课程学术", "竞赛比赛")));
        verify(profileClient, times(1)).learn(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void followUpKeepsAuthorizedProfileContext() {
        RuntimeAiConfig config = mock(RuntimeAiConfig.class);
        EventVectorStore store = mock(EventVectorStore.class);
        UserProfileMemoryClient profileClient = mock(UserProfileMemoryClient.class);
        when(config.resolveDecisionAgent()).thenReturn((query, scopes, personalized) ->
                new SearchPlan("PERSONAL_SCHEDULE", List.of(),
                        query.contains("明天") ? "TOMORROW" : "ANY",
                        scopes, true, personalized, 5));
        when(profileClient.getProfile()).thenReturn(
                ApiResponse.ok(new ProfileMemory(List.of("课程学术"), 0.5)));
        when(store.search(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.eq(7L)))
                .thenReturn(List.of());
        AiApplicationService service = service(config, store, profileClient);

        service.chat("personal-session", "我的课表", List.of(), true, 7L);
        ChatResponse followUp = service.chat("personal-session", "那明天呢", List.of(), false, 7L);

        assertThat(followUp.plan().usePersonalProfile()).isTrue();
        assertThat(followUp.plan().scopes()).contains("课程学术");
        verify(profileClient, times(2)).getProfile();
    }

    private static AiApplicationService service(RuntimeAiConfig config, EventVectorStore store,
                                                 UserProfileMemoryClient profileClient) {
        return service(config, store, profileClient, mock(WebSearchTool.class));
    }

    private static AiApplicationService service(RuntimeAiConfig config, EventVectorStore store,
                                                 UserProfileMemoryClient profileClient,
                                                 WebSearchTool webSearchTool) {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(null);
        ConversationMemory memory = new ConversationMemory(
                redisProvider, new ObjectMapper().findAndRegisterModules(), Duration.ofHours(24));
        return new AiApplicationService(config, store, memory, profileClient, webSearchTool, 0.5);
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

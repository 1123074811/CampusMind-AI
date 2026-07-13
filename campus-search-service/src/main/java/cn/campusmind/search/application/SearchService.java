package cn.campusmind.search.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.search.controller.SearchItemResponse;
import cn.campusmind.search.controller.SearchResponse;
import cn.campusmind.search.domain.CampusEvent;
import cn.campusmind.search.feign.VectorSearchFeignClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * 搜索编排服务：调用决策 Agent 识别意图，按意图路由到不同检索策略，返回统一结果。
 */
@Service
public class SearchService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final DecisionClient decisionClient;
    private final EventSearchService eventSearchService;
    private final ObjectMapper objectMapper;
    private final VectorSearchFeignClient vectorSearchClient;

    public SearchService(DecisionClient decisionClient,
                         EventSearchService eventSearchService,
                         ObjectMapper objectMapper,
                         VectorSearchFeignClient vectorSearchClient) {
        this.decisionClient = decisionClient;
        this.eventSearchService = eventSearchService;
        this.objectMapper = objectMapper;
        this.vectorSearchClient = vectorSearchClient;
    }

    public SearchResponse search(CurrentUser user, String query, boolean usePersonalProfile) {
        if (!StringUtils.hasText(query)) {
            throw new BusinessException("QUERY_REQUIRED", "搜索内容不能为空", HttpStatus.BAD_REQUEST);
        }
        DecisionPlan plan = decisionClient.plan(query, List.of(), usePersonalProfile);

        List<CampusEvent> events;
        String message;
        String retrievalMode = "FILTER";
        boolean fallback = false;
        Map<String, Double> vectorScores = Map.of();
        switch (plan.intent() == null ? "" : plan.intent()) {
            case "IMPORT_HELP" -> {
                events = List.of();
                message = "导入帮助：请在导入入口粘贴雨课堂 JSON，系统会为本人生成私有事件。";
            }
            case "PERSONAL_SCHEDULE" -> {
                events = eventSearchService.personalSchedule(user.userId(), plan);
                message = "已按个人日程检索近期事件";
            }
            case "FEED_QUERY" -> {
                events = eventSearchService.feedQuery(user.userId(), plan);
                message = "已按条件查询事件";
            }
            case "SEMANTIC_SEARCH" -> {
                SemanticResult result = semanticSearch(user.userId(), query, plan);
                events = result.events();
                retrievalMode = result.mode();
                fallback = result.fallback();
                vectorScores = result.scores();
                message = result.message();
            }
            case "QA_EXPLAIN" -> {
                SemanticResult result = semanticSearch(user.userId(), query, plan);
                events = result.events();
                retrievalMode = result.mode();
                fallback = result.fallback();
                vectorScores = result.scores();
                message = result.message();
            }
            default -> {
                events = eventSearchService.feedQuery(user.userId(), plan);
                message = "已查询事件";
            }
        }

        String actualMode = retrievalMode;
        Map<String, Double> actualScores = vectorScores;
        List<SearchItemResponse> items = events.stream()
                .map(event -> toItem(event, actualMode, StringUtils.hasText(event.getVectorDocId())
                        ? actualScores.get(event.getVectorDocId()) : null))
                .toList();
        return new SearchResponse(query, plan.intent(), plan, items, items.size(), message, retrievalMode, fallback);
    }

    private SemanticResult semanticSearch(Long userId, String query, DecisionPlan plan) {
        int topK = plan.topK() > 0 ? plan.topK() : 10;
        try {
            var response = vectorSearchClient.search(Map.of("query", query, "topK", topK));
            if (response != null && response.success() && response.data() != null
                    && response.data().hits() != null) {
                List<String> docIds = response.data().hits().stream()
                        .map(VectorSearchFeignClient.VectorHit::docId)
                        .filter(StringUtils::hasText)
                        .toList();
                List<CampusEvent> vectorEvents = eventSearchService.findVectorHits(userId, docIds, topK);
                if (!vectorEvents.isEmpty()) {
                    Map<String, Double> scores = new HashMap<>();
                    response.data().hits().forEach(hit -> {
                        if (StringUtils.hasText(hit.docId())) {
                            scores.putIfAbsent(hit.docId(), hit.score());
                        }
                    });
                    return new SemanticResult(vectorEvents, "SEMANTIC", false,
                            "已按 AI 语义相关性检索事件", Map.copyOf(scores));
                }
            }
        } catch (RuntimeException ignored) {
            // 向量服务异常不应阻断基础搜索，下面返回带显式标记的关键词降级结果。
        }
        return new SemanticResult(eventSearchService.keywordSearch(userId, query, plan),
                "KEYWORD", true, "AI 语义检索暂不可用，已降级为关键词检索", Map.of());
    }

    private record SemanticResult(List<CampusEvent> events, String mode, boolean fallback,
                                  String message, Map<String, Double> scores) {
    }

    private SearchItemResponse toItem(CampusEvent event, String mode, Double score) {
        return new SearchItemResponse(
                event.getId(),
                event.getTitle(),
                event.getSummary(),
                event.getEventType(),
                event.getSourceType(),
                event.getStatus(),
                "AI_PUBLISHED".equals(event.getStatus()),
                event.getStartTime(),
                event.getEndTime(),
                event.getLocation(),
                event.getOrganizer(),
                parseTags(event.getTags()),
                score,
                "SEMANTIC".equals(mode) ? "VECTOR" : mode
        );
    }

    private List<String> parseTags(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }
}

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private final JdbcTemplate jdbcTemplate;

    public SearchService(DecisionClient decisionClient,
                         EventSearchService eventSearchService,
                         ObjectMapper objectMapper,
                         VectorSearchFeignClient vectorSearchClient,
                         JdbcTemplate jdbcTemplate) {
        this.decisionClient = decisionClient;
        this.eventSearchService = eventSearchService;
        this.objectMapper = objectMapper;
        this.vectorSearchClient = vectorSearchClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    public SearchResponse search(CurrentUser user, String query, boolean usePersonalProfile) {
        if (!StringUtils.hasText(query)) {
            throw new BusinessException("QUERY_REQUIRED", "搜索内容不能为空", HttpStatus.BAD_REQUEST);
        }
        DecisionPlan plan = decisionClient.plan(query, List.of(), usePersonalProfile);

        List<CampusEvent> events;
        List<SearchItemResponse> directItems = null;
        String message;
        String retrievalMode = "FILTER";
        boolean fallback = false;
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
                events = List.of();
                directItems = result.items();
                retrievalMode = result.mode();
                fallback = result.fallback();
                message = result.message();
            }
            case "QA_EXPLAIN" -> {
                SemanticResult result = semanticSearch(user.userId(), query, plan);
                events = List.of();
                directItems = result.items();
                retrievalMode = result.mode();
                fallback = result.fallback();
                message = result.message();
            }
            default -> {
                events = eventSearchService.feedQuery(user.userId(), plan);
                message = "已查询事件";
            }
        }

        String actualMode = retrievalMode;
        List<SearchItemResponse> items = directItems != null ? directItems : events.stream()
                .map(event -> toItem(event, actualMode, null)).toList();
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
                Map<String, Double> scores = new HashMap<>();
                response.data().hits().forEach(hit -> scores.putIfAbsent(hit.docId(), hit.score()));
                List<SearchItemResponse> informationItems = informationByVector(docIds, scores, topK);
                if (!informationItems.isEmpty()) {
                    return new SemanticResult(informationItems, "SEMANTIC", false,
                            "已按 AI 语义相关性检索信息");
                }
                List<CampusEvent> vectorEvents = eventSearchService.findVectorHits(userId, docIds, topK);
                if (!vectorEvents.isEmpty()) {
                    return new SemanticResult(vectorEvents.stream()
                            .map(event -> toItem(event, "SEMANTIC", scores.get(event.getVectorDocId())))
                            .toList(), "SEMANTIC", false, "已按 AI 语义相关性检索事件");
                }
            }
        } catch (RuntimeException ignored) {
            // 向量服务异常不应阻断基础搜索，下面返回带显式标记的关键词降级结果。
        }
        List<SearchItemResponse> informationItems = informationByKeyword(query, topK);
        if (informationItems.isEmpty()) {
            informationItems = eventSearchService.keywordSearch(userId, query, plan).stream()
                    .map(event -> toItem(event, "KEYWORD", null)).toList();
        }
        return new SemanticResult(informationItems, "KEYWORD", true,
                "AI 语义检索暂不可用，已降级为关键词检索");
    }

    private List<SearchItemResponse> informationByVector(List<String> docIds,
                                                         Map<String, Double> scores, int topK) {
        Map<Long, String> ids = new LinkedHashMap<>();
        for (String docId : docIds) {
            if (docId != null && docId.matches("info-\\d+")) {
                ids.putIfAbsent(Long.parseLong(docId.substring(5)), docId);
            }
        }
        if (ids.isEmpty()) return List.of();
        Map<Long, SearchItemResponse> items = informationRows(ids.keySet().stream().toList(), null, topK,
                id -> scores.get(ids.get(id)), "VECTOR").stream()
                .collect(java.util.stream.Collectors.toMap(SearchItemResponse::id, item -> item));
        List<SearchItemResponse> ranked = new ArrayList<>();
        ids.keySet().forEach(id -> {
            if (items.containsKey(id)) ranked.add(items.get(id));
        });
        return ranked.stream().limit(topK).toList();
    }

    private List<SearchItemResponse> informationByKeyword(String query, int topK) {
        return informationRows(List.of(), query, topK, ignored -> null, "KEYWORD");
    }

    private List<SearchItemResponse> informationRows(List<Long> ids, String keyword, int topK,
                                                     java.util.function.Function<Long, Double> score,
                                                     String matchedBy) {
        int limit = Math.min(Math.max(topK, 1), 50);
        String where;
        List<Object> args = new ArrayList<>();
        if (!ids.isEmpty()) {
            where = "id IN (" + String.join(",", java.util.Collections.nCopies(ids.size(), "?")) + ")";
            args.addAll(ids);
        } else {
            where = "(title LIKE ? OR detail_content LIKE ? OR ai_summary LIKE ?)";
            String pattern = "%" + keyword.trim() + "%";
            args.add(pattern); args.add(pattern); args.add(pattern);
        }
        args.add(limit);
        return jdbcTemplate.query("""
                SELECT id,title,source_name,publish_time,fetched_at,detail_content,item_status,
                       ai_status,ai_event_type,ai_summary
                FROM information_item
                WHERE item_status IN ('ACTIVE','UPDATED') AND parse_status='DETAIL_SUCCESS' AND %s
                ORDER BY fetched_at DESC,id DESC LIMIT ?
                """.formatted(where), (rs, rowNum) -> {
            long id = rs.getLong("id");
            String aiStatus = rs.getString("ai_status");
            boolean validAi = List.of("SUCCESS", "REVIEW").contains(aiStatus)
                    && StringUtils.hasText(rs.getString("ai_summary"));
            String snippet = validAi ? rs.getString("ai_summary") : preview(rs.getString("detail_content"));
            Timestamp publishedAt = rs.getTimestamp("publish_time");
            String sourceName = rs.getString("source_name");
            return new SearchItemResponse(id, rs.getString("title"), snippet,
                    rs.getString("ai_event_type"), "INFORMATION", rs.getString("item_status"), validAi,
                    publishedAt == null ? null : publishedAt.toLocalDateTime(), null, null, sourceName,
                    List.of(), score.apply(id), matchedBy, sourceName, snippet);
        }, args.toArray());
    }

    private static String preview(String text) {
        if (!StringUtils.hasText(text)) return "";
        String normalized = text.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240);
    }

    private record SemanticResult(List<SearchItemResponse> items, String mode, boolean fallback,
                                  String message) {
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
                "SEMANTIC".equals(mode) ? "VECTOR" : mode,
                event.getOrganizer(),
                event.getSummary()
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

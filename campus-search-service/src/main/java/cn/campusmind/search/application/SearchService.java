package cn.campusmind.search.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.search.controller.SearchItemResponse;
import cn.campusmind.search.controller.SearchResponse;
import cn.campusmind.search.domain.CampusEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

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

    public SearchService(DecisionClient decisionClient,
                         EventSearchService eventSearchService,
                         ObjectMapper objectMapper) {
        this.decisionClient = decisionClient;
        this.eventSearchService = eventSearchService;
        this.objectMapper = objectMapper;
    }

    public SearchResponse search(CurrentUser user, String query, boolean usePersonalProfile) {
        if (!StringUtils.hasText(query)) {
            throw new BusinessException("QUERY_REQUIRED", "搜索内容不能为空", HttpStatus.BAD_REQUEST);
        }
        DecisionPlan plan = decisionClient.plan(query, List.of(), usePersonalProfile);

        List<CampusEvent> events;
        String message;
        switch (plan.intent() == null ? "" : plan.intent()) {
            case "IMPORT_HELP" -> {
                events = List.of();
                message = "导入帮助：请在导入入口粘贴雨课堂JSON或一次性Cookie，系统会在后台解析并生成待审核事件。";
            }
            case "PERSONAL_SCHEDULE" -> {
                events = eventSearchService.personalSchedule(user.userId(), plan);
                message = "已按个人日程检索近期事件";
            }
            case "FEED_QUERY" -> {
                events = eventSearchService.feedQuery(plan);
                message = "已按条件查询事件";
            }
            case "SEMANTIC_SEARCH" -> {
                events = eventSearchService.semanticSearch(query, plan);
                message = "已按语义检索事件（向量库未接入，使用关键字降级）";
            }
            case "QA_EXPLAIN" -> {
                events = eventSearchService.semanticSearch(query, plan);
                message = "已按问答意图检索相关事件";
            }
            default -> {
                events = eventSearchService.feedQuery(plan);
                message = "已查询事件";
            }
        }

        List<SearchItemResponse> items = events.stream()
                .map(this::toItem)
                .toList();
        return new SearchResponse(plan.intent(), plan, items, items.size(), message);
    }

    private SearchItemResponse toItem(CampusEvent event) {
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
                parseTags(event.getTags())
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

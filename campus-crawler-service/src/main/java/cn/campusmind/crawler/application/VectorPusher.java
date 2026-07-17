package cn.campusmind.crawler.application;

import cn.campusmind.crawler.domain.InformationItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将已提取 AI 卡片的事件推送到 AI 服务的向量库，供 RAG 检索使用。
 * 推送失败不影响主流程（静默记录日志）。
 */
@Component
class VectorPusher {

    private static final Logger log = LoggerFactory.getLogger(VectorPusher.class);
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int MAX_CONTENT_LENGTH = 5000;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final URI endpoint;

    VectorPusher(ObjectMapper objectMapper,
                 @Value("${campus.ai.vector-store-url:http://localhost:8089/api/v1/ai/vector/store}") String endpoint) {
        this.objectMapper = objectMapper;
        this.endpoint = URI.create(endpoint);
    }

    /**
     * 将一条事件推送到向量库。异常时只记录日志，不抛出。
     */
    void push(InformationItem item, AiCardExtractor.Result result) {
        try {
            Map<String, Object> body = buildBody(item, result);

            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) {
                log.debug("向量库推送成功 itemId={}", item.getId());
            } else {
                log.warn("向量库推送失败 itemId={} HTTP {}", item.getId(), response.statusCode());
            }
        } catch (Exception ex) {
            log.warn("向量库推送异常 itemId={}: {}", item.getId(), ex.getMessage());
        }
    }

    Map<String, Object> buildBody(InformationItem item, AiCardExtractor.Result result) throws Exception {
        String cardJson = result == null ? item.getAiCardJson() : result.cardJson();
        JsonNode card = cardJson == null || cardJson.isBlank() ? objectMapper.createObjectNode()
                : objectMapper.readTree(cardJson);

        Map<String, Object> event = new HashMap<>();
        event.put("title", item.getTitle() == null ? "" : item.getTitle());
        event.put("eventType", result == null ? defaultText(item.getAiEventType(), "OTHER")
                : defaultText(result.eventType(), "OTHER"));
        event.put("summary", result == null ? defaultText(item.getAiSummary(), "")
                : defaultText(result.summary(), ""));
        copyText(card, event, "startTime");
        copyText(card, event, "endTime");
        copyText(card, event, "location");
        copyArray(card, event, "targetScopes");
        copyArray(card, event, "tags");
        String content = item.getDetailContent();
        event.put("content", content != null && content.length() > MAX_CONTENT_LENGTH
                ? content.substring(0, MAX_CONTENT_LENGTH) : defaultText(content, ""));

        Map<String, Object> body = new HashMap<>();
        body.put("docId", "info-" + item.getId());
        body.put("businessId", item.getId());
        body.put("sourceName", item.getSourceName());
        body.put("originalUrl", item.getItemUrl());
        body.put("sourceType", "PUBLIC_WEB");
        body.put("publishedAt", item.getPublishTime() == null ? null : item.getPublishTime().format(ISO_FMT));
        body.put("contentHash", item.getContentHash());
        body.put("status", defaultText(item.getItemStatus(), "ACTIVE"));
        body.put("event", event);
        return body;
    }

    private static void copyText(JsonNode source, Map<String, Object> target, String field) {
        JsonNode value = source.get(field);
        if (value != null && value.isTextual() && !value.textValue().isBlank()) {
            target.put(field, value.textValue());
        }
    }

    private static void copyArray(JsonNode source, Map<String, Object> target, String field) {
        JsonNode value = source.get(field);
        if (value != null && value.isArray()) {
            target.put(field, objectMapperValue(value));
        }
    }

    private static List<String> objectMapperValue(JsonNode array) {
        List<String> values = new java.util.ArrayList<>();
        array.forEach(node -> {
            if (node.isTextual() && !node.textValue().isBlank()) {
                values.add(node.textValue());
            }
        });
        return values;
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

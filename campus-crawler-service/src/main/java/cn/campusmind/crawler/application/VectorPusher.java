package cn.campusmind.crawler.application;

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
import java.time.LocalDateTime;
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
    void push(Long itemId, String title, String eventType, String summary,
              LocalDateTime publishTime, String content, String sourceName, String contentHash) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("title", title != null ? title : "");
            event.put("eventType", eventType != null ? eventType : "OTHER");
            event.put("summary", summary != null ? summary : "");
            if (publishTime != null) {
                event.put("startTime", publishTime.format(ISO_FMT));
            }
            String truncated = content != null && content.length() > MAX_CONTENT_LENGTH
                    ? content.substring(0, MAX_CONTENT_LENGTH)
                    : (content != null ? content : "");
            event.put("content", truncated);

            Map<String, Object> body = new HashMap<>();
            body.put("docId", "info-" + itemId);
            body.put("businessId", itemId);
            body.put("sourceName", sourceName);
            body.put("sourceType", "PUBLIC_WEB");
            body.put("publishedAt", publishTime == null ? null : publishTime.format(ISO_FMT));
            body.put("contentHash", contentHash);
            body.put("status", "ACTIVE");
            body.put("event", event);

            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) {
                log.debug("向量库推送成功 itemId={}", itemId);
            } else {
                log.warn("向量库推送失败 itemId={} HTTP {}", itemId, response.statusCode());
            }
        } catch (Exception ex) {
            log.warn("向量库推送异常 itemId={}: {}", itemId, ex.getMessage());
        }
    }
}

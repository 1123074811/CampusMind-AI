package cn.campusmind.crawler.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
class AiCardExtractor {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final URI endpoint;

    AiCardExtractor(ObjectMapper objectMapper,
                    @Value("${campus.ai.extract-url:http://localhost:8089/api/v1/ai/cognition/extract}") String endpoint) {
        this.objectMapper = objectMapper;
        this.endpoint = URI.create(endpoint);
    }

    Result extract(Long itemId, String itemUrl, String content) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "sourceType", "PUBLIC_WEB",
                "plainText", content.length() <= 200000 ? content : content.substring(0, 200000),
                "originalItemId", itemId,
                "originalUrl", itemUrl
        ));
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("智能体 HTTP " + response.statusCode());
        }
        return parse(response.body());
    }

    Result parse(String responseBody) throws Exception {
        JsonNode data = objectMapper.readTree(responseBody).path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw new IllegalStateException("智能体未返回数据");
        }
        return new Result(
                data.path("eventType").asText("OTHER"),
                data.path("summary").asText(""),
                data.path("confidence").asDouble(0),
                data.path("needHumanReview").asBoolean(false),
                objectMapper.writeValueAsString(data)
        );
    }

    record Result(String eventType, String summary, double confidence, boolean needHumanReview, String cardJson) {
    }
}

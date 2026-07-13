package cn.campusmind.crawler.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final boolean requireLlm;
    private final String promptVersion;

    @Autowired
    AiCardExtractor(ObjectMapper objectMapper,
                    @Value("${campus.ai.extract-url:http://localhost:8089/api/v1/ai/cognition/extract}") String endpoint) {
        this(objectMapper, endpoint, false, "unknown");
    }

    AiCardExtractor(ObjectMapper objectMapper,
                    @Value("${campus.ai.extract-url:http://localhost:8089/api/v1/ai/cognition/extract}") String endpoint,
                    @Value("${campus.ai.require-llm:true}") boolean requireLlm,
                    @Value("${campus.ai.prompt-version:${CAMPUS_AI_PROMPT_VERSION:llm-v1}}") String promptVersion) {
        this.objectMapper = objectMapper;
        this.endpoint = URI.create(endpoint);
        this.requireLlm = requireLlm;
        this.promptVersion = promptVersion;
    }

    Result extract(Long itemId, String itemUrl, String content) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "sourceType", "PUBLIC_WEB",
                "plainText", content.length() <= 200000 ? content : content.substring(0, 200000),
                "originalItemId", itemId,
                "originalUrl", itemUrl,
                "requireLlm", requireLlm
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
        String mode = response.headers().firstValue("X-Campus-Ai-Mode").orElse("UNKNOWN");
        if (requireLlm && !"LLM".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("智能摘要仅接受真实 LLM 结果，当前模式：" + mode);
        }
        String returnedPromptVersion = response.headers()
                .firstValue("X-Campus-Ai-Prompt-Version").orElse(promptVersion);
        if (requireLlm && !promptVersion.equals(returnedPromptVersion)) {
            throw new IllegalStateException("AI Prompt 版本不一致，拒绝保存结果");
        }
        Result result = parse(response.body());
        validateSummary(result.summary(), content);
        return result.withMetadata(mode,
                response.headers().firstValue("X-Campus-Ai-Model-Version").orElse("unknown"),
                returnedPromptVersion);
    }

    Result parse(String responseBody) throws Exception {
        JsonNode data = objectMapper.readTree(responseBody).path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw new IllegalStateException("智能体未返回数据");
        }
        return new Result(
                data.path("eventType").asText("OTHER"),
                data.path("summary").asText(""),
                data.path("needHumanReview").asBoolean(false),
                objectMapper.writeValueAsString(data),
                "UNKNOWN", "unknown", promptVersion
        );
    }

    String promptVersion() {
        return promptVersion;
    }

    static void validateSummary(String summary, String content) {
        String normalizedSummary = normalize(summary);
        String normalizedContent = normalize(content);
        if (normalizedSummary.isBlank()) {
            throw new IllegalStateException("智能体未返回摘要");
        }
        if (normalizedSummary.length() > 240) {
            throw new IllegalStateException("智能摘要超过长度限制");
        }
        if (normalizedSummary.length() >= 30 && normalizedContent.contains(normalizedSummary)) {
            throw new IllegalStateException("智能摘要与原文重复，未保存伪摘要");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }

    record Result(String eventType, String summary, boolean needHumanReview, String cardJson,
                  String mode, String modelVersion, String promptVersion) {
        Result withMetadata(String mode, String modelVersion, String promptVersion) {
            return new Result(eventType, summary, needHumanReview, cardJson, mode, modelVersion, promptVersion);
        }
    }
}

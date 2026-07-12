package cn.campusmind.importing.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.importing.config.ImportProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通过 HTTP 调用 campus-event-service 的 POST /api/v1/events 端点。
 */
@Component
public class HttpEventServiceClient implements EventServiceClient {

    private static final ParameterizedTypeReference<ApiResponse<Long>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public HttpEventServiceClient(ImportProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.aiConnectTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.aiReadTimeoutSeconds()));
        this.restClient = RestClient.builder()
                .baseUrl(properties.eventBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public Long createEvent(String title, String summary, String eventType, String sourceType,
                            String startTime, String endTime,
                            String location, String organizer,
                            String targetScopeJson, String tagsJson,
                            String dedupKey, String rawDocId, String sourceUrl, String contentHash) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("summary", summary);
        body.put("eventType", eventType);
        body.put("sourceType", sourceType);
        body.put("startTime", startTime);
        body.put("endTime", endTime);
        body.put("location", location);
        body.put("organizer", organizer);
        body.put("targetScopeJson", targetScopeJson);
        body.put("tagsJson", tagsJson);
        body.put("dedupKey", dedupKey);
        body.put("rawDocId", rawDocId);
        body.put("sourceUrl", sourceUrl);
        body.put("contentHash", contentHash);

        ApiResponse<Long> response;
        try {
            response = restClient.post()
                    .uri("/api/v1/events")
                    .body(body)
                    .retrieve()
                    .body(RESPONSE_TYPE);
        } catch (RuntimeException ex) {
            throw new BusinessException("EVENT_SERVICE_UNAVAILABLE",
                    "事件服务暂不可用：" + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }
        if (response == null || !response.success() || response.data() == null) {
            String message = response == null ? "无响应" : response.message();
            throw new BusinessException("EVENT_SERVICE_FAILED",
                    "事件服务创建失败：" + message, HttpStatus.BAD_GATEWAY);
        }
        return response.data();
    }
}

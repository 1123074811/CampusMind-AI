package cn.campusmind.importing.application;

import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.importing.config.ImportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通过 HTTP 调用 campus-feed-service 的 POST /api/v1/information 端点。
 */
@Component
public class HttpInformationServiceClient implements InformationServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HttpInformationServiceClient.class);
    private static final ParameterizedTypeReference<ApiResponse<Long>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public HttpInformationServiceClient(ImportProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.aiConnectTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.aiReadTimeoutSeconds()));
        this.restClient = RestClient.builder()
                .baseUrl(properties.feedBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public Long createItem(String title, String detailContent, String sourceName,
                           String sourceUrl, String itemUrl, String contentHash) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("detailContent", detailContent);
        body.put("sourceName", sourceName);
        body.put("sourceUrl", sourceUrl);
        body.put("itemUrl", itemUrl);
        body.put("contentHash", contentHash);

        ApiResponse<Long> response;
        try {
            response = restClient.post()
                    .uri("/api/v1/information")
                    .body(body)
                    .retrieve()
                    .body(RESPONSE_TYPE);
        } catch (RuntimeException ex) {
            log.warn("信息服务创建条目失败（不影响事件创建）: {}", ex.getMessage());
            return null;
        }
        if (response == null || !response.success() || response.data() == null) {
            log.warn("信息服务创建条目返回异常: {}", response == null ? "无响应" : response.message());
            return null;
        }
        return response.data();
    }
}

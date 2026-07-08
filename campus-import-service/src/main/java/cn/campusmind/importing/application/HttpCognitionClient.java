package cn.campusmind.importing.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.importing.config.ImportProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 通过 HTTP 调用 campus-ai-service 的 /api/v1/ai/cognition/extract 端点。
 * 测试中可通过 @MockBean CognitionClient 替换，避免依赖真实 ai-service 运行。
 */
@Component
public class HttpCognitionClient implements CognitionClient {

    private static final ParameterizedTypeReference<ApiResponse<CognitionResult>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public HttpCognitionClient(ImportProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.aiBaseUrl())
                .build();
    }

    @Override
    public CognitionResult extract(String sourceType, String plainText) {
        ApiResponse<CognitionResult> response;
        try {
            response = restClient.post()
                    .uri("/api/v1/ai/cognition/extract")
                    .body(Map.of("sourceType", sourceType, "plainText", plainText))
                    .retrieve()
                    .body(RESPONSE_TYPE);
        } catch (RuntimeException ex) {
            throw new BusinessException("COGNITION_UNAVAILABLE",
                    "AI 认知服务暂不可用：" + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }
        if (response == null || !response.success() || response.data() == null) {
            String message = response == null ? "无响应" : response.message();
            throw new BusinessException("COGNITION_FAILED",
                    "AI 认知抽取失败：" + message, HttpStatus.BAD_GATEWAY);
        }
        return response.data();
    }
}

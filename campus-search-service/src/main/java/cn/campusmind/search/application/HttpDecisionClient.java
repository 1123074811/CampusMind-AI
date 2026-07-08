package cn.campusmind.search.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.search.config.SearchProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 通过 HTTP 调用 campus-ai-service 的 /api/v1/ai/decision/plan 端点。
 * 测试中可通过 @MockBean DecisionClient 替换，避免依赖真实 ai-service 运行。
 */
@Component
public class HttpDecisionClient implements DecisionClient {

    private static final ParameterizedTypeReference<ApiResponse<DecisionPlan>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public HttpDecisionClient(SearchProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.aiBaseUrl())
                .build();
    }

    @Override
    public DecisionPlan plan(String query, List<String> userScopes, boolean usePersonalProfile) {
        ApiResponse<DecisionPlan> response;
        try {
            response = restClient.post()
                    .uri("/api/v1/ai/decision/plan")
                    .body(Map.of(
                            "query", query == null ? "" : query,
                            "userScopes", userScopes == null ? List.of() : userScopes,
                            "usePersonalProfile", usePersonalProfile
                    ))
                    .retrieve()
                    .body(RESPONSE_TYPE);
        } catch (RuntimeException ex) {
            throw new BusinessException("DECISION_UNAVAILABLE",
                    "AI 决策服务暂不可用：" + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }
        if (response == null || !response.success() || response.data() == null) {
            String message = response == null ? "无响应" : response.message();
            throw new BusinessException("DECISION_FAILED",
                    "AI 意图识别失败：" + message, HttpStatus.BAD_GATEWAY);
        }
        return response.data();
    }
}

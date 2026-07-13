package cn.campusmind.search.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.search.feign.DecisionFeignClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 决策客户端适配器：通过 Feign 调用 campus-ai-service 的决策规划接口。
 *
 * <p>底层使用 {@link DecisionFeignClient}（基于 OpenFeign + Nacos 服务发现），
 * 替代原先的 RestClient 硬编码 URL 方式。
 * 测试中可通过 @MockBean DecisionClient 替换，避免依赖真实 ai-service 运行。
 */
@Component
public class HttpDecisionClient implements DecisionClient {

    private final DecisionFeignClient feignClient;

    public HttpDecisionClient(DecisionFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    @Override
    public DecisionPlan plan(String query, List<String> userScopes, boolean usePersonalProfile) {
        ApiResponse<DecisionPlan> response;
        try {
            response = feignClient.plan(Map.of(
                    "query", query == null ? "" : query,
                    "userScopes", userScopes == null ? List.of() : userScopes,
                    "usePersonalProfile", usePersonalProfile
            ));
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

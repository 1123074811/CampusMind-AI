package cn.campusmind.search.feign;

import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.search.application.DecisionPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AI 决策服务 Sentinel 熔断降级：当 campus-ai-service 不可用时，
 * 返回降级响应。搜索服务可根据降级状态回退到关键词搜索。
 */
@Component
public class DecisionFeignClientFallback implements DecisionFeignClient {

    private static final Logger log = LoggerFactory.getLogger(DecisionFeignClientFallback.class);

    @Override
    public ApiResponse<DecisionPlan> plan(Map<String, Object> body) {
        log.warn("[SENTINEL-FALLBACK] AI 决策服务熔断降级, query={}", body.get("query"));
        return ApiResponse.fail("DECISION_DEGRADED", "AI 决策服务暂不可用，请稍后重试");
    }
}

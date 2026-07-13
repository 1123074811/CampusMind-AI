package cn.campusmind.search.feign;

import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.search.application.DecisionPlan;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * campus-ai-service 的 Feign 客户端（决策规划）。
 *
 * <p>通过 Nacos 服务发现自动解析服务地址，无需硬编码 URL。
 * 请求头透传由 {@link cn.campusmind.common.feign.FeignAuthRequestInterceptor} 自动处理。
 * Sentinel 熔断时由 {@link DecisionFeignClientFallback} 提供降级响应。
 */
@FeignClient(name = "campus-ai-service", contextId = "decisionFeignClient",
        fallback = DecisionFeignClientFallback.class)
public interface DecisionFeignClient {

    /**
     * 调用 AI 决策规划接口，识别意图并生成检索计划。
     *
     * @param body 包含 query、userScopes、usePersonalProfile 的请求体
     */
    @PostMapping("/api/v1/ai/decision/plan")
    ApiResponse<DecisionPlan> plan(@RequestBody Map<String, Object> body);
}

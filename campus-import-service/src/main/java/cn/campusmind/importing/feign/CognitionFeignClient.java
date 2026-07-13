package cn.campusmind.importing.feign;

import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.importing.application.CognitionResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * campus-ai-service 的 Feign 客户端（认知抽取）。
 *
 * <p>通过 Nacos 服务发现自动解析服务地址，无需硬编码 URL。
 * Sentinel 熔断时由 {@link CognitionFeignClientFallback} 提供降级响应。
 */
@FeignClient(name = "campus-ai-service", contextId = "cognitionFeignClient",
        fallback = CognitionFeignClientFallback.class)
public interface CognitionFeignClient {

    /**
     * 调用 AI 认知抽取接口，将非结构化文本转为结构化事件候选。
     *
     * @param body 包含 sourceType、plainText、originalItemId、originalUrl 的请求体
     */
    @PostMapping("/api/v1/ai/cognition/extract")
    ApiResponse<CognitionResult> extract(@RequestBody Map<String, Object> body);
}

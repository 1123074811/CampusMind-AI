package cn.campusmind.importing.feign;

import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.importing.application.CognitionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AI 认知服务 Sentinel 熔断降级：当 campus-ai-service 不可用或响应超时时，
 * 返回降级响应，避免级联故障。
 */
@Component
public class CognitionFeignClientFallback implements CognitionFeignClient {

    private static final Logger log = LoggerFactory.getLogger(CognitionFeignClientFallback.class);

    @Override
    public ApiResponse<CognitionResult> extract(Map<String, Object> body) {
        log.warn("[SENTINEL-FALLBACK] AI 认知抽取服务熔断降级, sourceType={}", body.get("sourceType"));
        return ApiResponse.fail("COGNITION_DEGRADED", "AI 认知服务暂不可用，请稍后重试");
    }
}

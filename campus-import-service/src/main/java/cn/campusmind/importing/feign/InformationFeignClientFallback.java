package cn.campusmind.importing.feign;

import cn.campusmind.common.web.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 信息服务 Sentinel 熔断降级：当 campus-feed-service 不可用时，
 * 返回降级响应。信息条目创建是非关键路径，调用方可忽略失败。
 */
@Component
public class InformationFeignClientFallback implements InformationFeignClient {

    private static final Logger log = LoggerFactory.getLogger(InformationFeignClientFallback.class);

    @Override
    public ApiResponse<Long> createItem(Map<String, Object> body) {
        log.warn("[SENTINEL-FALLBACK] 信息服务熔断降级, title={}", body.get("title"));
        return ApiResponse.fail("INFORMATION_SERVICE_DEGRADED", "信息服务暂不可用");
    }
}

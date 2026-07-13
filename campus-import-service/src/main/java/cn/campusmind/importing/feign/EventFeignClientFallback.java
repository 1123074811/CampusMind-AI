package cn.campusmind.importing.feign;

import cn.campusmind.common.web.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 事件服务 Sentinel 熔断降级：当 campus-event-service 不可用时，
 * 返回降级响应。事件创建是关键路径，调用方应根据降级响应做补偿处理。
 */
@Component
public class EventFeignClientFallback implements EventFeignClient {

    private static final Logger log = LoggerFactory.getLogger(EventFeignClientFallback.class);

    @Override
    public ApiResponse<Long> createEvent(Map<String, Object> body, Long userId) {
        log.warn("[SENTINEL-FALLBACK] 事件服务熔断降级, title={}", body.get("title"));
        return ApiResponse.fail("EVENT_SERVICE_DEGRADED", "事件服务暂不可用，请稍后重试");
    }
}

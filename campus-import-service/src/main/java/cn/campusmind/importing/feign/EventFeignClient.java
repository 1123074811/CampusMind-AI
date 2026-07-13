package cn.campusmind.importing.feign;

import cn.campusmind.common.web.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * campus-event-service 的 Feign 客户端。
 *
 * <p>通过 Nacos 服务发现自动解析服务地址，无需硬编码 URL。
 * 请求头透传由 {@link cn.campusmind.common.feign.FeignAuthRequestInterceptor} 自动处理。
 * Sentinel 熔断时由 {@link EventFeignClientFallback} 提供降级响应。
 */
@FeignClient(name = "campus-event-service", contextId = "eventFeignClient",
        fallback = EventFeignClientFallback.class)
public interface EventFeignClient {

    /**
     * 幂等创建事件，返回事件 ID。
     *
     * @param body   事件创建请求体（与 campus-event-service 的 CreateEventRequest 对齐）
     * @param userId 私有事件所属用户 ID（通过 X-User-Id 请求头传递）
     */
    @PostMapping("/api/v1/events")
    ApiResponse<Long> createEvent(@RequestBody Map<String, Object> body,
                                   @RequestHeader(value = "X-User-Id", required = false) Long userId);
}

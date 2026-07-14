package cn.campusmind.importing.feign;

import cn.campusmind.common.web.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * campus-feed-service 的 Feign 客户端（信息条目创建）。
 *
 * <p>通过 Nacos 服务发现自动解析服务地址，无需硬编码 URL。
 * 请求头透传由 {@link cn.campusmind.common.feign.FeignAuthRequestInterceptor} 自动处理。
 * Sentinel 熔断时由 {@link InformationFeignClientFallback} 提供降级响应。
 */
@FeignClient(name = "campus-feed-service", contextId = "informationFeignClient",
        fallback = InformationFeignClientFallback.class)
public interface InformationFeignClient {

    /**
     * 幂等创建信息条目，返回信息条目 ID。
     *
     * @param body 包含 title、detailContent、sourceName、sourceUrl、itemUrl、contentHash、publishTime、submittedBy、submittedByUserId 的请求体
     */
    @PostMapping("/api/v1/information")
    ApiResponse<Long> createItem(@RequestBody Map<String, Object> body);
}

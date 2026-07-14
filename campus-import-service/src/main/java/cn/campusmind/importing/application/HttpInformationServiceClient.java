package cn.campusmind.importing.application;

import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.importing.feign.InformationFeignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 信息服务客户端适配器：通过 Feign 调用 campus-feed-service 创建 information_item。
 *
 * <p>底层使用 {@link InformationFeignClient}（基于 OpenFeign + Nacos 服务发现），
 * 替代原先的 RestClient 硬编码 URL 方式。请求头透传由
 * {@link cn.campusmind.common.feign.FeignAuthRequestInterceptor} 自动处理。
 */
@Component
public class HttpInformationServiceClient implements InformationServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HttpInformationServiceClient.class);

    private final InformationFeignClient feignClient;

    public HttpInformationServiceClient(InformationFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    @Override
    public Long createItem(String title, String detailContent, String sourceName,
                           String sourceUrl, String itemUrl, String contentHash,
                           LocalDateTime publishTime, String submittedBy, Long submittedByUserId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("detailContent", detailContent);
        body.put("sourceName", sourceName);
        body.put("sourceUrl", sourceUrl);
        body.put("itemUrl", itemUrl);
        body.put("contentHash", contentHash);
        body.put("publishTime", publishTime);
        body.put("submittedBy", submittedBy);
        body.put("submittedByUserId", submittedByUserId);

        ApiResponse<Long> response;
        try {
            response = feignClient.createItem(body);
        } catch (RuntimeException ex) {
            log.warn("信息服务创建条目失败（不影响事件创建）: {}", ex.getMessage());
            return null;
        }
        if (response == null || !response.success() || response.data() == null) {
            log.warn("信息服务创建条目返回异常: {}", response == null ? "无响应" : response.message());
            return null;
        }
        return response.data();
    }
}

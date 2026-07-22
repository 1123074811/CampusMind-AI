package cn.campusmind.importing.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.importing.feign.EventFeignClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 事件服务客户端适配器：通过 Feign 调用 campus-event-service 创建事件。
 *
 * <p>底层使用 {@link EventFeignClient}（基于 OpenFeign + Nacos 服务发现），
 * 替代原先的 RestClient 硬编码 URL 方式。请求头透传由
 * {@link cn.campusmind.common.feign.FeignAuthRequestInterceptor} 自动处理。
 */
@Component
public class HttpEventServiceClient implements EventServiceClient {

    private final EventFeignClient feignClient;

    public HttpEventServiceClient(EventFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    @Override
    public Long createEvent(String title, String summary, String eventType, String sourceType,
                            String startTime, String endTime,
                            String location, String organizer,
                            String targetScopeJson, String tagsJson,
                            String visibility, Long ownerUserId,
                            String dedupKey, String rawDocId, String sourceUrl, String contentHash) {
        Map<String, Object> body = eventBody(title, summary, eventType, sourceType, startTime,
                endTime, location, organizer, targetScopeJson, tagsJson, visibility,
                dedupKey, rawDocId, sourceUrl, contentHash);

        ApiResponse<Long> response;
        try {
            response = feignClient.createEvent(body, ownerUserId);
        } catch (RuntimeException ex) {
            throw new BusinessException("EVENT_SERVICE_UNAVAILABLE",
                    "事件服务暂不可用：" + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }
        if (response == null || !response.success() || response.data() == null) {
            String message = response == null ? "无响应" : response.message();
            throw new BusinessException("EVENT_SERVICE_FAILED",
                    "事件服务创建失败：" + message, HttpStatus.BAD_GATEWAY);
        }
        return response.data();
    }

    @Override
    public EventWriteResult createEventIncremental(
            String title, String summary, String eventType, String sourceType,
            String startTime, String endTime, String location, String organizer,
            String targetScopeJson, String tagsJson, String visibility, Long ownerUserId,
            String dedupKey, String rawDocId, String sourceUrl, String contentHash,
            String publishedAt) {
        Map<String, Object> body = eventBody(title, summary, eventType, sourceType, startTime,
                endTime, location, organizer, targetScopeJson, tagsJson, visibility,
                dedupKey, rawDocId, sourceUrl, contentHash);
        body.put("publishedAt", publishedAt);
        ApiResponse<Map<String, Object>> response;
        try {
            response = feignClient.createEventIncremental(body, ownerUserId);
        } catch (RuntimeException ex) {
            throw new BusinessException("EVENT_SERVICE_UNAVAILABLE",
                    "事件服务暂不可用：" + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }
        if (response == null || !response.success() || response.data() == null) {
            String message = response == null ? "无响应" : response.message();
            throw new BusinessException("EVENT_SERVICE_FAILED",
                    "事件服务创建失败：" + message, HttpStatus.BAD_GATEWAY);
        }
        Object id = response.data().get("eventId");
        if (!(id instanceof Number number)) {
            throw new BusinessException("EVENT_SERVICE_FAILED", "事件服务返回无效", HttpStatus.BAD_GATEWAY);
        }
        return new EventWriteResult(number.longValue(), Boolean.TRUE.equals(response.data().get("skipped")));
    }

    private Map<String, Object> eventBody(
            String title, String summary, String eventType, String sourceType,
            String startTime, String endTime, String location, String organizer,
            String targetScopeJson, String tagsJson, String visibility,
            String dedupKey, String rawDocId, String sourceUrl, String contentHash) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("summary", summary);
        body.put("eventType", eventType);
        body.put("sourceType", sourceType);
        body.put("startTime", startTime);
        body.put("endTime", endTime);
        body.put("location", location);
        body.put("organizer", organizer);
        body.put("targetScopeJson", targetScopeJson);
        body.put("tagsJson", tagsJson);
        body.put("visibility", visibility);
        body.put("dedupKey", dedupKey);
        body.put("rawDocId", rawDocId);
        body.put("sourceUrl", sourceUrl);
        body.put("contentHash", contentHash);
        return body;
    }

    @Override
    public Map<String, Object> deleteOwnedSource(String sourceType, Long ownerUserId) {
        ApiResponse<Map<String, Object>> response;
        try {
            response = feignClient.deleteOwnedSource(sourceType, ownerUserId);
        } catch (RuntimeException ex) {
            throw new BusinessException("EVENT_SERVICE_UNAVAILABLE",
                    "事件服务暂不可用：" + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }
        if (response == null || !response.success() || response.data() == null) {
            String message = response == null ? "无响应" : response.message();
            throw new BusinessException("EVENT_SERVICE_FAILED",
                    "事件删除失败：" + message, HttpStatus.BAD_GATEWAY);
        }
        return response.data();
    }
}

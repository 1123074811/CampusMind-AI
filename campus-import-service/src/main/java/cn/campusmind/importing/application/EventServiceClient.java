package cn.campusmind.importing.application;

/**
 * 事件服务客户端：通过 HTTP 调用 campus-event-service 创建事件。
 */
public interface EventServiceClient {

    /**
     * 幂等创建事件，返回事件 ID。
     */
    Long createEvent(String title, String summary, String eventType, String sourceType,
                     String startTime, String endTime,
                     String location, String organizer,
                     String targetScopeJson, String tagsJson,
                     String visibility, Long ownerUserId,
                     String dedupKey, String rawDocId, String sourceUrl, String contentHash);
}

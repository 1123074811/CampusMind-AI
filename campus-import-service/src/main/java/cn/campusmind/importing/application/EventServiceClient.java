package cn.campusmind.importing.application;

import java.util.Map;

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

    default EventWriteResult createEventIncremental(
            String title, String summary, String eventType, String sourceType,
            String startTime, String endTime, String location, String organizer,
            String targetScopeJson, String tagsJson, String visibility, Long ownerUserId,
            String dedupKey, String rawDocId, String sourceUrl, String contentHash) {
        return createEventIncremental(title, summary, eventType, sourceType, startTime,
                endTime, location, organizer, targetScopeJson, tagsJson, visibility,
                ownerUserId, dedupKey, rawDocId, sourceUrl, contentHash, null);
    }

    default EventWriteResult createEventIncremental(
            String title, String summary, String eventType, String sourceType,
            String startTime, String endTime, String location, String organizer,
            String targetScopeJson, String tagsJson, String visibility, Long ownerUserId,
            String dedupKey, String rawDocId, String sourceUrl, String contentHash,
            String publishedAt) {
        return new EventWriteResult(createEvent(title, summary, eventType, sourceType,
                startTime, endTime, location, organizer, targetScopeJson, tagsJson,
                visibility, ownerUserId, dedupKey, rawDocId, sourceUrl, contentHash), false);
    }

    record EventWriteResult(Long eventId, boolean skipped) {}

    Map<String, Object> deleteOwnedSource(String sourceType, Long ownerUserId);
}

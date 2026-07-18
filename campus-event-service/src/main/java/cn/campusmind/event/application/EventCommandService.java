package cn.campusmind.event.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.event.domain.CampusEvent;
import cn.campusmind.event.domain.EventSourceRef;
import cn.campusmind.event.infrastructure.mapper.CampusEventMapper;
import cn.campusmind.event.infrastructure.mapper.EventSourceRefMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class EventCommandService {

    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd HH"
    };
    private static final Set<String> PRIVATE_SOURCES = Set.of(
            "RAIN_CLASSROOM", "XIQUEER", "XJU_EHALL", "USER_TEXT", "USER_IMAGE", "USER_FILE"
    );

    private final CampusEventMapper campusEventMapper;
    private final EventSourceRefMapper eventSourceRefMapper;
    private final EventVectorLifecycleClient eventVectorLifecycleClient;

    public EventCommandService(CampusEventMapper campusEventMapper,
                               EventSourceRefMapper eventSourceRefMapper,
                               EventVectorLifecycleClient eventVectorLifecycleClient) {
        this.campusEventMapper = campusEventMapper;
        this.eventSourceRefMapper = eventSourceRefMapper;
        this.eventVectorLifecycleClient = eventVectorLifecycleClient;
    }

    /**
     * 幂等创建/更新事件 + 来源引用。如果 dedupKey 已存在则复用事件 ID。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long upsertEvent(UpsertEventRequest req) {
        if (!StringUtils.hasText(req.contentHash())) {
            throw new BusinessException("EVENT_CONTENT_HASH_REQUIRED", "事件内容哈希不能为空", HttpStatus.BAD_REQUEST);
        }
        String dedupKey = req.dedupKey();
        String sourceType = StringUtils.hasText(req.sourceType())
                ? req.sourceType().trim().toUpperCase(Locale.ROOT)
                : "UNKNOWN";
        String visibility = PRIVATE_SOURCES.contains(sourceType) || "PRIVATE".equalsIgnoreCase(req.visibility())
                ? "PRIVATE" : "PUBLIC";
        if ("PRIVATE".equals(visibility) && req.ownerUserId() == null) {
            throw new BusinessException("EVENT_OWNER_REQUIRED", "私有事件必须绑定用户", HttpStatus.BAD_REQUEST);
        }

        // 1. 先按 dedupKey 查找已有事件
        CampusEvent existing = null;
        if (StringUtils.hasText(dedupKey)) {
            LambdaQueryWrapper<CampusEvent> query = new LambdaQueryWrapper<CampusEvent>()
                    .eq(CampusEvent::getDedupKey, dedupKey)
                    .eq(CampusEvent::getVisibility, visibility);
            if ("PRIVATE".equals(visibility)) {
                query.eq(CampusEvent::getOwnerUserId, req.ownerUserId())
                        .eq(CampusEvent::getSourceType, sourceType);
            } else {
                query.isNull(CampusEvent::getOwnerUserId);
            }
            existing = campusEventMapper.selectOne(query.last("LIMIT 1"));
        }

        // 2. dedupKey 未命中时，按 contentHash 查找已有事件（信息融合）
        if (existing == null && StringUtils.hasText(req.contentHash())) {
            existing = campusEventMapper.findByContentHashAndScope(
                    req.contentHash(), visibility, sourceType,
                    "PRIVATE".equals(visibility) ? req.ownerUserId() : null);
        }

        Long eventId;
        if (existing != null) {
            applyMutableFields(existing, req, sourceType, visibility);
            campusEventMapper.updateById(existing);
            eventId = existing.getId();
        } else {
            CampusEvent event = new CampusEvent();
            applyMutableFields(event, req, sourceType, visibility);
            event.setStatus("AI_PUBLISHED");
            event.setDedupKey(dedupKey);
            event.setPublishedAt(LocalDateTime.now());
            campusEventMapper.insert(event);
            eventId = event.getId();
        }

        if (!sourceRefExists(eventId, req.contentHash(), req.sourceUrl())) {
            EventSourceRef ref = new EventSourceRef();
            ref.setEventId(eventId);
            ref.setRawDocId(req.rawDocId());
            ref.setSourceUrl(req.sourceUrl());
            ref.setSourceTitle(req.title());
            ref.setContentHash(req.contentHash());
            eventSourceRefMapper.insert(ref);
        }

        return eventId;
    }

    @Transactional(rollbackFor = Exception.class)
    public DeleteOwnedEventsResult deleteOwnedBySource(Long ownerUserId, String sourceType) {
        if (ownerUserId == null) {
            throw new BusinessException("EVENT_OWNER_REQUIRED", "必须登录后删除私有事件", HttpStatus.UNAUTHORIZED);
        }
        String normalizedSource = StringUtils.hasText(sourceType)
                ? sourceType.trim().toUpperCase(Locale.ROOT) : "";
        if (!PRIVATE_SOURCES.contains(normalizedSource)) {
            throw new BusinessException("EVENT_SOURCE_DELETE_FORBIDDEN", "只能删除本人导入的数据", HttpStatus.BAD_REQUEST);
        }
        List<CampusEvent> events = campusEventMapper.selectList(new LambdaQueryWrapper<CampusEvent>()
                .eq(CampusEvent::getOwnerUserId, ownerUserId)
                .eq(CampusEvent::getVisibility, "PRIVATE")
                .eq(CampusEvent::getSourceType, normalizedSource));
        if (events.isEmpty()) {
            return new DeleteOwnedEventsResult(0, 0);
        }
        List<String> vectorDocIds = events.stream()
                .map(CampusEvent::getVectorDocId)
                .filter(StringUtils::hasText)
                .toList();
        eventVectorLifecycleClient.deleteVectors(vectorDocIds);
        List<Long> eventIds = events.stream().map(CampusEvent::getId).toList();
        eventSourceRefMapper.delete(new LambdaQueryWrapper<EventSourceRef>()
                .in(EventSourceRef::getEventId, eventIds));
        campusEventMapper.deleteByIds(eventIds);
        return new DeleteOwnedEventsResult(eventIds.size(), vectorDocIds.size());
    }

    private void applyMutableFields(CampusEvent event, UpsertEventRequest req,
                                    String sourceType, String visibility) {
        event.setTitle(StringUtils.hasText(req.title()) ? req.title().trim() : "未命名事件");
        event.setSummary(req.summary());
        event.setEventType(StringUtils.hasText(req.eventType()) ? req.eventType().trim() : "OTHER");
        event.setSourceType(sourceType);
        event.setVisibility(visibility);
        event.setOwnerUserId("PRIVATE".equals(visibility) ? req.ownerUserId() : null);
        event.setStartTime(parseDateTime(req.startTime()));
        event.setEndTime(parseDateTime(req.endTime()));
        event.setLocation(req.location());
        event.setOrganizer(req.organizer());
        event.setTargetScope(req.targetScopeJson());
        event.setTags(req.tagsJson());
        if (!StringUtils.hasText(event.getDedupKey()) && StringUtils.hasText(req.dedupKey())) {
            event.setDedupKey(req.dedupKey());
        }
    }

    private boolean sourceRefExists(Long eventId, String contentHash, String sourceUrl) {
        if (!StringUtils.hasText(contentHash)) {
            return false;
        }
        LambdaQueryWrapper<EventSourceRef> query = new LambdaQueryWrapper<EventSourceRef>()
                .eq(EventSourceRef::getEventId, eventId)
                .eq(EventSourceRef::getContentHash, contentHash);
        if (StringUtils.hasText(sourceUrl)) {
            query.eq(EventSourceRef::getSourceUrl, sourceUrl);
        } else {
            query.isNull(EventSourceRef::getSourceUrl);
        }
        return eventSourceRefMapper.selectCount(query) > 0;
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim().replace('T', ' ');
        for (String pattern : DATE_PATTERNS) {
            try {
                return LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {}
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 创建事件的请求 DTO。
     */
    public record UpsertEventRequest(
            String title,
            String summary,
            String eventType,
            String sourceType,
            String startTime,
            String endTime,
            String location,
            String organizer,
            String targetScopeJson,
            String tagsJson,
            String visibility,
            Long ownerUserId,
            String dedupKey,
            String rawDocId,
            String sourceUrl,
            String contentHash
    ) {}

    public record DeleteOwnedEventsResult(int deletedEvents, int deletedVectors) {}
}

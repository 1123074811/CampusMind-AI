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

@Service
public class EventCommandService {

    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd HH"
    };

    private final CampusEventMapper campusEventMapper;
    private final EventSourceRefMapper eventSourceRefMapper;

    public EventCommandService(CampusEventMapper campusEventMapper,
                               EventSourceRefMapper eventSourceRefMapper) {
        this.campusEventMapper = campusEventMapper;
        this.eventSourceRefMapper = eventSourceRefMapper;
    }

    /**
     * 幂等创建/更新事件 + 来源引用。如果 dedupKey 已存在则复用事件 ID。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long upsertEvent(UpsertEventRequest req) {
        String dedupKey = req.dedupKey();
        String visibility = "PRIVATE".equalsIgnoreCase(req.visibility()) ? "PRIVATE" : "PUBLIC";
        if ("PRIVATE".equals(visibility) && req.ownerUserId() == null) {
            throw new BusinessException("EVENT_OWNER_REQUIRED", "私有事件必须绑定用户", HttpStatus.BAD_REQUEST);
        }
        if ("RAIN_CLASSROOM".equals(req.sourceType()) && !"PRIVATE".equals(visibility)) {
            throw new BusinessException("EVENT_VISIBILITY_INVALID", "雨课堂事件必须为私有事件", HttpStatus.BAD_REQUEST);
        }

        // 1. 先按 dedupKey 查找已有事件
        CampusEvent existing = null;
        if (StringUtils.hasText(dedupKey)) {
            existing = campusEventMapper.selectOne(
                    new LambdaQueryWrapper<CampusEvent>()
                            .eq(CampusEvent::getDedupKey, dedupKey)
                            .last("LIMIT 1"));
        }

        // 2. dedupKey 未命中时，按 contentHash 查找已有事件（信息融合）
        if (existing == null && StringUtils.hasText(req.contentHash())) {
            existing = findEventByContentHash(req.contentHash());
        }

        Long eventId;
        if (existing != null) {
            eventId = existing.getId();
        } else {
            CampusEvent event = new CampusEvent();
            event.setTitle(StringUtils.hasText(req.title()) ? req.title() : "未命名事件");
            event.setSummary(req.summary());
            event.setEventType(StringUtils.hasText(req.eventType()) ? req.eventType() : "OTHER");
            event.setSourceType(req.sourceType());
            event.setVisibility(visibility);
            event.setOwnerUserId("PRIVATE".equals(visibility) ? req.ownerUserId() : null);
            event.setStatus("AI_PUBLISHED");
            event.setStartTime(parseDateTime(req.startTime()));
            event.setEndTime(parseDateTime(req.endTime()));
            event.setLocation(req.location());
            event.setOrganizer(req.organizer());
            event.setTargetScope(req.targetScopeJson());
            event.setTags(req.tagsJson());
            event.setDedupKey(dedupKey);
            event.setPublishedAt(LocalDateTime.now());
            campusEventMapper.insert(event);
            eventId = event.getId();
        }

        // 写入来源引用
        EventSourceRef ref = new EventSourceRef();
        ref.setEventId(eventId);
        ref.setRawDocId(req.rawDocId());
        ref.setSourceUrl(req.sourceUrl());
        ref.setSourceTitle(req.title());
        ref.setContentHash(req.contentHash());
        eventSourceRefMapper.insert(ref);

        return eventId;
    }

    /**
     * 通过 contentHash 查找已有事件（信息融合：同一内容来自不同来源时复用事件）。
     */
    private CampusEvent findEventByContentHash(String contentHash) {
        EventSourceRef ref = eventSourceRefMapper.selectOne(
                new LambdaQueryWrapper<EventSourceRef>()
                        .eq(EventSourceRef::getContentHash, contentHash)
                        .last("LIMIT 1"));
        if (ref == null || ref.getEventId() == null) {
            return null;
        }
        return campusEventMapper.selectById(ref.getEventId());
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
}

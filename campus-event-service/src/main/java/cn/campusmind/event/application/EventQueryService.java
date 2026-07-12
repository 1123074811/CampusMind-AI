package cn.campusmind.event.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.event.controller.EventDetailResponse;
import cn.campusmind.event.controller.EventSearchResponse;
import cn.campusmind.event.controller.EventSourceRefResponse;
import cn.campusmind.event.domain.CampusEvent;
import cn.campusmind.event.domain.EventSourceRef;
import cn.campusmind.event.infrastructure.mapper.CampusEventMapper;
import cn.campusmind.event.infrastructure.mapper.EventSourceRefMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EventQueryService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final CampusEventMapper campusEventMapper;
    private final EventSourceRefMapper eventSourceRefMapper;
    private final ObjectMapper objectMapper;

    public EventQueryService(CampusEventMapper campusEventMapper,
                             EventSourceRefMapper eventSourceRefMapper,
                             ObjectMapper objectMapper) {
        this.campusEventMapper = campusEventMapper;
        this.eventSourceRefMapper = eventSourceRefMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public EventDetailResponse getById(Long id, Long userId, String role) {
        CampusEvent event = campusEventMapper.selectById(id);
        if (event == null || !isVisibleTo(event, userId, role)) {
            throw new BusinessException("EVENT_NOT_FOUND", "事件不存在", HttpStatus.NOT_FOUND);
        }
        return toResponse(event);
    }

    @Transactional(readOnly = true)
    public EventSearchResponse search(
            String eventType,
            String status,
            String keyword,
            LocalDateTime startFrom,
            LocalDateTime startTo,
            Long userId,
            String role,
            long page,
            long size
    ) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<CampusEvent> query = new LambdaQueryWrapper<CampusEvent>()
                .eq(StringUtils.hasText(eventType), CampusEvent::getEventType, eventType)
                .eq(StringUtils.hasText(status), CampusEvent::getStatus, status)
                .ge(startFrom != null, CampusEvent::getStartTime, startFrom)
                .le(startTo != null, CampusEvent::getStartTime, startTo)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(CampusEvent::getTitle, keyword)
                        .or()
                        .like(CampusEvent::getSummary, keyword))
                .orderByDesc(CampusEvent::getPublishedAt)
                .orderByDesc(CampusEvent::getCreatedAt);
        if (!"ADMIN".equals(role)) {
            query.and(wrapper -> wrapper.eq(CampusEvent::getVisibility, "PUBLIC")
                    .or()
                    .isNull(CampusEvent::getVisibility)
                    .or(userId != null, nested -> nested.eq(CampusEvent::getOwnerUserId, userId)));
        }

        Page<CampusEvent> result = campusEventMapper.selectPage(Page.of(safePage, safeSize), query);
        List<EventDetailResponse> items = result.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return new EventSearchResponse(items, result.getTotal(), safePage, safeSize, result.hasNext());
    }

    private boolean isVisibleTo(CampusEvent event, Long userId, String role) {
        return "ADMIN".equals(role)
                || event.getVisibility() == null || "PUBLIC".equals(event.getVisibility())
                || (userId != null && userId.equals(event.getOwnerUserId()));
    }

    private EventDetailResponse toResponse(CampusEvent event) {
        List<EventSourceRefResponse> sources = loadSourceRefs(event.getId());
        return new EventDetailResponse(
                event.getId(),
                event.getTitle(),
                event.getSummary(),
                event.getEventType(),
                event.getSourceType(),
                event.getStatus(),
                "AI_PUBLISHED".equals(event.getStatus()),
                event.getStartTime(),
                event.getEndTime(),
                event.getLocation(),
                event.getOrganizer(),
                readStringList(event.getTargetScope()),
                readStringList(event.getTags()),
                event.getPublishedAt(),
                event.getCreatedAt(),
                event.getUpdatedAt(),
                sources
        );
    }

    private List<EventSourceRefResponse> loadSourceRefs(Long eventId) {
        if (eventId == null) {
            return List.of();
        }
        List<EventSourceRef> refs = eventSourceRefMapper.selectList(
                new LambdaQueryWrapper<EventSourceRef>()
                        .eq(EventSourceRef::getEventId, eventId)
                        .orderByAsc(EventSourceRef::getCreatedAt));
        return refs.stream()
                .map(ref -> new EventSourceRefResponse(
                        ref.getId(),
                        ref.getSourceId(),
                        ref.getRawDocId(),
                        ref.getSourceUrl(),
                        ref.getSourceTitle(),
                        ref.getContentHash(),
                        ref.getCreatedAt()))
                .toList();
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }
}

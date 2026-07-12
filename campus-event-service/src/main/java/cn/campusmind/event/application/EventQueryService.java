package cn.campusmind.event.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.event.controller.EventDetailResponse;
import cn.campusmind.event.controller.EventSearchResponse;
import cn.campusmind.event.domain.CampusEvent;
import cn.campusmind.event.infrastructure.mapper.CampusEventMapper;
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
    private final ObjectMapper objectMapper;

    public EventQueryService(CampusEventMapper campusEventMapper, ObjectMapper objectMapper) {
        this.campusEventMapper = campusEventMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public EventDetailResponse getById(Long id, Long userId) {
        CampusEvent event = campusEventMapper.selectById(id);
        if (event == null || !isVisibleTo(event, userId)) {
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
                .and(wrapper -> wrapper.eq(CampusEvent::getVisibility, "PUBLIC")
                        .or()
                        .isNull(CampusEvent::getVisibility)
                        .or(userId != null)
                        .eq(userId != null, CampusEvent::getOwnerUserId, userId))
                .orderByDesc(CampusEvent::getPublishedAt)
                .orderByDesc(CampusEvent::getCreatedAt);

        Page<CampusEvent> result = campusEventMapper.selectPage(Page.of(safePage, safeSize), query);
        List<EventDetailResponse> items = result.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return new EventSearchResponse(items, result.getTotal(), safePage, safeSize, result.hasNext());
    }

    private boolean isVisibleTo(CampusEvent event, Long userId) {
        return event.getVisibility() == null || "PUBLIC".equals(event.getVisibility())
                || (userId != null && userId.equals(event.getOwnerUserId()));
    }

    private EventDetailResponse toResponse(CampusEvent event) {
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
                event.getUpdatedAt()
        );
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

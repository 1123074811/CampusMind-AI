package cn.campusmind.feed.application;

import cn.campusmind.feed.controller.FeedItemResponse;
import cn.campusmind.feed.controller.FeedResponse;
import cn.campusmind.feed.domain.CampusEvent;
import cn.campusmind.feed.domain.UserProfile;
import cn.campusmind.feed.infrastructure.mapper.CampusEventMapper;
import cn.campusmind.feed.infrastructure.mapper.UserProfileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class FeedService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final CampusEventMapper campusEventMapper;
    private final UserProfileMapper userProfileMapper;
    private final ObjectMapper objectMapper;

    public FeedService(
            CampusEventMapper campusEventMapper,
            UserProfileMapper userProfileMapper,
            ObjectMapper objectMapper
    ) {
        this.campusEventMapper = campusEventMapper;
        this.userProfileMapper = userProfileMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public FeedResponse feed(Long userId, LocalDateTime cursor, int size, String eventType) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        UserProfile profile = userId == null ? null : userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId));
        Set<String> interests = new HashSet<>();
        if (profile != null) {
            interests.addAll(readStringList(profile.getInterestTags()));
            interests.addAll(readStringList(profile.getCourseCodes()));
        }

        LambdaQueryWrapper<CampusEvent> query = new LambdaQueryWrapper<CampusEvent>()
                .notIn(CampusEvent::getStatus, List.of("REJECTED", "OFFLINE"))
                .eq(StringUtils.hasText(eventType), CampusEvent::getEventType, eventType)
                .lt(cursor != null, CampusEvent::getPublishedAt, cursor)
                .orderByDesc(CampusEvent::getPublishedAt)
                .orderByDesc(CampusEvent::getCreatedAt);

        Page<CampusEvent> page = campusEventMapper.selectPage(Page.of(1, safeSize + 1L), query);
        List<CampusEvent> records = page.getRecords();
        boolean hasMore = records.size() > safeSize;
        List<FeedItemResponse> items = records.stream()
                .limit(safeSize)
                .map(event -> toItem(event, interests))
                .toList();
        LocalDateTime nextCursor = items.isEmpty()
                ? null
                : records.get(Math.min(items.size(), records.size()) - 1).getPublishedAt();
        return new FeedResponse(items, nextCursor, hasMore);
    }

    private FeedItemResponse toItem(CampusEvent event, Set<String> interests) {
        List<String> tags = readStringList(event.getTags());
        int relevanceScore = 0;
        for (String tag : tags) {
            if (interests.contains(tag)) {
                relevanceScore += 20;
            }
        }
        for (String scope : readStringList(event.getTargetScope())) {
            if (interests.contains(scope)) {
                relevanceScore += 10;
            }
        }
        return new FeedItemResponse(
                event.getId(),
                event.getTitle(),
                event.getSummary(),
                event.getEventType(),
                event.getStatus(),
                "AI_PUBLISHED".equals(event.getStatus()),
                event.getConfidence(),
                event.getStartTime(),
                event.getLocation(),
                tags,
                relevanceScore
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

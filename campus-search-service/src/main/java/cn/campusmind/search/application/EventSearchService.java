package cn.campusmind.search.application;

import cn.campusmind.search.config.SearchProperties;
import cn.campusmind.search.domain.CampusEvent;
import cn.campusmind.search.domain.UserProfile;
import cn.campusmind.search.infrastructure.mapper.CampusEventMapper;
import cn.campusmind.search.infrastructure.mapper.UserProfileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 事件检索服务：基于 MySQL 的条件检索、个人日程检索、语义检索关键字降级。
 * 向量库（PGVector）未接入时，语义检索降级为关键字 like 检索。
 */
@Service
public class EventSearchService {

    private static final List<String> VISIBLE_STATUS = List.of("AI_PUBLISHED", "REVIEWED", "CORRECTED");

    private final CampusEventMapper campusEventMapper;
    private final UserProfileMapper userProfileMapper;
    private final SearchProperties properties;

    public EventSearchService(CampusEventMapper campusEventMapper,
                              UserProfileMapper userProfileMapper,
                              SearchProperties properties) {
        this.campusEventMapper = campusEventMapper;
        this.userProfileMapper = userProfileMapper;
        this.properties = properties;
    }

    public List<CampusEvent> feedQuery(DecisionPlan plan) {
        LocalDateTime[] window = timeWindow(plan.timeRange());
        int topK = resolveTopK(plan, properties.defaultTopK());
        List<String> eventTypes = plan.eventTypes();
        LambdaQueryWrapper<CampusEvent> query = new LambdaQueryWrapper<CampusEvent>()
                .in(CampusEvent::getStatus, VISIBLE_STATUS)
                .in(eventTypes != null && !eventTypes.isEmpty(), CampusEvent::getEventType, eventTypes)
                .ge(window != null, CampusEvent::getStartTime, window == null ? null : window[0])
                .le(window != null, CampusEvent::getStartTime, window == null ? null : window[1])
                .orderByDesc(CampusEvent::getPublishedAt)
                .orderByDesc(CampusEvent::getCreatedAt)
                .last("LIMIT " + topK);
        return campusEventMapper.selectList(query);
    }

    public List<CampusEvent> personalSchedule(Long userId, DecisionPlan plan) {
        UserProfile profile = userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>()
                        .eq(UserProfile::getUserId, userId)
                        .last("LIMIT 1"));
        int topK = resolveTopK(plan, properties.defaultTopK());
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<CampusEvent> query = new LambdaQueryWrapper<CampusEvent>()
                .in(CampusEvent::getStatus, VISIBLE_STATUS)
                .ge(CampusEvent::getStartTime, now)
                .orderByAsc(CampusEvent::getStartTime)
                .last("LIMIT " + topK);
        if (profile != null && StringUtils.hasText(profile.getCollege())) {
            query.like(CampusEvent::getTargetScope, profile.getCollege());
        }
        return campusEventMapper.selectList(query);
    }

    public List<CampusEvent> semanticSearch(String query, DecisionPlan plan) {
        int topK = resolveTopK(plan, properties.keywordFallbackTopK());
        String keyword = sanitizeKeyword(query);
        LambdaQueryWrapper<CampusEvent> wrapper = new LambdaQueryWrapper<CampusEvent>()
                .in(CampusEvent::getStatus, VISIBLE_STATUS)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(CampusEvent::getTitle, keyword)
                        .or()
                        .like(CampusEvent::getSummary, keyword))
                .orderByDesc(CampusEvent::getPublishedAt)
                .last("LIMIT " + topK);
        return campusEventMapper.selectList(wrapper);
    }

    private static int resolveTopK(DecisionPlan plan, int fallback) {
        int topK = plan.topK();
        return topK > 0 ? topK : fallback;
    }

    private static String sanitizeKeyword(String query) {
        if (query == null) {
            return null;
        }
        String cleaned = query.trim()
                .replaceAll("(今天|明天|本周|这周|最近|有什么|有哪些|的|了|吗|呢|？|\\?)", " ")
                .trim();
        return cleaned.isBlank() ? query.trim() : cleaned;
    }

    private static LocalDateTime[] timeWindow(String timeRange) {
        if (timeRange == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        return switch (timeRange) {
            case "TODAY" -> new LocalDateTime[]{now.toLocalDate().atStartOfDay(), now.toLocalDate().atTime(23, 59, 59)};
            case "TOMORROW" -> {
                LocalDate t = now.toLocalDate().plusDays(1);
                yield new LocalDateTime[]{t.atStartOfDay(), t.atTime(23, 59, 59)};
            }
            case "THIS_WEEK", "RECENT" -> new LocalDateTime[]{now.minusDays(7), now.plusDays(1)};
            default -> null;
        };
    }
}

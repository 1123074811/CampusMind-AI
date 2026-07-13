package cn.campusmind.feed.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.feed.controller.*;
import cn.campusmind.feed.domain.DataSource;
import cn.campusmind.feed.domain.InformationItem;
import cn.campusmind.feed.domain.UserInformationState;
import cn.campusmind.feed.domain.UserSourceSubscription;
import cn.campusmind.feed.infrastructure.mapper.DataSourceMapper;
import cn.campusmind.feed.infrastructure.mapper.InformationItemMapper;
import cn.campusmind.feed.infrastructure.mapper.UserInformationStateMapper;
import cn.campusmind.feed.infrastructure.mapper.UserSourceSubscriptionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class InformationService {

    private static final Set<String> VISIBLE_ITEM_STATUS = Set.of("ACTIVE", "UPDATED");
    private static final Set<String> READ_STATUSES = Set.of("NEW", "READ", "FAVORITED", "ARCHIVED");
    private static final int PREVIEW_LENGTH = 160;
    private static final TypeReference<Map<String, Object>> AI_CARD = new TypeReference<>() { };

    private final InformationItemMapper informationItemMapper;
    private final UserInformationStateMapper userInformationStateMapper;
    private final UserSourceSubscriptionMapper userSourceSubscriptionMapper;
    private final DataSourceMapper dataSourceMapper;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final int sourceSubscriptionWeight;

    public InformationService(InformationItemMapper informationItemMapper,
                              UserInformationStateMapper userInformationStateMapper,
                              UserSourceSubscriptionMapper userSourceSubscriptionMapper,
                              DataSourceMapper dataSourceMapper,
                              ObjectMapper objectMapper,
                              JdbcTemplate jdbcTemplate,
                              @Value("${campus.feed.source-subscription-weight:100}") int sourceSubscriptionWeight) {
        this.informationItemMapper = informationItemMapper;
        this.userInformationStateMapper = userInformationStateMapper;
        this.userSourceSubscriptionMapper = userSourceSubscriptionMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.sourceSubscriptionWeight = sourceSubscriptionWeight;
    }

    @Transactional(readOnly = true)
    public InformationFeedResponse feed(Long userId, LocalDateTime cursor, int size) {
        return feed(userId, cursor, null, null, size, "ALL");
    }

    @Transactional(readOnly = true)
    public InformationFeedResponse feed(Long userId, LocalDateTime cursor, Long cursorId,
                                        Integer cursorSubscriptionMatch, int size, String mode) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        String normalizedMode = mode == null ? "ALL" : mode.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "SUBSCRIBED_ONLY").contains(normalizedMode)) {
            throw new BusinessException("FEED_MODE_INVALID", "信息流模式仅支持 ALL 或 SUBSCRIBED_ONLY", HttpStatus.BAD_REQUEST);
        }
        boolean onlySubscribed = "SUBSCRIBED_ONLY".equals(normalizedMode);
        List<InformationItem> records = informationItemMapper.selectRankedFeed(
                userId, onlySubscribed, cursor, cursorId, cursorSubscriptionMatch, safeSize + 1);
        boolean hasMore = records.size() > safeSize;
        List<InformationItem> visibleRecords = records.stream().limit(safeSize).toList();
        Set<Long> subscribedSourceIds = subscribedSourceIds(userId);
        Map<Long, String> readStatuses = readStatuses(userId, visibleRecords);
        List<InformationFeedItemResponse> items = visibleRecords.stream()
                .map(item -> toFeedItem(item,
                        normalizeReadStatus(readStatuses.getOrDefault(item.getId(), "NEW")),
                        subscribedSourceIds.contains(item.getSourceId())))
                .toList();
        LocalDateTime nextCursor = items.isEmpty()
                ? null
                : visibleRecords.get(visibleRecords.size() - 1).getFetchedAt();
        Long nextCursorId = items.isEmpty() ? null : visibleRecords.get(visibleRecords.size() - 1).getId();
        Integer nextSubscriptionMatch = items.isEmpty() ? null
                : (subscribedSourceIds.contains(visibleRecords.get(visibleRecords.size() - 1).getSourceId()) ? 1 : 0);
        return new InformationFeedResponse(items, nextCursor, nextCursorId, nextSubscriptionMatch, hasMore);
    }

    private Set<Long> subscribedSourceIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return userSourceSubscriptionMapper.selectList(new LambdaQueryWrapper<UserSourceSubscription>()
                        .eq(UserSourceSubscription::getUserId, userId)
                        .eq(UserSourceSubscription::getEnabled, 1))
                .stream().map(UserSourceSubscription::getSourceId).collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public InformationDetailResponse detail(Long userId, Long itemId) {
        InformationItem item = informationItemMapper.selectById(itemId);
        if (item == null || !isVisible(item)) {
            throw new BusinessException("INFORMATION_ITEM_NOT_FOUND", "信息条目不存在", HttpStatus.NOT_FOUND);
        }
        String readStatus = normalizeReadStatus(readStatus(userId, itemId));
        return new InformationDetailResponse(
                item.getId(),
                item.getTitle(),
                item.getDetailContent(),
                item.getSourceName(),
                item.getSourceUrl(),
                item.getItemUrl(),
                item.getPublishTime(),
                item.getFetchedAt(),
                item.getContentHash(),
                item.getItemStatus(),
                readStatus,
                item.getCreatedAt(),
                item.getUpdatedAt(),
                item.getAiStatus(),
                item.getAiEventType(),
                item.getAiSummary(),
                item.getAiNeedReview(),
                aiCard(item)
        );
    }

    /**
     * 幂等创建信息条目（按 contentHash 去重），供 import-service 内部调用。
     */
    @Transactional
    public Long createItem(CreateInformationItemRequest request) {
        InformationItem existing = informationItemMapper.selectOne(
                new LambdaQueryWrapper<InformationItem>()
                        .eq(InformationItem::getContentHash, request.contentHash())
                        .last("LIMIT 1"));
        if (existing != null) {
            return existing.getId();
        }
        LocalDateTime now = LocalDateTime.now();
        InformationItem item = new InformationItem();
        DataSource source = dataSourceMapper.selectOne(new LambdaQueryWrapper<DataSource>()
                .eq(DataSource::getName, request.sourceName())
                .last("LIMIT 1"));
        if (source == null) {
            throw new BusinessException("DATA_SOURCE_NOT_FOUND", "数据源不存在", HttpStatus.BAD_REQUEST);
        }
        item.setSourceId(source.getId());
        item.setTitle(request.title());
        item.setDetailContent(request.detailContent());
        item.setSourceName(request.sourceName());
        item.setSourceUrl(request.sourceUrl() == null ? "" : request.sourceUrl());
        item.setItemUrl(request.itemUrl() == null ? "campusmind://user-import" : request.itemUrl());
        item.setContentHash(request.contentHash());
        item.setFetchedAt(now);
        item.setItemStatus("ACTIVE");
        item.setParseStatus("DETAIL_SUCCESS");
        item.setAiStatus("PENDING");
        informationItemMapper.insert(item);
        return item.getId();
    }

    @Transactional
    public InformationDetailResponse updateReadStatus(Long userId, Long itemId, String readStatus) {
        if (userId == null) {
            throw new BusinessException("USER_REQUIRED", "更新阅读状态需要用户身份", HttpStatus.UNAUTHORIZED);
        }
        String normalizedReadStatus = normalizeReadStatus(readStatus);
        if (!READ_STATUSES.contains(readStatus)) {
            throw new BusinessException("READ_STATUS_INVALID", "阅读状态无效", HttpStatus.BAD_REQUEST);
        }
        InformationItem item = informationItemMapper.selectById(itemId);
        if (item == null || !isVisible(item)) {
            throw new BusinessException("INFORMATION_ITEM_NOT_FOUND", "信息条目不存在", HttpStatus.NOT_FOUND);
        }
        UserInformationState existing = userInformationStateMapper.selectOne(new LambdaQueryWrapper<UserInformationState>()
                .eq(UserInformationState::getItemId, itemId)
                .eq(UserInformationState::getUserId, userId)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        UserInformationState state = existing == null ? new UserInformationState() : existing;
        state.setUserId(userId);
        state.setItemId(itemId);
        if (existing == null) {
            state.setFirstSeenAt(now);
        }
        if ("FAVORITED".equals(normalizedReadStatus)) {
            if (state.getReadAt() == null) {
                state.setReadAt(now);
            }
            state.setFavoritedAt(now);
        } else if ("READ".equals(normalizedReadStatus)) {
            state.setReadAt(now);
            state.setFavoritedAt(null);
        } else if ("NEW".equals(normalizedReadStatus)) {
            state.setReadAt(null);
            state.setFavoritedAt(null);
        }
        if (existing == null) {
            userInformationStateMapper.insert(state);
        } else {
            userInformationStateMapper.updateById(state);
        }
        return detail(userId, itemId);
    }

    private Map<Long, String> readStatuses(Long userId, List<InformationItem> items) {
        if (userId == null || items.isEmpty()) {
            return Map.of();
        }
        List<Long> itemIds = items.stream().map(InformationItem::getId).toList();
        return userInformationStateMapper.selectList(new LambdaQueryWrapper<UserInformationState>()
                        .eq(UserInformationState::getUserId, userId)
                        .in(UserInformationState::getItemId, itemIds))
                .stream()
                .collect(Collectors.toMap(UserInformationState::getItemId, InformationService::readStatus,
                        (first, second) -> first));
    }

    private String readStatus(Long userId, Long itemId) {
        if (userId == null) {
            return "NEW";
        }
        UserInformationState state = userInformationStateMapper.selectOne(new LambdaQueryWrapper<UserInformationState>()
                .eq(UserInformationState::getUserId, userId)
                .eq(UserInformationState::getItemId, itemId)
                .last("LIMIT 1"));
        return state == null ? "NEW" : readStatus(state);
    }

    private static String readStatus(UserInformationState state) {
        if (state.getFavoritedAt() != null) {
            return "FAVORITED";
        }
        return state.getReadAt() != null ? "READ" : "NEW";
    }

    private String normalizeReadStatus(String status) {
        return "ARCHIVED".equals(status) ? "FAVORITED" : status;
    }

    private InformationFeedItemResponse toFeedItem(InformationItem item, String readStatus) {
        return toFeedItem(item, readStatus, false);
    }

    private InformationFeedItemResponse toFeedItem(InformationItem item, String readStatus, boolean subscribed) {
        return new InformationFeedItemResponse(
                item.getId(),
                item.getTitle(),
                item.getSourceName(),
                item.getPublishTime(),
                item.getFetchedAt(),
                readStatus,
                item.getItemStatus(),
                preview(StringUtils.hasText(item.getAiSummary()) ? item.getAiSummary() : item.getDetailContent()),
                item.getItemUrl(),
                item.getAiStatus(),
                item.getAiEventType(),
                item.getAiSummary(),
                item.getAiNeedReview(),
                aiCard(item),
                subscribed ? sourceSubscriptionWeight : 0,
                subscribed ? List.of("来自你订阅的" + item.getSourceName()) : List.of()
        );
    }

    private Map<String, Object> aiCard(InformationItem item) {
        if (!StringUtils.hasText(item.getAiCardJson())) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(item.getAiCardJson(), AI_CARD);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private boolean isVisible(InformationItem item) {
        return VISIBLE_ITEM_STATUS.contains(item.getItemStatus())
                && "DETAIL_SUCCESS".equals(item.getParseStatus());
    }

    private String preview(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim().replaceAll("\\s+", " ");
        return normalized.length() <= PREVIEW_LENGTH
                ? normalized
                : normalized.substring(0, PREVIEW_LENGTH);
    }

    // ========== 新增接口：用户统计 ==========

    @Transactional(readOnly = true)
    public UserStatsResponse stats(Long userId) {
        if (userId == null) {
            return new UserStatsResponse(0, 0, 0);
        }
        long readCount = userInformationStateMapper.selectCount(new LambdaQueryWrapper<UserInformationState>()
                .eq(UserInformationState::getUserId, userId)
                .isNotNull(UserInformationState::getReadAt));
        long favoriteCount = userInformationStateMapper.selectCount(new LambdaQueryWrapper<UserInformationState>()
                .eq(UserInformationState::getUserId, userId)
                .isNotNull(UserInformationState::getFavoritedAt));
        long subscriptionCount = userSourceSubscriptionMapper.selectCount(new LambdaQueryWrapper<UserSourceSubscription>()
                .eq(UserSourceSubscription::getUserId, userId)
                .eq(UserSourceSubscription::getEnabled, 1));
        return new UserStatsResponse(readCount, favoriteCount, subscriptionCount);
    }

    // ========== 新增接口：收藏夹 ==========

    @Transactional(readOnly = true)
    public InformationFeedResponse favorites(Long userId, int size) {
        if (userId == null) {
            return new InformationFeedResponse(List.of(), null, false);
        }
        int safeSize = Math.min(Math.max(size, 1), 50);
        List<UserInformationState> states = userInformationStateMapper.selectList(
                new LambdaQueryWrapper<UserInformationState>()
                        .eq(UserInformationState::getUserId, userId)
                        .isNotNull(UserInformationState::getFavoritedAt)
                        .orderByDesc(UserInformationState::getFavoritedAt)
                        .last("LIMIT " + safeSize));
        if (states.isEmpty()) {
            return new InformationFeedResponse(List.of(), null, false);
        }
        List<Long> itemIds = states.stream().map(UserInformationState::getItemId).toList();
        Map<Long, InformationItem> items = informationItemMapper.selectBatchIds(itemIds)
                .stream().collect(Collectors.toMap(InformationItem::getId, i -> i));
        List<InformationFeedItemResponse> responses = states.stream()
                .map(s -> items.get(s.getItemId()))
                .filter(Objects::nonNull)
                .filter(this::isVisible)
                .map(item -> toFeedItem(item, "FAVORITED"))
                .toList();
        return new InformationFeedResponse(responses, null, false);
    }

    // ========== 新增接口：阅读历史 ==========

    @Transactional(readOnly = true)
    public InformationFeedResponse readHistory(Long userId, int size) {
        if (userId == null) {
            return new InformationFeedResponse(List.of(), null, false);
        }
        int safeSize = Math.min(Math.max(size, 1), 50);
        List<UserInformationState> states = userInformationStateMapper.selectList(
                new LambdaQueryWrapper<UserInformationState>()
                        .eq(UserInformationState::getUserId, userId)
                        .isNotNull(UserInformationState::getReadAt)
                        .orderByDesc(UserInformationState::getReadAt)
                        .last("LIMIT " + safeSize));
        if (states.isEmpty()) {
            return new InformationFeedResponse(List.of(), null, false);
        }
        List<Long> itemIds = states.stream().map(UserInformationState::getItemId).toList();
        Map<Long, InformationItem> items = informationItemMapper.selectBatchIds(itemIds)
                .stream().collect(Collectors.toMap(InformationItem::getId, i -> i));
        List<InformationFeedItemResponse> responses = states.stream()
                .filter(state -> items.containsKey(state.getItemId()))
                .filter(state -> isVisible(items.get(state.getItemId())))
                .map(state -> toFeedItem(items.get(state.getItemId()), readStatus(state)))
                .toList();
        return new InformationFeedResponse(responses, null, false);
    }

    // ========== 新增接口：我的订阅 ==========

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> subscriptions(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<UserSourceSubscription> subs = userSourceSubscriptionMapper.selectList(
                new LambdaQueryWrapper<UserSourceSubscription>()
                        .eq(UserSourceSubscription::getUserId, userId)
                        .orderByDesc(UserSourceSubscription::getCreatedAt));
        if (subs.isEmpty()) {
            return List.of();
        }
        List<Long> sourceIds = subs.stream().map(UserSourceSubscription::getSourceId).toList();
        Map<Long, DataSource> sources = dataSourceMapper.selectBatchIds(sourceIds)
                .stream().collect(Collectors.toMap(DataSource::getId, s -> s));
        return subs.stream()
                .map(sub -> {
                    DataSource ds = sources.get(sub.getSourceId());
                    if (ds == null) return null;
                    return new SubscriptionResponse(
                            ds.getId(),
                            ds.getName(),
                            ds.getSourceType(),
                            sub.getEnabled() == 1,
                            sub.getCreatedAt());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    // ========== 新增接口：更新订阅状态 ==========

    @Transactional
    public SubscriptionResponse updateSubscription(Long userId, Long sourceId, boolean enabled) {
        if (userId == null) {
            throw new BusinessException("USER_REQUIRED", "需要用户身份", HttpStatus.UNAUTHORIZED);
        }
        DataSource ds = dataSourceMapper.selectById(sourceId);
        if (ds == null) {
            throw new BusinessException("SOURCE_NOT_FOUND", "数据源不存在", HttpStatus.NOT_FOUND);
        }
        UserSourceSubscription existing = userSourceSubscriptionMapper.selectOne(
                new LambdaQueryWrapper<UserSourceSubscription>()
                        .eq(UserSourceSubscription::getUserId, userId)
                        .eq(UserSourceSubscription::getSourceId, sourceId)
                        .last("LIMIT 1"));
        if (existing == null) {
            UserSourceSubscription sub = new UserSourceSubscription();
            sub.setUserId(userId);
            sub.setSourceId(sourceId);
            sub.setEnabled(enabled ? 1 : 0);
            sub.setCreatedAt(LocalDateTime.now());
            userSourceSubscriptionMapper.insert(sub);
        } else {
            existing.setEnabled(enabled ? 1 : 0);
            userSourceSubscriptionMapper.updateById(existing);
        }
        return new SubscriptionResponse(ds.getId(), ds.getName(), ds.getSourceType(), enabled, null);
    }

    @Transactional
    public Map<String, Object> confirmAction(Long userId, Long itemId, String title) {
        if (userId == null) {
            throw new BusinessException("USER_REQUIRED", "确认行动需要用户身份", HttpStatus.UNAUTHORIZED);
        }
        InformationItem item = informationItemMapper.selectById(itemId);
        if (item == null || !isVisible(item)) {
            throw new BusinessException("INFORMATION_ITEM_NOT_FOUND", "信息条目不存在", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> card = aiCard(item);
        List<String> actions = stringList(card.get("requiredActions"));
        if (!"SUCCESS".equals(item.getAiStatus()) || Boolean.TRUE.equals(item.getAiNeedReview())
                || !StringUtils.hasText(title) || !actions.contains(title.trim())) {
            throw new BusinessException("ACTION_NOT_CONFIRMABLE", "该行动尚未通过AI质量校验", HttpStatus.CONFLICT);
        }
        LocalDateTime dueAt = parseDateTime(card.get("registrationDeadline"));
        try {
            jdbcTemplate.update("""
                    INSERT INTO user_action_item (
                      user_id, information_item_id, title, due_at, original_url, status, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, 'CONFIRMED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, userId, itemId, title.trim(), dueAt, item.getItemUrl());
        } catch (DuplicateKeyException ignored) {
            // 用户重复点击时返回同一行动，不重复创建提醒。
        }
        Map<String, Object> action = jdbcTemplate.queryForMap("""
                SELECT id, information_item_id AS informationItemId, title, due_at AS dueAt,
                       original_url AS originalUrl, status
                FROM user_action_item WHERE user_id = ? AND information_item_id = ? AND title = ?
                """, userId, itemId, title.trim());
        if (dueAt != null && dueAt.isAfter(LocalDateTime.now())) {
            LocalDateTime remindAt = dueAt.minusDays(1);
            if (remindAt.isAfter(LocalDateTime.now())) {
                try {
                    jdbcTemplate.update("""
                            INSERT INTO user_reminder (
                              action_item_id, user_id, remind_at, status, created_at
                            ) VALUES (?, ?, ?, 'PENDING', CURRENT_TIMESTAMP)
                            """, action.get("id"), userId, remindAt);
                } catch (DuplicateKeyException ignored) {
                    // 同一行动同一提醒时间由数据库唯一键保证幂等。
                }
            }
        }
        return action;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> actions(Long userId) {
        if (userId == null) {
            throw new BusinessException("USER_REQUIRED", "查看行动需要用户身份", HttpStatus.UNAUTHORIZED);
        }
        return jdbcTemplate.queryForList("""
                SELECT id, information_item_id AS informationItemId, title, due_at AS dueAt,
                       original_url AS originalUrl, status, created_at AS createdAt
                FROM user_action_item WHERE user_id = ? ORDER BY due_at IS NULL, due_at, id DESC
                """, userId);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private static LocalDateTime parseDateTime(Object value) {
        if (!(value instanceof String text) || !StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim());
        } catch (Exception ignored) {
            try {
                return java.time.OffsetDateTime.parse(text.trim()).toLocalDateTime();
            } catch (Exception ignoredOffset) {
                return null;
            }
        }
    }

}

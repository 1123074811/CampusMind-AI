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
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
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

    public InformationService(InformationItemMapper informationItemMapper,
                              UserInformationStateMapper userInformationStateMapper,
                              UserSourceSubscriptionMapper userSourceSubscriptionMapper,
                              DataSourceMapper dataSourceMapper,
                              ObjectMapper objectMapper) {
        this.informationItemMapper = informationItemMapper;
        this.userInformationStateMapper = userInformationStateMapper;
        this.userSourceSubscriptionMapper = userSourceSubscriptionMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public InformationFeedResponse feed(Long userId, LocalDateTime cursor, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        LambdaQueryWrapper<InformationItem> query = new LambdaQueryWrapper<InformationItem>()
                .in(InformationItem::getItemStatus, VISIBLE_ITEM_STATUS)
                .eq(InformationItem::getParseStatus, "DETAIL_SUCCESS")
                .lt(cursor != null, InformationItem::getFetchedAt, cursor)
                .orderByDesc(InformationItem::getFetchedAt)
                .orderByDesc(InformationItem::getId);

        Page<InformationItem> page = informationItemMapper.selectPage(Page.of(1, safeSize + 1L), query);
        List<InformationItem> records = page.getRecords();
        boolean hasMore = records.size() > safeSize;
        List<InformationItem> visibleRecords = records.stream().limit(safeSize).toList();
        Map<Long, String> readStatuses = readStatuses(userId, visibleRecords);
        List<InformationFeedItemResponse> items = visibleRecords.stream()
                .map(item -> toFeedItem(item, normalizeReadStatus(readStatuses.getOrDefault(item.getId(), "NEW"))))
                .toList();
        LocalDateTime nextCursor = items.isEmpty()
                ? null
                : visibleRecords.get(visibleRecords.size() - 1).getFetchedAt();
        return new InformationFeedResponse(items, nextCursor, hasMore);
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

    private static final Map<String, Long> SOURCE_NAME_TO_ID = Map.of(
            "用户文本提交", 9405L,
            "用户文件上传", 9406L,
            "雨课堂导入", 9403L,
            "用户截图 OCR", 9404L
    );

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
        item.setSourceId(SOURCE_NAME_TO_ID.getOrDefault(request.sourceName(), 0L));
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
        state.setReadStatus(normalizedReadStatus);
        if (existing == null) {
            state.setFirstSeenAt(now);
        }
        if ("READ".equals(normalizedReadStatus)) {
            state.setReadAt(now);
        }
        if ("FAVORITED".equals(normalizedReadStatus)) {
            state.setArchivedAt(now);
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
                .collect(Collectors.toMap(UserInformationState::getItemId, UserInformationState::getReadStatus,
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
        return state == null ? "NEW" : state.getReadStatus();
    }

    private String normalizeReadStatus(String status) {
        return "ARCHIVED".equals(status) ? "FAVORITED" : status;
    }

    private InformationFeedItemResponse toFeedItem(InformationItem item, String readStatus) {
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
                aiCard(item)
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
                .eq(UserInformationState::getReadStatus, "READ"));
        long favoriteCount = userInformationStateMapper.selectCount(new LambdaQueryWrapper<UserInformationState>()
                .eq(UserInformationState::getUserId, userId)
                .eq(UserInformationState::getReadStatus, "FAVORITED"));
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
                        .eq(UserInformationState::getReadStatus, "FAVORITED")
                        .orderByDesc(UserInformationState::getArchivedAt)
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
                        .eq(UserInformationState::getReadStatus, "READ")
                        .orderByDesc(UserInformationState::getReadAt)
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
                .map(item -> toFeedItem(item, "READ"))
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

}

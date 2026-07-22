package cn.campusmind.feed.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.feed.controller.*;
import cn.campusmind.feed.domain.DataSource;
import cn.campusmind.feed.domain.InformationItem;
import cn.campusmind.feed.domain.UserInformationState;
import cn.campusmind.feed.domain.UserProfile;
import cn.campusmind.feed.domain.UserSourceSubscription;
import cn.campusmind.feed.infrastructure.mapper.DataSourceMapper;
import cn.campusmind.feed.infrastructure.mapper.InformationItemMapper;
import cn.campusmind.feed.infrastructure.mapper.UserInformationStateMapper;
import cn.campusmind.feed.infrastructure.mapper.UserProfileMapper;
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
    private static final List<String> ACTION_VERBS = List.of(
            "报名", "提交", "上传", "缴费", "预约", "申请", "填写", "打印", "下载", "领取", "办理", "准备");
    private static final List<String> NON_ACTION_RULES = List.of(
            "不得", "禁止", "严禁", "不准", "不可", "迟到", "入场", "离场", "安检", "查询成绩", "公布成绩");
    private static final int PREVIEW_LENGTH = 160;
    private static final TypeReference<Map<String, Object>> AI_CARD = new TypeReference<>() { };
    private static final java.util.regex.Pattern DEADLINE_LINE =
            java.util.regex.Pattern.compile("(?m)^截止时间：\\s*([^\\r\\n]+)");

    private final InformationItemMapper informationItemMapper;
    private final UserInformationStateMapper userInformationStateMapper;
    private final UserSourceSubscriptionMapper userSourceSubscriptionMapper;
    private final DataSourceMapper dataSourceMapper;
    private final UserProfileMapper userProfileMapper;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final int sourceSubscriptionWeight;

    public InformationService(InformationItemMapper informationItemMapper,
                              UserInformationStateMapper userInformationStateMapper,
                              UserSourceSubscriptionMapper userSourceSubscriptionMapper,
                              DataSourceMapper dataSourceMapper,
                              UserProfileMapper userProfileMapper,
                              ObjectMapper objectMapper,
                              JdbcTemplate jdbcTemplate,
                              @Value("${campus.feed.source-subscription-weight:12}") int sourceSubscriptionWeight) {
        this.informationItemMapper = informationItemMapper;
        this.userInformationStateMapper = userInformationStateMapper;
        this.userSourceSubscriptionMapper = userSourceSubscriptionMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.userProfileMapper = userProfileMapper;
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
        Set<Long> subscribedSourceIds = subscribedSourceIds(userId);
        // ponytail: 当前每用户仅数百条；可见池超过 1 万条时再改为物化推荐分与数据库游标。
        List<InformationItem> records = informationItemMapper.selectFeedCandidates(userId, onlySubscribed);
        List<UserInformationState> states = userStates(userId, records);
        Map<Long, String> readStatuses = states.stream().collect(Collectors.toMap(
                UserInformationState::getItemId, InformationService::readStatus, (first, second) -> first));
        boolean personalized = hasPersonalizationConsent(userId);
        UserProfile userProfile = personalized ? loadUserProfile(userId) : null;
        RankingSignals signals = personalized ? rankingSignals(userId, records, states) : RankingSignals.empty();
        LocalDateTime now = LocalDateTime.now();

        List<InformationFeedItemResponse> ranked = records.stream()
                .map(item -> toRankedFeedItem(item,
                        normalizeReadStatus(readStatuses.getOrDefault(item.getId(), "NEW")),
                        subscribedSourceIds.contains(item.getSourceId()), userProfile, signals, now, personalized))
                .sorted(InformationService::compareRankedItems)
                .filter(item -> isAfterCursor(item, cursor, cursorId, cursorSubscriptionMatch))
                .toList();
        boolean hasMore = ranked.size() > safeSize;
        List<InformationFeedItemResponse> items = ranked.stream().limit(safeSize).toList();
        InformationFeedItemResponse last = items.isEmpty() ? null : items.get(items.size() - 1);
        LocalDateTime nextCursor = last == null ? null : rankingTimestamp(last);
        Long nextCursorId = last == null ? null : last.id();
        Integer nextRecommendationScore = last == null ? null : last.recommendationScore();
        return new InformationFeedResponse(items, nextCursor, nextCursorId,
                nextRecommendationScore, hasMore, records.size());
    }

    @Transactional
    public int deleteOwnedRainItems(Long userId) {
        if (userId == null) {
            throw new BusinessException("USER_REQUIRED", "删除雨课堂信息需要用户身份", HttpStatus.UNAUTHORIZED);
        }
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT id FROM information_item
                WHERE submitted_by_user_id = ? AND source_name = '雨课堂导入'
                """, Long.class, userId);
        return deleteInformationItems(ids);
    }

    @Transactional
    public int deleteOwnedRainItem(Long userId, Long eventId) {
        if (userId == null) {
            throw new BusinessException("USER_REQUIRED", "删除雨课堂信息需要用户身份", HttpStatus.UNAUTHORIZED);
        }
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT DISTINCT i.id
                FROM information_item i
                JOIN event_source_ref r ON r.content_hash = i.content_hash
                JOIN campus_event e ON e.id = r.event_id
                WHERE e.id = ? AND e.owner_user_id = ?
                  AND e.source_type = 'RAIN_CLASSROOM'
                  AND i.submitted_by_user_id = ? AND i.source_name = '雨课堂导入'
                """, Long.class, eventId, userId, userId);
        return deleteInformationItems(ids);
    }

    private int deleteInformationItems(List<Long> ids) {
        for (Long id : ids) {
            jdbcTemplate.update("DELETE FROM user_reminder WHERE action_item_id IN (SELECT id FROM user_action_item WHERE information_item_id = ?)", id);
            jdbcTemplate.update("DELETE FROM user_action_item WHERE information_item_id = ?", id);
            jdbcTemplate.update("DELETE FROM user_information_state WHERE item_id = ?", id);
            informationItemMapper.deleteById(id);
        }
        return ids.size();
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

    private boolean hasPersonalizationConsent(Long userId) {
        if (userId == null) {
            return false;
        }
        List<Integer> grants = jdbcTemplate.queryForList("""
                SELECT granted FROM user_consent_record
                WHERE user_id = ? AND consent_type = 'PERSONALIZATION'
                ORDER BY id DESC LIMIT 1
                """, Integer.class, userId);
        return !grants.isEmpty() && grants.get(0) == 1;
    }

    @Transactional(readOnly = true)
    public InformationDetailResponse detail(Long userId, Long itemId) {
        InformationItem item = informationItemMapper.selectById(itemId);
        if (item == null || !isVisible(item, userId)) {
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
                aiCard(item),
                item.getSubmittedBy(),
                item.getSubmittedByUserId()
        );
    }

    /**
     * 幂等创建信息条目（按 contentHash 去重），供 import-service 内部调用。
     */
    @Transactional
    public Long createItem(CreateInformationItemRequest request) {
        LambdaQueryWrapper<InformationItem> duplicateQuery = new LambdaQueryWrapper<InformationItem>()
                .eq(InformationItem::getContentHash, request.contentHash());
        if (request.submittedByUserId() == null) {
            duplicateQuery.isNull(InformationItem::getSubmittedByUserId);
        } else {
            duplicateQuery.eq(InformationItem::getSubmittedByUserId, request.submittedByUserId());
        }
        InformationItem existing = informationItemMapper.selectOne(duplicateQuery.last("LIMIT 1"));
        if (existing != null) {
            String rainType = rainEventType(request.sourceName(), request.detailContent());
            boolean changed = false;
            if (request.publishTime() != null && !request.publishTime().equals(existing.getPublishTime())) {
                existing.setPublishTime(request.publishTime());
                changed = true;
            }
            if (rainType != null && !rainType.equals(existing.getAiEventType())) {
                existing.setAiEventType(rainType);
                changed = true;
            }
            if (changed) {
                informationItemMapper.updateById(existing);
            }
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
        String itemUrl = request.itemUrl();
        if (!StringUtils.hasText(itemUrl)) {
            itemUrl = request.submittedByUserId() == null
                    ? "campusmind://information/" + request.contentHash()
                    : "campusmind://user-import/" + request.submittedByUserId() + "/" + request.contentHash();
        }
        item.setItemUrl(itemUrl);
        item.setContentHash(request.contentHash());
        item.setFetchedAt(now);
        item.setItemStatus("ACTIVE");
        item.setParseStatus("DETAIL_SUCCESS");
        item.setAiStatus("PENDING");
        item.setAiEventType(rainEventType(request.sourceName(), request.detailContent()));
        // 雨课堂只使用来源发布时间；来源未提供时保持为空，禁止伪装成导入时间。
        item.setPublishTime("雨课堂导入".equals(request.sourceName())
                ? request.publishTime()
                : request.publishTime() != null ? request.publishTime() : now);
        item.setSubmittedBy(request.submittedBy());
        item.setSubmittedByUserId(request.submittedByUserId());
        informationItemMapper.insert(item);
        return item.getId();
    }

    private static String rainEventType(String sourceName, String detailContent) {
        if (!"雨课堂导入".equals(sourceName) || detailContent == null) return null;
        return List.of("COURSE", "NOTICE", "HOMEWORK", "EXAM").stream()
                .filter(type -> detailContent.contains("类型：" + type))
                .findFirst().orElse(null);
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
        if (item == null || !isVisible(item, userId)) {
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
        return userStates(userId, items).stream()
                .collect(Collectors.toMap(UserInformationState::getItemId, InformationService::readStatus,
                        (first, second) -> first));
    }

    private List<UserInformationState> userStates(Long userId, List<InformationItem> items) {
        if (userId == null || items.isEmpty()) return List.of();
        return userInformationStateMapper.selectList(new LambdaQueryWrapper<UserInformationState>()
                .eq(UserInformationState::getUserId, userId)
                .in(UserInformationState::getItemId, items.stream().map(InformationItem::getId).toList()));
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

    private UserProfile loadUserProfile(Long userId) {
        return userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId)
                .last("LIMIT 1"));
    }

    private InformationFeedItemResponse toFeedItem(InformationItem item, String readStatus) {
        return feedItem(item, readStatus, 0, List.of());
    }

    private InformationFeedItemResponse toRankedFeedItem(
            InformationItem item, String readStatus, boolean subscribed, UserProfile profile,
            RankingSignals signals, LocalDateTime now, boolean personalized) {
        List<String> reasons = new ArrayList<>();
        int score = freshnessScore(rankingTimestamp(item), now) + importanceScore(item.getAiEventType());
        if ("NEW".equals(readStatus)) score += 3;
        if (subscribed) {
            score += sourceSubscriptionWeight;
            reasons.add("来自你订阅的" + item.getSourceName());
        }
        Map<String, Object> card = aiCard(item);
        LocalDateTime deadline = signals.actionDueAt().get(item.getId());
        if (deadline == null) deadline = itemDeadline(item, card);
        score += deadlineScore(deadline, now, reasons);

        if (personalized) {
            if (signals.favoriteItemIds().contains(item.getId())) {
                score += 8;
                reasons.add("你已收藏");
            }
            if (signals.pendingActionItemIds().contains(item.getId())) {
                score += 30;
                reasons.add("已加入你的待办");
            }
            int sourceAffinity = signals.favoriteSourceCounts().getOrDefault(item.getSourceId(), 0)
                    - (signals.favoriteItemIds().contains(item.getId()) ? 1 : 0);
            if (sourceAffinity > 0) {
                score += Math.min(sourceAffinity, 3) * 4;
                reasons.add("与你此前收藏的来源相似");
            }
            String eventType = normalizedEventType(item.getAiEventType());
            int typeAffinity = signals.favoriteTypeCounts().getOrDefault(eventType, 0)
                    - (signals.favoriteItemIds().contains(item.getId()) ? 1 : 0);
            if (typeAffinity > 0) {
                score += Math.min(typeAffinity, 3) * 3;
                reasons.add("与你此前收藏的内容类型相似");
            }
            score += profileScore(item, card, profile, reasons);
        }
        return feedItem(item, readStatus, score, reasons.stream().distinct().limit(5).toList());
    }

    private InformationFeedItemResponse feedItem(
            InformationItem item, String readStatus, int score, List<String> reasons) {
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
                score,
                reasons,
                item.getSubmittedByUserId()
        );
    }

    private RankingSignals rankingSignals(Long userId, List<InformationItem> items,
                                           List<UserInformationState> states) {
        Map<Long, InformationItem> itemsById = items.stream()
                .collect(Collectors.toMap(InformationItem::getId, item -> item));
        Set<Long> favoriteItemIds = states.stream()
                .filter(state -> state.getFavoritedAt() != null)
                .map(UserInformationState::getItemId)
                .collect(Collectors.toSet());
        Map<Long, Integer> favoriteSourceCounts = new HashMap<>();
        Map<String, Integer> favoriteTypeCounts = new HashMap<>();
        for (Long itemId : favoriteItemIds) {
            InformationItem favorite = itemsById.get(itemId);
            if (favorite == null) continue;
            favoriteSourceCounts.merge(favorite.getSourceId(), 1, Integer::sum);
            favoriteTypeCounts.merge(normalizedEventType(favorite.getAiEventType()), 1, Integer::sum);
        }

        Set<Long> pendingActionItemIds = new HashSet<>();
        Map<Long, LocalDateTime> actionDueAt = new HashMap<>();
        if (userId != null) {
            for (Map<String, Object> row : jdbcTemplate.queryForList("""
                    SELECT information_item_id AS informationItemId, due_at AS dueAt
                    FROM user_action_item WHERE user_id = ? AND status = 'CONFIRMED'
                    """, userId)) {
                Long itemId = ((Number) row.get("informationItemId")).longValue();
                pendingActionItemIds.add(itemId);
                LocalDateTime dueAt = toLocalDateTime(row.get("dueAt"));
                if (dueAt != null) {
                    actionDueAt.merge(itemId, dueAt,
                            (first, second) -> first.isBefore(second) ? first : second);
                }
            }
        }
        return new RankingSignals(favoriteItemIds, pendingActionItemIds, actionDueAt,
                favoriteSourceCounts, favoriteTypeCounts);
    }

    private int profileScore(InformationItem item, Map<String, Object> card,
                             UserProfile profile, List<String> reasons) {
        if (profile == null) return 0;
        int score = 0;
        if (StringUtils.hasText(item.getAiEventType()) && StringUtils.hasText(profile.getInterestTags())) {
            for (String tag : profile.getInterestTags().split("[,，、]")) {
                if (!tag.isBlank() && matchesEventType(item.getAiEventType(), tag.trim().toLowerCase())) {
                    score += 8;
                    reasons.add("与你的兴趣标签「" + tag.trim() + "」匹配");
                    break;
                }
            }
        }
        Object scopesObject = card.get("targetScopes");
        if (scopesObject instanceof List<?> scopes) {
            for (Object scope : scopes) {
                if (!(scope instanceof String text)) continue;
                if (StringUtils.hasText(profile.getGrade()) && text.contains(profile.getGrade())) {
                    score += 12;
                    reasons.add("面向你所在的" + profile.getGrade() + "年级");
                    break;
                }
                if (StringUtils.hasText(profile.getMajor()) && text.contains(profile.getMajor())) {
                    score += 12;
                    reasons.add("面向" + profile.getMajor() + "专业");
                    break;
                }
            }
        }
        if (StringUtils.hasText(profile.getCourseCodes())) {
            String searchable = item.getTitle() + "\n" + item.getDetailContent();
            for (String code : profile.getCourseCodes().split("[,，、]")) {
                if (code.trim().length() >= 3 && searchable.contains(code.trim())) {
                    score += 15;
                    reasons.add("与你的课程「" + code.trim() + "」相关");
                    break;
                }
            }
        }
        return score;
    }

    private static int freshnessScore(LocalDateTime publishedAt, LocalDateTime now) {
        long ageHours = java.time.Duration.between(publishedAt, now).toHours();
        if (ageHours <= 24) return 40;
        if (ageHours <= 72) return 34;
        if (ageHours <= 168) return 28;
        if (ageHours <= 720) return 20;
        if (ageHours <= 2160) return 10;
        if (ageHours <= 8760) return 0;
        return -50;
    }

    private static int importanceScore(String eventType) {
        return switch (normalizedEventType(eventType)) {
            case "EXAM" -> 24;
            case "HOMEWORK" -> 20;
            case "COMPETITION" -> 16;
            case "NOTICE" -> 12;
            case "ACTIVITY", "LECTURE" -> 8;
            case "SERVICE" -> 5;
            default -> 0;
        };
    }

    private static int deadlineScore(LocalDateTime deadline, LocalDateTime now, List<String> reasons) {
        if (deadline == null) return 0;
        long hours = java.time.Duration.between(now, deadline).toHours();
        if (hours < -24) return -60;
        if (hours < 0) return -25;
        if (hours <= 24) {
            reasons.add("24小时内截止");
            return 35;
        }
        if (hours <= 72) {
            reasons.add("即将截止（剩余" + (hours / 24 + 1) + "天）");
            return 28;
        }
        if (hours <= 168) {
            reasons.add("一周内截止");
            return 16;
        }
        return 0;
    }

    private static LocalDateTime itemDeadline(InformationItem item, Map<String, Object> card) {
        for (String key : List.of("registrationDeadline", "dueAt", "deadline", "endTime", "startTime")) {
            LocalDateTime value = parseDateTime(card.get(key));
            if (value != null) return value;
        }
        if (StringUtils.hasText(item.getDetailContent())) {
            var match = DEADLINE_LINE.matcher(item.getDetailContent());
            if (match.find()) return parseDateTime(match.group(1));
        }
        return null;
    }

    private static String normalizedEventType(String eventType) {
        return StringUtils.hasText(eventType) ? eventType.trim().toUpperCase(Locale.ROOT) : "OTHER";
    }

    private static LocalDateTime rankingTimestamp(InformationItem item) {
        return item.getPublishTime() != null ? item.getPublishTime() : item.getFetchedAt();
    }

    private static LocalDateTime rankingTimestamp(InformationFeedItemResponse item) {
        return item.publishTime() != null ? item.publishTime() : item.fetchedAt();
    }

    private static int compareRankedItems(InformationFeedItemResponse first,
                                          InformationFeedItemResponse second) {
        int byScore = Integer.compare(second.recommendationScore(), first.recommendationScore());
        if (byScore != 0) return byScore;
        int byPublishedAt = rankingTimestamp(second).compareTo(rankingTimestamp(first));
        return byPublishedAt != 0 ? byPublishedAt : Long.compare(second.id(), first.id());
    }

    private static boolean isAfterCursor(InformationFeedItemResponse item, LocalDateTime cursor,
                                         Long cursorId, Integer cursorScore) {
        if (cursor == null) return true;
        if (cursorScore != null && item.recommendationScore() != cursorScore) {
            return item.recommendationScore() < cursorScore;
        }
        int byTime = rankingTimestamp(item).compareTo(cursor);
        return byTime < 0 || (byTime == 0 && cursorId != null && item.id() < cursorId);
    }

    private record RankingSignals(
            Set<Long> favoriteItemIds,
            Set<Long> pendingActionItemIds,
            Map<Long, LocalDateTime> actionDueAt,
            Map<Long, Integer> favoriteSourceCounts,
            Map<String, Integer> favoriteTypeCounts) {
        private static RankingSignals empty() {
            return new RankingSignals(Set.of(), Set.of(), Map.of(), Map.of(), Map.of());
        }
    }

    private static boolean matchesEventType(String eventType, String tag) {
        String lower = eventType.toLowerCase();
        return switch (lower) {
            case "exam" -> tag.contains("考试") || tag.contains("考研") || tag.contains("cet");
            case "competition" -> tag.contains("竞赛") || tag.contains("比赛");
            case "lecture" -> tag.contains("讲座") || tag.contains("学术");
            case "course" -> tag.contains("课程") || tag.contains("教学");
            case "activity" -> tag.contains("活动") || tag.contains("社团") || tag.contains("志愿");
            case "service" -> tag.contains("服务") || tag.contains("后勤");
            case "notice" -> tag.contains("通知") || tag.contains("教务");
            default -> tag.contains("校园") || tag.contains("信息");
        };
    }

    private Map<String, Object> aiCard(InformationItem item) {
        if (!StringUtils.hasText(item.getAiCardJson())) {
            return Map.of();
        }
        try {
            Map<String, Object> card = objectMapper.readValue(item.getAiCardJson(), AI_CARD);
            List<String> actions = stringList(card.get("requiredActions")).stream()
                    .map(String::trim)
                    .filter(InformationService::isConfirmableAction)
                    .distinct()
                    .limit(3)
                    .toList();
            Map<String, Object> sanitized = new LinkedHashMap<>(card);
            sanitized.put("requiredActions", actions);
            return sanitized;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static boolean isConfirmableAction(String action) {
        return StringUtils.hasText(action)
                && action.length() <= 80
                && NON_ACTION_RULES.stream().noneMatch(action::contains)
                && ACTION_VERBS.stream().anyMatch(action::contains);
    }

    private boolean isVisible(InformationItem item, Long userId) {
        return VISIBLE_ITEM_STATUS.contains(item.getItemStatus())
                && "DETAIL_SUCCESS".equals(item.getParseStatus())
                && (item.getSubmittedByUserId() == null
                    || Objects.equals(item.getSubmittedByUserId(), userId));
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
                .filter(item -> isVisible(item, userId))
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
                .filter(state -> isVisible(items.get(state.getItemId()), userId))
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
        if (item == null || !isVisible(item, userId)) {
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
    public List<ActionItemResponse> actions(Long userId) {
        if (userId == null) {
            throw new BusinessException("USER_REQUIRED", "查看行动需要用户身份", HttpStatus.UNAUTHORIZED);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT a.id, a.information_item_id AS informationItemId, a.title,
                       a.due_at AS dueAt, a.original_url AS originalUrl,
                       a.status, a.created_at AS createdAt
                FROM user_action_item a
                WHERE a.user_id = ? AND a.status <> 'CANCELLED'
                ORDER BY a.due_at IS NULL, a.due_at, a.id DESC
                """, userId);
        return rows.stream().map(row -> {
            Long itemId = ((Number) row.get("informationItemId")).longValue();
            InformationItem info = informationItemMapper.selectById(itemId);
            if (info == null || !isVisible(info, userId)) {
                return null;
            }
            Map<String, Object> card = aiCard(info);
            List<String> materials = stringList(card.get("requiredMaterials"));
            return new ActionItemResponse(
                    ((Number) row.get("id")).longValue(),
                    itemId,
                    (String) row.get("title"),
                    toLocalDateTime(row.get("dueAt")),
                    (String) row.get("originalUrl"),
                    (String) row.get("status"),
                    toLocalDateTime(row.get("createdAt")),
                    info.getTitle(),
                    info.getSourceName(),
                    materials
            );
        }).filter(Objects::nonNull).toList();
    }

    @Transactional
    public Map<String, Object> completeAction(Long userId, Long actionId) {
        return updateActionStatus(userId, actionId, "COMPLETED");
    }

    @Transactional
    public Map<String, Object> cancelAction(Long userId, Long actionId) {
        return updateActionStatus(userId, actionId, "CANCELLED");
    }

    private Map<String, Object> updateActionStatus(Long userId, Long actionId, String status) {
        if (userId == null) {
            throw new BusinessException("USER_REQUIRED", "操作待办需要用户身份", HttpStatus.UNAUTHORIZED);
        }
        int updated = jdbcTemplate.update("""
                UPDATE user_action_item SET status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND user_id = ? AND status = 'CONFIRMED'
                """, status, actionId, userId);
        if (updated == 0) {
            List<String> existing = jdbcTemplate.queryForList(
                    "SELECT status FROM user_action_item WHERE id = ? AND user_id = ?",
                    String.class, actionId, userId);
            if (existing.isEmpty()) {
                throw new BusinessException("ACTION_NOT_FOUND", "待办不存在", HttpStatus.NOT_FOUND);
            }
            if (!status.equals(existing.get(0))) {
                throw new BusinessException("ACTION_ALREADY_PROCESSED", "待办已经处理", HttpStatus.CONFLICT);
            }
        }
        jdbcTemplate.update("""
                UPDATE user_reminder SET status = 'DISMISSED'
                WHERE action_item_id = ? AND user_id = ? AND status IN ('PENDING', 'DUE')
                """, actionId, userId);
        return Map.of("id", actionId, "status", status);
    }

    @Transactional(readOnly = true)
    public List<ReminderItemResponse> reminders(Long userId) {
        if (userId == null) {
            throw new BusinessException("USER_REQUIRED", "查看提醒需要用户身份", HttpStatus.UNAUTHORIZED);
        }
        return jdbcTemplate.queryForList("""
                SELECT r.id, r.action_item_id AS actionItemId,
                       a.information_item_id AS informationItemId,
                       a.title AS actionTitle,
                       i.title AS sourceTitle,
                       a.original_url AS originalUrl,
                       r.remind_at AS remindAt,
                       a.due_at AS dueAt,
                       r.status, r.sent_at AS sentAt
                FROM user_reminder r
                JOIN user_action_item a ON a.id = r.action_item_id
                LEFT JOIN information_item i ON i.id = a.information_item_id
                WHERE r.user_id = ?
                ORDER BY r.remind_at
                """, userId)
                .stream()
                .map(row -> new ReminderItemResponse(
                        ((Number) row.get("id")).longValue(),
                        ((Number) row.get("actionItemId")).longValue(),
                        row.get("informationItemId") != null ? ((Number) row.get("informationItemId")).longValue() : null,
                        (String) row.get("actionTitle"),
                        (String) row.get("sourceTitle"),
                        (String) row.get("originalUrl"),
                        toLocalDateTime(row.get("remindAt")),
                        toLocalDateTime(row.get("dueAt")),
                        (String) row.get("status"),
                        toLocalDateTime(row.get("sentAt"))
                ))
                .toList();
    }

    @Transactional
    public ReminderItemResponse dismissReminder(Long userId, Long reminderId) {
        if (userId == null) {
            throw new BusinessException("USER_REQUIRED", "操作提醒需要用户身份", HttpStatus.UNAUTHORIZED);
        }
        int updated = jdbcTemplate.update("""
                UPDATE user_reminder SET status = 'DISMISSED'
                WHERE id = ? AND user_id = ? AND status IN ('PENDING', 'DUE')
                """, reminderId, userId);
        if (updated == 0) {
            throw new BusinessException("REMINDER_NOT_FOUND", "提醒不存在或已处理", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT r.id, r.action_item_id AS actionItemId,
                       a.information_item_id AS informationItemId,
                       a.title AS actionTitle,
                       i.title AS sourceTitle,
                       a.original_url AS originalUrl,
                       r.remind_at AS remindAt,
                       a.due_at AS dueAt,
                       r.status, r.sent_at AS sentAt
                FROM user_reminder r
                JOIN user_action_item a ON a.id = r.action_item_id
                LEFT JOIN information_item i ON i.id = a.information_item_id
                WHERE r.id = ?
                """, reminderId);
        return new ReminderItemResponse(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("actionItemId")).longValue(),
                row.get("informationItemId") != null ? ((Number) row.get("informationItemId")).longValue() : null,
                (String) row.get("actionTitle"),
                (String) row.get("sourceTitle"),
                (String) row.get("originalUrl"),
                toLocalDateTime(row.get("remindAt")),
                toLocalDateTime(row.get("dueAt")),
                (String) row.get("status"),
                toLocalDateTime(row.get("sentAt"))
        );
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        if (value instanceof String s && !s.isBlank()) {
            try { return LocalDateTime.parse(s); }
            catch (Exception ignored) { return null; }
        }
        return null;
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
        String normalized = text.trim();
        if (normalized.matches("\\d{10,13}")) {
            long epoch = Long.parseLong(normalized);
            if (normalized.length() == 10) epoch *= 1000;
            return java.time.Instant.ofEpochMilli(epoch)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        }
        normalized = normalized.replaceFirst(
                "^(\\d{4}-\\d{2}-\\d{2})/(\\d{2}:\\d{2})(?:/.*)?$", "$1T$2");
        try {
            return LocalDateTime.parse(normalized);
        } catch (Exception ignored) {
            try {
                return java.time.OffsetDateTime.parse(normalized).toLocalDateTime();
            } catch (Exception ignoredOffset) {
                return null;
            }
        }
    }

    // ========== 相关信息（去重融合） ==========

    @Transactional(readOnly = true)
    public List<RelatedItemResponse> relatedItems(Long userId, Long itemId) {
        InformationItem item = informationItemMapper.selectById(itemId);
        if (item == null || !isVisible(item, userId)) {
            return List.of();
        }
        // 查找同来源或同事件类型的其他可见条目（最多 5 条）
        LambdaQueryWrapper<InformationItem> query = new LambdaQueryWrapper<InformationItem>()
                .ne(InformationItem::getId, itemId)
                .and(w -> w.eq(InformationItem::getSourceId, item.getSourceId())
                        .or()
                        .eq(item.getAiEventType() != null, InformationItem::getAiEventType, item.getAiEventType()))
                .in(InformationItem::getItemStatus, VISIBLE_ITEM_STATUS)
                .eq(InformationItem::getParseStatus, "DETAIL_SUCCESS")
                .orderByDesc(InformationItem::getFetchedAt)
                .last("LIMIT 5");
        if (userId == null) {
            query.isNull(InformationItem::getSubmittedByUserId);
        } else {
            query.and(w -> w.isNull(InformationItem::getSubmittedByUserId)
                    .or().eq(InformationItem::getSubmittedByUserId, userId));
        }
        List<InformationItem> related = informationItemMapper.selectList(query);
        return related.stream().map(r -> new RelatedItemResponse(
                r.getId(),
                r.getTitle(),
                r.getSourceName(),
                r.getPublishTime() != null ? r.getPublishTime().toString() : r.getFetchedAt().toString(),
                r.getSourceId().equals(item.getSourceId()) ? "与「" + item.getSourceName() + "」来源已融合为 1 条" : "补充背景，已关联上文"
        )).toList();
    }

    // ========== 热门/趋势 ==========

    @Transactional(readOnly = true)
    public List<TrendingItemResponse> trending(Long userId, int size) {
        int safeSize = Math.min(Math.max(size, 1), 10);
        // 按阅读量+收藏量排序的热门条目
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT i.id, i.title,
                       COUNT(s.id) AS heat
                FROM information_item i
                LEFT JOIN user_information_state s ON s.item_id = i.id
                WHERE i.item_status IN ('ACTIVE', 'UPDATED')
                  AND i.parse_status = 'DETAIL_SUCCESS'
                  AND (i.submitted_by_user_id IS NULL OR i.submitted_by_user_id = ?)
                GROUP BY i.id, i.title
                ORDER BY heat DESC, i.fetched_at DESC
                LIMIT ?
                """, userId, safeSize);
        List<TrendingItemResponse> result = new ArrayList<>();
        for (int idx = 0; idx < rows.size(); idx++) {
            Map<String, Object> row = rows.get(idx);
            long heat = ((Number) row.get("heat")).longValue();
            String heatLabel = heat >= 1000 ? String.format("热度 %.1fk", heat / 1000.0) : "热度 " + heat;
            result.add(new TrendingItemResponse(
                    ((Number) row.get("id")).longValue(),
                    "#" + (idx + 1),
                    (String) row.get("title"),
                    heatLabel
            ));
        }
        return result;
    }

}

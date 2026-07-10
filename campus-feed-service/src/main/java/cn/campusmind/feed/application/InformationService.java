package cn.campusmind.feed.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.feed.controller.InformationDetailResponse;
import cn.campusmind.feed.controller.InformationFeedItemResponse;
import cn.campusmind.feed.controller.InformationFeedResponse;
import cn.campusmind.feed.domain.InformationItem;
import cn.campusmind.feed.domain.UserInformationState;
import cn.campusmind.feed.infrastructure.mapper.InformationItemMapper;
import cn.campusmind.feed.infrastructure.mapper.UserInformationStateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InformationService {

    private static final Set<String> VISIBLE_ITEM_STATUS = Set.of("ACTIVE", "UPDATED");
    private static final Set<String> READ_STATUSES = Set.of("NEW", "READ", "FAVORITED", "ARCHIVED");
    private static final int PREVIEW_LENGTH = 160;

    private final InformationItemMapper informationItemMapper;
    private final UserInformationStateMapper userInformationStateMapper;

    public InformationService(InformationItemMapper informationItemMapper,
                              UserInformationStateMapper userInformationStateMapper) {
        this.informationItemMapper = informationItemMapper;
        this.userInformationStateMapper = userInformationStateMapper;
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
                item.getUpdatedAt()
        );
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
                preview(item.getDetailContent()),
                item.getItemUrl()
        );
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
}

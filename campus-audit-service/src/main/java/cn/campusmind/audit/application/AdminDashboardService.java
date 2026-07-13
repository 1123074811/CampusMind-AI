package cn.campusmind.audit.application;

import cn.campusmind.audit.controller.AdminAuditLogListResponse;
import cn.campusmind.audit.controller.AdminAuditLogResponse;
import cn.campusmind.audit.controller.AdminDashboardResponse;
import cn.campusmind.audit.controller.AdminDataSourceResponse;
import cn.campusmind.audit.controller.AdminEventResponse;
import cn.campusmind.audit.controller.AdminTaskResponse;
import cn.campusmind.audit.controller.MetricsResponse;
import cn.campusmind.audit.domain.CrawlTask;
import cn.campusmind.audit.domain.DataSource;
import cn.campusmind.audit.domain.EventAuditLog;
import cn.campusmind.audit.domain.ImportTask;
import cn.campusmind.audit.infrastructure.mapper.CrawlTaskMapper;
import cn.campusmind.audit.infrastructure.mapper.DataSourceMapper;
import cn.campusmind.audit.infrastructure.mapper.EventAuditLogMapper;
import cn.campusmind.audit.infrastructure.mapper.ImportTaskMapper;
import cn.campusmind.audit.infrastructure.mapper.InformationItemMapper;
import cn.campusmind.audit.domain.InformationItem;
import cn.campusmind.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminDashboardService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> AI_CARD = new TypeReference<>() { };
    private static final Set<String> VISIBLE_ITEM_STATUSES = Set.of("ACTIVE", "UPDATED", "OFFLINE");
    private static final DateTimeFormatter EVENT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter TASK_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final InformationItemMapper informationItemMapper;
    private final DataSourceMapper dataSourceMapper;
    private final CrawlTaskMapper crawlTaskMapper;
    private final ImportTaskMapper importTaskMapper;
    private final EventAuditLogMapper eventAuditLogMapper;
    private final ObjectMapper objectMapper;

    public AdminDashboardService(
            InformationItemMapper informationItemMapper,
            DataSourceMapper dataSourceMapper,
            CrawlTaskMapper crawlTaskMapper,
            ImportTaskMapper importTaskMapper,
            EventAuditLogMapper eventAuditLogMapper,
            ObjectMapper objectMapper
    ) {
        this.informationItemMapper = informationItemMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.crawlTaskMapper = crawlTaskMapper;
        this.importTaskMapper = importTaskMapper;
        this.eventAuditLogMapper = eventAuditLogMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard(Long userId, String role) {
        List<InformationItem> events = informationItemMapper.selectList(new LambdaQueryWrapper<InformationItem>()
                .in(InformationItem::getItemStatus, VISIBLE_ITEM_STATUSES)
                .eq(InformationItem::getParseStatus, "DETAIL_SUCCESS")
                .orderByDesc(InformationItem::getFetchedAt)
                .last("LIMIT 30"));
        List<DataSource> sources = dataSourceMapper.selectList(new LambdaQueryWrapper<DataSource>()
                .orderByDesc(DataSource::getLastCrawledAt));
        List<CrawlTask> tasks = crawlTaskMapper.selectPage(Page.of(1, 20), new LambdaQueryWrapper<CrawlTask>()
                .orderByDesc(CrawlTask::getStartedAt)
                .orderByDesc(CrawlTask::getId)).getRecords();
        List<ImportTask> imports = importTaskMapper.selectPage(Page.of(1, 10), new LambdaQueryWrapper<ImportTask>()
                .eq(!"ADMIN".equals(role), ImportTask::getUserId, userId)
                .orderByDesc(ImportTask::getCreatedAt)).getRecords();

        Map<Long, DataSource> sourceById = sources.stream()
                .collect(Collectors.toMap(DataSource::getId, java.util.function.Function.identity(), (left, right) -> left));
        MetricsResponse metrics = buildMetrics(events, sources, tasks);
        List<AdminTaskResponse> mergedTasks = mergeTasks(tasks, imports, sourceById);

        return new AdminDashboardResponse(
                metrics,
                events.stream().map(this::toEvent).toList(),
                sources.stream().map(source -> toSource(source, tasks)).toList(),
                mergedTasks
        );
    }

    @Transactional
    public AdminEventResponse review(Long eventId, Long operatorId, String status, String comment) {
        InformationItem event = informationItemMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException("EVENT_NOT_FOUND", "事件不存在", HttpStatus.NOT_FOUND);
        }
        String beforeStatus = event.getItemStatus();
        event.setItemStatus(toItemStatus(status));
        informationItemMapper.updateById(event);

        EventAuditLog log = new EventAuditLog();
        log.setEventId(null);
        log.setOperatorId(operatorId == null ? 9901L : operatorId);
        log.setAction(toAction(status));
        log.setBeforeSnapshot(writeJson(Map.of("status", beforeStatus)));
        log.setAfterSnapshot(writeJson(Map.of("status", status)));
        log.setComment("信息#" + eventId + "：" + comment);
        eventAuditLogMapper.insert(log);
        return toEvent(event);
    }

    @Transactional
    public AdminEventResponse updateEvent(Long eventId, Long operatorId,
                                          String title, String summary, String eventType) {
        InformationItem event = informationItemMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException("EVENT_NOT_FOUND", "事件不存在", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> before = new java.util.LinkedHashMap<>();
        before.put("title", event.getTitle());
        before.put("summary", event.getAiSummary());
        before.put("eventType", event.getAiEventType());
        if (StringUtils.hasText(title)) {
            event.setTitle(title);
        }
        if (summary != null) {
            event.setAiSummary(summary);
        }
        if (StringUtils.hasText(eventType)) {
            event.setAiEventType(eventType);
        }
        informationItemMapper.updateById(event);

        Map<String, Object> after = new java.util.LinkedHashMap<>();
        after.put("title", event.getTitle());
        after.put("summary", event.getAiSummary());
        after.put("eventType", event.getAiEventType());
        EventAuditLog log = new EventAuditLog();
        log.setEventId(null);
        log.setOperatorId(operatorId == null ? 9901L : operatorId);
        log.setAction("CORRECT");
        log.setBeforeSnapshot(writeJson(before));
        log.setAfterSnapshot(writeJson(after));
        log.setComment("信息#" + eventId + "：管理员修订AI结果");
        eventAuditLogMapper.insert(log);
        return toEvent(event);
    }

    @Transactional
    public void deleteEvent(Long eventId, Long operatorId) {
        InformationItem event = informationItemMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException("EVENT_NOT_FOUND", "事件不存在", HttpStatus.NOT_FOUND);
        }
        String beforeStatus = event.getItemStatus();
        event.setItemStatus("OFFLINE");
        informationItemMapper.updateById(event);

        EventAuditLog log = new EventAuditLog();
        log.setEventId(null);
        log.setOperatorId(operatorId == null ? 9901L : operatorId);
        log.setAction("DELETE");
        log.setBeforeSnapshot(writeJson(Map.of("status", beforeStatus)));
        log.setAfterSnapshot(writeJson(Map.of("status", "OFFLINE")));
        log.setComment("信息#" + eventId + "：管理员删除");
        eventAuditLogMapper.insert(log);
    }

    @Transactional
    public void batchDeleteEvents(List<Long> ids, Long operatorId) {
        for (Long id : ids) {
            deleteEvent(id, operatorId);
        }
    }

    @Transactional(readOnly = true)
    public AdminAuditLogListResponse auditLogs(String action, Long operatorId, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<EventAuditLog> query = new LambdaQueryWrapper<EventAuditLog>()
                .eq(StringUtils.hasText(action), EventAuditLog::getAction, action)
                .eq(operatorId != null, EventAuditLog::getOperatorId, operatorId)
                .orderByDesc(EventAuditLog::getCreatedAt)
                .orderByDesc(EventAuditLog::getId);
        Page<EventAuditLog> page = eventAuditLogMapper.selectPage(Page.of(1, safeSize), query);
        return new AdminAuditLogListResponse(
                page.getRecords().stream().map(this::toAuditLog).toList(),
                page.getTotal()
        );
    }

    private MetricsResponse buildMetrics(List<InformationItem> events, List<DataSource> sources, List<CrawlTask> tasks) {
        long reviewCount = events.stream().filter(event -> !"OFFLINE".equals(event.getItemStatus())).count();
        long urgentCount = events.stream()
                .filter(event -> "UPDATED".equals(event.getItemStatus()))
                .count();
        long successfulTasks = tasks.stream().filter(task -> "SUCCESS".equals(task.getTaskStatus())).count();
        int sourceSuccessRate = tasks.isEmpty() ? 0 : (int) Math.round(successfulTasks * 100.0 / tasks.size());
        long sourcesNeedAuth = sources.stream().filter(source -> "NEEDS_AUTH".equals(sourceStatus(source, tasks))).count();
        long vectorPending = 0;
        return new MetricsResponse(reviewCount, urgentCount, sourceSuccessRate, sourcesNeedAuth, vectorPending);
    }

    private List<AdminTaskResponse> mergeTasks(List<CrawlTask> tasks, List<ImportTask> imports, Map<Long, DataSource> sourceById) {
        List<AdminTaskResponse> crawlResponses = tasks.stream()
                .map(task -> toTask(task, sourceById))
                .toList();
        List<AdminTaskResponse> importResponses = imports.stream()
                .map(this::toImportTask)
                .toList();
        return java.util.stream.Stream.concat(crawlResponses.stream(), importResponses.stream())
                .sorted(Comparator.comparing(AdminTaskResponse::time).reversed())
                .limit(12)
                .toList();
    }

    private AdminEventResponse toEvent(InformationItem event) {
        Map<String, Object> card = readAiCard(event.getAiCardJson());
        return new AdminEventResponse(
                event.getId(),
                event.getTitle(),
                event.getSourceName(),
                event.getItemUrl(),
                StringUtils.hasText(event.getAiEventType()) ? event.getAiEventType() : eventType(event.getTitle()),
                reviewStatus(event.getItemStatus()),
                text(card.get("location"), "-"),
                informationTime(event.getPublishTime()),
                text(card.get("endTime"), "待补充"),
                text(card.get("organizer"), event.getSourceName()),
                listText(card.get("targetScopes"), "全体学生"),
                StringUtils.hasText(event.getAiSummary()) ? event.getAiSummary() : event.getDetailContent(),
                riskText(event),
                stringList(card.get("tags"), List.of("校园信息")),
                event.getAiStatus() == null ? "PENDING" : event.getAiStatus(),
                Boolean.TRUE.equals(event.getAiNeedReview()),
                card
        );
    }

    private Map<String, Object> readAiCard(String json) {
        if (!StringUtils.hasText(json)) return Map.of();
        try {
            return objectMapper.readValue(json, AI_CARD);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private static String text(Object value, String fallback) {
        return value instanceof String text && StringUtils.hasText(text) ? text : fallback;
    }

    private static String listText(Object value, String fallback) {
        List<String> values = stringList(value, List.of());
        return values.isEmpty() ? fallback : String.join("、", values);
    }

    private static List<String> stringList(Object value, List<String> fallback) {
        if (!(value instanceof List<?> values)) return fallback;
        List<String> result = values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        return result.isEmpty() ? fallback : result;
    }

    private AdminAuditLogResponse toAuditLog(EventAuditLog log) {
        return new AdminAuditLogResponse(
                log.getId(),
                log.getEventId(),
                log.getOperatorId(),
                log.getAction(),
                log.getBeforeSnapshot(),
                log.getAfterSnapshot(),
                log.getComment(),
                log.getCreatedAt()
        );
    }

    private AdminDataSourceResponse toSource(DataSource source, List<CrawlTask> tasks) {
        List<CrawlTask> sourceTasks = tasks.stream()
                .filter(task -> Objects.equals(task.getSourceId(), source.getId()))
                .toList();
        long success = sourceTasks.stream().filter(task -> "SUCCESS".equals(task.getTaskStatus())).count();
        int successRate = sourceTasks.isEmpty() ? 0 : (int) Math.round(success * 100.0 / sourceTasks.size());
        long pending = sourceTasks.stream().filter(task -> "PENDING".equals(task.getTaskStatus()) || "RUNNING".equals(task.getTaskStatus())).count();
        return new AdminDataSourceResponse(
                source.getId(),
                source.getName(),
                source.getBaseUrl(),
                source.getSourceType(),
                sourceStatus(source, tasks),
                relativeTime(source.getLastCrawledAt()),
                successRate,
                pending
        );
    }

    private AdminTaskResponse toTask(CrawlTask task, Map<Long, DataSource> sourceById) {
        DataSource source = sourceById.get(task.getSourceId());
        String name = source == null ? "未知数据源抓取" : source.getName() + "抓取";
        String note = switch (task.getTaskStatus()) {
            case "SUCCESS" -> "HTTP " + nullToDash(task.getHttpStatus()) + "，已完成解析";
            case "FAILED" -> StringUtils.hasText(task.getFailReason()) ? task.getFailReason() : "采集失败";
            case "RUNNING" -> "正在抓取 " + task.getCrawlUrl();
            default -> "等待调度";
        };
        return new AdminTaskResponse(task.getId(), name, task.getTaskStatus(), "感知 Agent", taskTime(task.getStartedAt()), note);
    }

    private AdminTaskResponse toImportTask(ImportTask task) {
        String owner = switch (task.getImportType()) {
            case "RAIN_JSON", "USER_TEXT" -> "认知 Agent";
            case "USER_IMAGE" -> "OCR Agent";
            default -> "导入服务";
        };
        return new AdminTaskResponse(task.getId(), importTaskName(task.getImportType()), task.getTaskStatus(), owner, taskTime(task.getCreatedAt()), importTaskNote(task));
    }

    private String sourceStatus(DataSource source, List<CrawlTask> tasks) {
        if (source.getEnabled() != null && source.getEnabled() == 0) {
            return "PAUSED";
        }
        boolean hasRunning = tasks.stream().anyMatch(task -> Objects.equals(task.getSourceId(), source.getId()) && "RUNNING".equals(task.getTaskStatus()));
        if (hasRunning) {
            return "RUNNING";
        }
        boolean hasAuthFailure = tasks.stream().anyMatch(task -> Objects.equals(task.getSourceId(), source.getId())
                && "FAILED".equals(task.getTaskStatus())
                && StringUtils.hasText(task.getFailReason())
                && (task.getFailReason().contains("403") || task.getFailReason().contains("授权")));
        if (hasAuthFailure) {
            return "NEEDS_AUTH";
        }
        return "HEALTHY";
    }

    private String riskText(InformationItem event) {
        return switch (event.getItemStatus()) {
            case "UPDATED" -> "原文已更新，建议复核";
            case "OFFLINE" -> "已从学生端下线";
            default -> "原文已解析，学生端正在展示";
        };
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

    private String writeJson(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private static String reviewStatus(String itemStatus) {
        return switch (itemStatus) {
            case "UPDATED" -> "CORRECTED";
            case "OFFLINE" -> "OFFLINE";
            case "FAILED" -> "REJECTED";
            default -> "AI_PUBLISHED";
        };
    }

    private static String toItemStatus(String status) {
        return switch (status) {
            case "OFFLINE", "REJECTED" -> "OFFLINE";
            case "CORRECTED" -> "UPDATED";
            default -> "ACTIVE";
        };
    }

    private static String eventType(String title) {
        if (title.contains("考试") || title.contains("考研")) return "EXAM";
        if (title.contains("竞赛") || title.contains("比赛")) return "COMPETITION";
        if (title.contains("讲座") || title.contains("报告")) return "LECTURE";
        if (title.contains("课程") || title.contains("教学") || title.contains("调课")) return "COURSE";
        if (title.contains("作业")) return "HOMEWORK";
        if (title.contains("活动")) return "ACTIVITY";
        if (title.contains("服务") || title.contains("维护")) return "SERVICE";
        return "NOTICE";
    }

    private static String importTaskName(String importType) {
        return switch (importType) {
            case "RAIN_JSON" -> "雨课堂 JSON 解析";
            case "RAIN_COOKIE" -> "雨课堂 Cookie 临时导入";
            case "USER_TEXT" -> "用户文本解析";
            case "USER_IMAGE" -> "用户截图 OCR";
            default -> "导入任务";
        };
    }

    private static String importTaskNote(ImportTask task) {
        return switch (task.getTaskStatus()) {
            case "SUCCESS" -> "已生成候选事件";
            case "RUNNING" -> "正在抽取结构化字段";
            case "FAILED" -> "解析失败，等待重试";
            default -> "等待进入解析队列";
        };
    }

    private static String toAction(String status) {
        return switch (status) {
            case "REVIEWED" -> "REVIEW";
            case "REJECTED" -> "REJECT";
            case "CORRECTED" -> "CORRECT";
            case "OFFLINE" -> "OFFLINE";
            default -> status;
        };
    }

    private static String nullToDash(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private static String taskTime(LocalDateTime time) {
        return time == null ? "时间待补充" : time.format(TASK_TIME);
    }

    private static String informationTime(LocalDateTime time) {
        if (time == null) {
            return "待补充";
        }
        return time.getHour() == 0 && time.getMinute() == 0 && time.getSecond() == 0
                ? time.toLocalDate().toString()
                : time.format(EVENT_TIME);
    }

    private static String relativeTime(LocalDateTime time) {
        if (time == null) {
            return "未同步";
        }
        Duration duration = Duration.between(time, LocalDateTime.now());
        if (duration.toMinutes() < 1) {
            return "刚刚";
        }
        if (duration.toHours() < 1) {
            return duration.toMinutes() + " 分钟前";
        }
        if (duration.toDays() < 1) {
            return duration.toHours() + " 小时前";
        }
        return duration.toDays() + " 天前";
    }
}

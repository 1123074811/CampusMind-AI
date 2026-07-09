package cn.campusmind.audit.application;

import cn.campusmind.audit.controller.AdminAuditLogListResponse;
import cn.campusmind.audit.controller.AdminAuditLogResponse;
import cn.campusmind.audit.controller.AdminDashboardResponse;
import cn.campusmind.audit.controller.AdminDataSourceResponse;
import cn.campusmind.audit.controller.AdminEventResponse;
import cn.campusmind.audit.controller.AdminTaskResponse;
import cn.campusmind.audit.controller.MetricsResponse;
import cn.campusmind.audit.domain.CampusEvent;
import cn.campusmind.audit.domain.CrawlTask;
import cn.campusmind.audit.domain.DataSource;
import cn.campusmind.audit.domain.EventAuditLog;
import cn.campusmind.audit.domain.EventSourceRef;
import cn.campusmind.audit.domain.ImportTask;
import cn.campusmind.audit.infrastructure.mapper.CampusEventMapper;
import cn.campusmind.audit.infrastructure.mapper.CrawlTaskMapper;
import cn.campusmind.audit.infrastructure.mapper.DataSourceMapper;
import cn.campusmind.audit.infrastructure.mapper.EventAuditLogMapper;
import cn.campusmind.audit.infrastructure.mapper.EventSourceRefMapper;
import cn.campusmind.audit.infrastructure.mapper.ImportTaskMapper;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminDashboardService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final Set<String> REVIEW_STATUSES = Set.of("AI_PUBLISHED", "CORRECTED");
    private static final DateTimeFormatter EVENT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter TASK_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CampusEventMapper campusEventMapper;
    private final DataSourceMapper dataSourceMapper;
    private final CrawlTaskMapper crawlTaskMapper;
    private final ImportTaskMapper importTaskMapper;
    private final EventAuditLogMapper eventAuditLogMapper;
    private final EventSourceRefMapper eventSourceRefMapper;
    private final ObjectMapper objectMapper;

    public AdminDashboardService(
            CampusEventMapper campusEventMapper,
            DataSourceMapper dataSourceMapper,
            CrawlTaskMapper crawlTaskMapper,
            ImportTaskMapper importTaskMapper,
            EventAuditLogMapper eventAuditLogMapper,
            EventSourceRefMapper eventSourceRefMapper,
            ObjectMapper objectMapper
    ) {
        this.campusEventMapper = campusEventMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.crawlTaskMapper = crawlTaskMapper;
        this.importTaskMapper = importTaskMapper;
        this.eventAuditLogMapper = eventAuditLogMapper;
        this.eventSourceRefMapper = eventSourceRefMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard() {
        List<CampusEvent> events = campusEventMapper.selectList(new LambdaQueryWrapper<CampusEvent>()
                .orderByDesc(CampusEvent::getCreatedAt)
                .last("LIMIT 30"));
        List<DataSource> sources = dataSourceMapper.selectList(new LambdaQueryWrapper<DataSource>()
                .orderByDesc(DataSource::getLastCrawledAt));
        List<CrawlTask> tasks = crawlTaskMapper.selectPage(Page.of(1, 20), new LambdaQueryWrapper<CrawlTask>()
                .orderByDesc(CrawlTask::getStartedAt)
                .orderByDesc(CrawlTask::getId)).getRecords();
        List<ImportTask> imports = importTaskMapper.selectPage(Page.of(1, 10), new LambdaQueryWrapper<ImportTask>()
                .orderByDesc(ImportTask::getCreatedAt)).getRecords();

        Map<Long, DataSource> sourceById = sources.stream()
                .collect(Collectors.toMap(DataSource::getId, Function.identity(), (left, right) -> left));
        Map<Long, EventSourceRef> refByEventId = eventSourceRefMapper.selectList(new LambdaQueryWrapper<EventSourceRef>()
                        .in(!events.isEmpty(), EventSourceRef::getEventId, events.stream().map(CampusEvent::getId).toList()))
                .stream()
                .collect(Collectors.toMap(EventSourceRef::getEventId, Function.identity(), (left, right) -> left));
        MetricsResponse metrics = buildMetrics(events, sources, tasks);
        List<AdminTaskResponse> mergedTasks = mergeTasks(tasks, imports, sourceById);

        return new AdminDashboardResponse(
                metrics,
                events.stream().map(event -> toEvent(event, sourceById, refByEventId.get(event.getId()))).toList(),
                sources.stream().map(source -> toSource(source, tasks)).toList(),
                mergedTasks
        );
    }

    @Transactional
    public AdminEventResponse review(Long eventId, Long operatorId, String status, String comment) {
        CampusEvent event = campusEventMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException("EVENT_NOT_FOUND", "事件不存在", HttpStatus.NOT_FOUND);
        }
        String beforeStatus = event.getStatus();
        event.setStatus(status);
        campusEventMapper.updateById(event);

        EventAuditLog log = new EventAuditLog();
        log.setEventId(eventId);
        log.setOperatorId(operatorId == null ? 9901L : operatorId);
        log.setAction(toAction(status));
        log.setBeforeSnapshot(writeJson(Map.of("status", beforeStatus)));
        log.setAfterSnapshot(writeJson(Map.of("status", status)));
        log.setComment(comment);
        eventAuditLogMapper.insert(log);
        return toEvent(event, Map.of(), null);
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

    private MetricsResponse buildMetrics(List<CampusEvent> events, List<DataSource> sources, List<CrawlTask> tasks) {
        long reviewCount = events.stream().filter(event -> REVIEW_STATUSES.contains(event.getStatus())).count();
        long urgentCount = events.stream()
                .filter(event -> REVIEW_STATUSES.contains(event.getStatus()))
                .filter(event -> "CORRECTED".equals(event.getStatus()) || safeConfidence(event).compareTo(new BigDecimal("0.7500")) < 0)
                .count();
        int avgConfidence = events.isEmpty()
                ? 0
                : (int) Math.round(events.stream().mapToDouble(event -> safeConfidence(event).doubleValue()).average().orElse(0) * 100);
        long successfulTasks = tasks.stream().filter(task -> "SUCCESS".equals(task.getTaskStatus())).count();
        int sourceSuccessRate = tasks.isEmpty() ? 0 : (int) Math.round(successfulTasks * 100.0 / tasks.size());
        long sourcesNeedAuth = sources.stream().filter(source -> "NEEDS_AUTH".equals(sourceStatus(source, tasks))).count();
        long vectorPending = events.stream()
                .filter(event -> !"REJECTED".equals(event.getStatus()) && !"OFFLINE".equals(event.getStatus()))
                .filter(event -> !StringUtils.hasText(event.getVectorDocId()))
                .count();
        return new MetricsResponse(reviewCount, urgentCount, avgConfidence, sourceSuccessRate, sourcesNeedAuth, vectorPending);
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

    private AdminEventResponse toEvent(CampusEvent event, Map<Long, DataSource> sourceById, EventSourceRef ref) {
        DataSource linkedSource = ref == null ? null : sourceById.get(ref.getSourceId());
        String sourceName = linkedSource == null ? sourceName(event.getSourceType()) : linkedSource.getName();
        return new AdminEventResponse(
                event.getId(),
                event.getTitle(),
                sourceName,
                event.getEventType(),
                event.getStatus(),
                safeConfidence(event).setScale(2, RoundingMode.HALF_UP).doubleValue(),
                nullToDash(event.getLocation()),
                event.getStartTime() == null ? "待补充" : event.getStartTime().format(EVENT_TIME),
                String.join("、", readStringList(event.getTargetScope())),
                event.getSummary(),
                riskText(event),
                readStringList(event.getTags())
        );
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

    private String riskText(CampusEvent event) {
        if ("REJECTED".equals(event.getStatus())) {
            return "已标记为无效事件";
        }
        if ("REVIEWED".equals(event.getStatus())) {
            return "已人工确认";
        }
        if ("CORRECTED".equals(event.getStatus())) {
            return "字段存在纠错记录，需要复核";
        }
        if (safeConfidence(event).compareTo(new BigDecimal("0.7500")) < 0) {
            return "置信度偏低，建议抽查原文";
        }
        if (!StringUtils.hasText(event.getLocation()) || event.getStartTime() == null) {
            return "时间或地点缺失，需要人工补充";
        }
        return "字段完整，建议快速确认";
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

    private String writeJson(Map<String, String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private static BigDecimal safeConfidence(CampusEvent event) {
        return event.getConfidence() == null ? BigDecimal.ZERO : event.getConfidence();
    }

    private static String sourceName(String sourceType) {
        return switch (sourceType) {
            case "PUBLIC_WEB" -> "公开网页";
            case "RAIN_CLASSROOM" -> "雨课堂";
            case "USER_TEXT" -> "用户文本";
            case "USER_IMAGE" -> "用户截图";
            default -> sourceType;
        };
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

package cn.campusmind.audit.application;

import cn.campusmind.audit.controller.AdminAuditLogListResponse;
import cn.campusmind.audit.controller.AdminAuditLogResponse;
import cn.campusmind.audit.controller.AdminDashboardResponse;
import cn.campusmind.audit.controller.AdminDataSourceResponse;
import cn.campusmind.audit.controller.AdminEventResponse;
import cn.campusmind.audit.controller.AdminTaskResponse;
import cn.campusmind.audit.controller.MetricsResponse;
import cn.campusmind.audit.controller.UpsertDataSourceRequest;
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
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

    public AdminDashboardService(
            InformationItemMapper informationItemMapper,
            DataSourceMapper dataSourceMapper,
            CrawlTaskMapper crawlTaskMapper,
            ImportTaskMapper importTaskMapper,
            EventAuditLogMapper eventAuditLogMapper,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.informationItemMapper = informationItemMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.crawlTaskMapper = crawlTaskMapper;
        this.importTaskMapper = importTaskMapper;
        this.eventAuditLogMapper = eventAuditLogMapper;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
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
        if ("OFFLINE".equals(event.getItemStatus())) withdrawItemReminders(eventId);

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
        withdrawItemReminders(eventId);

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

    @Transactional
    public List<AdminEventResponse> batchReview(List<Long> ids, Long operatorId, String status, String comment) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String safeComment = StringUtils.hasText(comment) ? comment : "批量审核";
        return ids.stream()
                .distinct()
                .map(id -> review(id, operatorId, status, safeComment))
                .toList();
    }

    @Transactional(readOnly = true)
    public cn.campusmind.audit.controller.EventImpactResponse eventImpact(Long eventId) {
        InformationItem event = informationItemMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException("EVENT_NOT_FOUND", "事件不存在", HttpStatus.NOT_FOUND);
        }
        Long pending = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_reminder r
                JOIN user_action_item a ON a.id = r.action_item_id
                WHERE a.information_item_id = ? AND r.status = 'PENDING'
                """, Long.class, eventId);
        Long due = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_reminder r
                JOIN user_action_item a ON a.id = r.action_item_id
                WHERE a.information_item_id = ? AND r.status = 'DUE'
                """, Long.class, eventId);
        Long deliveries = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM notification_delivery d
                JOIN user_reminder r ON r.id = d.reminder_id
                JOIN user_action_item a ON a.id = r.action_item_id
                WHERE a.information_item_id = ? AND d.status IN ('PENDING','RETRY','SENDING','SENT')
                """, Long.class, eventId);
        Long users = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT r.user_id) FROM user_reminder r
                JOIN user_action_item a ON a.id = r.action_item_id
                WHERE a.information_item_id = ? AND r.status IN ('PENDING','DUE')
                """, Long.class, eventId);
        return new cn.campusmind.audit.controller.EventImpactResponse(
                eventId,
                pending == null ? 0 : pending,
                due == null ? 0 : due,
                deliveries == null ? 0 : deliveries,
                users == null ? 0 : users
        );
    }

    private void withdrawItemReminders(Long itemId) {
        jdbcTemplate.update("""
                UPDATE user_reminder r JOIN user_action_item a ON a.id = r.action_item_id
                SET r.status = 'DISMISSED'
                WHERE a.information_item_id = ? AND r.status IN ('PENDING', 'DUE')
                """, itemId);
        jdbcTemplate.update("""
                UPDATE notification_delivery d
                JOIN user_reminder r ON r.id = d.reminder_id
                JOIN user_action_item a ON a.id = r.action_item_id
                SET d.status = 'WITHDRAWN', d.withdrawn_at = COALESCE(d.withdrawn_at, CURRENT_TIMESTAMP)
                WHERE a.information_item_id = ? AND d.status <> 'WITHDRAWN'
                """, itemId);
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

    @Transactional(readOnly = true)
    public List<cn.campusmind.audit.controller.ChangeLogResponse> changeLogs(int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return jdbcTemplate.queryForList("""
                SELECT cl.id, cl.item_id AS itemId,
                       i.title AS itemTitle, i.source_name AS sourceName,
                       cl.old_content_hash AS oldContentHash,
                       cl.new_content_hash AS newContentHash,
                       cl.changed_fields AS changedFields,
                       cl.changed_at AS changedAt
                FROM information_change_log cl
                LEFT JOIN information_item i ON i.id = cl.item_id
                ORDER BY cl.changed_at DESC, cl.id DESC
                LIMIT ?
                """, safeSize)
                .stream()
                .map(row -> new cn.campusmind.audit.controller.ChangeLogResponse(
                        ((Number) row.get("id")).longValue(),
                        ((Number) row.get("itemId")).longValue(),
                        (String) row.get("itemTitle"),
                        (String) row.get("sourceName"),
                        (String) row.get("oldContentHash"),
                        (String) row.get("newContentHash"),
                        (String) row.get("changedFields"),
                        row.get("changedAt") instanceof java.sql.Timestamp ts
                                ? ts.toLocalDateTime()
                                : (row.get("changedAt") instanceof LocalDateTime ldt ? ldt : null)
                ))
                .toList();
    }

    @Transactional
    public AdminDataSourceResponse createSource(Long operatorId, UpsertDataSourceRequest request) {
        validateSelectorConfig(request.selectorConfig());
        ensureUniqueBaseUrl(null, request.baseUrl());
        DataSource source = new DataSource();
        applySource(source, request);
        if (source.getEnabled() == null) source.setEnabled(1);
        dataSourceMapper.insert(source);
        writeSourceVersion(source, operatorId, "CREATE");
        writeSourceAudit(operatorId, "SOURCE_CREATE", null, source);
        return toSource(source, List.of());
    }

    @Transactional
    public AdminDataSourceResponse updateSource(Long sourceId, Long operatorId, UpsertDataSourceRequest request) {
        DataSource source = requireSource(sourceId);
        String before = writeJson(sourceSnapshot(source));
        validateSelectorConfig(request.selectorConfig());
        ensureUniqueBaseUrl(sourceId, request.baseUrl());
        applySource(source, request);
        dataSourceMapper.updateById(source);
        writeSourceVersion(source, operatorId, "UPDATE");
        writeSourceAudit(operatorId, "SOURCE_UPDATE", before, source);
        return toSource(source, List.of());
    }

    @Transactional
    public AdminDataSourceResponse setSourceEnabled(Long sourceId, Long operatorId, boolean enabled) {
        DataSource source = requireSource(sourceId);
        String before = writeJson(sourceSnapshot(source));
        source.setEnabled(enabled ? 1 : 0);
        dataSourceMapper.updateById(source);
        writeSourceVersion(source, operatorId, enabled ? "ENABLE" : "DISABLE");
        writeSourceAudit(operatorId, enabled ? "SOURCE_ENABLE" : "SOURCE_PAUSE", before, source);
        return toSource(source, List.of());
    }

    @Transactional(readOnly = true)
    public List<cn.campusmind.audit.controller.DataSourceVersionResponse> sourceVersions(Long sourceId) {
        requireSource(sourceId);
        return jdbcTemplate.query("""
                SELECT id, source_id, version_no, action, snapshot, operator_id, created_at
                FROM data_source_version WHERE source_id = ? ORDER BY version_no DESC
                """, (rs, rowNum) -> {
            try {
                return new cn.campusmind.audit.controller.DataSourceVersionResponse(
                        rs.getLong("id"), rs.getLong("source_id"), rs.getInt("version_no"),
                        rs.getString("action"), objectMapper.readValue(rs.getString("snapshot"), new TypeReference<>() { }),
                        rs.getObject("operator_id", Long.class), rs.getTimestamp("created_at").toLocalDateTime());
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("数据源版本快照损坏", ex);
            }
        }, sourceId);
    }

    @Transactional
    public AdminDataSourceResponse rollbackSource(Long sourceId, Long operatorId, int versionNo) {
        DataSource source = requireSource(sourceId);
        String snapshot = jdbcTemplate.query("""
                SELECT snapshot FROM data_source_version WHERE source_id = ? AND version_no = ?
                """, rs -> rs.next() ? rs.getString(1) : null, sourceId, versionNo);
        if (snapshot == null) {
            throw new BusinessException("SOURCE_VERSION_NOT_FOUND", "数据源版本不存在", HttpStatus.NOT_FOUND);
        }
        String before = writeJson(sourceSnapshot(source));
        try {
            JsonNode node = objectMapper.readTree(snapshot);
            source.setName(node.path("name").asText());
            source.setSourceType(node.path("sourceType").asText());
            source.setBaseUrl(node.path("baseUrl").asText());
            source.setRobotsUrl(node.path("robotsUrl").isNull() ? null : node.path("robotsUrl").asText(null));
            source.setCrawlIntervalSeconds(node.path("crawlIntervalSeconds").asInt(3600));
            source.setParserType(node.path("parserType").asText("WEBMAGIC"));
            source.setSelectorConfig(node.path("selectorConfig").isNull() ? null : node.path("selectorConfig").asText(null));
            source.setEnabled(node.path("enabled").asInt(1));
        } catch (JsonProcessingException ex) {
            throw new BusinessException("SOURCE_VERSION_INVALID", "历史版本数据已损坏", HttpStatus.CONFLICT);
        }
        ensureUniqueBaseUrl(sourceId, source.getBaseUrl());
        dataSourceMapper.updateById(source);
        writeSourceVersion(source, operatorId, "ROLLBACK");
        writeSourceAudit(operatorId, "SOURCE_ROLLBACK", before, source);
        return toSource(source, List.of());
    }

    private void writeSourceVersion(DataSource source, Long operatorId, String action) {
        Integer next = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version_no), 0) + 1 FROM data_source_version WHERE source_id = ?",
                Integer.class, source.getId());
        jdbcTemplate.update("""
                INSERT INTO data_source_version(source_id, version_no, action, snapshot, operator_id)
                VALUES (?, ?, ?, ?, ?)
                """, source.getId(), next, action, writeJson(sourceSnapshot(source)), operatorId);
    }

    private DataSource requireSource(Long sourceId) {
        DataSource source = dataSourceMapper.selectById(sourceId);
        if (source == null) {
            throw new BusinessException("SOURCE_NOT_FOUND", "数据源不存在", HttpStatus.NOT_FOUND);
        }
        return source;
    }

    private void ensureUniqueBaseUrl(Long sourceId, String baseUrl) {
        LambdaQueryWrapper<DataSource> query = new LambdaQueryWrapper<DataSource>()
                .eq(DataSource::getBaseUrl, baseUrl)
                .ne(sourceId != null, DataSource::getId, sourceId);
        if (dataSourceMapper.selectCount(query) > 0) {
            throw new BusinessException("SOURCE_URL_EXISTS", "该数据源地址已存在", HttpStatus.CONFLICT);
        }
    }

    private void validateSelectorConfig(String selectorConfig) {
        if (!StringUtils.hasText(selectorConfig)) return;
        try {
            objectMapper.readTree(selectorConfig);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("INVALID_SELECTOR_CONFIG", "选择器配置必须是合法 JSON", HttpStatus.BAD_REQUEST);
        }
    }

    private static void applySource(DataSource source, UpsertDataSourceRequest request) {
        source.setName(request.name().trim());
        source.setSourceType(request.sourceType().trim().toUpperCase());
        source.setBaseUrl(request.baseUrl().trim());
        source.setRobotsUrl(StringUtils.hasText(request.robotsUrl()) ? request.robotsUrl().trim() : null);
        source.setCrawlIntervalSeconds(request.crawlIntervalSeconds() == null ? 3600 : request.crawlIntervalSeconds());
        source.setParserType(request.parserType().trim().toUpperCase());
        source.setSelectorConfig(StringUtils.hasText(request.selectorConfig()) ? request.selectorConfig().trim() : null);
        if (request.enabled() != null) source.setEnabled(request.enabled() ? 1 : 0);
    }

    private void writeSourceAudit(Long operatorId, String action, String before, DataSource source) {
        EventAuditLog log = new EventAuditLog();
        log.setEventId(null);
        log.setOperatorId(operatorId == null ? 9901L : operatorId);
        log.setAction(action);
        log.setBeforeSnapshot(before);
        log.setAfterSnapshot(writeJson(sourceSnapshot(source)));
        log.setComment("数据源#" + source.getId() + "：" + source.getName());
        eventAuditLogMapper.insert(log);
    }

    private static Map<String, Object> sourceSnapshot(DataSource source) {
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("name", source.getName());
        values.put("sourceType", source.getSourceType());
        values.put("baseUrl", source.getBaseUrl());
        values.put("robotsUrl", source.getRobotsUrl());
        values.put("crawlIntervalSeconds", source.getCrawlIntervalSeconds());
        values.put("parserType", source.getParserType());
        values.put("selectorConfig", source.getSelectorConfig());
        values.put("enabled", source.getEnabled());
        return values;
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

        // AI 处理状态统计
        long aiPending = 0, aiProcessing = 0, aiSuccess = 0, aiFailed = 0;
        try {
            List<Map<String, Object>> aiStats = jdbcTemplate.queryForList("""
                    SELECT ai_status, COUNT(*) AS cnt
                    FROM information_item
                    WHERE ai_status IS NOT NULL
                    GROUP BY ai_status
                    """);
            for (Map<String, Object> row : aiStats) {
                String status = (String) row.get("ai_status");
                long cnt = ((Number) row.get("cnt")).longValue();
                switch (status) {
                    case "PENDING" -> aiPending = cnt;
                    case "PROCESSING" -> aiProcessing = cnt;
                    case "SUCCESS" -> aiSuccess = cnt;
                    case "FAILED", "REVIEW" -> aiFailed += cnt;
                }
            }
        } catch (Exception ignored) {
            // 表可能不存在或查询失败，静默处理
        }

        return new MetricsResponse(reviewCount, urgentCount, sourceSuccessRate, sourcesNeedAuth, vectorPending,
                aiPending, aiProcessing, aiSuccess, aiFailed);
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
                card,
                event.getSubmittedBy(),
                event.getSubmittedByUserId()
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
                source.getRobotsUrl(),
                source.getCrawlIntervalSeconds(),
                source.getParserType(),
                source.getSelectorConfig(),
                source.getEnabled() == null || source.getEnabled() == 1,
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

    // ─── 通知运营 ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public cn.campusmind.audit.controller.DeliveryStatsResponse deliveryStats() {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT
                  COUNT(*) AS total,
                  SUM(CASE WHEN status='PENDING'  THEN 1 ELSE 0 END) AS pending,
                  SUM(CASE WHEN status='SENDING'  THEN 1 ELSE 0 END) AS sending,
                  SUM(CASE WHEN status='SENT'     THEN 1 ELSE 0 END) AS sent,
                  SUM(CASE WHEN status='RETRY'    THEN 1 ELSE 0 END) AS retry,
                  SUM(CASE WHEN status='FAILED'   THEN 1 ELSE 0 END) AS failed,
                  SUM(CASE WHEN status='WITHDRAWN' THEN 1 ELSE 0 END) AS withdrawn
                FROM notification_delivery
                """);
        return new cn.campusmind.audit.controller.DeliveryStatsResponse(
                ((Number) row.get("total")).longValue(),
                ((Number) row.get("pending")).longValue(),
                ((Number) row.get("sending")).longValue(),
                ((Number) row.get("sent")).longValue(),
                ((Number) row.get("retry")).longValue(),
                ((Number) row.get("failed")).longValue(),
                ((Number) row.get("withdrawn")).longValue()
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> deliveries(String status, int page, int size) {
        final int safeSize = Math.min(Math.max(size, 1), 200);
        final int safePage = Math.max(page, 0);
        final int offset = safePage * safeSize;

        if (StringUtils.hasText(status) && !"ALL".equalsIgnoreCase(status)) {
            return jdbcTemplate.queryForList(
                """
                SELECT d.id, d.reminder_id AS reminderId, d.user_id AS userId,
                       d.channel, d.status, d.attempt_count AS attemptCount,
                       d.last_error AS lastError, d.sent_at AS sentAt,
                       d.withdrawn_at AS withdrawnAt, d.created_at AS createdAt
                FROM notification_delivery d
                WHERE d.status = ?
                ORDER BY d.id DESC LIMIT ? OFFSET ?
                """,
                status, safeSize, offset);
        }
        return jdbcTemplate.queryForList(
                """
                SELECT d.id, d.reminder_id AS reminderId, d.user_id AS userId,
                       d.channel, d.status, d.attempt_count AS attemptCount,
                       d.last_error AS lastError, d.sent_at AS sentAt,
                       d.withdrawn_at AS withdrawnAt, d.created_at AS createdAt
                FROM notification_delivery d
                ORDER BY d.id DESC LIMIT ? OFFSET ?
                """,
                safeSize, offset);
    }

    @Transactional
    public void retryDelivery(Long id) {
        int changed = jdbcTemplate.update(
                "UPDATE notification_delivery SET status='RETRY', next_attempt_at=CURRENT_TIMESTAMP WHERE id=? AND status='FAILED'", id);
        if (changed == 0) {
            throw new BusinessException("DELIVERY_NOT_RETRYABLE", "投递记录不存在或状态不允许重试", HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public void withdrawDelivery(Long id) {
        int changed = jdbcTemplate.update(
                "UPDATE notification_delivery SET status='WITHDRAWN', withdrawn_at=CURRENT_TIMESTAMP WHERE id=? AND status <> 'WITHDRAWN'", id);
        if (changed == 0) {
            throw new BusinessException("DELIVERY_NOT_FOUND", "投递记录不存在或已撤回", HttpStatus.NOT_FOUND);
        }
    }
}

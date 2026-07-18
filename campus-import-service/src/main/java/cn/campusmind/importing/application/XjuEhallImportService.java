package cn.campusmind.importing.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.importing.config.XjuEhallProperties;
import cn.campusmind.importing.controller.XjuEhallConfigResponse;
import cn.campusmind.importing.controller.XjuEhallImportRequest;
import cn.campusmind.importing.controller.XjuEhallImportResponse;
import cn.campusmind.importing.domain.ImportTask;
import cn.campusmind.importing.feign.UserPrivacyFeignClient;
import cn.campusmind.importing.infrastructure.mapper.ImportTaskMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class XjuEhallImportService {

    private static final List<String> SUPPORTED_SCOPES = List.of("TIMETABLE", "EXAM", "HOMEWORK");
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final XjuEhallProperties properties;
    private final ImportService importService;
    private final ImportTaskMapper importTaskMapper;
    private final EventServiceClient eventServiceClient;
    private final UserPrivacyFeignClient userPrivacyFeignClient;
    private final ObjectMapper objectMapper;

    public XjuEhallImportService(XjuEhallProperties properties,
                                 ImportService importService,
                                 ImportTaskMapper importTaskMapper,
                                 EventServiceClient eventServiceClient,
                                 UserPrivacyFeignClient userPrivacyFeignClient,
                                 ObjectMapper objectMapper) {
        this.properties = properties;
        this.importService = importService;
        this.importTaskMapper = importTaskMapper;
        this.eventServiceClient = eventServiceClient;
        this.userPrivacyFeignClient = userPrivacyFeignClient;
        this.objectMapper = objectMapper;
    }

    public XjuEhallConfigResponse config() {
        List<String> authHosts = normalizeHosts(properties.safeAuthHosts());
        List<String> dataHosts = normalizeHosts(properties.safeDataHosts());
        boolean enabled = properties.enabled() && validLoginUrl(authHosts) && !dataHosts.isEmpty();
        return new XjuEhallConfigResponse(
                enabled,
                properties.loginUrl(),
                authHosts,
                dataHosts,
                SUPPORTED_SCOPES,
                properties.schemaVersion(),
                properties.policyVersion(),
                properties.maxPayloadBytes(),
                properties.maxSourceItems());
    }

    public XjuEhallImportResponse importData(CurrentUser user, XjuEhallImportRequest request) {
        requireEnabled();
        importService.checkRateLimit(user.userId());
        validateRequest(request);
        requireConsent(request);

        List<PendingEvent> pending = new ArrayList<>();
        List<XjuEhallImportResponse.FailureReason> failures = new ArrayList<>();
        int invalidItems = 0;
        for (int index = 0; index < request.items().size(); index++) {
            try {
                List<PendingEvent> events = normalizeItem(
                        request.items().get(index), request.semester(), request.scopes(), index);
                if (pending.size() + events.size() > properties.maxExpandedEvents()) {
                    throw new BusinessException("XJU_EXPANDED_EVENTS_TOO_MANY",
                            "课表展开后的事件数量超过限制", HttpStatus.PAYLOAD_TOO_LARGE);
                }
                pending.addAll(events);
            } catch (BusinessException ex) {
                if ("XJU_EXPANDED_EVENTS_TOO_MANY".equals(ex.getCode())) {
                    throw ex;
                }
                invalidItems++;
                addFailure(failures, index, ex.getCode(), ex.getMessage());
            }
        }

        ImportTask task = createTask(user.userId());
        int success = 0;
        int skipped = 0;
        int eventWriteFailures = 0;
        Map<String, Integer> byType = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        String sourceUrl = sourceOrigin();

        for (PendingEvent event : pending) {
            String dedupKey = event.dedupKey(user.userId());
            if (!seen.add(dedupKey)) {
                skipped++;
                continue;
            }
            try {
                eventServiceClient.createEvent(
                        event.title(),
                        event.summary(),
                        event.type(),
                        "XJU_EHALL",
                        format(event.startTime()),
                        format(event.endTime()),
                        event.location(),
                        event.teacherName(),
                        toJson(event.targetScopes()),
                        toJson(event.tags()),
                        "PRIVATE",
                        user.userId(),
                        dedupKey,
                        null,
                        sourceUrl,
                        event.contentHash());
                success++;
                byType.merge(event.type(), 1, Integer::sum);
            } catch (RuntimeException ex) {
                eventWriteFailures++;
                addFailure(failures, event.sourceIndex(), "EVENT_WRITE_FAILED", "事件保存失败，请稍后重试");
            }
        }

        int failed = invalidItems + eventWriteFailures;
        int total = pending.size() + invalidItems;
        String status = success > 0 ? "SUCCESS" : "FAILED";
        XjuEhallImportResponse.Summary responseSummary = new XjuEhallImportResponse.Summary(
                total, success, skipped, failed, Map.copyOf(byType), List.copyOf(failures));
        finishTask(task, status, request, responseSummary);
        String message = success > 0
                ? "教务数据同步完成，成功" + success + "条，跳过" + skipped + "条，失败" + failed + "条"
                : "教务数据同步失败，未生成任何事件";
        return new XjuEhallImportResponse(task.getId(), status, message, responseSummary);
    }

    public Map<String, Object> deleteData(CurrentUser user) {
        Map<String, Object> deleted = eventServiceClient.deleteOwnedSource("XJU_EHALL", user.userId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deletedEvents", number(deleted.get("deletedEvents")));
        result.put("deletedReminders", 0);
        result.put("deletedActions", 0);
        result.put("deletedVectors", number(deleted.get("deletedVectors")));
        return result;
    }

    private void validateRequest(XjuEhallImportRequest request) {
        if (request.schemaVersion() != properties.schemaVersion()) {
            throw new BusinessException("XJU_SCHEMA_UNSUPPORTED", "不支持的数据版本，请升级 App", HttpStatus.BAD_REQUEST);
        }
        if (!properties.policyVersion().equals(request.consentVersion())) {
            throw new BusinessException("XJU_POLICY_VERSION_MISMATCH", "隐私政策版本已更新，请重新授权", HttpStatus.CONFLICT);
        }
        if (request.items().size() > properties.maxSourceItems()) {
            throw new BusinessException("XJU_ITEMS_TOO_MANY", "同步条目超过最大数量限制", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        try {
            if (objectMapper.writeValueAsBytes(request).length > properties.maxPayloadBytes()) {
                throw new BusinessException("XJU_PAYLOAD_TOO_LARGE", "同步数据超过最大大小限制", HttpStatus.PAYLOAD_TOO_LARGE);
            }
        } catch (JsonProcessingException ex) {
            throw new BusinessException("XJU_PAYLOAD_INVALID", "同步数据无法序列化", HttpStatus.BAD_REQUEST);
        }
        if (Duration.between(request.collectedAt().toInstant(), OffsetDateTime.now().toInstant()).abs()
                .compareTo(Duration.ofMinutes(properties.collectedAtSkewMinutes())) > 0) {
            throw new BusinessException("XJU_COLLECTION_EXPIRED", "同步数据已过期，请重新获取", HttpStatus.BAD_REQUEST);
        }
        Set<String> dataHosts = new HashSet<>(normalizeHosts(properties.safeDataHosts()));
        boolean invalidOrigin = request.originHosts().stream()
                .map(host -> host.toLowerCase(Locale.ROOT))
                .anyMatch(host -> !dataHosts.contains(host));
        if (invalidOrigin) {
            throw new BusinessException("XJU_ORIGIN_FORBIDDEN", "数据来源不在允许范围内", HttpStatus.FORBIDDEN);
        }
        if (request.semester().endDate().isBefore(request.semester().startDate())) {
            throw new BusinessException("XJU_SEMESTER_INVALID", "学期结束日期不得早于开始日期", HttpStatus.BAD_REQUEST);
        }
    }

    private void requireConsent(XjuEhallImportRequest request) {
        ApiResponse<UserPrivacyFeignClient.PrivacyStatus> response;
        try {
            response = userPrivacyFeignClient.privacy();
        } catch (RuntimeException ex) {
            throw new BusinessException("CONSENT_SERVICE_UNAVAILABLE", "暂时无法核验授权，请稍后重试", HttpStatus.BAD_GATEWAY);
        }
        if (response == null || !response.success() || response.data() == null) {
            throw new BusinessException("CONSENT_SERVICE_UNAVAILABLE", "暂时无法核验授权，请稍后重试", HttpStatus.BAD_GATEWAY);
        }
        UserPrivacyFeignClient.Consent consent = response.data().consents() == null ? null
                : response.data().consents().stream()
                .filter(item -> "ACADEMIC_DATA_IMPORT".equals(item.consentType()))
                .findFirst().orElse(null);
        Set<String> grantedScopes = consent == null || consent.scopes() == null
                ? Set.of() : new HashSet<>(consent.scopes());
        if (consent == null || !consent.granted()
                || !properties.policyVersion().equals(consent.policyVersion())
                || !grantedScopes.containsAll(request.scopes())) {
            throw new BusinessException("ACADEMIC_DATA_CONSENT_REQUIRED",
                    "请先授权所选教务数据范围", HttpStatus.FORBIDDEN);
        }
    }

    private List<PendingEvent> normalizeItem(XjuEhallImportRequest.Item item,
                                             XjuEhallImportRequest.Semester semester,
                                             List<String> scopes,
                                             int sourceIndex) {
        String requiredScope = switch (item.type()) {
            case "EXAM" -> "EXAM";
            case "HOMEWORK" -> "HOMEWORK";
            default -> "TIMETABLE";
        };
        if (!scopes.contains(requiredScope)) {
            throw new BusinessException("XJU_SCOPE_MISMATCH", "条目不属于已选择的数据范围", HttpStatus.BAD_REQUEST);
        }
        if (item.schedule() != null) {
            if (!"COURSE".equals(item.type())) {
                throw new BusinessException("XJU_SCHEDULE_TYPE_INVALID", "只有课程可以使用周次课表", HttpStatus.BAD_REQUEST);
            }
            return expandSchedule(item, semester, sourceIndex);
        }

        LocalDateTime start = local(item.startTime());
        LocalDateTime end = local(item.endTime());
        if ("HOMEWORK".equals(item.type())) {
            start = local(item.deadline());
            end = start;
        }
        if (start == null) {
            throw new BusinessException("XJU_TIME_REQUIRED", "条目缺少明确时间", HttpStatus.BAD_REQUEST);
        }
        if (end != null && end.isBefore(start)) {
            throw new BusinessException("XJU_TIME_RANGE_INVALID", "结束时间不得早于开始时间", HttpStatus.BAD_REQUEST);
        }
        return List.of(toPending(item, start, end, start.toLocalDate(), sourceIndex));
    }

    private List<PendingEvent> expandSchedule(XjuEhallImportRequest.Item item,
                                              XjuEhallImportRequest.Semester semester,
                                              int sourceIndex) {
        XjuEhallImportRequest.Schedule schedule = item.schedule();
        if (schedule.endSection() < schedule.startSection()
                || !schedule.endClock().isAfter(schedule.startClock())) {
            throw new BusinessException("XJU_SCHEDULE_RANGE_INVALID", "课程节次或时间范围无效", HttpStatus.BAD_REQUEST);
        }
        LocalDate weekOneMonday = semester.startDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<PendingEvent> result = new ArrayList<>();
        for (Integer week : new LinkedHashSet<>(schedule.weekNumbers()).stream().sorted().toList()) {
            LocalDate date = weekOneMonday.plusWeeks(week - 1L).plusDays(schedule.weekday() - 1L);
            if (date.isBefore(semester.startDate()) || date.isAfter(semester.endDate())) {
                throw new BusinessException("XJU_SCHEDULE_OUTSIDE_SEMESTER", "课程周次超出学期范围", HttpStatus.BAD_REQUEST);
            }
            result.add(toPending(item,
                    LocalDateTime.of(date, schedule.startClock()),
                    LocalDateTime.of(date, schedule.endClock()),
                    date,
                    sourceIndex));
        }
        return result;
    }

    private PendingEvent toPending(XjuEhallImportRequest.Item item,
                                   LocalDateTime start,
                                   LocalDateTime end,
                                   LocalDate occurrenceDate,
                                   int sourceIndex) {
        String title = item.title().trim();
        String summary = StringUtils.hasText(item.description())
                ? item.description().trim()
                : StringUtils.hasText(item.courseName()) ? item.courseName().trim() : title;
        List<String> scopes = StringUtils.hasText(item.courseCode())
                ? List.of(item.courseCode().trim()) : List.of();
        List<String> tags = new ArrayList<>();
        tags.add("新疆大学教务");
        if (StringUtils.hasText(item.courseName())) {
            tags.add(item.courseName().trim());
        }
        String identity = StringUtils.hasText(item.providerItemId())
                ? item.providerItemId().trim() + "|" + occurrenceDate
                : item.type() + "|" + normalize(title) + "|" + start + "|" + normalize(item.location());
        String contentHash = sha256(String.join("|",
                identity, summary, value(item.teacherName()), value(item.location()), value(end)));
        return new PendingEvent(
                sourceIndex,
                identity,
                item.type(),
                title,
                summary,
                value(item.teacherName()),
                value(item.location()),
                start,
                end,
                scopes,
                List.copyOf(tags),
                contentHash);
    }

    private void requireEnabled() {
        XjuEhallConfigResponse config = config();
        if (!config.enabled()) {
            throw new BusinessException("XJU_EHALL_DISABLED", "教务同步尚未开放", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private boolean validLoginUrl(List<String> authHosts) {
        try {
            URI uri = URI.create(properties.loginUrl());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && authHosts.contains(uri.getHost().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String sourceOrigin() {
        URI uri = URI.create(properties.loginUrl());
        return uri.getScheme() + "://" + uri.getHost();
    }

    private ImportTask createTask(Long userId) {
        ImportTask task = new ImportTask();
        task.setUserId(userId);
        task.setImportType("XJU_EHALL");
        task.setTaskStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        importTaskMapper.insert(task);
        return task;
    }

    private void finishTask(ImportTask task,
                            String status,
                            XjuEhallImportRequest request,
                            XjuEhallImportResponse.Summary summary) {
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("provider", "XJU_EHALL");
        stored.put("scopes", request.scopes());
        stored.put("consentVersion", request.consentVersion());
        stored.put("collectedAt", request.collectedAt());
        stored.put("collectionMode", "APP_WEBVIEW");
        stored.put("semester", request.semester().code());
        stored.put("summary", summary);
        task.setTaskStatus(status);
        task.setResultSummary(toJson(stored));
        if ("FAILED".equals(status)) {
            task.setErrorMessage("教务数据未生成事件");
        }
        task.setFinishedAt(LocalDateTime.now());
        importTaskMapper.updateById(task);
    }

    private static void addFailure(List<XjuEhallImportResponse.FailureReason> failures,
                                   int index, String code, String message) {
        if (failures.size() < 20) {
            failures.add(new XjuEhallImportResponse.FailureReason(index, code, message));
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON serialization failed", ex);
        }
    }

    private static List<String> normalizeHosts(List<String> hosts) {
        return hosts.stream()
                .filter(StringUtils::hasText)
                .map(host -> host.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static LocalDateTime local(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(SHANGHAI).toLocalDateTime();
    }

    private static String format(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private record PendingEvent(
            int sourceIndex,
            String identity,
            String type,
            String title,
            String summary,
            String teacherName,
            String location,
            LocalDateTime startTime,
            LocalDateTime endTime,
            List<String> targetScopes,
            List<String> tags,
            String contentHash
    ) {
        String dedupKey(Long ownerUserId) {
            return sha256(ownerUserId + "|XJU_EHALL|" + identity);
        }
    }
}

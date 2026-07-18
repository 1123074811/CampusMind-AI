package cn.campusmind.importing.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.importing.controller.ImageImportRequest;
import cn.campusmind.importing.controller.ImportTaskResponse;
import cn.campusmind.importing.controller.RainCookieImportRequest;
import cn.campusmind.importing.controller.RainJsonImportRequest;
import cn.campusmind.importing.controller.TextImportRequest;
import cn.campusmind.importing.domain.ImportTask;
import cn.campusmind.importing.domain.RawDocument;
import cn.campusmind.importing.infrastructure.mapper.ImportTaskMapper;
import cn.campusmind.importing.config.ImportProperties;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private static final String[] DATE_PATTERNS = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd HH"};

    private final ImportTaskMapper importTaskMapper;
    private final RawDocumentService rawDocumentService;
    private final CognitionClient cognitionClient;
    private final EventServiceClient eventServiceClient;
    private final InformationServiceClient informationServiceClient;
    private final RainClassroomParser rainClassroomParser;
    private final RainCookieStore rainCookieStore;
    private final FileTextExtractor fileTextExtractor;
    private final OcrTextExtractor ocrTextExtractor;
    private final ObjectMapper objectMapper;
    private final ImportProperties properties;
    private final StringRedisTemplate redisTemplate;

    public ImportService(ImportTaskMapper importTaskMapper,
                         RawDocumentService rawDocumentService,
                         CognitionClient cognitionClient,
                         EventServiceClient eventServiceClient,
                         InformationServiceClient informationServiceClient,
                         RainClassroomParser rainClassroomParser,
                         RainCookieStore rainCookieStore,
                         FileTextExtractor fileTextExtractor,
                         OcrTextExtractor ocrTextExtractor,
                         ObjectMapper objectMapper,
                         ImportProperties properties,
                         StringRedisTemplate redisTemplate) {
        this.importTaskMapper = importTaskMapper;
        this.rawDocumentService = rawDocumentService;
        this.cognitionClient = cognitionClient;
        this.eventServiceClient = eventServiceClient;
        this.informationServiceClient = informationServiceClient;
        this.rainClassroomParser = rainClassroomParser;
        this.rainCookieStore = rainCookieStore;
        this.fileTextExtractor = fileTextExtractor;
        this.ocrTextExtractor = ocrTextExtractor;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportTaskResponse submitTextImport(CurrentUser user, TextImportRequest request) {
        checkRateLimit(user.userId());
        String text = request.text();
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("TEXT_REQUIRED", "导入文本不能为空", HttpStatus.BAD_REQUEST);
        }
        if (text.length() > properties.maxTextLength()) {
            throw new BusinessException("TEXT_TOO_LONG", "文本超过最大长度限制", HttpStatus.BAD_REQUEST);
        }
        String contentHash = sha256(text);
        RawDocument doc = rawDocumentService.save(buildRawDocument("USER_TEXT", user.userId(), null, text, contentHash, null));
        ImportTask task = createTask(user.userId(), "USER_TEXT", doc.getId());
        try {
            CognitionResult candidate = cognitionClient.extract("USER_TEXT", text);
            Long eventId = persistEvent(user, candidate, "USER_TEXT", contentHash, doc.getId(), null, true, text);
            boolean infoCompensated = persistInformationItemWithCompensation(candidate, "用户文本提交", text, contentHash, eventId, user);
            succeedTask(task, eventId, candidate, infoCompensated);
            return response(task, "文本导入完成，已生成AI预测事件");
        } catch (Exception ex) {
            failTask(task, ex.getMessage());
            return response(task, "文本导入失败：" + safeMessage(ex));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportTaskResponse submitImageImport(CurrentUser user, ImageImportRequest request) {
        checkRateLimit(user.userId());
        String base64 = request.imageBase64();
        if (!StringUtils.hasText(base64)) {
            throw new BusinessException("IMAGE_REQUIRED", "图片数据不能为空", HttpStatus.BAD_REQUEST);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("IMAGE_INVALID", "图片Base64格式无效", HttpStatus.BAD_REQUEST);
        }
        if (bytes.length > properties.maxImageBytes()) {
            throw new BusinessException("IMAGE_TOO_LARGE", "图片大小超过限制", HttpStatus.BAD_REQUEST);
        }

        // 先创建任务记录
        ImportTask task = createTask(user.userId(), "USER_IMAGE", null);

        try {
            // 1. OCR 提取文字
            String ocrText = ocrTextExtractor.extractText(bytes);
            if (!StringUtils.hasText(ocrText)) {
                throw new BusinessException("OCR_EMPTY", "图片中未识别到文字内容", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (ocrText.length() > properties.maxTextLength()) {
                ocrText = ocrText.substring(0, properties.maxTextLength());
            }

            // 2. 保存 RawDocument（含 OCR 元数据）
            String contentHash = sha256(ocrText);
            Map<String, Object> ocrMeta = new LinkedHashMap<>();
            ocrMeta.put("engine", "tess4j");
            ocrMeta.put("language", properties.ocrLanguage());
            ocrMeta.put("imageName", request.imageName());
            ocrMeta.put("imageBytes", bytes.length);
            RawDocument doc = rawDocumentService.save(buildRawDocument("USER_IMAGE", user.userId(), null, ocrText, contentHash, ocrMeta));
            task.setRawDocId(doc.getId());
            importTaskMapper.updateById(task);

            // 3. 调用认知服务生成事件
            CognitionResult candidate = cognitionClient.extract("USER_IMAGE", ocrText);
            Long eventId = persistEvent(user, candidate, "USER_IMAGE", contentHash, doc.getId(), null, true, ocrText);
            boolean infoCompensated = persistInformationItemWithCompensation(candidate, "用户图片OCR", ocrText, contentHash, eventId, user);
            succeedTask(task, eventId, candidate, infoCompensated);
            return response(task, "图片OCR识别完成，已生成AI预测事件");
        } catch (BusinessException ex) {
            failTask(task, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            failTask(task, ex.getMessage());
            return response(task, "图片导入失败：" + safeMessage(ex));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportTaskResponse submitFileImport(CurrentUser user, MultipartFile file) {
        checkRateLimit(user.userId());
        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_REQUIRED", "上传文件不能为空", HttpStatus.BAD_REQUEST);
        }
        String originalFilename = file.getOriginalFilename();
        if (!fileTextExtractor.isSupported(originalFilename)) {
            throw new BusinessException("FILE_TYPE_UNSUPPORTED",
                    "不支持的文件类型，仅支持 PDF、DOCX、TXT、XLSX 和图片文件",
                    HttpStatus.BAD_REQUEST);
        }
        long size = file.getSize();
        if (size > properties.maxFileBytes()) {
            throw new BusinessException("FILE_TOO_LARGE", "文件大小超过限制（最大10MB）", HttpStatus.BAD_REQUEST);
        }
        // 先创建任务记录，确保即使提取失败也有痕迹
        ImportTask task = createTask(user.userId(), "USER_FILE", null);
        try {
            // 提取纯文本
            String text = fileTextExtractor.extractText(file);
            if (text.length() > properties.maxTextLength()) {
                text = text.substring(0, properties.maxTextLength());
            }
            String contentHash = sha256(text);
            Map<String, Object> fileMeta = new LinkedHashMap<>();
            fileMeta.put("fileName", originalFilename);
            fileMeta.put("fileSize", size);
            fileMeta.put("extension", getExtension(originalFilename));
            RawDocument rawDocument = buildRawDocument("USER_FILE", user.userId(), null, text, contentHash, fileMeta);
            rawDocument.setOriginalFile(file.getBytes());
            rawDocument.setOriginalFileName(originalFilename);
            rawDocument.setOriginalContentType(file.getContentType());
            RawDocument doc = rawDocumentService.save(rawDocument);
            task.setRawDocId(doc.getId());
            importTaskMapper.updateById(task);

            CognitionResult candidate = cognitionClient.extract("USER_FILE", text);
            Long eventId = persistEvent(user, candidate, "USER_FILE", contentHash, doc.getId(), null, true, text);
            boolean infoCompensated = persistInformationItemWithCompensation(candidate, "用户文件上传", text, contentHash, eventId, user);
            succeedTask(task, eventId, candidate, infoCompensated);
            return response(task, "文件导入完成，已生成AI预测事件");
        } catch (Exception ex) {
            failTask(task, ex.getMessage());
            return response(task, "文件导入失败：" + safeMessage(ex));
        }
    }

    /**
     * 分页查询用户导入任务列表。
     */
    public List<ImportTask> listUserTasks(Long userId, String status, int page, int size) {
        LambdaQueryWrapper<ImportTask> wrapper = new LambdaQueryWrapper<ImportTask>()
                .eq(ImportTask::getUserId, userId)
                .eq(status != null && !status.isBlank(), ImportTask::getTaskStatus, status)
                .orderByDesc(ImportTask::getCreatedAt)
                .last("LIMIT " + size + " OFFSET " + (page * size));
        return importTaskMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportTaskResponse deleteRawDocument(CurrentUser user, Long taskId) {
        ImportTask task = importTaskMapper.selectOne(new LambdaQueryWrapper<ImportTask>()
                .eq(ImportTask::getId, taskId)
                .eq(ImportTask::getUserId, user.userId())
                .last("LIMIT 1"));
        if (task == null) {
            throw new BusinessException("IMPORT_TASK_NOT_FOUND", "导入任务不存在", HttpStatus.NOT_FOUND);
        }
        if (!StringUtils.hasText(task.getRawDocId())) {
            return response(task, "原始数据已删除");
        }
        rawDocumentService.deleteOwned(task.getRawDocId(), user.userId());
        importTaskMapper.update(null, new LambdaUpdateWrapper<ImportTask>()
                .eq(ImportTask::getId, task.getId())
                .set(ImportTask::getRawDocId, null));
        return response(task, "原始数据已删除，私有事件仍保留");
    }

    private static String getExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1) : "";
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportTaskResponse submitRainJsonImport(CurrentUser user, RainJsonImportRequest request) {
        checkRateLimit(user.userId());
        String rawJson = request.rawJson();
        if (!StringUtils.hasText(rawJson)) {
            throw new BusinessException("RAIN_JSON_REQUIRED", "雨课堂JSON不能为空", HttpStatus.BAD_REQUEST);
        }
        if (rawJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > properties.maxRainJsonBytes()) {
            throw new BusinessException("RAIN_JSON_TOO_LARGE", "雨课堂JSON超过最大大小限制", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        List<RawRainItem> items;
        try {
            items = rainClassroomParser.parseJson(rawJson);
        } catch (Exception ex) {
            throw new BusinessException("RAIN_JSON_INVALID", "雨课堂JSON解析失败：" + ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
        if (items.isEmpty()) {
            throw new BusinessException("RAIN_JSON_EMPTY", "未解析到任何雨课堂数据", HttpStatus.BAD_REQUEST);
        }
        String contentHash = sha256(rawJson);
        RawDocument doc = rawDocumentService.save(buildRawDocument("RAIN_CLASSROOM", user.userId(), null, rawJson, contentHash, null));
        ImportTask task = createTask(user.userId(), "RAIN_JSON", doc.getId());
        int success = 0;
        int skipped = 0;
        int fail = 0;
        List<Long> eventIds = new ArrayList<>();
        List<String> failureReasons = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < items.size(); index++) {
            RawRainItem item = items.get(index);
            String plainText = item.toPlainText();
            if (!seen.add(sha256(plainText))) {
                skipped++;
                continue;
            }
            try {
                CognitionResult candidate = cognitionClient.extract("RAIN_CLASSROOM", plainText);
                Long eid = persistEvent(user, candidate, "RAIN_CLASSROOM", sha256(plainText), doc.getId(), null, true, plainText);
                eventIds.add(eid);
                success++;
            } catch (Exception ex) {
                fail++;
                if (failureReasons.size() < 20) {
                    failureReasons.add("第" + (index + 1) + "条：" + truncate(safeMessage(ex), 200));
                }
            }
        }
        task.setTaskStatus(success > 0 ? "SUCCESS" : "FAILED");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", items.size());
        summary.put("success", success);
        summary.put("skipped", skipped);
        summary.put("fail", fail);
        summary.put("failureReasons", failureReasons);
        summary.put("eventIds", eventIds);
        task.setResultSummary(toJson(summary));
        if (success == 0) {
            task.setErrorMessage("全部条目解析失败");
        }
        task.setFinishedAt(LocalDateTime.now());
        importTaskMapper.updateById(task);
        return response(task, success > 0
                ? "雨课堂JSON导入完成，成功" + success + "条，跳过" + skipped + "条，失败" + fail + "条"
                : "雨课堂JSON导入失败，未生成任何事件");
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportTaskResponse submitRainCookieImport(CurrentUser user, RainCookieImportRequest request) {
        checkRateLimit(user.userId());
        if (!properties.rainCookieEnabled()) {
            throw new BusinessException("RAIN_COOKIE_DISABLED", "Cookie导入尚未获得授权，暂不开放", HttpStatus.FORBIDDEN);
        }
        if (!Boolean.TRUE.equals(request.agreeOneTimeUse())) {
            throw new BusinessException("RAIN_COOKIE_CONSENT_REQUIRED", "必须同意一次性授权使用", HttpStatus.BAD_REQUEST);
        }
        String cookie = request.cookie();
        if (!StringUtils.hasText(cookie)) {
            throw new BusinessException("RAIN_COOKIE_REQUIRED", "Cookie不能为空", HttpStatus.BAD_REQUEST);
        }
        if (!cookie.contains("=")) {
            throw new BusinessException("RAIN_COOKIE_INVALID", "Cookie格式无效", HttpStatus.BAD_REQUEST);
        }
        ImportTask task = createTask(user.userId(), "RAIN_COOKIE", null);
        rainCookieStore.save("task:" + task.getId(), cookie);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("scopes", request.importScopes());
        summary.put("cookieStored", true);
        summary.put("ttlMinutes", properties.rainCookieTtlMinutes());
        summary.put("note", "Cookie已临时保存于Redis，一次性使用，不落库；雨课堂无公开API，任务待处理");
        task.setTaskStatus("PENDING");
        task.setResultSummary(toJson(summary));
        importTaskMapper.updateById(task);

        return response(task, "雨课堂Cookie已临时接收（一次性授权），任务已创建");
    }

    private Long persistEvent(CurrentUser user, CognitionResult candidate, String sourceType,
                              String contentHash, String rawDocId, String sourceUrl,
                              boolean privateEvent, String originalText) {
        String dedupKey = computeDedupKey(candidate.title(), candidate.startTime(), sourceType,
                privateEvent ? user.userId() : null);
        return eventServiceClient.createEvent(
                candidate.title(),
                originalText,
                candidate.eventType(),
                sourceType,
                candidate.startTime(),
                candidate.endTime(),
                candidate.location(),
                candidate.organizer(),
                toJson(candidate.targetScopes()),
                toJson(candidate.tags()),
                privateEvent ? "PRIVATE" : "PUBLIC",
                privateEvent ? user.userId() : null,
                dedupKey,
                rawDocId,
                sourceUrl,
                contentHash
        );
    }

    /**
     * 尝试创建信息条目；若失败则记录补偿日志并返回 true 表示需要补偿。
     * 事件已创建成功时，信息条目失败不影响主流程，但会标记待补偿状态供后续对账。
     */
    private boolean persistInformationItemWithCompensation(
            CognitionResult candidate, String sourceName, String text,
            String contentHash, Long eventId, CurrentUser user) {
        try {
            if (persistInformationItem(candidate, sourceName, text, contentHash, user) == null) {
                throw new IllegalStateException("信息条目创建失败");
            }
            return false;
        } catch (Exception ex) {
            log.warn("[SAGA-COMPENSATION] eventId={} 已创建但信息条目失败，待补偿: sourceName={}, title={}, cause={}",
                    eventId, sourceName, candidate.title(), ex.getMessage());
            return true;
        }
    }

    private Long persistInformationItem(CognitionResult candidate, String sourceName, String text,
                                         String contentHash, CurrentUser user) {
        String title = StringUtils.hasText(candidate.title()) ? candidate.title() : "未命名信息";
        String content = StringUtils.hasText(candidate.summary()) ? candidate.summary() : text;
        return informationServiceClient.createItem(title, content, sourceName, null, null, contentHash,
                LocalDateTime.now(), user.username(), user.userId());
    }

    public RawDocument getOwnedOriginalFile(CurrentUser user, String contentHash) {
        if (!StringUtils.hasText(contentHash) || !contentHash.matches("(?i)[0-9a-f]{64}")) {
            throw new BusinessException("CONTENT_HASH_INVALID", "内容哈希格式不正确", HttpStatus.BAD_REQUEST);
        }
        RawDocument document = rawDocumentService.findOwnedUserFile(contentHash, user.userId());
        if (document == null || (document.getOriginalFile() == null && !StringUtils.hasText(document.getPlainText()))) {
            throw new BusinessException("ORIGINAL_FILE_NOT_FOUND", "原文件不存在或已过保留期", HttpStatus.NOT_FOUND);
        }
        return document;
    }

    private RawDocument buildRawDocument(String sourceType, Long ownerUserId, String sourceUrl,
                                         String plainText, String contentHash, Map<String, Object> ocrMeta) {
        RawDocument d = new RawDocument();
        d.setSourceType(sourceType);
        d.setOwnerUserId(ownerUserId);
        d.setPrivacyLevel("PRIVATE");
        d.setSourceUrl(sourceUrl);
        d.setPlainText(plainText);
        d.setContentHash(contentHash);
        d.setOcrMeta(ocrMeta);
        return d;
    }

    private ImportTask createTask(Long userId, String importType, String rawDocId) {
        ImportTask t = new ImportTask();
        t.setUserId(userId);
        t.setImportType(importType);
        t.setTaskStatus("PENDING");
        t.setRawDocId(rawDocId);
        t.setCreatedAt(LocalDateTime.now());
        importTaskMapper.insert(t);
        return t;
    }

    private void succeedTask(ImportTask task, Long eventId, CognitionResult candidate, boolean infoCompensationNeeded) {
        task.setTaskStatus("SUCCESS");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("eventId", eventId);
        summary.put("eventType", candidate.eventType());
        summary.put("needHumanReview", candidate.needHumanReview());
        if (infoCompensationNeeded) {
            summary.put("infoCompensationNeeded", true);
            summary.put("infoCompensationReason", "information_item_creation_failed");
        }
        task.setResultSummary(toJson(summary));
        task.setFinishedAt(LocalDateTime.now());
        importTaskMapper.updateById(task);
    }

    private void failTask(ImportTask task, String error) {
        task.setTaskStatus("FAILED");
        String message = error == null ? "未知错误" : error;
        if (message.length() > 1000) {
            message = message.substring(0, 1000);
        }
        task.setErrorMessage(message);
        task.setFinishedAt(LocalDateTime.now());
        importTaskMapper.updateById(task);
    }

    private ImportTaskResponse response(ImportTask task, String message) {
        return new ImportTaskResponse(task.getId(), task.getTaskStatus(), message);
    }

    private String computeDedupKey(String title, String startTime, String sourceType, Long ownerUserId) {
        String normalized = (ownerUserId == null ? "" : ownerUserId + "|")
                + normalizeTitle(title) + "|" + (startTime == null ? "" : startTime.trim()) + "|" + sourceType;
        return sha256(normalized);
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim().replace('T', ' ');
        for (String pattern : DATE_PATTERNS) {
            try {
                return LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {
            }
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (Exception ignored) {
        }
        return null;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String sha256(String value) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null ? ex.getClass().getSimpleName() : message;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * 基于 Redis 的滑动窗口速率限制：单用户每分钟最多 rateLimitPerMinute 次导入。
     */
    void checkRateLimit(Long userId) {
        String key = "import:rate:" + userId;
        int limit = properties.rateLimitPerMinute();
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, 1, TimeUnit.MINUTES);
        }
        if (count != null && count > limit) {
            throw new BusinessException("RATE_LIMIT_EXCEEDED",
                    "导入操作过于频繁，每分钟最多 " + limit + " 次，请稍后再试",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
    }
}

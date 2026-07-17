package cn.campusmind.importing.application;

import cn.campusmind.importing.domain.RawDocument;
import cn.campusmind.importing.config.ImportProperties;
import com.mongodb.client.result.DeleteResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 原文存储服务：将原始网页、雨课堂 JSON、用户文本、OCR 图片元数据存入 MongoDB raw_documents 集合。
 */
@Service
public class RawDocumentService {

    private static final Logger log = LoggerFactory.getLogger(RawDocumentService.class);

    /** 脱敏字段白名单：这些字段在存储前会被删除 */
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "name", "phone", "mobile", "avatar", "studentid", "student_id",
            "deviceid", "device_id", "token", "accesstoken", "access_token",
            "refreshtoken", "refresh_token", "cookie", "authorization", "password",
            "secret", "apikey", "api_key"
    );

    private final MongoTemplate mongoTemplate;
    private final ImportProperties properties;
    private final ObjectMapper objectMapper;

    public RawDocumentService(MongoTemplate mongoTemplate, ImportProperties properties, ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public RawDocument save(RawDocument document) {
        if (document.getCreatedAt() == null) {
            document.setCreatedAt(LocalDateTime.now());
        }
        Instant expiresAt = Instant.now().plus(properties.rawDocumentRetentionDays(), ChronoUnit.DAYS);
        if (document.getExpiresAt() == null) {
            document.setExpiresAt(expiresAt);
        }
        document.setRetentionUntil(expiresAt);

        // 字段白名单脱敏：对 plainText 进行 JSON 脱敏处理
        if (StringUtils.hasText(document.getPlainText())) {
            document.setPlainText(sanitizeRawJson(document.getPlainText(), document.getSourceType()));
        }
        // 脱敏 httpMeta
        if (document.getHttpMeta() != null) {
            sanitizeMap(document.getHttpMeta());
        }
        // 脱敏 ocrMeta
        if (document.getOcrMeta() != null) {
            sanitizeMap(document.getOcrMeta());
        }

        return mongoTemplate.save(document);
    }

    public boolean deleteOwned(String documentId, Long ownerUserId) {
        DeleteResult result = mongoTemplate.remove(Query.query(Criteria.where("_id").is(documentId)
                .and("ownerUserId").is(ownerUserId)), RawDocument.class);
        return result.getDeletedCount() > 0;
    }

    public List<RawDocument> listOwned(Long ownerUserId) {
        return mongoTemplate.find(Query.query(Criteria.where("ownerUserId").is(ownerUserId)), RawDocument.class);
    }

    public RawDocument findOwnedUserFile(String contentHash, Long ownerUserId) {
        Query query = Query.query(Criteria.where("contentHash").is(contentHash)
                .and("ownerUserId").is(ownerUserId)
                .and("sourceType").is("USER_FILE"));
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return mongoTemplate.findOne(query, RawDocument.class);
    }

    public long deleteOwnedByUser(Long ownerUserId) {
        return mongoTemplate.remove(
                Query.query(Criteria.where("ownerUserId").is(ownerUserId)), RawDocument.class).getDeletedCount();
    }

    /**
     * 主动查询并删除已过期文档（作为 MongoDB TTL 的兑底）。
     */
    public int deleteExpired() {
        Query query = Query.query(Criteria.where("expiresAt").lt(Instant.now()));
        DeleteResult result = mongoTemplate.remove(query, RawDocument.class);
        int deleted = (int) result.getDeletedCount();
        if (deleted > 0) {
            log.info("清理过期原始文档: {} 条", deleted);
        }
        return deleted;
    }

    /**
     * 字段白名单脱敏：解析 JSON，删除敏感字段后返回脱敏后的 JSON。
     * 非 JSON 文本原样返回（不修改）。
     */
    public String sanitizeRawJson(String rawJson, String sourceType) {
        if (rawJson == null || rawJson.isBlank()) {
            return rawJson;
        }
        String trimmed = rawJson.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            // 非 JSON 文本，原样返回
            return rawJson;
        }
        try {
            Object parsed = objectMapper.readValue(trimmed, Object.class);
            sanitizeValue(parsed);
            return objectMapper.writeValueAsString(parsed);
        } catch (Exception ex) {
            log.debug("脱敏解析失败，原样保留: sourceType={}, error={}", sourceType, ex.getMessage());
            return rawJson;
        }
    }

    /**
     * 递归删除 Map 中的敏感字段。
     */
    private void sanitizeMap(Map<String, Object> map) {
        map.keySet().removeIf(key -> SENSITIVE_FIELDS.contains(key.toLowerCase(java.util.Locale.ROOT)));
        for (Object value : map.values()) {
            sanitizeValue(value);
        }
    }

    @SuppressWarnings("unchecked")
    private void sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            sanitizeMap((Map<String, Object>) map);
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(this::sanitizeValue);
        }
    }
}

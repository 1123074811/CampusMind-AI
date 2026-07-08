package cn.campusmind.importing.application;

import cn.campusmind.importing.domain.RawDocument;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 原文存储服务：将原始网页、雨课堂 JSON、用户文本、OCR 图片元数据存入 MongoDB raw_documents 集合。
 */
@Service
public class RawDocumentService {

    private final MongoTemplate mongoTemplate;

    public RawDocumentService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public RawDocument save(RawDocument document) {
        if (document.getCreatedAt() == null) {
            document.setCreatedAt(LocalDateTime.now());
        }
        return mongoTemplate.save(document);
    }
}

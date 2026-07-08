package cn.campusmind.ai.domain;

import java.util.Map;

/**
 * 向量检索命中的单个结果。
 *
 * @param docId     向量库文档ID
 * @param text      命中的事件向量文本
 * @param score     相似度得分（0-1，越大越相关）
 * @param metadata  附带元数据（如 eventId、eventType 等）
 */
public record VectorSearchHit(
        String docId,
        String text,
        double score,
        Map<String, Object> metadata
) {
}

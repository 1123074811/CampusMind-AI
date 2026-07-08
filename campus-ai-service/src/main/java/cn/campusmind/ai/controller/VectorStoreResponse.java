package cn.campusmind.ai.controller;

/**
 * 向量入库响应。
 *
 * @param docId 实际入库的文档ID（可用于回填 campus_event.vector_doc_id）
 * @param text  实际向量化的事件文本（便于调用方核对）
 */
public record VectorStoreResponse(
        String docId,
        String text
) {
}

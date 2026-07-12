package cn.campusmind.ai.vector;

import cn.campusmind.ai.domain.VectorSearchHit;

import java.util.List;
import java.util.Map;

/**
 * 事件向量存储抽象。负责将事件文本向量化并入库，以及按查询做语义检索。
 *
 * <p>两类实现：
 * <ul>
 *   <li>{@link InMemoryEventVectorStore}：rule 模式默认，用关键字重叠度近似检索，
 *       不依赖外部 embedding 模型，便于本地开发与测试</li>
 *   <li>{@link PgEventVectorStore}：llm 模式，基于 Spring AI {@code VectorStore} +
 *       {@code EmbeddingModel}，使用 PGVector 真实向量检索</li>
 * </ul>
 */
public interface EventVectorStore {

    /**
     * 存储一条事件向量文本。
     *
     * @param docId    文档ID（通常为 eventId 或 raw doc id）
     * @param text     向量文本（由 {@code /api/v1/ai/vector/text} 生成）
     * @param metadata 元数据（eventId、eventType 等，可空）
     * @return 实际入库的文档ID
     */
    String store(String docId, String text, Map<String, Object> metadata);

    /**
     * 语义检索（不含用户可见性过滤）。
     *
     * @param query 查询文本
     * @param topK  召回数量
     * @return 按相关性降序的命中列表
     */
    List<VectorSearchHit> search(String query, int topK);

    /**
     * 语义检索（含用户可见性过滤）。
     *
     * <p>私有事件只返回 ownerUserId 与 userId 匹配的文档；
     * userId 为 null 时只返回 PUBLIC 文档。
     *
     * @param query  查询文本
     * @param topK   召回数量
     * @param userId 当前用户 ID，可为 null（未登录）
     * @return 按相关性降序的命中列表
     */
    List<VectorSearchHit> search(String query, int topK, Long userId);
}

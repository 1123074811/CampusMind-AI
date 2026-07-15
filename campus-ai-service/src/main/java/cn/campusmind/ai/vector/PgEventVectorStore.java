package cn.campusmind.ai.vector;

import cn.campusmind.ai.domain.VectorSearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 基于 Spring AI {@link VectorStore}（PGVector）的事件向量库实现。
 *
 * <p>当 {@code campus.ai.vector-store=pg} 时启用。需要 PGVector 数据源与 embedding 模型可用
 * （由 {@code spring-ai-starter-vector-store-pgvector} 自动配置）。
 * {@link VectorStore#add(List)} 内部会调用 {@code EmbeddingModel} 生成向量并入库，
 * {@link VectorStore#similaritySearch(SearchRequest)} 内部 embed 查询再做向量检索。
 *
 * <p>依赖外部 PostgreSQL/pgvector 与真实 embedding 模型，需通过 {@code llm,pg}
 * profiles 做端到端验证。
 */
@Component
@ConditionalOnProperty(name = "campus.ai.vector-store", havingValue = "pg")
public class PgEventVectorStore implements EventVectorStore {

    private static final Logger log = LoggerFactory.getLogger(PgEventVectorStore.class);

    private static final String SCORE_KEY = "distance";

    private final VectorStore vectorStore;

    public PgEventVectorStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public String store(String docId, String text, Map<String, Object> metadata) {
        String resolvedDocId = (docId == null || docId.isBlank()) ? UUID.randomUUID().toString() : docId;
        Map<String, Object> meta = metadata == null ? Map.of() : metadata;
        Document document = new Document(resolvedDocId, text == null ? "" : text, meta);
        try {
            vectorStore.add(List.of(document));
            log.debug("PG 向量入库成功 docId={}", resolvedDocId);
            return resolvedDocId;
        } catch (RuntimeException ex) {
            log.warn("PG 向量入库失败 docId={}", resolvedDocId, ex);
            throw ex;
        }
    }

    @Override
    public List<VectorSearchHit> search(String query, int topK) {
        return search(query, topK, null);
    }

    @Override
    public List<VectorSearchHit> search(String query, int topK, Long userId) {
        int limit = topK <= 0 ? 10 : topK;
        try {
            SearchRequest.Builder requestBuilder = SearchRequest.builder()
                    .query(query)
                    .topK(limit);
            // 构建可见性过滤表达式
            if (userId != null) {
                requestBuilder.filterExpression(
                        "visibility == 'PUBLIC' || ownerUserId == '" + userId + "'");
            } else {
                requestBuilder.filterExpression("visibility == 'PUBLIC'");
            }
            SearchRequest request = requestBuilder.build();
            List<Document> documents = vectorStore.similaritySearch(request);
            if (documents == null || documents.isEmpty()) {
                return List.of();
            }
            // PG 向量库的 filterExpression 不一定生效，双重过滤保障
            List<VectorSearchHit> hits = new ArrayList<>(documents.size());
            for (Document doc : documents) {
                if (!isVisibleTo(doc.getMetadata(), userId)) {
                    continue;
                }
                double score = extractScore(doc);
                hits.add(new VectorSearchHit(doc.getId(), doc.getText(), score, doc.getMetadata()));
            }
            return hits;
        } catch (RuntimeException ex) {
            log.warn("PG 向量检索失败 query={}", query, ex);
            return List.of();
        }
    }

    @Override
    public void delete(List<String> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return;
        }
        vectorStore.delete(docIds);
    }

    /**
     * 本地可见性双重过滤，防止 filterExpression 失效时泄露私有事件。
     */
    private static boolean isVisibleTo(Map<String, Object> metadata, Long userId) {
        if (metadata == null) {
            return true;
        }
        Object visibility = metadata.get("visibility");
        if (visibility == null || "PUBLIC".equals(visibility.toString())) {
            return true;
        }
        if ("PRIVATE".equals(visibility.toString())) {
            Object ownerUserId = metadata.get("ownerUserId");
            if (ownerUserId == null) {
                return false;
            }
            return userId != null && userId.toString().equals(ownerUserId.toString());
        }
        return true;
    }

    private static double extractScore(Document doc) {
        Map<String, Object> metadata = doc.getMetadata();
        if (metadata == null) {
            return 1.0;
        }
        Object raw = metadata.get(SCORE_KEY);
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        return 1.0;
    }
}

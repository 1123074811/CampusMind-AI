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
 * <p>依赖外部 PG 与 embedding 模型，单测无法覆盖；运行需真实环境。
 * 默认 {@code vector-store=memory}，要启用 PG 真实向量检索需显式配置
 * {@code campus.ai.vector-store=pg} 并提供 embedding + datasource。
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
        int limit = topK <= 0 ? 10 : topK;
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(limit)
                    .build();
            List<Document> documents = vectorStore.similaritySearch(request);
            if (documents == null || documents.isEmpty()) {
                return List.of();
            }
            List<VectorSearchHit> hits = new ArrayList<>(documents.size());
            for (Document doc : documents) {
                double score = extractScore(doc);
                hits.add(new VectorSearchHit(doc.getId(), doc.getText(), score, doc.getMetadata()));
            }
            return hits;
        } catch (RuntimeException ex) {
            log.warn("PG 向量检索失败 query={}", query, ex);
            return List.of();
        }
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

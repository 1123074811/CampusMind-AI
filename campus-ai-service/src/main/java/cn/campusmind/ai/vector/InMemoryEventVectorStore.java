package cn.campusmind.ai.vector;

import cn.campusmind.ai.domain.VectorSearchHit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于内存与关键字重叠度的事件向量库实现。
 *
 * <p>当 {@code campus.ai.vector-store=memory}（默认）时启用，与 {@code campus.ai.mode}
 * 解耦——意味着 llm 模式下也可用内存向量库（开发阶段 LLM 接 DeepSeek 但不依赖 PG/embedding）。
 * 不依赖外部 embedding 模型，用 token 集合的 Jaccard 相似度近似语义检索，便于本地开发与测试。
 * 设计文档中"向量库未接入时降级为关键字检索"即指此实现。
 *
 * <p>分词策略：中文按单字切分（"人工智能主题讲座"→人/工/智/能/...），英文数字按词切分
 * （"LECTURE"→lecture），使短查询能命中长文本中的关键词。
 *
 * <p>线程安全：存储与检索均加 {@code synchronized}，适合开发期低并发场景。
 */
@Component
@ConditionalOnProperty(name = "campus.ai.vector-store", havingValue = "memory", matchIfMissing = true)
public class InMemoryEventVectorStore implements EventVectorStore {

    private static final Pattern CJK_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]");
    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z0-9]+");

    private final List<StoredDoc> docs = new ArrayList<>();

    @Override
    public synchronized String store(String docId, String text, Map<String, Object> metadata) {
        String resolvedDocId = (docId == null || docId.isBlank()) ? UUID.randomUUID().toString() : docId;
        docs.removeIf(existing -> existing.docId().equals(resolvedDocId));
        docs.add(new StoredDoc(resolvedDocId, text == null ? "" : text, metadata));
        return resolvedDocId;
    }

    @Override
    public synchronized List<VectorSearchHit> search(String query, int topK) {
        return search(query, topK, null);
    }

    @Override
    public synchronized List<VectorSearchHit> search(String query, int topK, Long userId) {
        Set<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }
        List<VectorSearchHit> hits = new ArrayList<>();
        for (StoredDoc doc : docs) {
            if (!isVisibleTo(doc.metadata, userId)) {
                continue;
            }
            Set<String> textTokens = tokenize(doc.text);
            double score = jaccard(queryTokens, textTokens);
            if (score > 0) {
                hits.add(new VectorSearchHit(doc.docId, doc.text, score, doc.metadata));
            }
        }
        hits.sort(Comparator.comparingDouble(VectorSearchHit::score).reversed());
        int limit = topK <= 0 ? 10 : topK;
        return hits.size() <= limit ? hits : new ArrayList<>(hits.subList(0, limit));
    }

    @Override
    public synchronized void delete(List<String> docIds) {
        if (docIds != null && !docIds.isEmpty()) {
            docs.removeIf(doc -> docIds.contains(doc.docId()));
        }
    }

    /**
     * 可见性检查：PUBLIC 文档对所有人可见，PRIVATE 文档只对 owner 可见。
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

    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new HashSet<>();
        Matcher cjk = CJK_PATTERN.matcher(text.toLowerCase());
        while (cjk.find()) {
            tokens.add(cjk.group());
        }
        Matcher word = WORD_PATTERN.matcher(text.toLowerCase());
        while (word.find()) {
            tokens.add(word.group());
        }
        return tokens;
    }

    private static double jaccard(Set<String> queryTokens, Set<String> textTokens) {
        if (queryTokens.isEmpty() || textTokens.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(queryTokens);
        intersection.retainAll(textTokens);
        Set<String> union = new HashSet<>(queryTokens);
        union.addAll(textTokens);
        if (union.isEmpty()) {
            return 0.0;
        }
        return Math.round(((double) intersection.size() / union.size()) * 1000.0) / 1000.0;
    }

    private record StoredDoc(String docId, String text, Map<String, Object> metadata) {
    }
}

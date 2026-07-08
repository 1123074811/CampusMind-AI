package cn.campusmind.ai.vector;

import cn.campusmind.ai.domain.VectorSearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryEventVectorStoreTest {

    @Test
    void shouldStoreAndSearchByKeywordOverlap() {
        InMemoryEventVectorStore store = new InMemoryEventVectorStore();
        store.store("evt-1", "标题：人工智能主题讲座\n类型：LECTURE\n地点：图书馆报告厅", Map.of("eventType", "LECTURE"));
        store.store("evt-2", "标题：期末考试安排\n类型：EXAM\n地点：第一教学楼", Map.of("eventType", "EXAM"));

        List<VectorSearchHit> hits = store.search("AI 讲座", 10);

        assertEquals(1, hits.size());
        assertEquals("evt-1", hits.get(0).docId());
        assertTrue(hits.get(0).score() > 0);
    }

    @Test
    void shouldRespectTopKLimit() {
        InMemoryEventVectorStore store = new InMemoryEventVectorStore();
        store.store("a", "校园活动 报名 社团", null);
        store.store("b", "社团活动 报名", null);
        store.store("c", "社团 报名 活动", null);

        List<VectorSearchHit> hits = store.search("社团活动报名", 2);

        assertTrue(hits.size() <= 2);
        assertTrue(hits.stream().allMatch(h -> h.score() > 0));
    }

    @Test
    void shouldReturnEmptyWhenNoOverlap() {
        InMemoryEventVectorStore store = new InMemoryEventVectorStore();
        store.store("evt-1", "期末考试安排", Map.of());

        List<VectorSearchHit> hits = store.search("图书馆开放时间", 10);

        assertTrue(hits.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenQueryBlank() {
        InMemoryEventVectorStore store = new InMemoryEventVectorStore();
        store.store("evt-1", "校园事件", Map.of());

        List<VectorSearchHit> hits = store.search("   ", 10);

        assertTrue(hits.isEmpty());
    }

    @Test
    void shouldGenerateDocIdWhenAbsent() {
        InMemoryEventVectorStore store = new InMemoryEventVectorStore();
        String docId = store.store(null, "校园事件文本", Map.of());

        assertEquals("校园事件文本", store.search("校园事件", 10).get(0).text());
        assertTrue(docId != null && !docId.isBlank());
    }
}

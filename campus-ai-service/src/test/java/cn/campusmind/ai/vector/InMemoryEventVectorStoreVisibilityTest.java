package cn.campusmind.ai.vector;

import cn.campusmind.ai.domain.VectorSearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 向量库可见性过滤测试：确保私有事件不被其他用户检索到。
 */
class InMemoryEventVectorStoreVisibilityTest {

    @Test
    void shouldNotReturnPrivateEventsToOtherUsers() {
        InMemoryEventVectorStore store = new InMemoryEventVectorStore();
        // 用户A的私有事件
        store.store("private-a", "标题：我的课表数据\n类型：COURSE",
                Map.of("visibility", "PRIVATE", "ownerUserId", 100L));
        // 公共事件
        store.store("public-1", "标题：校园讲座通知\n类型：LECTURE",
                Map.of("visibility", "PUBLIC"));

        // 用户B搜索，不应看到用户A的私有事件
        List<VectorSearchHit> hitsForUserB = store.search("课表", 10, 200L);
        assertTrue(hitsForUserB.isEmpty(), "用户B不应检索到用户A的私有事件");

        // 用户A搜索自己的私有事件，应能命中
        List<VectorSearchHit> hitsForUserA = store.search("课表", 10, 100L);
        assertEquals(1, hitsForUserA.size());
        assertEquals("private-a", hitsForUserA.get(0).docId());
    }

    @Test
    void shouldReturnPublicEventsToAllUsers() {
        InMemoryEventVectorStore store = new InMemoryEventVectorStore();
        store.store("public-1", "标题：人工智能讲座\n类型：LECTURE",
                Map.of("visibility", "PUBLIC"));
        store.store("public-2", "标题：数学竞赛报名\n类型：COMPETITION",
                Map.of("visibility", "PUBLIC"));

        // 用户A可以搜索到公共事件
        List<VectorSearchHit> hitsUserA = store.search("讲座", 10, 100L);
        assertEquals(1, hitsUserA.size());
        assertEquals("public-1", hitsUserA.get(0).docId());

        // 用户B也可以搜索到公共事件
        List<VectorSearchHit> hitsUserB = store.search("讲座", 10, 200L);
        assertEquals(1, hitsUserB.size());
        assertEquals("public-1", hitsUserB.get(0).docId());

        // 无用户身份（null）也可以搜索到公共事件
        List<VectorSearchHit> hitsAnonymous = store.search("讲座", 10, null);
        assertEquals(1, hitsAnonymous.size());
        assertEquals("public-1", hitsAnonymous.get(0).docId());
    }

    @Test
    void shouldNotReturnPrivateEventsToAnonymousUser() {
        InMemoryEventVectorStore store = new InMemoryEventVectorStore();
        store.store("private-1", "标题：我的作业\n类型：HOMEWORK",
                Map.of("visibility", "PRIVATE", "ownerUserId", 100L));

        // 匿名用户（null userId）不应看到私有事件
        List<VectorSearchHit> hits = store.search("作业", 10, null);
        assertTrue(hits.isEmpty(), "匿名用户不应检索到私有事件");
    }

    @Test
    void shouldTreatNullVisibilityAsPublic() {
        InMemoryEventVectorStore store = new InMemoryEventVectorStore();
        // 没有 visibility metadata 的文档应视为 PUBLIC
        store.store("legacy-1", "标题：旧数据通知\n类型：NOTICE", null);

        List<VectorSearchHit> hits = store.search("通知", 10, 200L);
        assertEquals(1, hits.size());
        assertEquals("legacy-1", hits.get(0).docId());
    }
}

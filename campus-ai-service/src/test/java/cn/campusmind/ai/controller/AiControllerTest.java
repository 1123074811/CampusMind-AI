package cn.campusmind.ai.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void cognitionExtractsCampusEventCandidate() throws Exception {
        mockMvc.perform(post("/api/v1/ai/cognition/extract")
                        .contentType("application/json")
                        .content("""
                                {
                                  "sourceType": "USER_TEXT",
                                  "plainText": "人工智能主题讲座通知\\n时间：2026年7月8日 19:00\\n地点：图书馆报告厅\\n主办：软件学院\\n对象：软件学院 学生"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventType").value("LECTURE"))
                .andExpect(jsonPath("$.data.location").value("图书馆报告厅"))
                .andExpect(jsonPath("$.data.startTime").value("2026-07-08T19:00:00+08:00"))
                .andExpect(jsonPath("$.data.tags[1]").value("AI"))
                .andExpect(jsonPath("$.data.needHumanReview").value(false));
    }

    @Test
    void cognitionBuildsTraceableCompetitionCard() throws Exception {
        mockMvc.perform(post("/api/v1/ai/cognition/extract")
                        .contentType("application/json")
                        .content("""
                                {
                                  "sourceType": "PUBLIC_WEB",
                                  "originalItemId": 120,
                                  "originalUrl": "https://example.edu/competition/120",
                                  "plainText": "创新创业竞赛报名通知\\n报名时间：2026年7月1日 09:00\\n报名截止：2026年7月15日 18:00\\n所需材料：报名表、项目计划书、学生证照片\\n报名方式：在线填写报名表\\n组队要求：每队3至5人\\n报名网址：https://example.edu/apply"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventType").value("COMPETITION"))
                .andExpect(jsonPath("$.data.originalItemId").value(120))
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.edu/competition/120"))
                .andExpect(jsonPath("$.data.registrationDeadline").value("2026年7月15日 18:00"))
                .andExpect(jsonPath("$.data.requiredMaterials[0]").value("报名表"))
                .andExpect(jsonPath("$.data.registrationUrl").value("https://example.edu/apply"));
    }

    @Test
    void decisionPlansSemanticSearch() throws Exception {
        mockMvc.perform(post("/api/v1/ai/decision/plan")
                        .contentType("application/json")
                        .content("""
                                {
                                  "query": "最近有没有 AI 相关讲座",
                                  "userScopes": ["软件学院"],
                                  "usePersonalProfile": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("SEMANTIC_SEARCH"))
                .andExpect(jsonPath("$.data.eventTypes[0]").value("LECTURE"))
                .andExpect(jsonPath("$.data.timeRange").value("RECENT"))
                .andExpect(jsonPath("$.data.useVectorSearch").value(true));
    }

    @Test
    void vectorTextBuildsStableTextForEmbedding() throws Exception {
        mockMvc.perform(post("/api/v1/ai/vector/text")
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "人工智能主题讲座通知",
                                  "summary": "软件学院举办AI讲座",
                                  "eventType": "LECTURE",
                                  "startTime": "2026-07-08T19:00:00+08:00",
                                  "location": "图书馆报告厅",
                                  "targetScopes": ["软件学院"],
                                  "tags": ["AI", "讲座"],
                                  "content": "欢迎同学参加"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.text", containsString("标题：人工智能主题讲座通知")))
                .andExpect(jsonPath("$.data.text", containsString("标签：AI、讲座")));
    }

    @Test
    void chatReturnsPlanBackedAnswer() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType("application/json")
                        .content("""
                                {
                                  "sessionId": "chat-test",
                                  "message": "我本周有哪些作业",
                                  "usePersonalProfile": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value("chat-test"))
                .andExpect(jsonPath("$.data.plan.intent").value("PERSONAL_SCHEDULE"))
                .andExpect(jsonPath("$.data.plan.usePersonalProfile").value(true));
    }

    @Test
    void vectorStoreAndSearchRoundTrip() throws Exception {
        mockMvc.perform(post("/api/v1/ai/vector/store")
                        .contentType("application/json")
                        .content("""
                                {
                                  "docId": "evt-1",
                                  "event": {
                                    "title": "人工智能主题讲座通知",
                                    "summary": "软件学院举办AI讲座",
                                    "eventType": "LECTURE",
                                    "startTime": "2026-07-08T19:00:00+08:00",
                                    "location": "图书馆报告厅",
                                    "targetScopes": ["软件学院"],
                                    "tags": ["AI", "讲座"],
                                    "content": "欢迎同学参加"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.docId").value("evt-1"))
                .andExpect(jsonPath("$.data.text", containsString("标题：人工智能主题讲座通知")));

        mockMvc.perform(post("/api/v1/ai/vector/search")
                        .contentType("application/json")
                        .content("""
                                {
                                  "query": "AI 讲座",
                                  "topK": 10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hits[?(@.docId == 'evt-1')]", hasSize(1)))
                .andExpect(jsonPath("$.data.hits[?(@.docId == 'evt-1')].text", hasItem(containsString("人工智能主题讲座"))));
    }

    @Test
    void chatWithRagContextRecallsStoredEvent() throws Exception {
        mockMvc.perform(post("/api/v1/ai/vector/store")
                        .contentType("application/json")
                        .content("""
                                {
                                  "docId": "evt-rag",
                                  "event": {
                                    "title": "人工智能主题讲座",
                                    "eventType": "LECTURE",
                                    "summary": "软件学院AI讲座",
                                    "location": "图书馆报告厅",
                                    "tags": ["AI", "讲座"]
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType("application/json")
                        .content("""
                                {
                                  "sessionId": "rag-test",
                                  "message": "AI 讲座 相关 解释一下"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value("rag-test"))
                .andExpect(jsonPath("$.data.plan.intent").value("QA_EXPLAIN"))
                .andExpect(jsonPath("$.data.answer", containsString("人工智能主题讲座")));
    }
}

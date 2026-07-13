package cn.campusmind.crawler.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiCardExtractorTest {

    @Test
    void parsesWrappedCognitionResult() throws Exception {
        AiCardExtractor extractor = new AiCardExtractor(new ObjectMapper(), "http://localhost/unused");
        AiCardExtractor.Result result = extractor.parse("""
                {"data":{"eventType":"COMPETITION","summary":"报名摘要","needHumanReview":true}}
                """);

        assertEquals("COMPETITION", result.eventType());
        assertEquals("报名摘要", result.summary());
        assertTrue(result.needHumanReview());
    }

    @Test
    void rejectsSummaryCopiedFromLongOriginalContent() {
        String summary = "软件学院将于本周召开人工智能课程改革研讨会，讨论课程建设、教师培训与学生能力培养方案。";
        String content = "通知：" + summary + "请相关教师准时参会，并提前准备交流材料。";

        assertThrows(IllegalStateException.class, () -> AiCardExtractor.validateSummary(summary, content));
    }
}

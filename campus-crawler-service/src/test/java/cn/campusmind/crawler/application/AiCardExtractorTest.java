package cn.campusmind.crawler.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}

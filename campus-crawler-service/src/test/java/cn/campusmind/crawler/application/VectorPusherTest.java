package cn.campusmind.crawler.application;

import cn.campusmind.crawler.domain.InformationItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VectorPusherTest {

    @Test
    @SuppressWarnings("unchecked")
    void usesCardTimeInsteadOfPublishTimeAndKeepsSourceMetadata() throws Exception {
        InformationItem item = new InformationItem();
        item.setId(12L);
        item.setTitle("人工智能讲座");
        item.setSourceName("软件学院");
        item.setItemUrl("https://example.edu/notice/12");
        item.setPublishTime(LocalDateTime.of(2026, 7, 1, 8, 0));
        item.setItemStatus("ACTIVE");
        item.setDetailContent("讲座正文");
        item.setContentHash("hash");
        item.setAiEventType("LECTURE");
        item.setAiSummary("讲座摘要");
        item.setAiCardJson("""
                {"startTime":"2026-07-17T19:00:00+08:00","endTime":"2026-07-17T21:00:00+08:00",
                 "location":"报告厅","targetScopes":["软件学院"],"tags":["AI","讲座"]}
                """);

        Map<String, Object> body = new VectorPusher(new ObjectMapper(), "http://localhost:8089/vector")
                .buildBody(item, null);
        Map<String, Object> event = (Map<String, Object>) body.get("event");

        assertThat(event.get("startTime")).isEqualTo("2026-07-17T19:00:00+08:00");
        assertThat(event.get("startTime")).isNotEqualTo(body.get("publishedAt"));
        assertThat(event.get("targetScopes")).isEqualTo(List.of("软件学院"));
        assertThat(body.get("publishedAt")).isEqualTo("2026-07-01T08:00:00");
        assertThat(body.get("originalUrl")).isEqualTo("https://example.edu/notice/12");
    }
}

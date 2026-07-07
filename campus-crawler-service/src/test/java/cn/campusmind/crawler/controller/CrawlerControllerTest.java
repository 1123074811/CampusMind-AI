package cn.campusmind.crawler.controller;

import cn.campusmind.crawler.application.PublicWebFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CrawlerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @MockBean
    private PublicWebFetcher publicWebFetcher;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS data_source (
                      id BIGINT PRIMARY KEY,
                      name VARCHAR(128) NOT NULL,
                      source_type VARCHAR(64) NOT NULL,
                      base_url VARCHAR(1024) NOT NULL,
                      robots_url VARCHAR(1024),
                      crawl_interval_seconds INT NOT NULL,
                      parser_type VARCHAR(64) NOT NULL,
                      selector_config CLOB,
                      enabled TINYINT NOT NULL,
                      last_crawled_at TIMESTAMP,
                      updated_at TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crawl_task (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      source_id BIGINT NOT NULL,
                      task_status VARCHAR(32) NOT NULL,
                      crawl_url VARCHAR(1024) NOT NULL,
                      http_status INT,
                      etag VARCHAR(255),
                      last_modified VARCHAR(255),
                      fail_reason VARCHAR(1024),
                      started_at TIMESTAMP,
                      finished_at TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS web_crawl_item (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      task_id BIGINT NOT NULL,
                      source_id BIGINT NOT NULL,
                      source_name VARCHAR(128) NOT NULL,
                      source_url VARCHAR(1024) NOT NULL,
                      item_url VARCHAR(1024) NOT NULL,
                      title VARCHAR(512) NOT NULL,
                      date_text VARCHAR(64),
                      summary CLOB,
                      content_hash CHAR(64) NOT NULL,
                      parser_version VARCHAR(64),
                      fetched_at TIMESTAMP,
                      UNIQUE KEY uk_web_crawl_item_hash (content_hash)
                    )
                    """);
            statement.execute("DELETE FROM web_crawl_item");
            statement.execute("DELETE FROM crawl_task");
            statement.execute("DELETE FROM data_source");
        }
        insertSource();
        Mockito.when(publicWebFetcher.fetch("https://www.xju.edu.cn/xwzx/tzgg.htm"))
                .thenReturn(new PublicWebFetcher.FetchResult(200, "\"etag-1\"",
                        "Tue, 07 Jul 2026 07:57:55 GMT", """
                        <ul class="list1">
                          <li>
                            <div class="time flex-v-center"><span>07</span>2026/07</div>
                            <a href="../info/1030/28464.htm" title="新疆大学2026年度拟新增本科专业公示">
                              <div class="txt"><h4>新疆大学2026年度拟新增本科专业公示</h4><p>专业公示摘要</p></div>
                            </a>
                          </li>
                        </ul>
                        """));
    }

    @Test
    void crawlSourceCreatesTaskAndReturnsDiscoveredLinks() throws Exception {
        mockMvc.perform(post("/api/admin/crawler/sources/9411/crawl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.httpStatus").value(200))
                .andExpect(jsonPath("$.data.discoveredCount").value(1))
                .andExpect(jsonPath("$.data.persistedCount").value(1))
                .andExpect(jsonPath("$.data.links[0].title").value("新疆大学2026年度拟新增本科专业公示"))
                .andExpect(jsonPath("$.data.links[0].url").value("https://www.xju.edu.cn/info/1030/28464.htm"));

        mockMvc.perform(post("/api/admin/crawler/sources/9411/crawl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.discoveredCount").value(1))
                .andExpect(jsonPath("$.data.persistedCount").value(0));
    }

    private void insertSource() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO data_source (
                       id, name, source_type, base_url, robots_url, crawl_interval_seconds,
                       parser_type, selector_config, enabled
                     ) VALUES (?, ?, 'PUBLIC_WEB', ?, ?, 10, 'WEBMAGIC', ?, 1)
                     """)) {
            statement.setLong(1, 9411L);
            statement.setString(2, "新疆大学通知公告");
            statement.setString(3, "https://www.xju.edu.cn/xwzx/tzgg.htm");
            statement.setString(4, "https://www.xju.edu.cn/robots.txt");
            statement.setString(5, """
                    {
                      "parserVersion": "xju-main-v1",
                      "list": {
                        "item": ".list1 li",
                        "link": "a[href*=\\"info/\\"]",
                        "title": "a[title], h4",
                        "summary": ".txt p",
                        "date": ".time"
                      }
                    }
                    """);
            statement.executeUpdate();
        }
    }
}

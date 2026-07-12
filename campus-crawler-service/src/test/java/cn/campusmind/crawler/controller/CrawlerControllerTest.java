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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                      detail_title VARCHAR(512),
                      date_text VARCHAR(64),
                      summary CLOB,
                      detail_content CLOB,
                      content_hash CHAR(64) NOT NULL,
                      parser_version VARCHAR(64),
                      detail_http_status INT,
                      detail_fetched_at TIMESTAMP,
                      detail_content_hash CHAR(64),
                      parse_status VARCHAR(32) NOT NULL DEFAULT 'LIST_ONLY',
                      parse_error VARCHAR(1024),
                      fetched_at TIMESTAMP,
                      UNIQUE KEY uk_web_crawl_item_hash (content_hash)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS campus_event (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      title VARCHAR(255) NOT NULL,
                      summary CLOB,
                      event_type VARCHAR(64) NOT NULL,
                      source_type VARCHAR(64) NOT NULL,
                      status VARCHAR(32) NOT NULL,
                      organizer VARCHAR(255),
                      start_time TIMESTAMP,
                      target_scope CLOB,
                      tags CLOB,
                      dedup_key CHAR(64),
                      published_at TIMESTAMP,
                      UNIQUE KEY uk_event_dedup_key (dedup_key)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS information_item (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      source_id BIGINT NOT NULL,
                      source_name VARCHAR(128) NOT NULL,
                      source_url VARCHAR(1024) NOT NULL,
                      item_url VARCHAR(1024) NOT NULL,
                      title VARCHAR(512) NOT NULL,
                      publish_time TIMESTAMP,
                      fetched_at TIMESTAMP NOT NULL,
                      detail_content CLOB NOT NULL,
                      content_hash CHAR(64) NOT NULL,
                      item_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                      parse_status VARCHAR(32) NOT NULL,
                      parse_error VARCHAR(1024),
                      ai_status VARCHAR(32) DEFAULT 'PENDING',
                      ai_event_type VARCHAR(32),
                      ai_summary CLOB,
                      ai_card_json CLOB,
                      ai_need_review BOOLEAN DEFAULT FALSE,
                      ai_error VARCHAR(1024),
                      ai_processed_at TIMESTAMP,
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                      UNIQUE KEY uk_information_item_url_title (item_url, title)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS user_information_state (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      user_id BIGINT NOT NULL,
                      item_id BIGINT NOT NULL,
                      read_status VARCHAR(32) NOT NULL,
                      first_seen_at TIMESTAMP NOT NULL,
                      read_at TIMESTAMP,
                      archived_at TIMESTAMP,
                      UNIQUE KEY uk_user_item_state (user_id, item_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS event_source_ref (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      event_id BIGINT NOT NULL,
                      source_id BIGINT,
                      raw_doc_id VARCHAR(64) NOT NULL,
                      source_url VARCHAR(1024),
                      source_title VARCHAR(255),
                      content_hash CHAR(64)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS event_audit_log (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      event_id BIGINT,
                      operator_id BIGINT,
                      action VARCHAR(64) NOT NULL,
                      before_snapshot CLOB,
                      after_snapshot CLOB,
                      comment VARCHAR(512),
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("DELETE FROM event_audit_log");
            statement.execute("DELETE FROM event_source_ref");
            statement.execute("DELETE FROM campus_event");
            statement.execute("DELETE FROM information_item");
            statement.execute("DELETE FROM user_information_state");
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
        Mockito.when(publicWebFetcher.fetch("https://www.xju.edu.cn/info/1030/28464.htm"))
                .thenReturn(new PublicWebFetcher.FetchResult(200, null, null, """
                        <div class="arc-tit"><h1>新疆大学2026年度拟新增本科专业公示</h1></div>
                        <div class="arc-info">信息日期：2026-07-07 15:57:55</div>
                        <div id="vsb_content"><div class="v_news_content">
                          <p>根据自治区教育厅通知，学校组织专家组对申报专业进行评审。</p>
                        </div></div>
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

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT detail_title, detail_content, detail_http_status, parse_status
                     FROM web_crawl_item WHERE source_id = 9411
                     """);
             java.sql.ResultSet resultSet = statement.executeQuery()) {
            org.assertj.core.api.Assertions.assertThat(resultSet.next()).isTrue();
            org.assertj.core.api.Assertions.assertThat(resultSet.getString("detail_title"))
                    .isEqualTo("新疆大学2026年度拟新增本科专业公示");
            org.assertj.core.api.Assertions.assertThat(resultSet.getString("detail_content"))
                    .contains("学校组织专家组");
            org.assertj.core.api.Assertions.assertThat(resultSet.getInt("detail_http_status")).isEqualTo(200);
            org.assertj.core.api.Assertions.assertThat(resultSet.getString("parse_status")).isEqualTo("DETAIL_SUCCESS");
        }

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT title, publish_time, detail_content, item_status, parse_status
                     FROM information_item WHERE source_id = 9411
                     """);
             java.sql.ResultSet resultSet = statement.executeQuery()) {
            org.assertj.core.api.Assertions.assertThat(resultSet.next()).isTrue();
            org.assertj.core.api.Assertions.assertThat(resultSet.getString("title"))
                    .isEqualTo("新疆大学2026年度拟新增本科专业公示");
            org.assertj.core.api.Assertions.assertThat(resultSet.getTimestamp("publish_time").toLocalDateTime())
                    .isEqualTo(java.time.LocalDateTime.of(2026, 7, 7, 15, 57, 55));
            org.assertj.core.api.Assertions.assertThat(resultSet.getString("detail_content"))
                    .contains("学校组织专家组");
            org.assertj.core.api.Assertions.assertThat(resultSet.getString("item_status")).isEqualTo("ACTIVE");
            org.assertj.core.api.Assertions.assertThat(resultSet.getString("parse_status")).isEqualTo("DETAIL_SUCCESS");
        }

        mockMvc.perform(post("/api/admin/crawler/sources/9411/crawl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.discoveredCount").value(1))
                .andExpect(jsonPath("$.data.persistedCount").value(0));
    }

    @Test
    void latestItemsReturnsFavoriteCount() throws Exception {
        mockMvc.perform(post("/api/admin/crawler/sources/9411/crawl"))
                .andExpect(status().isOk());

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO user_information_state (user_id, item_id, read_status, first_seen_at)
                    SELECT 1, id, 'FAVORITED', CURRENT_TIMESTAMP FROM information_item
                    """);
        }

        mockMvc.perform(get("/api/admin/crawler/items").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].favoriteCount").value(1));
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
                      },
                      "detail": {
                        "title": ".arc-tit h1",
                        "meta": ".arc-info",
                        "content": "#vsb_content .v_news_content",
                        "publishedAtRegex": "信息日期[:：]\\\\s*([0-9]{4}-[0-9]{2}-[0-9]{2}\\\\s+[0-9]{2}:[0-9]{2}:[0-9]{2})"
                      }
                    }
                    """);
            statement.executeUpdate();
        }
    }
}

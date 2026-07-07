package cn.campusmind.audit.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS campus_event (
                      id BIGINT PRIMARY KEY,
                      title VARCHAR(255) NOT NULL,
                      summary CLOB,
                      event_type VARCHAR(64) NOT NULL,
                      source_type VARCHAR(64) NOT NULL,
                      status VARCHAR(32) NOT NULL,
                      confidence DECIMAL(5,4) NOT NULL,
                      start_time TIMESTAMP,
                      end_time TIMESTAMP,
                      location VARCHAR(255),
                      organizer VARCHAR(255),
                      target_scope VARCHAR(1024),
                      tags VARCHAR(1024),
                      vector_doc_id VARCHAR(128),
                      published_at TIMESTAMP,
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS data_source (
                      id BIGINT PRIMARY KEY,
                      name VARCHAR(128) NOT NULL,
                      source_type VARCHAR(64) NOT NULL,
                      base_url VARCHAR(1024) NOT NULL,
                      parser_type VARCHAR(64) NOT NULL,
                      enabled TINYINT NOT NULL,
                      last_crawled_at TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crawl_task (
                      id BIGINT PRIMARY KEY,
                      source_id BIGINT NOT NULL,
                      task_status VARCHAR(32) NOT NULL,
                      crawl_url VARCHAR(1024) NOT NULL,
                      http_status INT,
                      fail_reason VARCHAR(1024),
                      started_at TIMESTAMP,
                      finished_at TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS import_task (
                      id BIGINT PRIMARY KEY,
                      user_id BIGINT NOT NULL,
                      import_type VARCHAR(64) NOT NULL,
                      task_status VARCHAR(32) NOT NULL,
                      raw_doc_id VARCHAR(64),
                      result_summary VARCHAR(1024),
                      error_message VARCHAR(1024),
                      created_at TIMESTAMP,
                      finished_at TIMESTAMP
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
                      content_hash CHAR(64) NOT NULL,
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS event_audit_log (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      event_id BIGINT NOT NULL,
                      operator_id BIGINT NOT NULL,
                      action VARCHAR(64) NOT NULL,
                      before_snapshot VARCHAR(1024),
                      after_snapshot VARCHAR(1024),
                      comment VARCHAR(512),
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("DELETE FROM event_audit_log");
            statement.execute("DELETE FROM import_task");
            statement.execute("DELETE FROM crawl_task");
            statement.execute("DELETE FROM data_source");
            statement.execute("DELETE FROM event_source_ref");
            statement.execute("DELETE FROM campus_event");
        }
        insertEvent(1001L, "人工智能主题讲座通知", "LECTURE", "PUBLIC_WEB", "AI_PUBLISHED", "0.9100", "图书馆报告厅", "[\"软件学院本科生\"]", "[\"AI\",\"讲座\",\"软件学院\"]", null);
        insertEvent(1002L, "期末考试考场调整说明", "EXAM", "PUBLIC_WEB", "CORRECTED", "0.7400", "一号教学楼", "[\"2023级\"]", "[\"考试\",\"教务\"]", "vec-1002");
        insertEvent(1003L, "雨课堂作业提交提醒", "HOMEWORK", "RAIN_CLASSROOM", "AI_PUBLISHED", "0.6800", "线上", "[\"SE101\"]", "[\"雨课堂\",\"作业\"]", null);
        insertEvent(1004L, "创新创业竞赛报名开放", "ACTIVITY", "PUBLIC_WEB", "REVIEWED", "0.8800", "学生事务中心", "[\"全校学生\"]", "[\"竞赛\",\"报名\"]", "vec-1004");
        insertEvent(1005L, "旧通知误识别", "NOTICE", "USER_TEXT", "REJECTED", "0.4200", null, "[]", "[\"通知\"]", null);
        insertSource(1L, "软件学院通知", "PUBLIC_WEB", 1, "2026-07-07 18:28:00");
        insertSource(2L, "教务处公告", "PUBLIC_WEB", 1, "2026-07-07 18:10:00");
        insertSource(3L, "雨课堂导入", "RAIN_CLASSROOM", 1, "2026-07-07 18:06:00");
        insertSource(4L, "用户截图 OCR", "USER_IMAGE", 0, "2026-07-07 17:30:00");
        insertTask(501L, 1L, "SUCCESS", 200, null, "2026-07-07 18:20:00");
        insertTask(502L, 1L, "RUNNING", null, null, "2026-07-07 18:18:00");
        insertTask(503L, 3L, "FAILED", 403, "403 授权失效", "2026-07-07 18:16:00");
        insertTask(504L, 2L, "PENDING", null, null, "2026-07-07 18:11:00");
        insertImportTask(601L, "RAIN_JSON", "RUNNING", "2026-07-07 18:19:00");
        insertSourceRef(1001L, 1L);
        insertSourceRef(1002L, 2L);
        insertSourceRef(1003L, 3L);
    }

    @Test
    void dashboardReturnsSeededAdminData() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics.reviewCount").value(3))
                .andExpect(jsonPath("$.data.metrics.urgentCount").value(2))
                .andExpect(jsonPath("$.data.events[?(@.id==1005)].title").value("旧通知误识别"))
                .andExpect(jsonPath("$.data.events[?(@.id==1001)].source").value("软件学院通知"))
                .andExpect(jsonPath("$.data.events[?(@.id==1002)].source").value("教务处公告"))
                .andExpect(jsonPath("$.data.dataSources[?(@.id==3)].status").value("NEEDS_AUTH"))
                .andExpect(jsonPath("$.data.dataSources[?(@.id==4)].status").value("PAUSED"))
                .andExpect(jsonPath("$.data.tasks[?(@.status=='FAILED')].note").value("403 授权失效"));
    }

    @Test
    void reviewUpdatesStatusAndWritesAuditLog() throws Exception {
        mockMvc.perform(put("/api/admin/events/1001/review")
                        .header("X-User-Id", "1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "status": "REVIEWED",
                                  "comment": "字段完整，确认发布"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVIEWED"))
                .andExpect(jsonPath("$.data.risk").value("已人工确认"));

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events[?(@.id==1001)].status").value("REVIEWED"));
    }

    private void insertEvent(Long id, String title, String type, String sourceType, String status, String confidence,
                             String location, String scope, String tags, String vectorDocId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO campus_event (
                       id, title, summary, event_type, source_type, status, confidence,
                       start_time, location, organizer, target_scope, tags, vector_doc_id, published_at, created_at
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, TIMESTAMP '2026-07-08 19:00:00', ?,
                       '软件学院', ?, ?, ?, TIMESTAMP '2026-07-07 10:00:00', TIMESTAMP '2026-07-07 18:00:00')
                     """)) {
            statement.setLong(1, id);
            statement.setString(2, title);
            statement.setString(3, title + "摘要");
            statement.setString(4, type);
            statement.setString(5, sourceType);
            statement.setString(6, status);
            statement.setBigDecimal(7, new java.math.BigDecimal(confidence));
            statement.setString(8, location);
            statement.setString(9, scope);
            statement.setString(10, tags);
            statement.setString(11, vectorDocId);
            statement.executeUpdate();
        }
    }

    private void insertSource(Long id, String name, String sourceType, int enabled, String lastCrawledAt) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO data_source (id, name, source_type, base_url, parser_type, enabled, last_crawled_at)
                     VALUES (?, ?, ?, ?, 'WEBMAGIC', ?, ?)
                     """)) {
            statement.setLong(1, id);
            statement.setString(2, name);
            statement.setString(3, sourceType);
            statement.setString(4, "https://example.edu.cn/" + id);
            statement.setInt(5, enabled);
            statement.setString(6, lastCrawledAt);
            statement.executeUpdate();
        }
    }

    private void insertTask(Long id, Long sourceId, String status, Integer httpStatus, String failReason, String startedAt) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO crawl_task (id, source_id, task_status, crawl_url, http_status, fail_reason, started_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setLong(1, id);
            statement.setLong(2, sourceId);
            statement.setString(3, status);
            statement.setString(4, "https://example.edu.cn/notice/" + id);
            if (httpStatus == null) {
                statement.setNull(5, java.sql.Types.INTEGER);
            } else {
                statement.setInt(5, httpStatus);
            }
            statement.setString(6, failReason);
            statement.setString(7, startedAt);
            statement.executeUpdate();
        }
    }

    private void insertImportTask(Long id, String type, String status, String createdAt) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO import_task (id, user_id, import_type, task_status, created_at)
                     VALUES (?, 1, ?, ?, ?)
                     """)) {
            statement.setLong(1, id);
            statement.setString(2, type);
            statement.setString(3, status);
            statement.setString(4, createdAt);
            statement.executeUpdate();
        }
    }

    private void insertSourceRef(Long eventId, Long sourceId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO event_source_ref (event_id, source_id, raw_doc_id, source_title, content_hash)
                     VALUES (?, ?, ?, ?, ?)
                     """)) {
            statement.setLong(1, eventId);
            statement.setLong(2, sourceId);
            statement.setString(3, "raw-" + eventId);
            statement.setString(4, "source-" + eventId);
            statement.setString(5, "hash-" + eventId);
            statement.executeUpdate();
        }
    }
}

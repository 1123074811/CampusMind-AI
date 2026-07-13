package cn.campusmind.audit.controller;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                    CREATE TABLE IF NOT EXISTS information_item (
                      id BIGINT PRIMARY KEY,
                      source_name VARCHAR(128) NOT NULL,
                      source_url VARCHAR(1024) NOT NULL,
                      item_url VARCHAR(1024) NOT NULL,
                      title VARCHAR(512) NOT NULL,
                      publish_time TIMESTAMP,
                      fetched_at TIMESTAMP NOT NULL,
                      detail_content CLOB NOT NULL,
                      item_status VARCHAR(32) NOT NULL,
                      parse_status VARCHAR(32) NOT NULL,
                      ai_status VARCHAR(32) DEFAULT 'PENDING',
                      ai_event_type VARCHAR(32),
                      ai_summary CLOB,
                      ai_card_json CLOB,
                      ai_need_review BOOLEAN DEFAULT FALSE,
                      ai_error VARCHAR(1024),
                      ai_processed_at TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ai_processing_record (
                      id BIGINT PRIMARY KEY,
                      information_item_id BIGINT NOT NULL,
                      content_hash CHAR(64) NOT NULL,
                      task_type VARCHAR(32) NOT NULL,
                      trigger_type VARCHAR(32) NOT NULL,
                      status VARCHAR(32) NOT NULL,
                      provider VARCHAR(64),
                      model_version VARCHAR(128),
                      prompt_version VARCHAR(64) NOT NULL,
                      prompt_tokens INT,
                      completion_tokens INT,
                      error_message VARCHAR(1024),
                      started_at TIMESTAMP,
                      finished_at TIMESTAMP,
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
                      event_id BIGINT,
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
            statement.execute("DELETE FROM information_item");
            statement.execute("DELETE FROM ai_processing_record");
        }
        insertEvent(1001L, "人工智能主题讲座通知", "LECTURE", "PUBLIC_WEB", "AI_PUBLISHED", "图书馆报告厅", "[\"\u8f6f\u4ef6\u5b66\u9662\u672c\u79d1\u751f\"]", "[\"AI\",\"\u8bb2\u5ea7\",\"\u8f6f\u4ef6\u5b66\u9662\"]", null);
        insertEvent(1002L, "期末考试考场调整说明", "EXAM", "PUBLIC_WEB", "CORRECTED", "一号教学楼", "[\"2023\u7ea7\"]", "[\"\u8003\u8bd5\",\"\u6559\u52a1\"]", "vec-1002");
        insertEvent(1003L, "雨课堂作业提交提醒", "HOMEWORK", "RAIN_CLASSROOM", "AI_PUBLISHED", "线上", "[\"SE101\"]", "[\"\u96e8\u8bfe\u5802\",\"\u4f5c\u4e1a\"]", null);
        insertEvent(1004L, "创新创业竞赛报名开放", "ACTIVITY", "PUBLIC_WEB", "REVIEWED", "学生事务中心", "[\"\u5168\u6821\u5b66\u751f\"]", "[\"\u7ade\u8d5b\",\"\u62a5\u540d\"]", "vec-1004");
        insertEvent(1005L, "旧通知误识别", "NOTICE", "USER_TEXT", "REJECTED", null, "[]", "[\"\u901a\u77e5\"]", null);
        insertInformationItem(1001L, "人工智能主题讲座通知", "软件学院通知", "ACTIVE");
        insertInformationItem(1002L, "期末考试考场调整说明", "教务处公告", "UPDATED");
        insertInformationItem(1003L, "雨课堂作业提交提醒", "雨课堂导入", "ACTIVE");
        insertInformationItem(1004L, "创新创业竞赛报名开放", "软件学院通知", "ACTIVE");
        insertInformationItem(1005L, "旧通知误识别", "用户截图 OCR", "OFFLINE");
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
        insertAuditLog(1002L, 9901L, "CORRECT", "修正考试地点");
        insertAiProcessingRecord(7001L, 1001L, "FAILED");
        insertAiProcessingRecord(7002L, 1002L, "SUCCEEDED");
    }

    @Test
    void dashboardReturnsSeededAdminData() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics.reviewCount").value(4))
                .andExpect(jsonPath("$.data.metrics.urgentCount").value(1))
                .andExpect(jsonPath("$.data.events[?(@.id==1005)].title").value("旧通知误识别"))
                .andExpect(jsonPath("$.data.events[?(@.id==1001)].source").value("软件学院通知"))
                .andExpect(jsonPath("$.data.events[?(@.id==1001)].aiStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.events[?(@.id==1002)].source").value("教务处公告"))
                .andExpect(jsonPath("$.data.dataSources[?(@.id==3)].status").value("NEEDS_AUTH"))
                .andExpect(jsonPath("$.data.dataSources[?(@.id==4)].status").value("PAUSED"))
                .andExpect(jsonPath("$.data.tasks[?(@.status=='FAILED')].note").value("403 授权失效"));
    }

    @Test
    void reviewUpdatesStatusAndWritesAuditLog() throws Exception {
        mockMvc.perform(put("/api/admin/events/1001/review")
                        .header("Authorization", adminToken())
                        .contentType("application/json")
                        .content("""
                                {
                                  "status": "REVIEWED",
                                  "comment": "字段完整，确认发布"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AI_PUBLISHED"))
                .andExpect(jsonPath("$.data.risk").value("原文已解析，学生端正在展示"));

        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events[?(@.id==1001)].status").value("AI_PUBLISHED"));
    }

    @Test
    void logsReturnsAuditLogList() throws Exception {
        mockMvc.perform(get("/api/admin/logs?action=CORRECT").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].action").value("CORRECT"))
                .andExpect(jsonPath("$.data.items[0].operatorId").value(9901));
    }

    @Test
    void correctionWritesBeforeAndAfterSnapshots() throws Exception {
        mockMvc.perform(put("/api/admin/events/1001")
                        .header("Authorization", adminToken())
                        .contentType("application/json")
                        .content("""
                                {"title":"修订后的讲座通知","summary":"管理员确认后的摘要","eventType":"LECTURE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("修订后的讲座通知"));

        mockMvc.perform(get("/api/admin/logs")
                        .header("Authorization", adminToken()).param("action", "CORRECT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].operatorId").value(9901))
                .andExpect(jsonPath("$.data.items[0].beforeSnapshot").value(org.hamcrest.Matchers.containsString("人工智能主题讲座通知")))
                .andExpect(jsonPath("$.data.items[0].afterSnapshot").value(org.hamcrest.Matchers.containsString("管理员确认后的摘要")));
    }

    @Test
    void studentCannotAccessAdminDashboard() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", token("STUDENT", 9902L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminCanReadControlledTableRows() throws Exception {
        mockMvc.perform(get("/api/admin/tables/campus_event").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1005));

        mockMvc.perform(get("/api/admin/tables").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name=='ai_processing_record')].label").value("AI处理记录"));
    }

    @Test
    void adminCanRetryOnlyFailedAiProcessing() throws Exception {
        mockMvc.perform(post("/api/admin/ai-processing/7001/retry").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        assertEquals("PENDING", queryString("SELECT status FROM ai_processing_record WHERE id = 7001"));
        assertEquals("PENDING", queryString("SELECT ai_status FROM information_item WHERE id = 1001"));

        mockMvc.perform(post("/api/admin/ai-processing/7002/retry").header("Authorization", adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_PROCESSING_NOT_RETRYABLE"));
        mockMvc.perform(post("/api/admin/ai-processing/7001/retry")
                        .header("Authorization", token("OPERATOR", 9902L)))
                .andExpect(status().isForbidden());
    }

    private String adminToken() {
        return token("ADMIN", 9901L);
    }

    private String token(String role, Long userId) {
        String jwt = Jwts.builder()
                .issuer("campusmind-auth")
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(Keys.hmacShaKeyFor("test-secret-test-secret-test-secret-1234".getBytes(StandardCharsets.UTF_8)))
                .compact();
        return "Bearer " + jwt;
    }

    private void insertEvent(Long id, String title, String type, String sourceType, String status,
                             String location, String scope, String tags, String vectorDocId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO campus_event (
                       id, title, summary, event_type, source_type, status,
                       start_time, location, organizer, target_scope, tags, vector_doc_id, published_at, created_at
                     ) VALUES (?, ?, ?, ?, ?, ?, TIMESTAMP '2026-07-08 19:00:00', ?,
                       '软件学院', ?, ?, ?, TIMESTAMP '2026-07-07 10:00:00', TIMESTAMP '2026-07-07 18:00:00')
                     """)) {
            statement.setLong(1, id);
            statement.setString(2, title);
            statement.setString(3, title + "摘要");
            statement.setString(4, type);
            statement.setString(5, sourceType);
            statement.setString(6, status);
            statement.setString(7, location);
            statement.setString(8, scope);
            statement.setString(9, tags);
            statement.setString(10, vectorDocId);
            statement.executeUpdate();
        }
    }

    private void insertInformationItem(Long id, String title, String sourceName, String itemStatus) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO information_item (
                       id, source_name, source_url, item_url, title, publish_time, fetched_at,
                       detail_content, item_status, parse_status
                     ) VALUES (?, ?, 'https://example.edu.cn/list', ?, ?,
                       TIMESTAMP '2026-07-08 19:00:00', TIMESTAMP '2026-07-07 18:00:00',
                       ?, ?, 'DETAIL_SUCCESS')
                     """)) {
            statement.setLong(1, id);
            statement.setString(2, sourceName);
            statement.setString(3, "https://example.edu.cn/item/" + id);
            statement.setString(4, title);
            statement.setString(5, title + "完整正文");
            statement.setString(6, itemStatus);
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

    private void insertAuditLog(Long eventId, Long operatorId, String action, String comment) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO event_audit_log (event_id, operator_id, action, before_snapshot, after_snapshot, comment)
                     VALUES (?, ?, ?, '{"status":"AI_PUBLISHED"}', '{"status":"CORRECTED"}', ?)
                     """)) {
            statement.setLong(1, eventId);
            statement.setLong(2, operatorId);
            statement.setString(3, action);
            statement.setString(4, comment);
            statement.executeUpdate();
        }
    }

    private void insertAiProcessingRecord(Long id, Long informationItemId, String status) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO ai_processing_record (
                       id, information_item_id, content_hash, task_type, trigger_type,
                       status, prompt_version, error_message, created_at, updated_at
                     ) VALUES (?, ?, ?, 'SUMMARY', 'SCHEDULER', ?, 'prompt-v1', '模型调用失败', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                     """)) {
            statement.setLong(1, id);
            statement.setLong(2, informationItemId);
            statement.setString(3, String.format("%064d", id));
            statement.setString(4, status);
            statement.executeUpdate();
        }
    }

    private String queryString(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             java.sql.ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }
}

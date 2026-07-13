package cn.campusmind.audit.application;

import cn.campusmind.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminTableService {
    private static final Map<String, TableSpec> TABLES = Map.of(
            "campus_event", new TableSpec("校园事件", List.of("id", "title", "event_type", "source_type", "status", "visibility", "owner_user_id", "start_time", "created_at")),
            "information_item", new TableSpec("公共信息流", List.of("id", "source_name", "title", "item_status", "parse_status", "fetched_at")),
            "data_source", new TableSpec("数据源", List.of("id", "name", "source_type", "base_url", "enabled", "last_crawled_at")),
            "import_task", new TableSpec("导入任务", List.of("id", "user_id", "import_type", "task_status", "created_at", "finished_at")),
            "crawl_task", new TableSpec("采集任务", List.of("id", "source_id", "task_status", "http_status", "started_at", "finished_at")),
            "event_audit_log", new TableSpec("审计日志", List.of("id", "event_id", "operator_id", "action", "comment", "created_at")),
            "ai_processing_record", new TableSpec("AI处理记录", List.of("id", "information_item_id", "status", "provider", "model_version", "prompt_version", "prompt_tokens", "completion_tokens", "error_message", "started_at", "finished_at", "created_at")),
            "user", new TableSpec("用户", List.of("id", "username", "phone", "role", "status", "created_at", "updated_at"))
    );

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public AdminTableService(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    public List<Map<String, Object>> tables() {
        return TABLES.entrySet().stream().map(entry -> Map.<String, Object>of(
                "name", entry.getKey(), "label", entry.getValue().label(), "columns", entry.getValue().columns()
        )).toList();
    }

    public List<Map<String, Object>> rows(String table, int size) {
        TableSpec spec = require(table);
        List<String> available = spec.columns().stream()
                .filter(databaseColumns(table)::contains)
                .toList();
        if (available.isEmpty()) {
            throw new BusinessException("TABLE_SCHEMA_UNAVAILABLE", "数据表字段未识别，请检查数据库迁移", HttpStatus.CONFLICT);
        }
        String columns = String.join(",", available.stream().map(column -> "`" + column + "`").toList());
        return jdbcTemplate.queryForList("SELECT " + columns + " FROM `" + table + "` ORDER BY `id` DESC LIMIT ?", Math.min(Math.max(size, 1), 100));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> retryFailedAiProcessing(long id) {
        int updated = jdbcTemplate.update("""
                UPDATE ai_processing_record
                SET status = 'PENDING', trigger_type = 'MANUAL', error_message = NULL,
                    started_at = NULL, finished_at = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'FAILED'
                """, id);
        if (updated == 0) {
            List<String> statuses = jdbcTemplate.queryForList(
                    "SELECT status FROM ai_processing_record WHERE id = ?", String.class, id);
            if (statuses.isEmpty()) {
                throw new BusinessException("AI_PROCESSING_NOT_FOUND", "AI处理记录不存在", HttpStatus.NOT_FOUND);
            }
            throw new BusinessException("AI_PROCESSING_NOT_RETRYABLE", "只有失败的AI任务可以重试", HttpStatus.CONFLICT);
        }
        int itemUpdated = jdbcTemplate.update("""
                UPDATE information_item
                SET ai_status = 'PENDING', ai_error = NULL, ai_processed_at = NULL
                WHERE id = (SELECT information_item_id FROM ai_processing_record WHERE id = ?)
                """, id);
        if (itemUpdated != 1) {
            throw new BusinessException("AI_INFORMATION_ITEM_MISSING", "关联信息不存在，无法重试", HttpStatus.CONFLICT);
        }
        return Map.of("id", id, "status", "PENDING");
    }

    private Set<String> databaseColumns(String table) {
        try (Connection connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, table, null)) {
            java.util.HashSet<String> result = new java.util.HashSet<>();
            while (columns.next()) result.add(columns.getString("COLUMN_NAME").toLowerCase());
            return result;
        } catch (Exception ex) {
            throw new BusinessException("TABLE_SCHEMA_UNAVAILABLE", "无法读取数据表结构", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private static TableSpec require(String table) {
        TableSpec spec = TABLES.get(table);
        if (spec == null) throw new BusinessException("TABLE_NOT_ALLOWED", "该数据表不在受控管理范围", HttpStatus.FORBIDDEN);
        return spec;
    }

    private record TableSpec(String label, List<String> columns) {}
}

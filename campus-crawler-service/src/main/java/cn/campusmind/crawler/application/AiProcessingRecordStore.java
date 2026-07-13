package cn.campusmind.crawler.application;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 用数据库唯一键领取摘要任务，避免多个 crawler 实例重复调用模型。
 */
@Component
class AiProcessingRecordStore {

    private static final String TASK_TYPE = "SUMMARY";
    private final JdbcTemplate jdbcTemplate;

    AiProcessingRecordStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    boolean claim(Long informationItemId, String contentHash, String promptVersion,
                  String inputDigest, LocalDateTime startedAt, LocalDateTime retryBefore) {
        int resumed = jdbcTemplate.update("""
                UPDATE ai_processing_record
                SET status = 'PROCESSING', input_digest = ?, error_message = NULL,
                    started_at = ?, updated_at = ?
                WHERE information_item_id = ? AND content_hash = ? AND task_type = ? AND prompt_version = ?
                  AND (status = 'PENDING' OR (status = 'FAILED' AND updated_at < ?))
                """, inputDigest, startedAt, startedAt, informationItemId, contentHash,
                TASK_TYPE, promptVersion, retryBefore);
        if (resumed == 1) {
            return true;
        }
        try {
            return jdbcTemplate.update("""
                    INSERT INTO ai_processing_record (
                        information_item_id, content_hash, task_type, trigger_type, status,
                        prompt_version, input_digest, started_at, created_at, updated_at
                    ) VALUES (?, ?, ?, 'SCHEDULER', 'PROCESSING', ?, ?, ?, ?, ?)
                    """, informationItemId, contentHash, TASK_TYPE, promptVersion, inputDigest,
                    startedAt, startedAt, startedAt) == 1;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }

    void succeed(Long informationItemId, String contentHash, String promptVersion,
                 AiCardExtractor.Result result, LocalDateTime finishedAt) {
        jdbcTemplate.update("""
                UPDATE ai_processing_record
                SET status = 'SUCCEEDED', provider = ?, model_version = ?, prompt_version = ?,
                    output_json = ?, error_message = NULL, finished_at = ?, updated_at = ?
                WHERE information_item_id = ? AND content_hash = ? AND task_type = ? AND prompt_version = ?
                """, result.mode(), result.modelVersion(), result.promptVersion(), result.cardJson(),
                finishedAt, finishedAt, informationItemId, contentHash, TASK_TYPE, promptVersion);
    }

    void fail(Long informationItemId, String contentHash, String promptVersion,
              String errorMessage, LocalDateTime finishedAt) {
        jdbcTemplate.update("""
                UPDATE ai_processing_record
                SET status = 'FAILED', error_message = ?, finished_at = ?, updated_at = ?
                WHERE information_item_id = ? AND content_hash = ? AND task_type = ? AND prompt_version = ?
                """, errorMessage, finishedAt, finishedAt, informationItemId, contentHash,
                TASK_TYPE, promptVersion);
    }
}

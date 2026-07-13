package cn.campusmind.crawler.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiProcessingRecordStoreTest {

    private AiProcessingRecordStore store;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build());
        jdbcTemplate.execute("""
                CREATE TABLE ai_processing_record (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    information_item_id BIGINT NOT NULL,
                    content_hash VARCHAR(64) NOT NULL,
                    task_type VARCHAR(32) NOT NULL,
                    trigger_type VARCHAR(32) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    provider VARCHAR(64), model_version VARCHAR(128), prompt_version VARCHAR(64) NOT NULL,
                    input_digest VARCHAR(64) NOT NULL, output_json CLOB, error_message VARCHAR(1000),
                    started_at TIMESTAMP, finished_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
                    UNIQUE (information_item_id, content_hash, task_type, prompt_version)
                )
                """);
        store = new AiProcessingRecordStore(jdbcTemplate);
    }

    @Test
    void onlyOneWorkerCanClaimSameContentAndPrompt() {
        LocalDateTime now = LocalDateTime.now();

        assertTrue(store.claim(1L, "a".repeat(64), "llm-v1", "b".repeat(64), now, now.minusMinutes(30)));
        assertFalse(store.claim(1L, "a".repeat(64), "llm-v1", "b".repeat(64), now, now.minusMinutes(30)));
    }
}

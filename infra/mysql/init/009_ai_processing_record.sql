SET NAMES utf8mb4;

USE campusmind;

CREATE TABLE IF NOT EXISTS ai_processing_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    information_item_id BIGINT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    provider VARCHAR(64) NULL,
    model_version VARCHAR(128) NULL,
    prompt_version VARCHAR(64) NOT NULL,
    input_digest CHAR(64) NOT NULL,
    output_json JSON NULL,
    prompt_tokens INT NULL,
    completion_tokens INT NULL,
    error_message VARCHAR(1000) NULL,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ai_processing_item_hash_prompt (information_item_id, content_hash, task_type, prompt_version),
    KEY idx_ai_processing_status_created (status, created_at),
    CONSTRAINT fk_ai_processing_information_item
        FOREIGN KEY (information_item_id) REFERENCES information_item(id) ON DELETE CASCADE
);

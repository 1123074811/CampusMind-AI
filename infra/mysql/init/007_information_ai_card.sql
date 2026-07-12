SET NAMES utf8mb4;

USE campusmind;

-- 兼容存量库；新建库已由 006_information_item.sql 创建这些字段。
DELIMITER //

CREATE PROCEDURE add_information_ai_column_if_missing(
  IN column_name_value VARCHAR(64),
  IN column_definition_value VARCHAR(2048)
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'information_item'
      AND COLUMN_NAME = column_name_value
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE information_item ADD COLUMN ', column_definition_value);
    PREPARE statement_to_run FROM @ddl;
    EXECUTE statement_to_run;
    DEALLOCATE PREPARE statement_to_run;
  END IF;
END//

DELIMITER ;

CALL add_information_ai_column_if_missing('ai_status', 'ai_status VARCHAR(32) NOT NULL DEFAULT ''PENDING'' COMMENT ''PENDING/SUCCESS/REVIEW/FAILED''');
CALL add_information_ai_column_if_missing('ai_event_type', 'ai_event_type VARCHAR(32) NULL COMMENT ''智能体识别的信息类型''');
CALL add_information_ai_column_if_missing('ai_summary', 'ai_summary TEXT NULL COMMENT ''智能精简摘要''');
CALL add_information_ai_column_if_missing('ai_card_json', 'ai_card_json JSON NULL COMMENT ''智能体结构化信息卡片''');
CALL add_information_ai_column_if_missing('ai_confidence', 'ai_confidence DECIMAL(5,4) NULL COMMENT ''智能提取置信度''');
CALL add_information_ai_column_if_missing('ai_need_review', 'ai_need_review TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否需要人工复核''');
CALL add_information_ai_column_if_missing('ai_error', 'ai_error VARCHAR(1024) NULL COMMENT ''智能提取失败原因''');
CALL add_information_ai_column_if_missing('ai_processed_at', 'ai_processed_at DATETIME NULL COMMENT ''智能提取完成时间''');

DROP PROCEDURE add_information_ai_column_if_missing;

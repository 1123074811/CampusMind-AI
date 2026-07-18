SET NAMES utf8mb4;
USE campusmind;

SET @ddl = (SELECT IF(
  NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_consent_record' AND COLUMN_NAME = 'scopes_json'),
  'ALTER TABLE user_consent_record ADD COLUMN scopes_json JSON NULL COMMENT ''本次授权的数据范围'' AFTER source',
  'SELECT "user_consent_record.scopes_json already exists"'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE event_source_ref MODIFY COLUMN raw_doc_id VARCHAR(64) NULL
  COMMENT 'MongoDB原始文档ID；不保留原文的结构化导入可为空';

ALTER TABLE campus_event MODIFY COLUMN source_type VARCHAR(64) NOT NULL
  COMMENT 'PUBLIC_WEB/RAIN_CLASSROOM/XIQUEER/XJU_EHALL/USER_TEXT/USER_IMAGE/USER_FILE';

SET NAMES utf8mb4;

USE campusmind;

-- ============================================================
-- 003: 数据库结构完整性修复
-- 修复内容：
--   1. 补充缺失索引（campus_event.visibility、information_item.ai_status）
--   2. crawl_task 补充 created_at 字段
--   3. 修复列注释与实际类型不符的问题
--   4. 删除 campus_event 表冗余字段（raw_doc_id/source_url/content_hash，已由 event_source_ref 承载）
--   5. 修复外键删除策略（审计日志 SET NULL、采集任务 CASCADE、导入任务 SET NULL）
--   6. web_crawl_item 新增 information_item_id 关联字段
--   7. 清理迁移残留归档表
-- ============================================================

-- -------------------------------------------------------
-- 1. 补充缺失索引
-- -------------------------------------------------------

-- campus_event: 用户信息流高频查询 WHERE owner_user_id=? AND visibility='PRIVATE' ORDER BY start_time
SET @ddl = (SELECT IF(
  NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_event' AND INDEX_NAME = 'idx_event_owner_visibility'),
  'ALTER TABLE campus_event ADD KEY idx_event_owner_visibility (owner_user_id, visibility, start_time)',
  'SELECT "idx_event_owner_visibility already exists"'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- information_item: AI 处理队列 WHERE ai_status='PENDING' ORDER BY fetched_at
SET @ddl = (SELECT IF(
  NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'information_item' AND INDEX_NAME = 'idx_information_item_ai_status'),
  'ALTER TABLE information_item ADD KEY idx_information_item_ai_status (ai_status, fetched_at)',
  'SELECT "idx_information_item_ai_status already exists"'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------
-- 2. crawl_task 补充 created_at
-- -------------------------------------------------------

SET @ddl = (SELECT IF(
  NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crawl_task' AND COLUMN_NAME = 'created_at'),
  'ALTER TABLE crawl_task ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER task_status',
  'SELECT "crawl_task.created_at already exists"'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------
-- 3. 修复列注释
-- -------------------------------------------------------

ALTER TABLE data_source MODIFY COLUMN source_type VARCHAR(64) NOT NULL
  COMMENT 'PUBLIC_WEB/RAIN_CLASSROOM/USER_TEXT/USER_IMAGE/USER_FILE';

ALTER TABLE campus_event MODIFY COLUMN source_type VARCHAR(64) NOT NULL
  COMMENT 'PUBLIC_WEB/RAIN_CLASSROOM/USER_TEXT/USER_IMAGE/USER_FILE';

ALTER TABLE campus_event MODIFY COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC'
  COMMENT 'PUBLIC/PRIVATE（PRIVATE=用户私有事件，仅owner可见）';

ALTER TABLE event_audit_log MODIFY COLUMN action VARCHAR(64) NOT NULL
  COMMENT 'REVIEW/CORRECT/MERGE/REJECT/OFFLINE/MANUAL_CRAWL/AUTO_CRAWL';

-- information_item AI 字段补全注释（由 007 ALTER TABLE 添加，缺少 COMMENT）
ALTER TABLE information_item MODIFY COLUMN ai_status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
  COMMENT 'PENDING/SUCCESS/REVIEW/FAILED';
ALTER TABLE information_item MODIFY COLUMN ai_event_type VARCHAR(32) NULL
  COMMENT '智能体识别的信息类型';
ALTER TABLE information_item MODIFY COLUMN ai_summary TEXT NULL
  COMMENT '智能精简摘要';
ALTER TABLE information_item MODIFY COLUMN ai_card_json JSON NULL
  COMMENT '智能体结构化信息卡片';
ALTER TABLE information_item MODIFY COLUMN ai_confidence DECIMAL(5,4) NULL
  COMMENT '智能提取置信度';
ALTER TABLE information_item MODIFY COLUMN ai_need_review TINYINT(1) NOT NULL DEFAULT 0
  COMMENT '是否需要人工复核';
ALTER TABLE information_item MODIFY COLUMN ai_error VARCHAR(1024) NULL
  COMMENT '智能提取失败原因';
ALTER TABLE information_item MODIFY COLUMN ai_processed_at DATETIME NULL
  COMMENT '智能提取完成时间';

-- -------------------------------------------------------
-- 4. 删除 campus_event 冗余字段（已由 event_source_ref 承载）
-- -------------------------------------------------------

SET @ddl = (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_event' AND COLUMN_NAME = 'raw_doc_id'),
  'ALTER TABLE campus_event DROP COLUMN raw_doc_id',
  'SELECT "campus_event.raw_doc_id already dropped"'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_event' AND COLUMN_NAME = 'source_url'),
  'ALTER TABLE campus_event DROP COLUMN source_url',
  'SELECT "campus_event.source_url already dropped"'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_event' AND COLUMN_NAME = 'content_hash'),
  'ALTER TABLE campus_event DROP COLUMN content_hash',
  'SELECT "campus_event.content_hash already dropped"'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------
-- 5. 修复外键删除策略
-- -------------------------------------------------------

-- event_audit_log.event_id: RESTRICT -> SET NULL
-- 原因：审计日志应不可删，但删除事件时不应被阻塞；event_id 置 NULL 保留审计记录
-- 先删除旧约束（NO ACTION），再添加新约束（SET NULL）
SET @ddl = (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'fk_event_audit_log_event'
      AND DELETE_RULE IN ('NO ACTION', 'RESTRICT')),
  'ALTER TABLE event_audit_log DROP FOREIGN KEY fk_event_audit_log_event',
  'SELECT "fk_event_audit_log_event already fixed"'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
  NOT EXISTS(SELECT 1 FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'fk_event_audit_log_event'),
  'ALTER TABLE event_audit_log ADD CONSTRAINT fk_event_audit_log_event FOREIGN KEY (event_id) REFERENCES campus_event(id) ON DELETE SET NULL',
  'SELECT "fk_event_audit_log_event constraint exists"'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- event_audit_log.operator_id: RESTRICT -> SET NULL
-- 原因：删除用户时保留其审计操作记录
SET @ddl = (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'fk_event_audit_log_operator'
      AND DELETE_RULE IN ('NO ACTION', 'RESTRICT')),
  'ALTER TABLE event_audit_log DROP FOREIGN KEY fk_event_audit_log_operator',
  'SELECT "fk_event_audit_log_operator already fixed"'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
  NOT EXISTS(SELECT 1 FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'fk_event_audit_log_operator'),
  'ALTER TABLE event_audit_log ADD CONSTRAINT fk_event_audit_log_operator FOREIGN KEY (operator_id) REFERENCES user(id) ON DELETE SET NULL',
  'SELECT "fk_event_audit_log_operator constraint exists"'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- crawl_task.source_id: RESTRICT -> CASCADE
-- 原因：删除数据源时应级联清理其采集任务
SET @ddl = (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'fk_crawl_task_source'
      AND DELETE_RULE IN ('NO ACTION', 'RESTRICT')),
  'ALTER TABLE crawl_task DROP FOREIGN KEY fk_crawl_task_source',
  'SELECT "fk_crawl_task_source already fixed"'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
  NOT EXISTS(SELECT 1 FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'fk_crawl_task_source'),
  'ALTER TABLE crawl_task ADD CONSTRAINT fk_crawl_task_source FOREIGN KEY (source_id) REFERENCES data_source(id) ON DELETE CASCADE',
  'SELECT "fk_crawl_task_source constraint exists"'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- import_task.user_id: RESTRICT -> SET NULL
-- 原因：删除用户时保留导入任务记录用于审计追溯
SET @ddl = (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'fk_import_task_user'
      AND DELETE_RULE IN ('NO ACTION', 'RESTRICT')),
  'ALTER TABLE import_task MODIFY COLUMN user_id BIGINT NULL, DROP FOREIGN KEY fk_import_task_user',
  'SELECT "fk_import_task_user already fixed"'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
  NOT EXISTS(SELECT 1 FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'fk_import_task_user'),
  'ALTER TABLE import_task ADD CONSTRAINT fk_import_task_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE SET NULL',
  'SELECT "fk_import_task_user constraint exists"'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------
-- 6. web_crawl_item 新增 information_item_id 关联
-- -------------------------------------------------------

SET @ddl = (SELECT IF(
  NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'web_crawl_item' AND COLUMN_NAME = 'information_item_id'),
  'ALTER TABLE web_crawl_item ADD COLUMN information_item_id BIGINT NULL COMMENT ''关联的信息条目ID（AI处理完成后回填）''',
  'SELECT "web_crawl_item.information_item_id already exists"'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
  NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'web_crawl_item' AND INDEX_NAME = 'idx_web_crawl_item_info'),
  'ALTER TABLE web_crawl_item ADD KEY idx_web_crawl_item_info (information_item_id)',
  'SELECT "idx_web_crawl_item_info already exists"'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 幂等添加外键
SET @ddl = (SELECT IF(
  NOT EXISTS(SELECT 1 FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'fk_web_crawl_item_info'),
  'ALTER TABLE web_crawl_item ADD CONSTRAINT fk_web_crawl_item_info FOREIGN KEY (information_item_id) REFERENCES information_item(id) ON DELETE SET NULL',
  'SELECT "fk_web_crawl_item_info already exists"'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------
-- 7. 清理迁移残留归档表
-- -------------------------------------------------------
DROP TABLE IF EXISTS user_information_state_orphan_20260712;

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS campusmind
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE campusmind;

CREATE TABLE IF NOT EXISTS user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  username VARCHAR(64) NOT NULL COMMENT '登录名',
  phone VARCHAR(32) NULL COMMENT '手机号，需加密或脱敏展示',
  email VARCHAR(255) NULL COMMENT '用于账号找回的邮箱',
  password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
  role VARCHAR(32) NOT NULL DEFAULT 'STUDENT' COMMENT 'STUDENT/ADMIN/SUPER_ADMIN',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_username (username),
  UNIQUE KEY uk_user_email (email),
  KEY idx_user_role_status (role, status)
) COMMENT='用户表';

CREATE TABLE IF NOT EXISTS user_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  college VARCHAR(128) NULL COMMENT '学院',
  major VARCHAR(128) NULL COMMENT '专业',
  grade VARCHAR(32) NULL COMMENT '年级',
  class_name VARCHAR(128) NULL COMMENT '班级',
  interest_tags JSON NULL COMMENT '兴趣标签，如讲座/竞赛/就业',
  course_codes JSON NULL COMMENT '课程标识列表',
  sensitivity DOUBLE NULL DEFAULT 0.5 COMMENT '用户画像敏感度',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_profile_user (user_id),
  KEY idx_profile_college_major_grade (college, major, grade),
  CONSTRAINT fk_user_profile_user FOREIGN KEY (user_id) REFERENCES user (id)
) COMMENT='用户画像表';

CREATE TABLE IF NOT EXISTS campus_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL COMMENT '事件标题',
  summary TEXT NULL COMMENT 'AI摘要或人工摘要',
  event_type VARCHAR(64) NOT NULL COMMENT 'NOTICE/COURSE/EXAM/HOMEWORK/ACTIVITY/LECTURE/COMPETITION/SERVICE',
  source_type VARCHAR(64) NOT NULL COMMENT 'PUBLIC_WEB/RAIN_CLASSROOM/XIQUEER/XJU_EHALL/USER_TEXT/USER_IMAGE/USER_FILE',
  visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC/PRIVATE（PRIVATE=用户私有事件，仅owner可见）',
  owner_user_id BIGINT NULL COMMENT 'PRIVATE事件所属用户',
  status VARCHAR(32) NOT NULL DEFAULT 'AI_PUBLISHED' COMMENT 'AI_PUBLISHED/REVIEWED/CORRECTED/REJECTED/OFFLINE',
  start_time DATETIME NULL,
  end_time DATETIME NULL,
  location VARCHAR(255) NULL,
  organizer VARCHAR(255) NULL COMMENT '发布单位或课程教师',
  target_scope JSON NULL COMMENT '适用范围：学院/专业/年级/课程',
  tags JSON NULL COMMENT '标签',
  dedup_key CHAR(64) NULL COMMENT '标题+时间+来源生成的SHA256',
  vector_doc_id VARCHAR(128) NULL COMMENT '向量库文档ID',
  published_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_event_type_time (event_type, start_time),
  KEY idx_event_status_created (status, created_at),
  KEY idx_event_source_type (source_type),
  KEY idx_event_owner_visibility (owner_user_id, visibility, start_time),
  UNIQUE KEY uk_event_dedup_key (dedup_key),
  CONSTRAINT fk_campus_event_owner FOREIGN KEY (owner_user_id) REFERENCES user (id) ON DELETE CASCADE
) COMMENT='校园事件主表';

CREATE TABLE IF NOT EXISTS event_source_ref (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id BIGINT NOT NULL,
  source_id BIGINT NULL COMMENT '公开网页数据源ID',
  raw_doc_id VARCHAR(64) NULL COMMENT 'MongoDB原始文档ID；不保留原文的结构化导入可为空',
  source_url VARCHAR(1024) NULL,
  source_title VARCHAR(255) NULL,
  content_hash CHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ref_event (event_id),
  KEY idx_ref_hash (content_hash),
  CONSTRAINT fk_event_source_ref_event FOREIGN KEY (event_id) REFERENCES campus_event (id)
) COMMENT='事件来源引用表';

CREATE TABLE IF NOT EXISTS event_audit_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id BIGINT NULL,
  operator_id BIGINT NULL,
  action VARCHAR(64) NOT NULL COMMENT 'REVIEW/CORRECT/MERGE/REJECT/OFFLINE/MANUAL_CRAWL/AUTO_CRAWL',
  before_snapshot JSON NULL,
  after_snapshot JSON NULL,
  comment VARCHAR(512) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_audit_event_time (event_id, created_at),
  KEY idx_audit_operator (operator_id),
  CONSTRAINT fk_event_audit_log_event FOREIGN KEY (event_id) REFERENCES campus_event (id) ON DELETE SET NULL,
  CONSTRAINT fk_event_audit_log_operator FOREIGN KEY (operator_id) REFERENCES user (id) ON DELETE SET NULL
) COMMENT='事件审核日志表';

CREATE TABLE IF NOT EXISTS data_source (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  source_type VARCHAR(64) NOT NULL COMMENT 'PUBLIC_WEB/RAIN_CLASSROOM/USER_TEXT/USER_IMAGE/USER_FILE',
  base_url VARCHAR(1024) NOT NULL,
  robots_url VARCHAR(1024) NULL,
  crawl_interval_seconds INT NOT NULL DEFAULT 5 COMMENT '必须大于2秒',
  parser_type VARCHAR(64) NOT NULL COMMENT 'WEBMAGIC/PLAYWRIGHT/RSS/SITEMAP',
  selector_config JSON NULL COMMENT 'CSS/XPath/正文抽取规则',
  enabled TINYINT NOT NULL DEFAULT 1,
  last_crawled_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_source_enabled (enabled),
  UNIQUE KEY uk_source_base_url (base_url(255))
) COMMENT='公开网页数据源表';

SET @event_source_ref_source_fk_exists = (
  SELECT COUNT(*)
  FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE()
    AND table_name = 'event_source_ref'
    AND constraint_name = 'fk_event_source_ref_source'
);
SET @event_source_ref_source_fk_sql = IF(
  @event_source_ref_source_fk_exists = 0,
  'ALTER TABLE event_source_ref ADD CONSTRAINT fk_event_source_ref_source FOREIGN KEY (source_id) REFERENCES data_source (id) ON DELETE SET NULL',
  'DO 0'
);
PREPARE event_source_ref_source_fk_statement FROM @event_source_ref_source_fk_sql;
EXECUTE event_source_ref_source_fk_statement;
DEALLOCATE PREPARE event_source_ref_source_fk_statement;

CREATE TABLE IF NOT EXISTS crawl_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_id BIGINT NOT NULL,
  task_status VARCHAR(32) NOT NULL COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/SKIPPED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  crawl_url VARCHAR(1024) NOT NULL,
  http_status INT NULL,
  etag VARCHAR(255) NULL,
  last_modified VARCHAR(255) NULL,
  fail_reason VARCHAR(1024) NULL,
  started_at DATETIME NULL,
  finished_at DATETIME NULL,
  KEY idx_task_source_status (source_id, task_status),
  KEY idx_task_started (started_at),
  CONSTRAINT fk_crawl_task_source FOREIGN KEY (source_id) REFERENCES data_source (id) ON DELETE CASCADE
) COMMENT='采集任务表';

CREATE TABLE IF NOT EXISTS import_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NULL,
  import_type VARCHAR(64) NOT NULL COMMENT 'RAIN_COOKIE/RAIN_JSON/USER_TEXT/USER_IMAGE',
  task_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  raw_doc_id VARCHAR(64) NULL,
  result_summary JSON NULL,
  error_message VARCHAR(1024) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at DATETIME NULL,
  KEY idx_import_user_time (user_id, created_at),
  KEY idx_import_status (task_status),
  CONSTRAINT fk_import_task_user FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE SET NULL
) COMMENT='用户导入任务表';
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS campusmind
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE campusmind;

CREATE TABLE IF NOT EXISTS user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  username VARCHAR(64) NOT NULL COMMENT '登录名',
  phone VARCHAR(32) NULL COMMENT '手机号，需加密或脱敏展示',
  email VARCHAR(255) NULL COMMENT '用于账号找回的邮箱',
  password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
  role VARCHAR(32) NOT NULL DEFAULT 'STUDENT' COMMENT 'STUDENT/ADMIN/SUPER_ADMIN',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_username (username),
  UNIQUE KEY uk_user_email (email),
  KEY idx_user_role_status (role, status)
) COMMENT='用户表';

CREATE TABLE IF NOT EXISTS user_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  college VARCHAR(128) NULL COMMENT '学院',
  major VARCHAR(128) NULL COMMENT '专业',
  grade VARCHAR(32) NULL COMMENT '年级',
  class_name VARCHAR(128) NULL COMMENT '班级',
  interest_tags JSON NULL COMMENT '兴趣标签，如讲座/竞赛/就业',
  course_codes JSON NULL COMMENT '课程标识列表',
  sensitivity DOUBLE NULL DEFAULT 0.5 COMMENT '用户画像敏感度',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_profile_user (user_id),
  KEY idx_profile_college_major_grade (college, major, grade),
  CONSTRAINT fk_user_profile_user FOREIGN KEY (user_id) REFERENCES user (id)
) COMMENT='用户画像表';

CREATE TABLE IF NOT EXISTS campus_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL COMMENT '事件标题',
  summary TEXT NULL COMMENT 'AI摘要或人工摘要',
  event_type VARCHAR(64) NOT NULL COMMENT 'NOTICE/COURSE/EXAM/HOMEWORK/ACTIVITY/LECTURE/COMPETITION/SERVICE',
  source_type VARCHAR(64) NOT NULL COMMENT 'PUBLIC_WEB/RAIN_CLASSROOM/XIQUEER/XJU_EHALL/USER_TEXT/USER_IMAGE/USER_FILE',
  visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC/PRIVATE',
  owner_user_id BIGINT NULL COMMENT 'PRIVATE事件所属用户',
  status VARCHAR(32) NOT NULL DEFAULT 'AI_PUBLISHED' COMMENT 'AI_PUBLISHED/REVIEWED/CORRECTED/REJECTED/OFFLINE',
  start_time DATETIME NULL,
  end_time DATETIME NULL,
  location VARCHAR(255) NULL,
  organizer VARCHAR(255) NULL COMMENT '发布单位或课程教师',
  target_scope JSON NULL COMMENT '适用范围：学院/专业/年级/课程',
  tags JSON NULL COMMENT '标签',
  dedup_key CHAR(64) NULL COMMENT '标题+时间+来源生成的SHA256',
  vector_doc_id VARCHAR(128) NULL COMMENT '向量库文档ID',
  published_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_event_type_time (event_type, start_time),
  KEY idx_event_status_created (status, created_at),
  KEY idx_event_source_type (source_type),
  KEY idx_event_owner_visibility (owner_user_id, visibility, start_time),
  UNIQUE KEY uk_event_dedup_key (dedup_key),
  CONSTRAINT fk_campus_event_owner FOREIGN KEY (owner_user_id) REFERENCES user (id) ON DELETE CASCADE
) COMMENT='校园事件主表';

CREATE TABLE IF NOT EXISTS event_source_ref (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id BIGINT NOT NULL,
  source_id BIGINT NULL COMMENT '公开网页数据源ID',
  raw_doc_id VARCHAR(64) NULL COMMENT 'MongoDB原始文档ID；不保留原文的结构化导入可为空',
  source_url VARCHAR(1024) NULL,
  source_title VARCHAR(255) NULL,
  content_hash CHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ref_event (event_id),
  KEY idx_ref_hash (content_hash),
  CONSTRAINT fk_event_source_ref_event FOREIGN KEY (event_id) REFERENCES campus_event (id)
) COMMENT='事件来源引用表';

CREATE TABLE IF NOT EXISTS event_audit_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id BIGINT NULL,
  operator_id BIGINT NULL,
  action VARCHAR(64) NOT NULL COMMENT 'REVIEW/CORRECT/MERGE/REJECT/OFFLINE/MANUAL_CRAWL/AUTO_CRAWL',
  before_snapshot JSON NULL,
  after_snapshot JSON NULL,
  comment VARCHAR(512) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_audit_event_time (event_id, created_at),
  KEY idx_audit_operator (operator_id),
  CONSTRAINT fk_event_audit_log_event FOREIGN KEY (event_id) REFERENCES campus_event (id),
  CONSTRAINT fk_event_audit_log_operator FOREIGN KEY (operator_id) REFERENCES user (id)
) COMMENT='事件审核日志表';

CREATE TABLE IF NOT EXISTS data_source (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  source_type VARCHAR(64) NOT NULL COMMENT 'PUBLIC_WEB',
  base_url VARCHAR(1024) NOT NULL,
  robots_url VARCHAR(1024) NULL,
  crawl_interval_seconds INT NOT NULL DEFAULT 5 COMMENT '必须大于2秒',
  parser_type VARCHAR(64) NOT NULL COMMENT 'WEBMAGIC/PLAYWRIGHT/RSS/SITEMAP',
  selector_config JSON NULL COMMENT 'CSS/XPath/正文抽取规则',
  enabled TINYINT NOT NULL DEFAULT 1,
  last_crawled_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_source_enabled (enabled),
  UNIQUE KEY uk_source_base_url (base_url(255))
) COMMENT='公开网页数据源表';

CREATE TABLE IF NOT EXISTS crawl_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_id BIGINT NOT NULL,
  task_status VARCHAR(32) NOT NULL COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/SKIPPED',
  crawl_url VARCHAR(1024) NOT NULL,
  http_status INT NULL,
  etag VARCHAR(255) NULL,
  last_modified VARCHAR(255) NULL,
  fail_reason VARCHAR(1024) NULL,
  started_at DATETIME NULL,
  finished_at DATETIME NULL,
  KEY idx_task_source_status (source_id, task_status),
  KEY idx_task_started (started_at),
  CONSTRAINT fk_crawl_task_source FOREIGN KEY (source_id) REFERENCES data_source (id)
) COMMENT='采集任务表';

CREATE TABLE IF NOT EXISTS import_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  import_type VARCHAR(64) NOT NULL COMMENT 'RAIN_COOKIE/RAIN_JSON/USER_TEXT/USER_IMAGE',
  task_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  raw_doc_id VARCHAR(64) NULL,
  result_summary JSON NULL,
  error_message VARCHAR(1024) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at DATETIME NULL,
  KEY idx_import_user_time (user_id, created_at),
  KEY idx_import_status (task_status),
  CONSTRAINT fk_import_task_user FOREIGN KEY (user_id) REFERENCES user (id)
) COMMENT='用户导入任务表';

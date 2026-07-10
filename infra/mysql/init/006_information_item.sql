SET NAMES utf8mb4;

USE campusmind;

CREATE TABLE IF NOT EXISTS information_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_id BIGINT NOT NULL COMMENT '数据源ID',
  source_name VARCHAR(128) NOT NULL COMMENT '数据源名称',
  source_url VARCHAR(1024) NOT NULL COMMENT '列表页或栏目URL',
  item_url VARCHAR(1024) NOT NULL COMMENT '原网页详情URL',
  title VARCHAR(512) NOT NULL COMMENT '信息标题',
  publish_time DATETIME NULL COMMENT '页面发布时间，无法解析时为空',
  fetched_at DATETIME NOT NULL COMMENT '抓取时间',
  detail_content MEDIUMTEXT NOT NULL COMMENT '系统提取正文',
  content_hash CHAR(64) NOT NULL COMMENT '正文内容哈希',
  item_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/UPDATED/OFFLINE/FAILED',
  parse_status VARCHAR(32) NOT NULL COMMENT 'DETAIL_SUCCESS/PARSE_FAILED/DETAIL_FAILED',
  parse_error VARCHAR(1024) NULL COMMENT '解析失败原因',
  ai_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/REVIEW/FAILED',
  ai_event_type VARCHAR(32) NULL COMMENT '智能体识别的信息类型',
  ai_summary TEXT NULL COMMENT '智能精简摘要',
  ai_card_json JSON NULL COMMENT '智能体结构化信息卡片',
  ai_confidence DECIMAL(5,4) NULL COMMENT '智能提取置信度',
  ai_need_review TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否需要人工复核',
  ai_error VARCHAR(1024) NULL COMMENT '智能提取失败原因',
  ai_processed_at DATETIME NULL COMMENT '智能提取完成时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_information_item_url_title (item_url(512), title(191)),
  KEY idx_information_item_time (publish_time, fetched_at),
  KEY idx_information_item_source (source_id, fetched_at),
  KEY idx_information_item_status (item_status),
  KEY idx_information_item_ai_status (ai_status, fetched_at)
) COMMENT='信息集中站信息条目表';

CREATE TABLE IF NOT EXISTS user_information_state (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  read_status VARCHAR(32) NOT NULL DEFAULT 'NEW' COMMENT 'NEW/READ/FAVORITED',
  first_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  read_at DATETIME NULL,
  archived_at DATETIME NULL,
  UNIQUE KEY uk_user_item_state (user_id, item_id),
  KEY idx_user_read_status (user_id, read_status, first_seen_at)
) COMMENT='用户信息条目阅读状态表';

UPDATE user_information_state SET read_status = 'FAVORITED' WHERE read_status = 'ARCHIVED';

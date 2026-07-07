SET NAMES utf8mb4;

USE campusmind;

CREATE TABLE IF NOT EXISTS web_crawl_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL COMMENT '采集任务ID',
  source_id BIGINT NOT NULL COMMENT '公开网页数据源ID',
  source_name VARCHAR(128) NOT NULL COMMENT '数据源名称',
  source_url VARCHAR(1024) NOT NULL COMMENT '列表页URL',
  item_url VARCHAR(1024) NOT NULL COMMENT '详情页URL',
  title VARCHAR(512) NOT NULL COMMENT '列表页解析标题',
  date_text VARCHAR(64) NULL COMMENT '列表页日期文本',
  summary TEXT NULL COMMENT '列表页摘要',
  content_hash CHAR(64) NOT NULL COMMENT '标题+URL+日期摘要hash',
  parser_version VARCHAR(64) NULL COMMENT '解析器版本',
  fetched_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_web_crawl_item_hash (content_hash),
  KEY idx_web_crawl_item_source_time (source_id, fetched_at),
  KEY idx_web_crawl_item_task (task_id)
) COMMENT='公开网页列表采集结果表';

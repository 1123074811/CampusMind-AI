-- 011_information_change_log.sql
-- 原文变更追踪日志表：记录每次爬取时信息条目的内容变化情况。

CREATE TABLE IF NOT EXISTS information_change_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id       BIGINT          NOT NULL COMMENT '关联 information_item.id',
    old_content_hash VARCHAR(128) NULL     COMMENT '变更前的 content_hash',
    new_content_hash VARCHAR(128) NULL     COMMENT '变更后的 content_hash',
    changed_fields JSON           NULL     COMMENT '变化字段列表，如 ["content","ai_status"]',
    changed_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更检测时间',

    INDEX idx_change_item (item_id, changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='信息条目变更日志';

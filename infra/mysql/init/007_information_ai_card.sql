SET NAMES utf8mb4;

USE campusmind;

-- 仅用于存量数据库升级，新建库由 006_information_item.sql 直接建表完成
ALTER TABLE information_item
  ADD COLUMN ai_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/REVIEW/FAILED',
  ADD COLUMN ai_event_type VARCHAR(32) NULL COMMENT '智能体识别的信息类型',
  ADD COLUMN ai_summary TEXT NULL COMMENT '智能精简摘要',
  ADD COLUMN ai_card_json JSON NULL COMMENT '智能体结构化信息卡片',
  ADD COLUMN ai_confidence DECIMAL(5,4) NULL COMMENT '智能提取置信度',
  ADD COLUMN ai_need_review TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否需要人工复核',
  ADD COLUMN ai_error VARCHAR(1024) NULL COMMENT '智能提取失败原因',
  ADD COLUMN ai_processed_at DATETIME NULL COMMENT '智能提取完成时间';

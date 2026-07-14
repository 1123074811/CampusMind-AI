SET NAMES utf8mb4;

USE campusmind;

DELIMITER //

CREATE PROCEDURE add_email_if_missing()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'email'
  ) THEN
    ALTER TABLE user ADD COLUMN email VARCHAR(255) NULL COMMENT '用于账号找回的邮箱' AFTER phone;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND INDEX_NAME = 'uk_user_email'
  ) THEN
    ALTER TABLE user ADD UNIQUE KEY uk_user_email (email);
  END IF;
END//

DELIMITER ;

CALL add_email_if_missing();
DROP PROCEDURE add_email_if_missing;

CREATE TABLE IF NOT EXISTS data_source_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  action VARCHAR(32) NOT NULL COMMENT 'CREATE/UPDATE/ENABLE/DISABLE/ROLLBACK',
  snapshot JSON NOT NULL,
  operator_id BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_source_version (source_id, version_no),
  KEY idx_source_version_created (source_id, created_at),
  CONSTRAINT fk_source_version_source FOREIGN KEY (source_id) REFERENCES data_source(id) ON DELETE CASCADE,
  CONSTRAINT fk_source_version_operator FOREIGN KEY (operator_id) REFERENCES user(id) ON DELETE SET NULL
) COMMENT='数据源配置版本历史';

INSERT INTO data_source_version(source_id, version_no, action, snapshot, operator_id)
SELECT ds.id, 1, 'BASELINE', JSON_OBJECT(
  'name', ds.name,
  'sourceType', ds.source_type,
  'baseUrl', ds.base_url,
  'robotsUrl', ds.robots_url,
  'crawlIntervalSeconds', ds.crawl_interval_seconds,
  'parserType', ds.parser_type,
  'selectorConfig', ds.selector_config,
  'enabled', ds.enabled
), NULL
FROM data_source ds
WHERE NOT EXISTS (
  SELECT 1 FROM data_source_version dsv WHERE dsv.source_id = ds.id
);

CREATE TABLE IF NOT EXISTS user_consent_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  consent_type VARCHAR(64) NOT NULL COMMENT 'PRIVACY_POLICY/PERSONALIZATION/NOTIFICATION',
  policy_version VARCHAR(32) NOT NULL,
  granted TINYINT NOT NULL,
  source VARCHAR(32) NOT NULL DEFAULT 'APP',
  occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_consent_user_type (user_id, consent_type, occurred_at),
  CONSTRAINT fk_consent_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) COMMENT='用户授权与撤回记录';

CREATE TABLE IF NOT EXISTS user_device (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  device_id VARCHAR(128) NOT NULL,
  platform VARCHAR(32) NOT NULL,
  push_token VARCHAR(512) NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_device (user_id, device_id),
  KEY idx_device_delivery (enabled, platform),
  CONSTRAINT fk_device_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) COMMENT='用户通知设备';

CREATE TABLE IF NOT EXISTS notification_delivery (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  reminder_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  device_id BIGINT NULL,
  channel VARCHAR(32) NOT NULL COMMENT 'IN_APP/WEBHOOK/PUSH',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENDING/SENT/RETRY/FAILED/WITHDRAWN',
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME NULL,
  last_error VARCHAR(1000) NULL,
  dedup_key VARCHAR(191) NOT NULL,
  provider_message_id VARCHAR(255) NULL,
  sent_at DATETIME NULL,
  withdrawn_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_delivery_dedup (dedup_key),
  KEY idx_delivery_retry (status, next_attempt_at),
  KEY idx_delivery_user (user_id, created_at),
  CONSTRAINT fk_delivery_reminder FOREIGN KEY (reminder_id) REFERENCES user_reminder(id) ON DELETE CASCADE,
  CONSTRAINT fk_delivery_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
  CONSTRAINT fk_delivery_device FOREIGN KEY (device_id) REFERENCES user_device(id) ON DELETE SET NULL
) COMMENT='通知投递、重试与撤回记录';

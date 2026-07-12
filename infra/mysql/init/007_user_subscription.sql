SET NAMES utf8mb4;

USE campusmind;

-- 用户数据源订阅表
CREATE TABLE IF NOT EXISTS user_source_subscription (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  source_id BIGINT NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_source_sub (user_id, source_id),
  KEY idx_user_sub_user (user_id, enabled),
  CONSTRAINT fk_user_source_subscription_user FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE,
  CONSTRAINT fk_user_source_subscription_source FOREIGN KEY (source_id) REFERENCES data_source (id) ON DELETE CASCADE
) COMMENT='用户数据源订阅关系表';

-- 为用户默认订阅所有启用的公开数据源（仅插入尚未存在的订阅，避免 CROSS JOIN 全量扫描）
INSERT INTO user_source_subscription (user_id, source_id, enabled)
SELECT u.id, ds.id, 1
FROM user u
CROSS JOIN data_source ds
WHERE ds.enabled = 1
  AND ds.source_type = 'PUBLIC_WEB'
  AND NOT EXISTS (
    SELECT 1 FROM user_source_subscription uss
    WHERE uss.user_id = u.id AND uss.source_id = ds.id
  );
SET NAMES utf8mb4;

USE campusmind;

-- 用户数据源订阅表
CREATE TABLE IF NOT EXISTS user_source_subscription (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  source_id BIGINT NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_source_sub (user_id, source_id),
  KEY idx_user_sub_user (user_id, enabled),
  CONSTRAINT fk_user_source_subscription_user FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE,
  CONSTRAINT fk_user_source_subscription_source FOREIGN KEY (source_id) REFERENCES data_source (id) ON DELETE CASCADE
) COMMENT='用户数据源订阅关系表';

-- 为用户默认订阅所有启用的公开数据源
INSERT INTO user_source_subscription (user_id, source_id, enabled)
SELECT u.id, ds.id, 1
FROM user u
CROSS JOIN data_source ds
WHERE ds.enabled = 1
  AND ds.source_type = 'PUBLIC_WEB'
ON DUPLICATE KEY UPDATE enabled = VALUES(enabled);

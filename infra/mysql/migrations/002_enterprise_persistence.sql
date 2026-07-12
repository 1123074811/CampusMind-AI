SET NAMES utf8mb4;

-- 收藏和已读是两个独立事实。先归档无法关联的历史状态，避免静默丢失。
CREATE TABLE user_information_state_orphan_20260712 LIKE user_information_state;
ALTER TABLE user_information_state_orphan_20260712
  ADD COLUMN orphan_reason VARCHAR(64) NOT NULL,
  ADD COLUMN migrated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  COMMENT='迁移 002 发现的无效用户状态只读归档';

INSERT INTO user_information_state_orphan_20260712 (
  id, user_id, item_id, read_status, first_seen_at, read_at, archived_at, orphan_reason
)
SELECT
  state.id,
  state.user_id,
  state.item_id,
  state.read_status,
  state.first_seen_at,
  state.read_at,
  state.archived_at,
  CASE WHEN user.id IS NULL THEN 'USER_NOT_FOUND' ELSE 'INFORMATION_ITEM_NOT_FOUND' END
FROM user_information_state state
LEFT JOIN user ON user.id = state.user_id
LEFT JOIN information_item item ON item.id = state.item_id
WHERE user.id IS NULL OR item.id IS NULL;

DELETE state
FROM user_information_state state
LEFT JOIN user ON user.id = state.user_id
LEFT JOIN information_item item ON item.id = state.item_id
WHERE user.id IS NULL OR item.id IS NULL;

ALTER TABLE user_information_state
  CHANGE COLUMN archived_at favorited_at DATETIME NULL;

UPDATE user_information_state
SET read_at = COALESCE(read_at, first_seen_at)
WHERE read_status IN ('READ', 'FAVORITED', 'ARCHIVED');

UPDATE user_information_state
SET favorited_at = CASE
  WHEN read_status IN ('FAVORITED', 'ARCHIVED') THEN COALESCE(favorited_at, first_seen_at)
  ELSE NULL
END;

ALTER TABLE user_information_state
  DROP INDEX idx_user_read_status,
  DROP COLUMN read_status,
  ADD KEY idx_user_read_history (user_id, read_at),
  ADD KEY idx_user_favorites (user_id, favorited_at),
  COMMENT='用户信息条目阅读与收藏状态表';

CREATE TABLE IF NOT EXISTS user_source_subscription (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  source_id BIGINT NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_source_sub (user_id, source_id),
  KEY idx_user_sub_user (user_id, enabled)
) COMMENT='用户数据源订阅关系表';

INSERT INTO user_source_subscription (user_id, source_id, enabled)
SELECT user.id, source.id, 1
FROM user
CROSS JOIN data_source source
WHERE source.enabled = 1
  AND source.source_type = 'PUBLIC_WEB'
ON DUPLICATE KEY UPDATE enabled = VALUES(enabled);

DELIMITER //

CREATE PROCEDURE add_enterprise_fk_if_missing(
  IN constraint_name_value VARCHAR(64),
  IN ddl_value VARCHAR(2048)
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND CONSTRAINT_NAME = constraint_name_value
  ) THEN
    SET @ddl = ddl_value;
    PREPARE statement_to_run FROM @ddl;
    EXECUTE statement_to_run;
    DEALLOCATE PREPARE statement_to_run;
  END IF;
END//

DELIMITER ;

CALL add_enterprise_fk_if_missing(
  'fk_web_crawl_item_task',
  'ALTER TABLE web_crawl_item ADD CONSTRAINT fk_web_crawl_item_task FOREIGN KEY (task_id) REFERENCES crawl_task(id) ON DELETE CASCADE'
);
CALL add_enterprise_fk_if_missing(
  'fk_web_crawl_item_source',
  'ALTER TABLE web_crawl_item ADD CONSTRAINT fk_web_crawl_item_source FOREIGN KEY (source_id) REFERENCES data_source(id) ON DELETE RESTRICT'
);
CALL add_enterprise_fk_if_missing(
  'fk_information_item_source',
  'ALTER TABLE information_item ADD CONSTRAINT fk_information_item_source FOREIGN KEY (source_id) REFERENCES data_source(id) ON DELETE RESTRICT'
);
CALL add_enterprise_fk_if_missing(
  'fk_user_information_state_user',
  'ALTER TABLE user_information_state ADD CONSTRAINT fk_user_information_state_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE'
);
CALL add_enterprise_fk_if_missing(
  'fk_user_information_state_item',
  'ALTER TABLE user_information_state ADD CONSTRAINT fk_user_information_state_item FOREIGN KEY (item_id) REFERENCES information_item(id) ON DELETE RESTRICT'
);
CALL add_enterprise_fk_if_missing(
  'fk_user_source_subscription_user',
  'ALTER TABLE user_source_subscription ADD CONSTRAINT fk_user_source_subscription_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE'
);
CALL add_enterprise_fk_if_missing(
  'fk_user_source_subscription_source',
  'ALTER TABLE user_source_subscription ADD CONSTRAINT fk_user_source_subscription_source FOREIGN KEY (source_id) REFERENCES data_source(id) ON DELETE CASCADE'
);
CALL add_enterprise_fk_if_missing(
  'fk_event_source_ref_source',
  'ALTER TABLE event_source_ref ADD CONSTRAINT fk_event_source_ref_source FOREIGN KEY (source_id) REFERENCES data_source(id) ON DELETE SET NULL'
);
CALL add_enterprise_fk_if_missing(
  'fk_campus_event_owner',
  'ALTER TABLE campus_event ADD CONSTRAINT fk_campus_event_owner FOREIGN KEY (owner_user_id) REFERENCES user(id) ON DELETE CASCADE'
);

DROP PROCEDURE add_enterprise_fk_if_missing;

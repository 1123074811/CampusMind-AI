-- 为 information_item 增加用户提交来源追踪字段
-- 记录用户导入时的用户名和用户ID，便于管理员在后台识别具体提交人

SET NAMES utf8mb4;
USE campusmind;

DELIMITER //

CREATE PROCEDURE add_submitted_by_columns_if_missing()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'information_item' AND column_name = 'submitted_by'
  ) THEN
    ALTER TABLE information_item
      ADD COLUMN submitted_by VARCHAR(128) NULL COMMENT '提交用户名（用户导入时记录）' AFTER ai_need_review;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'information_item' AND column_name = 'submitted_by_user_id'
  ) THEN
    ALTER TABLE information_item
      ADD COLUMN submitted_by_user_id BIGINT NULL COMMENT '提交用户ID（用户导入时记录）' AFTER submitted_by;
  END IF;
END//

DELIMITER ;

CALL add_submitted_by_columns_if_missing();
DROP PROCEDURE add_submitted_by_columns_if_missing;

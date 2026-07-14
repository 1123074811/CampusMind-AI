-- 006: 用户画像敏感度字段
SET @ddl = (SELECT IF(
  NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'user_profile'
      AND column_name = 'sensitivity'
  ),
  'ALTER TABLE user_profile ADD COLUMN sensitivity DOUBLE NULL DEFAULT 0.5 COMMENT ''用户画像敏感度'' AFTER course_codes',
  'SELECT "user_profile.sensitivity already exists"'
));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

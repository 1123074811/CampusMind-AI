-- 006: 用户画像敏感度字段
ALTER TABLE user_profile ADD COLUMN IF NOT EXISTS sensitivity DOUBLE DEFAULT 0.5;

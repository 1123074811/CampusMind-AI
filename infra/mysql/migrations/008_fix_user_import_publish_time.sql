-- 用户导入条目的发布时间应为实际上传时间，而不是 AI 从正文提取的业务日期

SET NAMES utf8mb4;
USE campusmind;

UPDATE information_item
SET publish_time = fetched_at
WHERE submitted_by_user_id IS NOT NULL
  AND fetched_at IS NOT NULL
  AND (publish_time IS NULL OR publish_time <> fetched_at);

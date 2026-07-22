-- 旧版雨课堂导入曾将抓取时刻写成发布时间；无法从旧原文恢复时应保持未知。
UPDATE information_item
SET publish_time = NULL
WHERE source_name = '雨课堂导入'
  AND publish_time IS NOT NULL
  AND ABS(TIMESTAMPDIFF(SECOND, publish_time, fetched_at)) <= 1;

UPDATE campus_event
SET published_at = NULL
WHERE source_type = 'RAIN_CLASSROOM'
  AND published_at IS NOT NULL
  AND ABS(TIMESTAMPDIFF(SECOND, published_at, created_at)) <= 1;

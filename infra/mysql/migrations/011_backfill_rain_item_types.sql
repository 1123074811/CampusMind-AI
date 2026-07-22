-- 雨课堂数据类型来自原始结构化字段，不应等待 Agent 才能进入信息流。
UPDATE information_item
SET ai_event_type = CASE
    WHEN detail_content LIKE '%COURSE%' THEN 'COURSE'
    WHEN detail_content LIKE '%NOTICE%' THEN 'NOTICE'
    WHEN detail_content LIKE '%HOMEWORK%' THEN 'HOMEWORK'
    WHEN detail_content LIKE '%EXAM%' THEN 'EXAM'
    ELSE ai_event_type
END
WHERE HEX(source_name) = 'E99BA8E8AFBEE5A082E5AFBCE585A5'
  AND ai_event_type IS NULL;

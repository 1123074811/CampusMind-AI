SET NAMES utf8mb4;

USE campusmind;

-- 仅初始化开发登录账号和内部数据源。主键始终由 MySQL 分配；演示业务数据不进入默认数据库。
INSERT INTO user (username, phone, password_hash, role, status)
VALUES
  ('admin', '13800139901', '$2a$10$arc18WSWtVsrvB55MsG5B.aLl4Ec6Vp0bYxGER2n6JnfDr4b8nq0y', 'ADMIN', 1),
  ('123456', '13800139902', '$2a$10$zzzqLXxQyJmfa.JmtbSWzuaJwKaSFei/Nv2t9cT6oYw9wukVmakH2', 'STUDENT', 1)
AS seed
ON DUPLICATE KEY UPDATE
  username = seed.username;

INSERT INTO data_source (
  name, source_type, base_url, robots_url, crawl_interval_seconds,
  parser_type, selector_config, enabled, last_crawled_at
)
VALUES
  ('雨课堂导入', 'RAIN_CLASSROOM', 'https://www.yuketang.cn', NULL, 10, 'USER_JSON', JSON_OBJECT('mode', 'manual_json'), 1, NULL),
  ('用户截图 OCR', 'USER_IMAGE', 'campusmind://user-image-import', NULL, 10, 'OCR', JSON_OBJECT('engine', 'manual_upload'), 0, NULL),
  ('用户文本提交', 'USER_TEXT', 'campusmind://user-text-import', NULL, 10, 'USER_PASTE', JSON_OBJECT('mode', 'manual_text'), 1, NULL),
  ('用户文件上传', 'USER_FILE', 'campusmind://user-file-import', NULL, 10, 'USER_UPLOAD', JSON_OBJECT('mode', 'file_upload'), 1, NULL)
AS seed
ON DUPLICATE KEY UPDATE
  name = seed.name,
  source_type = seed.source_type,
  robots_url = seed.robots_url,
  crawl_interval_seconds = seed.crawl_interval_seconds,
  parser_type = seed.parser_type,
  selector_config = seed.selector_config,
  enabled = seed.enabled;

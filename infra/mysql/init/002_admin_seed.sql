SET NAMES utf8mb4;

USE campusmind;

ALTER TABLE event_audit_log MODIFY event_id BIGINT NULL;
ALTER TABLE event_audit_log MODIFY operator_id BIGINT NULL;

DELETE FROM event_audit_log WHERE event_id BETWEEN 9101 AND 9110 OR operator_id = 9901 OR action IN ('MANUAL_CRAWL', 'AUTO_CRAWL');
DELETE FROM import_task WHERE id BETWEEN 9601 AND 9604;
DELETE FROM crawl_task WHERE id BETWEEN 9501 AND 9508;
DELETE FROM crawl_task WHERE source_id BETWEEN 9401 AND 9404;
DELETE FROM web_crawl_item WHERE source_id BETWEEN 9401 AND 9404;
DELETE FROM information_item WHERE source_id BETWEEN 9401 AND 9404;
DELETE FROM event_source_ref WHERE event_id BETWEEN 9101 AND 9110;
DELETE FROM campus_event WHERE id BETWEEN 9101 AND 9110;
DELETE FROM data_source WHERE id BETWEEN 9401 AND 9404;
DELETE FROM user_profile WHERE user_id IN (9901, 9902);
DELETE FROM user WHERE id IN (9901, 9902);

INSERT INTO user (
  id, username, phone, password_hash, role, status
) VALUES
(
  9901, 'admin', '13800139901',
  '$2a$10$arc18WSWtVsrvB55MsG5B.aLl4Ec6Vp0bYxGER2n6JnfDr4b8nq0y',
  'ADMIN', 1
),
(
  9902, '123456', '13800139902',
  '$2a$10$zzzqLXxQyJmfa.JmtbSWzuaJwKaSFei/Nv2t9cT6oYw9wukVmakH2',
  'STUDENT', 1
);

INSERT INTO data_source (
  id, name, source_type, base_url, robots_url, crawl_interval_seconds,
  parser_type, selector_config, enabled, last_crawled_at
) VALUES
  (9401, '软件学院通知', 'PUBLIC_WEB', 'https://software.example.edu.cn/notices', 'https://software.example.edu.cn/robots.txt', 5, 'WEBMAGIC', JSON_OBJECT('list', '.notice-list a', 'title', 'h1'), 1, '2026-07-07 18:28:00'),
  (9402, '教务处公告', 'PUBLIC_WEB', 'https://jwc.example.edu.cn/news', 'https://jwc.example.edu.cn/robots.txt', 8, 'RSS', JSON_OBJECT('feed', '/rss.xml'), 1, '2026-07-07 18:10:00'),
  (9403, '雨课堂导入', 'RAIN_CLASSROOM', 'https://www.yuketang.cn', NULL, 10, 'USER_JSON', JSON_OBJECT('mode', 'manual_json'), 1, '2026-07-07 18:06:00'),
  (9404, '用户截图 OCR', 'USER_IMAGE', 'campusmind://user-image-import', NULL, 10, 'OCR', JSON_OBJECT('engine', 'manual_upload'), 0, '2026-07-07 17:30:00');

INSERT INTO campus_event (
  id, title, summary, event_type, source_type, status, confidence,
  start_time, end_time, location, organizer, target_scope, tags,
  dedup_key, vector_doc_id, published_at, created_at
) VALUES
  (9101, '人工智能主题讲座通知', '软件学院将于 7 月 8 日举办 AI 主题讲座，面向软件学院本科生开放。', 'LECTURE', 'PUBLIC_WEB', 'AI_PUBLISHED', 0.9100, '2026-07-08 19:00:00', '2026-07-08 21:00:00', '图书馆报告厅', '软件学院', JSON_ARRAY('软件学院本科生'), JSON_ARRAY('AI','讲座','软件学院'), SHA2('9101-ai-lecture', 256), NULL, '2026-07-07 10:00:00', '2026-07-07 18:00:00'),
  (9102, '期末考试考场调整说明', '部分课程考试地点变更，学生需以最终考场清单为准。', 'EXAM', 'PUBLIC_WEB', 'CORRECTED', 0.7400, '2026-07-11 09:30:00', '2026-07-11 11:30:00', '一号教学楼', '教务处', JSON_ARRAY('2023级'), JSON_ARRAY('考试','教务'), SHA2('9102-exam-change', 256), 'event-vec-9102', '2026-07-07 09:30:00', '2026-07-07 17:56:00'),
  (9103, '雨课堂作业提交提醒', 'SE101 课程作业截止到 7 月 9 日 23:59，来源为用户粘贴 JSON。', 'HOMEWORK', 'RAIN_CLASSROOM', 'AI_PUBLISHED', 0.6800, '2026-07-09 23:59:00', NULL, '线上', 'SE101 课程组', JSON_ARRAY('SE101'), JSON_ARRAY('雨课堂','作业'), SHA2('9103-rain-homework', 256), NULL, '2026-07-07 09:00:00', '2026-07-07 17:40:00'),
  (9104, '创新创业竞赛报名开放', '创新创业竞赛进入报名阶段，材料提交窗口开放至 7 月 15 日。', 'COMPETITION', 'PUBLIC_WEB', 'REVIEWED', 0.8800, '2026-07-15 18:00:00', NULL, '学生事务中心', '学工系统', JSON_ARRAY('全校学生'), JSON_ARRAY('竞赛','报名'), SHA2('9104-competition', 256), 'event-vec-9104', '2026-07-07 08:40:00', '2026-07-07 17:25:00'),
  (9105, '暑期图书馆开放服务调整', '图书馆暑期开放时间调整，部分阅览区暂停夜间服务。', 'SERVICE', 'PUBLIC_WEB', 'AI_PUBLISHED', 0.8300, '2026-07-10 08:30:00', '2026-08-25 22:00:00', '图书馆', '图书馆', JSON_ARRAY('全校师生'), JSON_ARRAY('服务','图书馆'), SHA2('9105-library-service', 256), NULL, '2026-07-07 08:20:00', '2026-07-07 17:10:00'),
  (9106, '软件工程课程调课通知', '软件工程课程本周四调至二号教学楼 305，请相关班级注意。', 'COURSE', 'PUBLIC_WEB', 'CORRECTED', 0.7200, '2026-07-09 14:00:00', '2026-07-09 16:00:00', '二号教学楼 305', '软件工程课程组', JSON_ARRAY('软件工程1班'), JSON_ARRAY('课程','调课'), SHA2('9106-course-change', 256), NULL, '2026-07-07 08:10:00', '2026-07-07 16:50:00'),
  (9107, '社团嘉年华活动安排', '社团嘉年华将在操场举办，面向全校学生开放报名。', 'ACTIVITY', 'USER_IMAGE', 'AI_PUBLISHED', 0.7600, '2026-07-12 18:30:00', '2026-07-12 21:00:00', '东区操场', '校团委', JSON_ARRAY('全校学生'), JSON_ARRAY('社团','活动'), SHA2('9107-club-activity', 256), NULL, '2026-07-07 08:00:00', '2026-07-07 16:30:00'),
  (9108, '校园网络维护通知', '信息中心将进行校园网络维护，部分区域网络可能短时中断。', 'NOTICE', 'PUBLIC_WEB', 'REVIEWED', 0.9300, '2026-07-13 00:30:00', '2026-07-13 02:00:00', '全校网络', '信息中心', JSON_ARRAY('全校师生'), JSON_ARRAY('通知','网络'), SHA2('9108-network-notice', 256), 'event-vec-9108', '2026-07-07 07:50:00', '2026-07-07 16:10:00'),
  (9109, '旧通知重复截图', '用户上传的截图与历史通知重复，已标记为无效。', 'OTHER', 'USER_IMAGE', 'REJECTED', 0.4200, NULL, NULL, NULL, '用户导入', JSON_ARRAY(), JSON_ARRAY('重复','截图'), SHA2('9109-duplicate-image', 256), NULL, NULL, '2026-07-07 15:50:00'),
  (9110, '过期服务公告下线', '公告已过有效期，不再在信息流展示。', 'SERVICE', 'USER_TEXT', 'OFFLINE', 0.6100, '2026-06-20 08:00:00', NULL, '线上', '用户导入', JSON_ARRAY('全校师生'), JSON_ARRAY('过期','服务'), SHA2('9110-offline-service', 256), NULL, NULL, '2026-07-07 15:40:00');

INSERT INTO event_source_ref (
  event_id, source_id, raw_doc_id, source_url, source_title, content_hash
) VALUES
  (9101, 9401, 'raw-admin-9101', 'https://software.example.edu.cn/notices/9101', '人工智能主题讲座通知', SHA2('raw-admin-9101', 256)),
  (9102, 9402, 'raw-admin-9102', 'https://jwc.example.edu.cn/news/9102', '期末考试考场调整说明', SHA2('raw-admin-9102', 256)),
  (9103, 9403, 'raw-admin-9103', NULL, '雨课堂作业提交提醒', SHA2('raw-admin-9103', 256)),
  (9107, 9404, 'raw-admin-9107', NULL, '社团嘉年华活动安排', SHA2('raw-admin-9107', 256));

INSERT INTO crawl_task (
  id, source_id, task_status, crawl_url, http_status, etag, last_modified, fail_reason, started_at, finished_at
) VALUES
  (9501, 9401, 'SUCCESS', 'https://software.example.edu.cn/notices', 200, 'etag-9501', 'Tue, 07 Jul 2026 10:20:00 GMT', NULL, '2026-07-07 18:20:00', '2026-07-07 18:20:12'),
  (9502, 9401, 'RUNNING', 'https://software.example.edu.cn/notices?page=2', NULL, NULL, NULL, NULL, '2026-07-07 18:18:00', NULL),
  (9503, 9403, 'FAILED', 'https://www.yuketang.cn/manual-json', 403, NULL, NULL, '403 授权失效', '2026-07-07 18:16:00', '2026-07-07 18:16:08'),
  (9504, 9402, 'PENDING', 'https://jwc.example.edu.cn/news', NULL, NULL, NULL, NULL, '2026-07-07 18:11:00', NULL),
  (9505, 9402, 'SKIPPED', 'https://jwc.example.edu.cn/news/archive', 304, 'etag-9505', 'Tue, 07 Jul 2026 09:00:00 GMT', '内容未变化', '2026-07-07 17:58:00', '2026-07-07 17:58:01'),
  (9506, 9404, 'FAILED', 'campusmind://user-image-import', NULL, NULL, NULL, 'OCR 队列暂停', '2026-07-07 17:30:00', '2026-07-07 17:30:04');

INSERT INTO import_task (
  id, user_id, import_type, task_status, raw_doc_id, result_summary, error_message, created_at, finished_at
) VALUES
  (9601, 9901, 'RAIN_JSON', 'RUNNING', 'raw-import-9601', JSON_OBJECT('message', '正在抽取课程作业字段'), NULL, '2026-07-07 18:19:00', NULL),
  (9602, 9901, 'USER_TEXT', 'SUCCESS', 'raw-import-9602', JSON_OBJECT('createdEvents', 1), NULL, '2026-07-07 18:05:00', '2026-07-07 18:05:09'),
  (9603, 9901, 'USER_IMAGE', 'FAILED', 'raw-import-9603', NULL, 'OCR 置信度过低', '2026-07-07 17:42:00', '2026-07-07 17:42:10'),
  (9604, 9901, 'RAIN_COOKIE', 'PENDING', NULL, JSON_OBJECT('ttlMinutes', 10), NULL, '2026-07-07 17:35:00', NULL);

INSERT INTO event_audit_log (
  event_id, operator_id, action, before_snapshot, after_snapshot, comment, created_at
) VALUES
  (9102, 9901, 'CORRECT', JSON_OBJECT('location', '待确认'), JSON_OBJECT('location', '一号教学楼'), '修正考试地点', '2026-07-07 18:02:00'),
  (9104, 9901, 'REVIEW', JSON_OBJECT('status', 'AI_PUBLISHED'), JSON_OBJECT('status', 'REVIEWED'), '活动信息完整', '2026-07-07 18:03:00'),
  (9109, 9901, 'REJECT', JSON_OBJECT('status', 'AI_PUBLISHED'), JSON_OBJECT('status', 'REJECTED'), '重复截图', '2026-07-07 18:04:00'),
  (NULL, NULL, 'MANUAL_CRAWL', NULL, JSON_OBJECT('sourceCount', 7, 'persistedCount', 10), '管理员手动抓取公开网页数据源', '2026-07-07 18:05:00'),
  (NULL, NULL, 'AUTO_CRAWL', NULL, JSON_OBJECT('sourceCount', 7, 'persistedCount', 0), '系统定时抓取公开网页数据源', '2026-07-07 06:30:00');

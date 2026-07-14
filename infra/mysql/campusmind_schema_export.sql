-- MySQL dump 10.13  Distrib 9.1.0, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: campusmind
-- ------------------------------------------------------
-- Server version	9.1.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `ai_processing_record`
--

DROP TABLE IF EXISTS `ai_processing_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_processing_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `information_item_id` bigint NOT NULL,
  `content_hash` char(64) NOT NULL,
  `task_type` varchar(32) NOT NULL,
  `trigger_type` varchar(32) NOT NULL,
  `status` varchar(16) NOT NULL,
  `provider` varchar(64) DEFAULT NULL,
  `model_version` varchar(128) DEFAULT NULL,
  `prompt_version` varchar(64) NOT NULL,
  `input_digest` char(64) NOT NULL,
  `output_json` json DEFAULT NULL,
  `prompt_tokens` int DEFAULT NULL,
  `completion_tokens` int DEFAULT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_processing_item_hash_prompt` (`information_item_id`,`content_hash`,`task_type`,`prompt_version`),
  KEY `idx_ai_processing_status_created` (`status`,`created_at`),
  CONSTRAINT `fk_ai_processing_information_item` FOREIGN KEY (`information_item_id`) REFERENCES `information_item` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `campus_event`
--

DROP TABLE IF EXISTS `campus_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `campus_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL COMMENT '事件标题',
  `summary` text COMMENT 'AI摘要或人工摘要',
  `event_type` varchar(64) NOT NULL COMMENT 'NOTICE/COURSE/EXAM/HOMEWORK/ACTIVITY/LECTURE/COMPETITION/SERVICE',
  `source_type` varchar(64) NOT NULL COMMENT 'PUBLIC_WEB/RAIN_CLASSROOM/USER_TEXT/USER_IMAGE/USER_FILE',
  `visibility` varchar(16) NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC/PRIVATE（PRIVATE=用户私有事件，仅owner可见）',
  `owner_user_id` bigint DEFAULT NULL COMMENT 'PRIVATE事件所属用户',
  `status` varchar(32) NOT NULL DEFAULT 'AI_PUBLISHED' COMMENT 'AI_PUBLISHED/REVIEWED/CORRECTED/REJECTED/OFFLINE',
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `location` varchar(255) DEFAULT NULL,
  `organizer` varchar(255) DEFAULT NULL COMMENT '发布单位或课程教师',
  `target_scope` json DEFAULT NULL COMMENT '适用范围：学院/专业/年级/课程',
  `tags` json DEFAULT NULL COMMENT '标签',
  `dedup_key` char(64) DEFAULT NULL COMMENT '标题+时间+来源生成的SHA256',
  `vector_doc_id` varchar(128) DEFAULT NULL COMMENT '向量库文档ID',
  `published_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_dedup_key` (`dedup_key`),
  KEY `idx_event_type_time` (`event_type`,`start_time`),
  KEY `idx_event_status_created` (`status`,`created_at`),
  KEY `idx_event_source_type` (`source_type`),
  KEY `idx_event_owner_visibility` (`owner_user_id`,`visibility`,`start_time`),
  CONSTRAINT `fk_campus_event_owner` FOREIGN KEY (`owner_user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=9194 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='校园事件主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crawl_task`
--

DROP TABLE IF EXISTS `crawl_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crawl_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_id` bigint NOT NULL,
  `task_status` varchar(32) NOT NULL COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/SKIPPED',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `crawl_url` varchar(1024) NOT NULL,
  `http_status` int DEFAULT NULL,
  `etag` varchar(255) DEFAULT NULL,
  `last_modified` varchar(255) DEFAULT NULL,
  `fail_reason` varchar(1024) DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_task_source_status` (`source_id`,`task_status`),
  KEY `idx_task_started` (`started_at`),
  CONSTRAINT `fk_crawl_task_source` FOREIGN KEY (`source_id`) REFERENCES `data_source` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=10326 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采集任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `data_source`
--

DROP TABLE IF EXISTS `data_source`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `data_source` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `source_type` varchar(64) NOT NULL COMMENT 'PUBLIC_WEB/RAIN_CLASSROOM/USER_TEXT/USER_IMAGE/USER_FILE',
  `base_url` varchar(1024) NOT NULL,
  `robots_url` varchar(1024) DEFAULT NULL,
  `crawl_interval_seconds` int NOT NULL DEFAULT '5' COMMENT '必须大于2秒',
  `parser_type` varchar(64) NOT NULL COMMENT 'WEBMAGIC/PLAYWRIGHT/RSS/SITEMAP',
  `selector_config` json DEFAULT NULL COMMENT 'CSS/XPath/正文抽取规则',
  `enabled` tinyint NOT NULL DEFAULT '1',
  `last_crawled_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_base_url` (`base_url`(255)),
  KEY `idx_source_enabled` (`enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=9533 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公开网页数据源表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `event_audit_log`
--

DROP TABLE IF EXISTS `event_audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `event_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_id` bigint DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `action` varchar(64) NOT NULL COMMENT 'REVIEW/CORRECT/MERGE/REJECT/OFFLINE/MANUAL_CRAWL/AUTO_CRAWL',
  `before_snapshot` json DEFAULT NULL,
  `after_snapshot` json DEFAULT NULL,
  `comment` varchar(512) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_event_time` (`event_id`,`created_at`),
  KEY `idx_audit_operator` (`operator_id`),
  CONSTRAINT `fk_event_audit_log_event` FOREIGN KEY (`event_id`) REFERENCES `campus_event` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_event_audit_log_operator` FOREIGN KEY (`operator_id`) REFERENCES `user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=786 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事件审核日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `event_source_ref`
--

DROP TABLE IF EXISTS `event_source_ref`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `event_source_ref` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_id` bigint NOT NULL,
  `source_id` bigint DEFAULT NULL COMMENT '公开网页数据源ID',
  `raw_doc_id` varchar(64) NOT NULL COMMENT 'MongoDB原始文档ID',
  `source_url` varchar(1024) DEFAULT NULL,
  `source_title` varchar(255) DEFAULT NULL,
  `content_hash` char(64) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ref_event` (`event_id`),
  KEY `idx_ref_hash` (`content_hash`),
  KEY `fk_event_source_ref_source` (`source_id`),
  CONSTRAINT `fk_event_source_ref_event` FOREIGN KEY (`event_id`) REFERENCES `campus_event` (`id`),
  CONSTRAINT `fk_event_source_ref_source` FOREIGN KEY (`source_id`) REFERENCES `data_source` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=217 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事件来源引用表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `import_task`
--

DROP TABLE IF EXISTS `import_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `import_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `import_type` varchar(64) NOT NULL COMMENT 'RAIN_COOKIE/RAIN_JSON/USER_TEXT/USER_IMAGE',
  `task_status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `raw_doc_id` varchar(64) DEFAULT NULL,
  `result_summary` json DEFAULT NULL,
  `error_message` varchar(1024) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_import_user_time` (`user_id`,`created_at`),
  KEY `idx_import_status` (`task_status`),
  CONSTRAINT `fk_import_task_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=9614 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户导入任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `information_change_log`
--

DROP TABLE IF EXISTS `information_change_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `information_change_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `item_id` bigint NOT NULL COMMENT '关联 information_item.id',
  `old_content_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '变更前的 content_hash',
  `new_content_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '变更后的 content_hash',
  `changed_fields` json DEFAULT NULL COMMENT '变化字段列表，如 ["content","ai_status"]',
  `changed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更检测时间',
  PRIMARY KEY (`id`),
  KEY `idx_change_item` (`item_id`,`changed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='信息条目变更日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `information_item`
--

DROP TABLE IF EXISTS `information_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `information_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_id` bigint NOT NULL COMMENT '数据源ID',
  `source_name` varchar(128) NOT NULL COMMENT '数据源名称',
  `source_url` varchar(1024) NOT NULL COMMENT '列表页或栏目URL',
  `item_url` varchar(1024) NOT NULL COMMENT '原网页详情URL',
  `title` varchar(512) NOT NULL COMMENT '信息标题',
  `publish_time` datetime DEFAULT NULL COMMENT '页面发布时间，无法解析时为空',
  `fetched_at` datetime NOT NULL COMMENT '抓取时间',
  `detail_content` mediumtext NOT NULL COMMENT '系统提取正文',
  `content_hash` char(64) NOT NULL COMMENT '正文内容哈希',
  `item_status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/UPDATED/OFFLINE/FAILED',
  `parse_status` varchar(32) NOT NULL COMMENT 'DETAIL_SUCCESS/PARSE_FAILED/DETAIL_FAILED',
  `parse_error` varchar(1024) DEFAULT NULL COMMENT '解析失败原因',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `ai_status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/REVIEW/FAILED',
  `ai_event_type` varchar(32) DEFAULT NULL COMMENT '智能体识别的信息类型',
  `ai_summary` text COMMENT '智能精简摘要',
  `ai_card_json` json DEFAULT NULL COMMENT '智能体结构化信息卡片',
  `ai_confidence` decimal(5,4) DEFAULT NULL COMMENT '智能提取置信度',
  `ai_need_review` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否需要人工复核',
  `submitted_by` varchar(128) DEFAULT NULL COMMENT '提交用户名（用户导入时记录）',
  `submitted_by_user_id` bigint DEFAULT NULL COMMENT '提交用户ID（用户导入时记录）',
  `ai_error` varchar(1024) DEFAULT NULL COMMENT '智能提取失败原因',
  `ai_processed_at` datetime DEFAULT NULL COMMENT '智能提取完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_information_item_url_title` (`item_url`(512),`title`(191)),
  KEY `idx_information_item_time` (`publish_time`,`fetched_at`),
  KEY `idx_information_item_source` (`source_id`,`fetched_at`),
  KEY `idx_information_item_status` (`item_status`),
  KEY `idx_information_item_ai_status` (`ai_status`,`fetched_at`),
  CONSTRAINT `fk_information_item_source` FOREIGN KEY (`source_id`) REFERENCES `data_source` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=190 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='信息集中站信息条目表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(64) NOT NULL COMMENT '登录名',
  `phone` varchar(32) DEFAULT NULL COMMENT '手机号，需加密或脱敏展示',
  `email` varchar(255) DEFAULT NULL COMMENT '用于账号找回的邮箱',
  `password_hash` varchar(255) NOT NULL COMMENT '密码哈希',
  `role` varchar(32) NOT NULL DEFAULT 'STUDENT' COMMENT 'STUDENT/ADMIN/SUPER_ADMIN',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1正常 0禁用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  UNIQUE KEY `uk_user_email` (`email`),
  KEY `idx_user_role_status` (`role`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=9923 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_action_item`
--

DROP TABLE IF EXISTS `user_action_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_action_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `information_item_id` bigint NOT NULL,
  `title` varchar(255) NOT NULL,
  `due_at` datetime DEFAULT NULL,
  `original_url` varchar(1024) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'CONFIRMED',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_information_action` (`user_id`,`information_item_id`,`title`),
  KEY `idx_user_action_due` (`user_id`,`status`,`due_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_information_state`
--

DROP TABLE IF EXISTS `user_information_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_information_state` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `item_id` bigint NOT NULL,
  `first_seen_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `read_at` datetime DEFAULT NULL,
  `favorited_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_item_state` (`user_id`,`item_id`),
  KEY `idx_user_read_history` (`user_id`,`read_at`),
  KEY `idx_user_favorites` (`user_id`,`favorited_at`),
  KEY `fk_user_information_state_item` (`item_id`),
  CONSTRAINT `fk_user_information_state_item` FOREIGN KEY (`item_id`) REFERENCES `information_item` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_user_information_state_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户信息条目阅读与收藏状态表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_profile`
--

DROP TABLE IF EXISTS `user_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `college` varchar(128) DEFAULT NULL COMMENT '学院',
  `major` varchar(128) DEFAULT NULL COMMENT '专业',
  `grade` varchar(32) DEFAULT NULL COMMENT '年级',
  `class_name` varchar(128) DEFAULT NULL COMMENT '班级',
  `interest_tags` json DEFAULT NULL COMMENT '兴趣标签，如讲座/竞赛/就业',
  `course_codes` json DEFAULT NULL COMMENT '课程标识列表',
  `sensitivity` double DEFAULT '0.5' COMMENT '用户画像敏感度',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_profile_user` (`user_id`),
  KEY `idx_profile_college_major_grade` (`college`,`major`,`grade`),
  CONSTRAINT `fk_user_profile_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户画像表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_reminder`
--

DROP TABLE IF EXISTS `user_reminder`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_reminder` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action_item_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `remind_at` datetime NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `sent_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_action_remind_at` (`action_item_id`,`remind_at`),
  KEY `idx_user_reminder_pending` (`user_id`,`status`,`remind_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_source_subscription`
--

DROP TABLE IF EXISTS `user_source_subscription`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_source_subscription` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `source_id` bigint NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '1启用 0禁用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_source_sub` (`user_id`,`source_id`),
  KEY `idx_user_sub_user` (`user_id`,`enabled`),
  KEY `fk_user_source_subscription_source` (`source_id`),
  CONSTRAINT `fk_user_source_subscription_source` FOREIGN KEY (`source_id`) REFERENCES `data_source` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_source_subscription_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=43 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户数据源订阅关系表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `web_crawl_item`
--

DROP TABLE IF EXISTS `web_crawl_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `web_crawl_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL COMMENT '采集任务ID',
  `source_id` bigint NOT NULL COMMENT '公开网页数据源ID',
  `source_name` varchar(128) NOT NULL COMMENT '数据源名称',
  `source_url` varchar(1024) NOT NULL COMMENT '列表页URL',
  `item_url` varchar(1024) NOT NULL COMMENT '详情页URL',
  `title` varchar(512) NOT NULL COMMENT '列表页解析标题',
  `detail_title` varchar(512) DEFAULT NULL COMMENT '详情页标题',
  `date_text` varchar(64) DEFAULT NULL COMMENT '列表页日期文本',
  `summary` text COMMENT '列表页摘要',
  `detail_content` mediumtext COMMENT '详情页正文文本',
  `content_hash` char(64) NOT NULL COMMENT '标题+URL+日期摘要hash',
  `parser_version` varchar(64) DEFAULT NULL COMMENT '解析器版本',
  `detail_http_status` int DEFAULT NULL COMMENT '详情页HTTP状态',
  `detail_fetched_at` datetime DEFAULT NULL COMMENT '详情页抓取时间',
  `detail_content_hash` char(64) DEFAULT NULL COMMENT '详情正文hash',
  `parse_status` varchar(32) NOT NULL DEFAULT 'LIST_ONLY' COMMENT 'LIST_ONLY/DETAIL_SUCCESS/PARSE_FAILED/DETAIL_FAILED',
  `parse_error` varchar(1024) DEFAULT NULL COMMENT '详情解析失败原因',
  `fetched_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `information_item_id` bigint DEFAULT NULL COMMENT '关联的信息条目ID（AI处理完成后回填）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_web_crawl_item_hash` (`content_hash`),
  KEY `idx_web_crawl_item_source_time` (`source_id`,`fetched_at`),
  KEY `idx_web_crawl_item_task` (`task_id`),
  KEY `idx_web_crawl_item_info` (`information_item_id`),
  CONSTRAINT `fk_web_crawl_item_info` FOREIGN KEY (`information_item_id`) REFERENCES `information_item` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_web_crawl_item_source` FOREIGN KEY (`source_id`) REFERENCES `data_source` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_web_crawl_item_task` FOREIGN KEY (`task_id`) REFERENCES `crawl_task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=311 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公开网页列表采集结果表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Enterprise closure tables
--

DROP TABLE IF EXISTS `data_source_version`;
CREATE TABLE `data_source_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_id` bigint NOT NULL,
  `version_no` int NOT NULL,
  `action` varchar(32) NOT NULL,
  `snapshot` json NOT NULL,
  `operator_id` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_version` (`source_id`,`version_no`),
  KEY `idx_source_version_created` (`source_id`,`created_at`),
  CONSTRAINT `fk_source_version_source` FOREIGN KEY (`source_id`) REFERENCES `data_source` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_source_version_operator` FOREIGN KEY (`operator_id`) REFERENCES `user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据源配置版本历史';

DROP TABLE IF EXISTS `user_consent_record`;
CREATE TABLE `user_consent_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `consent_type` varchar(64) NOT NULL,
  `policy_version` varchar(32) NOT NULL,
  `granted` tinyint NOT NULL,
  `source` varchar(32) NOT NULL DEFAULT 'APP',
  `occurred_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_consent_user_type` (`user_id`,`consent_type`,`occurred_at`),
  CONSTRAINT `fk_consent_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户授权与撤回记录';

DROP TABLE IF EXISTS `user_device`;
CREATE TABLE `user_device` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `device_id` varchar(128) NOT NULL,
  `platform` varchar(32) NOT NULL,
  `push_token` varchar(512) DEFAULT NULL,
  `enabled` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_device` (`user_id`,`device_id`),
  KEY `idx_device_delivery` (`enabled`,`platform`),
  CONSTRAINT `fk_device_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户通知设备';

DROP TABLE IF EXISTS `notification_delivery`;
CREATE TABLE `notification_delivery` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reminder_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `device_id` bigint DEFAULT NULL,
  `channel` varchar(32) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `attempt_count` int NOT NULL DEFAULT '0',
  `next_attempt_at` datetime DEFAULT NULL,
  `last_error` varchar(1000) DEFAULT NULL,
  `dedup_key` varchar(191) NOT NULL,
  `provider_message_id` varchar(255) DEFAULT NULL,
  `sent_at` datetime DEFAULT NULL,
  `withdrawn_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_delivery_dedup` (`dedup_key`),
  KEY `idx_delivery_retry` (`status`,`next_attempt_at`),
  KEY `idx_delivery_user` (`user_id`,`created_at`),
  CONSTRAINT `fk_delivery_reminder` FOREIGN KEY (`reminder_id`) REFERENCES `user_reminder` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_delivery_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_delivery_device` FOREIGN KEY (`device_id`) REFERENCES `user_device` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知投递、重试与撤回记录';

--
-- Dumping routines for database 'campusmind'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-14 13:29:40

mysqldump: [Warning] Using a password on the command line interface can be insecure.
-- MySQL dump 10.13  Distrib 9.1.0, for Win64 (x86_64)
--
-- Host: localhost    Database: campusmind
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
mysqldump: Error: 'Access denied; you need (at least one of) the PROCESS privilege(s) for this operation' when trying to dump tablespaces

--
-- Table structure for table `campus_event`
--

DROP TABLE IF EXISTS `campus_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `campus_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL COMMENT '浜嬩欢鏍囬',
  `summary` text COMMENT 'AI鎽樿鎴栦汉宸ユ憳瑕?,
  `event_type` varchar(64) NOT NULL COMMENT 'NOTICE/COURSE/EXAM/HOMEWORK/ACTIVITY/LECTURE/COMPETITION/SERVICE',
  `source_type` varchar(64) NOT NULL COMMENT 'PUBLIC_WEB/RAIN_CLASSROOM/USER_TEXT/USER_IMAGE/USER_FILE',
  `visibility` varchar(16) NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC/PRIVATE锛圥RIVATE=鐢ㄦ埛绉佹湁浜嬩欢锛屼粎owner鍙锛?,
  `owner_user_id` bigint DEFAULT NULL COMMENT 'PRIVATE浜嬩欢鎵€灞炵敤鎴?,
  `status` varchar(32) NOT NULL DEFAULT 'AI_PUBLISHED' COMMENT 'AI_PUBLISHED/REVIEWED/CORRECTED/REJECTED/OFFLINE',
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `location` varchar(255) DEFAULT NULL,
  `organizer` varchar(255) DEFAULT NULL COMMENT '鍙戝竷鍗曚綅鎴栬绋嬫暀甯?,
  `target_scope` json DEFAULT NULL COMMENT '閫傜敤鑼冨洿锛氬闄?涓撲笟/骞寸骇/璇剧▼',
  `tags` json DEFAULT NULL COMMENT '鏍囩',
  `dedup_key` char(64) DEFAULT NULL COMMENT '鏍囬+鏃堕棿+鏉ユ簮鐢熸垚鐨凷HA256',
  `vector_doc_id` varchar(128) DEFAULT NULL COMMENT '鍚戦噺搴撴枃妗D',
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
) ENGINE=InnoDB AUTO_INCREMENT=9193 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鏍″洯浜嬩欢涓昏〃';
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
) ENGINE=InnoDB AUTO_INCREMENT=9998 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='閲囬泦浠诲姟琛?;
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
  `crawl_interval_seconds` int NOT NULL DEFAULT '5' COMMENT '蹇呴』澶т簬2绉?,
  `parser_type` varchar(64) NOT NULL COMMENT 'WEBMAGIC/PLAYWRIGHT/RSS/SITEMAP',
  `selector_config` json DEFAULT NULL COMMENT 'CSS/XPath/姝ｆ枃鎶藉彇瑙勫垯',
  `enabled` tinyint NOT NULL DEFAULT '1',
  `last_crawled_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_base_url` (`base_url`(255)),
  KEY `idx_source_enabled` (`enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=9429 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鍏紑缃戦〉鏁版嵁婧愯〃';
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
) ENGINE=InnoDB AUTO_INCREMENT=458 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='浜嬩欢瀹℃牳鏃ュ織琛?;
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
  `source_id` bigint DEFAULT NULL COMMENT '鍏紑缃戦〉鏁版嵁婧怚D',
  `raw_doc_id` varchar(64) NOT NULL COMMENT 'MongoDB鍘熷鏂囨。ID',
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
) ENGINE=InnoDB AUTO_INCREMENT=216 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='浜嬩欢鏉ユ簮寮曠敤琛?;
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
) ENGINE=InnoDB AUTO_INCREMENT=9613 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鐢ㄦ埛瀵煎叆浠诲姟琛?;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `information_item`
--

DROP TABLE IF EXISTS `information_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `information_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_id` bigint NOT NULL COMMENT '鏁版嵁婧怚D',
  `source_name` varchar(128) NOT NULL COMMENT '鏁版嵁婧愬悕绉?,
  `source_url` varchar(1024) NOT NULL COMMENT '鍒楄〃椤垫垨鏍忕洰URL',
  `item_url` varchar(1024) NOT NULL COMMENT '鍘熺綉椤佃鎯匲RL',
  `title` varchar(512) NOT NULL COMMENT '淇℃伅鏍囬',
  `publish_time` datetime DEFAULT NULL COMMENT '椤甸潰鍙戝竷鏃堕棿锛屾棤娉曡В鏋愭椂涓虹┖',
  `fetched_at` datetime NOT NULL COMMENT '鎶撳彇鏃堕棿',
  `detail_content` mediumtext NOT NULL COMMENT '绯荤粺鎻愬彇姝ｆ枃',
  `content_hash` char(64) NOT NULL COMMENT '姝ｆ枃鍐呭鍝堝笇',
  `item_status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/UPDATED/OFFLINE/FAILED',
  `parse_status` varchar(32) NOT NULL COMMENT 'DETAIL_SUCCESS/PARSE_FAILED/DETAIL_FAILED',
  `parse_error` varchar(1024) DEFAULT NULL COMMENT '瑙ｆ瀽澶辫触鍘熷洜',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `ai_status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/REVIEW/FAILED',
  `ai_event_type` varchar(32) DEFAULT NULL COMMENT '鏅鸿兘浣撹瘑鍒殑淇℃伅绫诲瀷',
  `ai_summary` text COMMENT '鏅鸿兘绮剧畝鎽樿',
  `ai_card_json` json DEFAULT NULL COMMENT '鏅鸿兘浣撶粨鏋勫寲淇℃伅鍗＄墖',
  `ai_confidence` decimal(5,4) DEFAULT NULL COMMENT '鏅鸿兘鎻愬彇缃俊搴?,
  `ai_need_review` tinyint(1) NOT NULL DEFAULT '0' COMMENT '鏄惁闇€瑕佷汉宸ュ鏍?,
  `ai_error` varchar(1024) DEFAULT NULL COMMENT '鏅鸿兘鎻愬彇澶辫触鍘熷洜',
  `ai_processed_at` datetime DEFAULT NULL COMMENT '鏅鸿兘鎻愬彇瀹屾垚鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_information_item_url_title` (`item_url`(512),`title`(191)),
  KEY `idx_information_item_time` (`publish_time`,`fetched_at`),
  KEY `idx_information_item_source` (`source_id`,`fetched_at`),
  KEY `idx_information_item_status` (`item_status`),
  KEY `idx_information_item_ai_status` (`ai_status`,`fetched_at`),
  CONSTRAINT `fk_information_item_source` FOREIGN KEY (`source_id`) REFERENCES `data_source` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=188 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='淇℃伅闆嗕腑绔欎俊鎭潯鐩〃';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '鐢ㄦ埛ID',
  `username` varchar(64) NOT NULL COMMENT '鐧诲綍鍚?,
  `phone` varchar(32) DEFAULT NULL COMMENT '鎵嬫満鍙凤紝闇€鍔犲瘑鎴栬劚鏁忓睍绀?,
  `password_hash` varchar(255) NOT NULL COMMENT '瀵嗙爜鍝堝笇',
  `role` varchar(32) NOT NULL DEFAULT 'STUDENT' COMMENT 'STUDENT/ADMIN/SUPER_ADMIN',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1姝ｅ父 0绂佺敤',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  KEY `idx_user_role_status` (`role`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=9905 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鐢ㄦ埛琛?;
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
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鐢ㄦ埛淇℃伅鏉＄洰闃呰涓庢敹钘忕姸鎬佽〃';
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
  `college` varchar(128) DEFAULT NULL COMMENT '瀛﹂櫌',
  `major` varchar(128) DEFAULT NULL COMMENT '涓撲笟',
  `grade` varchar(32) DEFAULT NULL COMMENT '骞寸骇',
  `class_name` varchar(128) DEFAULT NULL COMMENT '鐝骇',
  `interest_tags` json DEFAULT NULL COMMENT '鍏磋叮鏍囩锛屽璁插骇/绔炶禌/灏变笟',
  `course_codes` json DEFAULT NULL COMMENT '璇剧▼鏍囪瘑鍒楄〃',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_profile_user` (`user_id`),
  KEY `idx_profile_college_major_grade` (`college`,`major`,`grade`),
  CONSTRAINT `fk_user_profile_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鐢ㄦ埛鐢诲儚琛?;
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
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '1鍚敤 0绂佺敤',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_source_sub` (`user_id`,`source_id`),
  KEY `idx_user_sub_user` (`user_id`,`enabled`),
  KEY `fk_user_source_subscription_source` (`source_id`),
  CONSTRAINT `fk_user_source_subscription_source` FOREIGN KEY (`source_id`) REFERENCES `data_source` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_source_subscription_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鐢ㄦ埛鏁版嵁婧愯闃呭叧绯昏〃';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `web_crawl_item`
--

DROP TABLE IF EXISTS `web_crawl_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `web_crawl_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL COMMENT '閲囬泦浠诲姟ID',
  `source_id` bigint NOT NULL COMMENT '鍏紑缃戦〉鏁版嵁婧怚D',
  `source_name` varchar(128) NOT NULL COMMENT '鏁版嵁婧愬悕绉?,
  `source_url` varchar(1024) NOT NULL COMMENT '鍒楄〃椤礥RL',
  `item_url` varchar(1024) NOT NULL COMMENT '璇︽儏椤礥RL',
  `title` varchar(512) NOT NULL COMMENT '鍒楄〃椤佃В鏋愭爣棰?,
  `detail_title` varchar(512) DEFAULT NULL COMMENT '璇︽儏椤垫爣棰?,
  `date_text` varchar(64) DEFAULT NULL COMMENT '鍒楄〃椤垫棩鏈熸枃鏈?,
  `summary` text COMMENT '鍒楄〃椤垫憳瑕?,
  `detail_content` mediumtext COMMENT '璇︽儏椤垫鏂囨枃鏈?,
  `content_hash` char(64) NOT NULL COMMENT '鏍囬+URL+鏃ユ湡鎽樿hash',
  `parser_version` varchar(64) DEFAULT NULL COMMENT '瑙ｆ瀽鍣ㄧ増鏈?,
  `detail_http_status` int DEFAULT NULL COMMENT '璇︽儏椤礖TTP鐘舵€?,
  `detail_fetched_at` datetime DEFAULT NULL COMMENT '璇︽儏椤垫姄鍙栨椂闂?,
  `detail_content_hash` char(64) DEFAULT NULL COMMENT '璇︽儏姝ｆ枃hash',
  `parse_status` varchar(32) NOT NULL DEFAULT 'LIST_ONLY' COMMENT 'LIST_ONLY/DETAIL_SUCCESS/PARSE_FAILED/DETAIL_FAILED',
  `parse_error` varchar(1024) DEFAULT NULL COMMENT '璇︽儏瑙ｆ瀽澶辫触鍘熷洜',
  `fetched_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `information_item_id` bigint DEFAULT NULL COMMENT '鍏宠仈鐨勪俊鎭潯鐩甀D锛圓I澶勭悊瀹屾垚鍚庡洖濉級',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_web_crawl_item_hash` (`content_hash`),
  KEY `idx_web_crawl_item_source_time` (`source_id`,`fetched_at`),
  KEY `idx_web_crawl_item_task` (`task_id`),
  KEY `idx_web_crawl_item_info` (`information_item_id`),
  CONSTRAINT `fk_web_crawl_item_info` FOREIGN KEY (`information_item_id`) REFERENCES `information_item` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_web_crawl_item_source` FOREIGN KEY (`source_id`) REFERENCES `data_source` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_web_crawl_item_task` FOREIGN KEY (`task_id`) REFERENCES `crawl_task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=310 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鍏紑缃戦〉鍒楄〃閲囬泦缁撴灉琛?;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-12 21:22:12

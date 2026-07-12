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
-- Current Database: `campusmind`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `campusmind` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `campusmind`;

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
  `source_type` varchar(64) NOT NULL COMMENT 'PUBLIC_WEB/RAIN_CLASSROOM/USER_TEXT/USER_IMAGE',
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
  KEY `idx_event_source_type` (`source_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='校园事件主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `campus_event`
--

LOCK TABLES `campus_event` WRITE;
/*!40000 ALTER TABLE `campus_event` DISABLE KEYS */;
/*!40000 ALTER TABLE `campus_event` ENABLE KEYS */;
UNLOCK TABLES;

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
  CONSTRAINT `fk_crawl_task_source` FOREIGN KEY (`source_id`) REFERENCES `data_source` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采集任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crawl_task`
--

LOCK TABLES `crawl_task` WRITE;
/*!40000 ALTER TABLE `crawl_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `crawl_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `data_source`
--

DROP TABLE IF EXISTS `data_source`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `data_source` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `source_type` varchar(64) NOT NULL COMMENT 'PUBLIC_WEB',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公开网页数据源表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `data_source`
--

LOCK TABLES `data_source` WRITE;
/*!40000 ALTER TABLE `data_source` DISABLE KEYS */;
/*!40000 ALTER TABLE `data_source` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `event_audit_log`
--

DROP TABLE IF EXISTS `event_audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `event_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_id` bigint NOT NULL,
  `operator_id` bigint NOT NULL,
  `action` varchar(64) NOT NULL COMMENT 'REVIEW/CORRECT/MERGE/REJECT/OFFLINE',
  `before_snapshot` json DEFAULT NULL,
  `after_snapshot` json DEFAULT NULL,
  `comment` varchar(512) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_event_time` (`event_id`,`created_at`),
  KEY `idx_audit_operator` (`operator_id`),
  CONSTRAINT `fk_event_audit_log_event` FOREIGN KEY (`event_id`) REFERENCES `campus_event` (`id`),
  CONSTRAINT `fk_event_audit_log_operator` FOREIGN KEY (`operator_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事件审核日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `event_audit_log`
--

LOCK TABLES `event_audit_log` WRITE;
/*!40000 ALTER TABLE `event_audit_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `event_audit_log` ENABLE KEYS */;
UNLOCK TABLES;

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
  CONSTRAINT `fk_event_source_ref_event` FOREIGN KEY (`event_id`) REFERENCES `campus_event` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事件来源引用表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `event_source_ref`
--

LOCK TABLES `event_source_ref` WRITE;
/*!40000 ALTER TABLE `event_source_ref` DISABLE KEYS */;
/*!40000 ALTER TABLE `event_source_ref` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `import_task`
--

DROP TABLE IF EXISTS `import_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `import_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
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
  CONSTRAINT `fk_import_task_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户导入任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `import_task`
--

LOCK TABLES `import_task` WRITE;
/*!40000 ALTER TABLE `import_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `import_task` ENABLE KEYS */;
UNLOCK TABLES;

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
  `password_hash` varchar(255) NOT NULL COMMENT '密码哈希',
  `role` varchar(32) NOT NULL DEFAULT 'STUDENT' COMMENT 'STUDENT/ADMIN/SUPER_ADMIN',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1正常 0禁用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  KEY `idx_user_role_status` (`role`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user`
--

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;

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
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_profile_user` (`user_id`),
  KEY `idx_profile_college_major_grade` (`college`,`major`,`grade`),
  CONSTRAINT `fk_user_profile_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户画像表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_profile`
--

LOCK TABLES `user_profile` WRITE;
/*!40000 ALTER TABLE `user_profile` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_profile` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'campusmind'
--

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

-- Dump completed on 2026-07-07 17:23:10

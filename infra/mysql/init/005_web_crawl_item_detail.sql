SET NAMES utf8mb4;

USE campusmind;

DELIMITER //

CREATE PROCEDURE add_web_crawl_item_column_if_missing(
  IN column_name_value VARCHAR(64),
  IN column_definition_value VARCHAR(2048)
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'web_crawl_item'
      AND COLUMN_NAME = column_name_value
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE web_crawl_item ADD COLUMN ', column_definition_value);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END//

DELIMITER ;

CALL add_web_crawl_item_column_if_missing(
  'detail_title',
  'detail_title VARCHAR(512) NULL COMMENT ''详情页标题'' AFTER title'
);
CALL add_web_crawl_item_column_if_missing(
  'detail_content',
  'detail_content MEDIUMTEXT NULL COMMENT ''详情页正文文本'' AFTER summary'
);
CALL add_web_crawl_item_column_if_missing(
  'detail_http_status',
  'detail_http_status INT NULL COMMENT ''详情页HTTP状态'' AFTER parser_version'
);
CALL add_web_crawl_item_column_if_missing(
  'detail_fetched_at',
  'detail_fetched_at DATETIME NULL COMMENT ''详情页抓取时间'' AFTER detail_http_status'
);
CALL add_web_crawl_item_column_if_missing(
  'detail_content_hash',
  'detail_content_hash CHAR(64) NULL COMMENT ''详情正文hash'' AFTER detail_fetched_at'
);
CALL add_web_crawl_item_column_if_missing(
  'parse_status',
  'parse_status VARCHAR(32) NOT NULL DEFAULT ''LIST_ONLY'' COMMENT ''LIST_ONLY/DETAIL_SUCCESS/PARSE_FAILED/DETAIL_FAILED'' AFTER detail_content_hash'
);
CALL add_web_crawl_item_column_if_missing(
  'parse_error',
  'parse_error VARCHAR(1024) NULL COMMENT ''详情解析失败原因'' AFTER parse_status'
);

DROP PROCEDURE add_web_crawl_item_column_if_missing;

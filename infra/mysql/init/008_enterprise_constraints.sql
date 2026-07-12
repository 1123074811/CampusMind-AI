SET NAMES utf8mb4;

USE campusmind;

DELIMITER //

CREATE PROCEDURE add_init_fk_if_missing(
  IN constraint_name_value VARCHAR(64),
  IN ddl_value VARCHAR(2048)
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND CONSTRAINT_NAME = constraint_name_value
  ) THEN
    SET @ddl = ddl_value;
    PREPARE statement_to_run FROM @ddl;
    EXECUTE statement_to_run;
    DEALLOCATE PREPARE statement_to_run;
  END IF;
END//

DELIMITER ;

CALL add_init_fk_if_missing('fk_user_profile_user', 'ALTER TABLE user_profile ADD CONSTRAINT fk_user_profile_user FOREIGN KEY (user_id) REFERENCES user(id)');
CALL add_init_fk_if_missing('fk_campus_event_owner', 'ALTER TABLE campus_event ADD CONSTRAINT fk_campus_event_owner FOREIGN KEY (owner_user_id) REFERENCES user(id) ON DELETE CASCADE');
CALL add_init_fk_if_missing('fk_event_source_ref_event', 'ALTER TABLE event_source_ref ADD CONSTRAINT fk_event_source_ref_event FOREIGN KEY (event_id) REFERENCES campus_event(id)');
CALL add_init_fk_if_missing('fk_event_source_ref_source', 'ALTER TABLE event_source_ref ADD CONSTRAINT fk_event_source_ref_source FOREIGN KEY (source_id) REFERENCES data_source(id) ON DELETE SET NULL');
CALL add_init_fk_if_missing('fk_event_audit_log_event', 'ALTER TABLE event_audit_log ADD CONSTRAINT fk_event_audit_log_event FOREIGN KEY (event_id) REFERENCES campus_event(id) ON DELETE SET NULL');
CALL add_init_fk_if_missing('fk_event_audit_log_operator', 'ALTER TABLE event_audit_log ADD CONSTRAINT fk_event_audit_log_operator FOREIGN KEY (operator_id) REFERENCES user(id) ON DELETE SET NULL');
CALL add_init_fk_if_missing('fk_crawl_task_source', 'ALTER TABLE crawl_task ADD CONSTRAINT fk_crawl_task_source FOREIGN KEY (source_id) REFERENCES data_source(id) ON DELETE CASCADE');
CALL add_init_fk_if_missing('fk_import_task_user', 'ALTER TABLE import_task ADD CONSTRAINT fk_import_task_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE SET NULL');
CALL add_init_fk_if_missing('fk_web_crawl_item_task', 'ALTER TABLE web_crawl_item ADD CONSTRAINT fk_web_crawl_item_task FOREIGN KEY (task_id) REFERENCES crawl_task(id) ON DELETE CASCADE');
CALL add_init_fk_if_missing('fk_web_crawl_item_source', 'ALTER TABLE web_crawl_item ADD CONSTRAINT fk_web_crawl_item_source FOREIGN KEY (source_id) REFERENCES data_source(id) ON DELETE RESTRICT');
CALL add_init_fk_if_missing('fk_information_item_source', 'ALTER TABLE information_item ADD CONSTRAINT fk_information_item_source FOREIGN KEY (source_id) REFERENCES data_source(id) ON DELETE RESTRICT');
CALL add_init_fk_if_missing('fk_user_information_state_user', 'ALTER TABLE user_information_state ADD CONSTRAINT fk_user_information_state_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE');
CALL add_init_fk_if_missing('fk_user_information_state_item', 'ALTER TABLE user_information_state ADD CONSTRAINT fk_user_information_state_item FOREIGN KEY (item_id) REFERENCES information_item(id) ON DELETE RESTRICT');
CALL add_init_fk_if_missing('fk_user_source_subscription_user', 'ALTER TABLE user_source_subscription ADD CONSTRAINT fk_user_source_subscription_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE');
CALL add_init_fk_if_missing('fk_user_source_subscription_source', 'ALTER TABLE user_source_subscription ADD CONSTRAINT fk_user_source_subscription_source FOREIGN KEY (source_id) REFERENCES data_source(id) ON DELETE CASCADE');
CALL add_init_fk_if_missing('fk_web_crawl_item_info', 'ALTER TABLE web_crawl_item ADD CONSTRAINT fk_web_crawl_item_info FOREIGN KEY (information_item_id) REFERENCES information_item(id) ON DELETE SET NULL');

DROP PROCEDURE add_init_fk_if_missing;

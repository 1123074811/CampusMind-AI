ALTER TABLE campus_event
  ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC/PRIVATE' AFTER source_type,
  ADD COLUMN owner_user_id BIGINT NULL COMMENT 'PRIVATE事件所属用户' AFTER visibility,
  ADD KEY idx_event_owner_visibility (owner_user_id, visibility, start_time);

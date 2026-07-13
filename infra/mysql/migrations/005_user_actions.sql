CREATE TABLE IF NOT EXISTS user_action_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  information_item_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  due_at DATETIME NULL,
  original_url VARCHAR(1024) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_information_action (user_id, information_item_id, title),
  KEY idx_user_action_due (user_id, status, due_at)
);

CREATE TABLE IF NOT EXISTS user_reminder (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  action_item_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  remind_at DATETIME NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  sent_at DATETIME NULL,
  UNIQUE KEY uk_action_remind_at (action_item_id, remind_at),
  KEY idx_user_reminder_pending (user_id, status, remind_at)
);

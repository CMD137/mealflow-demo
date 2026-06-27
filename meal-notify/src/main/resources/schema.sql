CREATE TABLE IF NOT EXISTS notify_message (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  biz_type VARCHAR(64) NOT NULL,
  content VARCHAR(512) NOT NULL,
  create_time TIMESTAMP NOT NULL,
  INDEX idx_notify_message_user_time (user_id, create_time)
);

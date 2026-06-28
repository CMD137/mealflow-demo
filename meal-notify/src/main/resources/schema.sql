CREATE TABLE IF NOT EXISTS notify_message (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  biz_type VARCHAR(64) NOT NULL,
  content VARCHAR(512) NOT NULL,
  create_time TIMESTAMP NOT NULL,
  INDEX idx_notify_message_user_time (user_id, create_time)
);

CREATE TABLE IF NOT EXISTS consumer_record (
  id BIGINT PRIMARY KEY,
  event_key VARCHAR(256) NOT NULL,
  consumer_group VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  last_error VARCHAR(512) NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  UNIQUE KEY uk_notify_consumer_event_group (event_key, consumer_group),
  INDEX idx_notify_consumer_status (status)
);

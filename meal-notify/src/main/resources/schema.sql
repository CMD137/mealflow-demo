CREATE TABLE IF NOT EXISTS notify_message (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  biz_type VARCHAR(64) NOT NULL,
  content VARCHAR(512) NOT NULL,
  create_time TIMESTAMP NOT NULL,
  INDEX idx_notify_message_user_time (user_id, create_time)
);

CREATE TABLE IF NOT EXISTS notify_template (
  template_code VARCHAR(64) PRIMARY KEY,
  biz_type VARCHAR(64) NOT NULL,
  content_template VARCHAR(512) NOT NULL,
  channels VARCHAR(128) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS notify_delivery (
  id BIGINT PRIMARY KEY,
  message_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  channel VARCHAR(32) NOT NULL,
  target VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  content VARCHAR(512) NOT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  INDEX idx_notify_delivery_message_id (message_id),
  INDEX idx_notify_delivery_user_id (user_id),
  INDEX idx_notify_delivery_status (status)
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

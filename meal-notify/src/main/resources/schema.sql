CREATE TABLE IF NOT EXISTS notify_message (
  id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, biz_type VARCHAR(64) NOT NULL,
  recipient_type VARCHAR(16) NOT NULL DEFAULT 'USER', recipient_id BIGINT NULL,
  content VARCHAR(512) NOT NULL, create_time TIMESTAMP NOT NULL,
  INDEX idx_notify_message_user_time (user_id, create_time),
  INDEX idx_notify_message_recipient_time (recipient_type, recipient_id, create_time)
);
ALTER TABLE notify_message ADD COLUMN IF NOT EXISTS recipient_type VARCHAR(16) NOT NULL DEFAULT 'USER';
ALTER TABLE notify_message ADD COLUMN IF NOT EXISTS recipient_id BIGINT NULL;
UPDATE notify_message SET recipient_id = user_id WHERE recipient_id IS NULL;
CREATE TABLE IF NOT EXISTS notify_template (
  template_code VARCHAR(64) PRIMARY KEY, biz_type VARCHAR(64) NOT NULL,
  content_template VARCHAR(512) NOT NULL, channels VARCHAR(128) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE, create_time TIMESTAMP NOT NULL, update_time TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS notify_delivery (
  id BIGINT PRIMARY KEY, message_id BIGINT NOT NULL, user_id BIGINT NOT NULL, channel VARCHAR(32) NOT NULL,
  target VARCHAR(128) NOT NULL, status VARCHAR(32) NOT NULL, content VARCHAR(512) NOT NULL,
  create_time TIMESTAMP NOT NULL, update_time TIMESTAMP NOT NULL,
  INDEX idx_notify_delivery_message_id (message_id), INDEX idx_notify_delivery_user_id (user_id),
  INDEX idx_notify_delivery_status (status)
);
CREATE TABLE IF NOT EXISTS consumer_record (
  id BIGINT PRIMARY KEY, event_key VARCHAR(256) NOT NULL, consumer_group VARCHAR(128) NOT NULL,
  event_type VARCHAR(128) NULL, payload_json TEXT NULL, status VARCHAR(32) NOT NULL,
  last_error VARCHAR(512) NULL, create_time TIMESTAMP NOT NULL, update_time TIMESTAMP NOT NULL,
  UNIQUE KEY uk_notify_consumer_event_group (event_key, consumer_group), INDEX idx_notify_consumer_status (status)
);

CREATE TABLE IF NOT EXISTS business_sequence (namespace VARCHAR(64) PRIMARY KEY, next_value BIGINT NOT NULL);
INSERT INTO business_sequence (namespace, next_value) VALUES ('notifyMessage', 10000)
ON DUPLICATE KEY UPDATE next_value = next_value;
INSERT INTO business_sequence (namespace, next_value) VALUES ('notifyDelivery', 10000)
ON DUPLICATE KEY UPDATE next_value = next_value;
INSERT INTO business_sequence (namespace, next_value) VALUES ('notifyConsumerRecord', 10000)
ON DUPLICATE KEY UPDATE next_value = next_value;

UPDATE business_sequence SET next_value = (SELECT COALESCE(MAX(id), 10000) FROM notify_message)
WHERE namespace = 'notifyMessage' AND next_value < (SELECT COALESCE(MAX(id), 10000) FROM notify_message);
UPDATE business_sequence SET next_value = (SELECT COALESCE(MAX(id), 10000) FROM notify_delivery)
WHERE namespace = 'notifyDelivery' AND next_value < (SELECT COALESCE(MAX(id), 10000) FROM notify_delivery);
UPDATE business_sequence SET next_value = (SELECT COALESCE(MAX(id), 10000) FROM consumer_record)
WHERE namespace = 'notifyConsumerRecord' AND next_value < (SELECT COALESCE(MAX(id), 10000) FROM consumer_record);

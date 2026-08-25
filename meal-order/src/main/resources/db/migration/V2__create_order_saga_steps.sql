INSERT INTO business_sequence (namespace, next_value) VALUES ('order_saga_step', 10000)
ON DUPLICATE KEY UPDATE next_value = next_value;

INSERT INTO business_sequence (namespace, next_value) VALUES ('order_consumer_record', 10000)
ON DUPLICATE KEY UPDATE next_value = next_value;

UPDATE business_sequence
SET next_value = (SELECT COALESCE(MAX(id), 10000) FROM order_consumer_record)
WHERE namespace = 'order_consumer_record'
  AND next_value < (SELECT COALESCE(MAX(id), 10000) FROM order_consumer_record);

CREATE TABLE IF NOT EXISTS order_saga_step (
  id BIGINT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  pay_order_id BIGINT NOT NULL,
  saga_type VARCHAR(64) NOT NULL,
  step_name VARCHAR(64) NOT NULL,
  step_order INT NOT NULL,
  reason VARCHAR(256) NULL,
  status VARCHAR(32) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_time TIMESTAMP NULL,
  lease_until TIMESTAMP NULL,
  last_error VARCHAR(512) NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  UNIQUE KEY uk_order_saga_step (order_id, saga_type, step_name),
  INDEX idx_order_saga_dispatch (status, next_retry_time),
  INDEX idx_order_saga_order (order_id, saga_type, step_order)
);

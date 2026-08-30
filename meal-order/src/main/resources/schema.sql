CREATE TABLE IF NOT EXISTS business_sequence (
  namespace VARCHAR(64) PRIMARY KEY,
  next_value BIGINT NOT NULL
);

INSERT INTO business_sequence (namespace, next_value) VALUES ('order', 10000)
ON DUPLICATE KEY UPDATE next_value = next_value;

INSERT INTO business_sequence (namespace, next_value) VALUES ('order_local_event', 10000)
ON DUPLICATE KEY UPDATE next_value = next_value;

CREATE TABLE IF NOT EXISTS idempotency_record (
  subject VARCHAR(128) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  lease_expire_time TIMESTAMP NULL,
  response_json TEXT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  PRIMARY KEY (subject, idempotency_key),
  INDEX idx_idempotency_expire (lease_expire_time)
);

CREATE TABLE IF NOT EXISTS customer_order (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  queue_ticket_id BIGINT NULL,
  capacity_token_id BIGINT NOT NULL,
  pay_order_id BIGINT NOT NULL,
  reservation_ids_json TEXT NOT NULL,
  voucher_lock_id BIGINT NULL,
  items_json TEXT NOT NULL,
  amount_cent INT NOT NULL,
  contact_name VARCHAR(64) NOT NULL,
  contact_phone VARCHAR(32) NOT NULL,
  delivery_address VARCHAR(255) NOT NULL,
  payment_expire_time TIMESTAMP NOT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  INDEX idx_customer_order_user_id (user_id),
  INDEX idx_customer_order_status (status),
  INDEX idx_customer_order_payment_expire (status, payment_expire_time),
  INDEX idx_customer_order_merchant_status_time (merchant_id, status, create_time),
  INDEX idx_customer_order_queue_ticket_id (queue_ticket_id),
  UNIQUE KEY uk_customer_order_queue_ticket_id (queue_ticket_id)
);

CREATE TABLE IF NOT EXISTS order_local_event (
  id BIGINT PRIMARY KEY,
  event_key VARCHAR(256) NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  event_version INT NOT NULL,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id BIGINT NOT NULL,
  payload_json TEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  last_error VARCHAR(512) NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  UNIQUE KEY uk_order_local_event_key (event_key),
  INDEX idx_order_local_event_status_id (status, id),
  INDEX idx_order_local_event_aggregate (aggregate_type, aggregate_id)
);

CREATE TABLE IF NOT EXISTS order_consumer_record (
  id BIGINT PRIMARY KEY,
  event_key VARCHAR(256) NOT NULL,
  consumer_group VARCHAR(128) NOT NULL,
  event_type VARCHAR(128) NULL,
  payload_json TEXT NULL,
  status VARCHAR(32) NOT NULL,
  last_error VARCHAR(512) NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  UNIQUE KEY uk_order_consumer_event_group (event_key, consumer_group),
  INDEX idx_order_consumer_status (status)
);

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
  promoted_ticket_id BIGINT NULL,
  promoted_capacity_token_id BIGINT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  UNIQUE KEY uk_order_saga_step (order_id, saga_type, step_name),
  INDEX idx_order_saga_dispatch (status, next_retry_time),
  INDEX idx_order_saga_order (order_id, saga_type, step_order)
);

UPDATE business_sequence SET next_value = (SELECT COALESCE(MAX(id), 10000) FROM customer_order)
WHERE namespace = 'order' AND next_value < (SELECT COALESCE(MAX(id), 10000) FROM customer_order);
UPDATE business_sequence SET next_value = (SELECT COALESCE(MAX(id), 10000) FROM order_local_event)
WHERE namespace = 'order_local_event' AND next_value < (SELECT COALESCE(MAX(id), 10000) FROM order_local_event);
UPDATE business_sequence SET next_value = (SELECT COALESCE(MAX(id), 10000) FROM order_saga_step)
WHERE namespace = 'order_saga_step' AND next_value < (SELECT COALESCE(MAX(id), 10000) FROM order_saga_step);

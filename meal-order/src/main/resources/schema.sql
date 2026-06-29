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
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  INDEX idx_customer_order_user_id (user_id),
  INDEX idx_customer_order_status (status),
  INDEX idx_customer_order_queue_ticket_id (queue_ticket_id)
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
  INDEX idx_order_local_event_status (status),
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

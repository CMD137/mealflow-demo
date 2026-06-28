CREATE TABLE IF NOT EXISTS payment_order (
  id BIGINT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  amount_cent INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  INDEX idx_payment_order_order_id (order_id),
  INDEX idx_payment_order_status (status)
);

CREATE TABLE IF NOT EXISTS local_event (
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
  UNIQUE KEY uk_payment_local_event_key (event_key),
  INDEX idx_payment_local_event_status (status),
  INDEX idx_payment_local_event_aggregate (aggregate_type, aggregate_id)
);

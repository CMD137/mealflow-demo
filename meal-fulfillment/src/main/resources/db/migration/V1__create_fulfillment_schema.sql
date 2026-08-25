CREATE TABLE IF NOT EXISTS business_sequence (namespace VARCHAR(64) PRIMARY KEY, next_value BIGINT NOT NULL);
INSERT INTO business_sequence (namespace, next_value) VALUES ('fulfillmentOperation', 10000)
ON DUPLICATE KEY UPDATE next_value = next_value;
INSERT INTO business_sequence (namespace, next_value) VALUES ('fulfillmentLocalEvent', 10000)
ON DUPLICATE KEY UPDATE next_value = next_value;

CREATE TABLE IF NOT EXISTS fulfillment_operation_log (
  id BIGINT PRIMARY KEY,
  request_id VARCHAR(128) NOT NULL,
  order_id BIGINT NOT NULL,
  action VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  message VARCHAR(512) NULL,
  create_time TIMESTAMP NOT NULL,
  INDEX idx_fulfillment_order_id (order_id),
  INDEX idx_fulfillment_request_id (request_id)
);

CREATE TABLE IF NOT EXISTS fulfillment_local_event (
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
  UNIQUE KEY uk_fulfillment_local_event_key (event_key),
  INDEX idx_fulfillment_local_event_status (status),
  INDEX idx_fulfillment_local_event_aggregate (aggregate_type, aggregate_id)
);

CREATE TABLE IF NOT EXISTS fulfillment_meal_ready_task (
  request_id VARCHAR(128) PRIMARY KEY,
  order_id BIGINT NOT NULL,
  capacity_token_id BIGINT NOT NULL,
  order_json TEXT NOT NULL,
  release_done BOOLEAN NOT NULL DEFAULT FALSE,
  ready_ticket_id BIGINT NULL,
  ready_capacity_token_id BIGINT NULL,
  promote_done BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(32) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_time TIMESTAMP NULL,
  lease_until TIMESTAMP NULL,
  last_error VARCHAR(512) NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  INDEX idx_fulfillment_task_dispatch (status, next_retry_time)
);

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
  UNIQUE KEY uk_fulfillment_local_event_key (event_key),
  INDEX idx_fulfillment_local_event_status (status),
  INDEX idx_fulfillment_local_event_aggregate (aggregate_type, aggregate_id)
);

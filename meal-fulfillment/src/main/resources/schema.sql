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

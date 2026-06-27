CREATE TABLE IF NOT EXISTS queue_ticket (
  id BIGINT PRIMARY KEY,
  ticket_no VARCHAR(64) NOT NULL,
  request_id VARCHAR(128) NOT NULL,
  user_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  score BIGINT NOT NULL,
  ahead_count_snapshot INT NOT NULL,
  estimated_wait_seconds INT NOT NULL,
  expire_time TIMESTAMP NOT NULL,
  snapshot_json TEXT NOT NULL,
  order_id BIGINT NULL,
  ready_time TIMESTAMP NULL,
  processing_time TIMESTAMP NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  INDEX idx_queue_ticket_merchant_status (merchant_id, status),
  INDEX idx_queue_ticket_status_score (status, score)
);

CREATE TABLE IF NOT EXISTS capacity_token (
  id BIGINT PRIMARY KEY,
  request_id VARCHAR(128) NOT NULL,
  merchant_id BIGINT NOT NULL,
  ticket_id BIGINT NULL,
  order_id BIGINT NULL,
  status VARCHAR(32) NOT NULL,
  expire_time TIMESTAMP NOT NULL,
  release_reason VARCHAR(128) NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  INDEX idx_capacity_token_merchant_status (merchant_id, status),
  INDEX idx_capacity_token_order_id (order_id),
  INDEX idx_capacity_token_ticket_id (ticket_id)
);

CREATE TABLE IF NOT EXISTS merchant_queue_limit (
  merchant_id BIGINT PRIMARY KEY,
  limit_value INT NOT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS business_sequence (
  namespace VARCHAR(64) PRIMARY KEY,
  next_value BIGINT NOT NULL
);

INSERT INTO business_sequence (namespace, next_value) VALUES ('queueTicket', 10000)
ON DUPLICATE KEY UPDATE next_value = next_value;
INSERT INTO business_sequence (namespace, next_value) VALUES ('capacityToken', 10000)
ON DUPLICATE KEY UPDATE next_value = next_value;

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
  UNIQUE KEY uk_queue_ticket_request (request_id),
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
  released_ticket_id BIGINT NULL,
  released_capacity_token_id BIGINT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  UNIQUE KEY uk_capacity_token_request (request_id),
  INDEX idx_capacity_token_merchant_status (merchant_id, status),
  INDEX idx_capacity_token_order_id (order_id),
  INDEX idx_capacity_token_ticket_id (ticket_id)
);

CREATE TABLE IF NOT EXISTS merchant_queue_limit (
  merchant_id BIGINT PRIMARY KEY,
  limit_value INT NOT NULL,
  inflight_count INT NOT NULL DEFAULT 0,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL
);

UPDATE business_sequence SET next_value = (SELECT COALESCE(MAX(id), 10000) FROM queue_ticket)
WHERE namespace = 'queueTicket' AND next_value < (SELECT COALESCE(MAX(id), 10000) FROM queue_ticket);
UPDATE business_sequence SET next_value = (SELECT COALESCE(MAX(id), 10000) FROM capacity_token)
WHERE namespace = 'capacityToken' AND next_value < (SELECT COALESCE(MAX(id), 10000) FROM capacity_token);

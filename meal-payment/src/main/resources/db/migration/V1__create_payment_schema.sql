CREATE TABLE payment_order (
  id BIGINT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  provider VARCHAR(32) NOT NULL,
  merchant_order_no VARCHAR(64) NOT NULL,
  channel_transaction_no VARCHAR(128) NULL,
  callback_digest CHAR(64) NULL,
  callback_status VARCHAR(32) NULL,
  amount_cent INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  INDEX idx_payment_order_order_id (order_id),
  INDEX idx_payment_order_status (status),
  INDEX idx_payment_order_user_id (user_id),
  UNIQUE KEY uk_payment_order_order_id (order_id),
  UNIQUE KEY uk_payment_order_merchant_order_no (merchant_order_no)
);

CREATE TABLE payment_idempotency_record (
  subject VARCHAR(128) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  lease_expire_time TIMESTAMP NULL,
  response_json TEXT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  PRIMARY KEY (subject, idempotency_key),
  INDEX idx_payment_idempotency_lease (lease_expire_time)
);

CREATE TABLE payment_local_event (
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
  next_retry_time TIMESTAMP NULL,
  lease_until TIMESTAMP NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  UNIQUE KEY uk_payment_local_event_key (event_key),
  INDEX idx_payment_local_event_dispatch (status, next_retry_time),
  INDEX idx_payment_local_event_aggregate (aggregate_type, aggregate_id)
);

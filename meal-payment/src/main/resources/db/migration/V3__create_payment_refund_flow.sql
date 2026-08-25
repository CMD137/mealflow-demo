INSERT INTO business_sequence (namespace, next_value) VALUES ('paymentRefund', 10000)
ON DUPLICATE KEY UPDATE next_value = next_value;

CREATE TABLE IF NOT EXISTS payment_refund (
  id BIGINT PRIMARY KEY,
  pay_order_id BIGINT NOT NULL,
  provider VARCHAR(32) NOT NULL,
  merchant_order_no VARCHAR(64) NOT NULL,
  refund_request_no VARCHAR(64) NOT NULL,
  amount_cent INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  channel_transaction_no VARCHAR(128) NULL,
  channel_refund_no VARCHAR(128) NULL,
  raw_response TEXT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  next_query_time TIMESTAMP NULL,
  last_error VARCHAR(512) NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  UNIQUE KEY uk_payment_refund_pay_order (pay_order_id),
  UNIQUE KEY uk_payment_refund_request (refund_request_no),
  INDEX idx_payment_refund_query (status, next_query_time)
);

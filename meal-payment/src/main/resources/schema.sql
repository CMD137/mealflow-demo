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

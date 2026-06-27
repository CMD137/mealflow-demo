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

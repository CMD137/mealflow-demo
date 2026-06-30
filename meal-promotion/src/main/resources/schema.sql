CREATE TABLE IF NOT EXISTS voucher (
  id BIGINT PRIMARY KEY,
  name VARCHAR(128) NOT NULL DEFAULT '',
  type VARCHAR(32) NOT NULL DEFAULT 'SECKILL',
  discount_cent INT NOT NULL,
  stock INT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  start_time TIMESTAMP NULL,
  end_time TIMESTAMP NULL,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_voucher_status (status)
);

CREATE TABLE IF NOT EXISTS user_voucher (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  voucher_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  UNIQUE KEY uk_user_voucher (user_id, voucher_id),
  INDEX idx_user_voucher_user_id (user_id),
  INDEX idx_user_voucher_status (status)
);

CREATE TABLE IF NOT EXISTS voucher_claim (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  voucher_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  INDEX idx_voucher_claim_user_id (user_id),
  INDEX idx_voucher_claim_status (status)
);

CREATE TABLE IF NOT EXISTS voucher_claim_retry (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  voucher_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  max_retries INT NOT NULL DEFAULT 3,
  last_error VARCHAR(512) NULL,
  next_retry_time TIMESTAMP NOT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  UNIQUE KEY uk_voucher_claim_retry_user_voucher (user_id, voucher_id),
  INDEX idx_voucher_claim_retry_status_time (status, next_retry_time)
);

CREATE TABLE IF NOT EXISTS voucher_lock (
  id BIGINT PRIMARY KEY,
  user_voucher_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  ticket_id BIGINT NULL,
  order_id BIGINT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  INDEX idx_voucher_lock_user_voucher_id (user_voucher_id),
  INDEX idx_voucher_lock_status (status),
  INDEX idx_voucher_lock_order_id (order_id)
);

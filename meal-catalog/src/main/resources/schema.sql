CREATE TABLE IF NOT EXISTS sku (
  id BIGINT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  category_id BIGINT NULL,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(255) NOT NULL DEFAULT '',
  image_url VARCHAR(255) NOT NULL DEFAULT '',
  price_cent INT NOT NULL,
  stock INT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ON_SHELF',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_sku_merchant_category (merchant_id, category_id),
  INDEX idx_sku_merchant_status (merchant_id, status)
);

CREATE TABLE IF NOT EXISTS category (
  id BIGINT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  sort_order INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  UNIQUE KEY uk_category_merchant_name (merchant_id, name),
  INDEX idx_category_merchant_status (merchant_id, status)
);

CREATE TABLE IF NOT EXISTS stock_reservation (
  id BIGINT PRIMARY KEY,
  request_id VARCHAR(128) NOT NULL,
  user_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  ticket_id BIGINT DEFAULT NULL,
  order_id BIGINT DEFAULT NULL,
  quantity INT NOT NULL,
  status TINYINT NOT NULL,
  expire_time TIMESTAMP NOT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  UNIQUE KEY uk_request_sku(request_id, sku_id),
  KEY idx_order_id(order_id),
  KEY idx_status_expire(status, expire_time)
);

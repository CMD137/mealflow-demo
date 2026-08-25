CREATE TABLE IF NOT EXISTS business_sequence (
  namespace VARCHAR(64) PRIMARY KEY,
  next_value BIGINT NOT NULL
);

INSERT INTO business_sequence (namespace, next_value) VALUES ('category', 1000)
ON DUPLICATE KEY UPDATE next_value = next_value;
INSERT INTO business_sequence (namespace, next_value) VALUES ('sku', 1000)
ON DUPLICATE KEY UPDATE next_value = next_value;
INSERT INTO business_sequence (namespace, next_value) VALUES ('stockReservation', 10000)
ON DUPLICATE KEY UPDATE next_value = next_value;

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

UPDATE business_sequence SET next_value = (SELECT COALESCE(MAX(id), 1000) FROM category)
WHERE namespace = 'category' AND next_value < (SELECT COALESCE(MAX(id), 1000) FROM category);
UPDATE business_sequence SET next_value = (SELECT COALESCE(MAX(id), 1000) FROM sku)
WHERE namespace = 'sku' AND next_value < (SELECT COALESCE(MAX(id), 1000) FROM sku);
UPDATE business_sequence SET next_value = (SELECT COALESCE(MAX(id), 10000) FROM stock_reservation)
WHERE namespace = 'stockReservation' AND next_value < (SELECT COALESCE(MAX(id), 10000) FROM stock_reservation);

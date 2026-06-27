CREATE TABLE IF NOT EXISTS sku (
  id BIGINT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  price_cent INT NOT NULL,
  stock INT NOT NULL
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

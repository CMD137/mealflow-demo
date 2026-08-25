CREATE TABLE IF NOT EXISTS business_sequence (
  namespace VARCHAR(64) PRIMARY KEY,
  next_value BIGINT NOT NULL
);
INSERT INTO business_sequence (namespace, next_value) VALUES ('cartItem', 10000)
ON DUPLICATE KEY UPDATE next_value = next_value;

CREATE TABLE IF NOT EXISTS cart_item (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  selected BOOLEAN NOT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  UNIQUE KEY uk_cart_user_sku (user_id, sku_id),
  INDEX idx_cart_user_id (user_id)
);

UPDATE business_sequence SET next_value = (SELECT COALESCE(MAX(id), 10000) FROM cart_item)
WHERE namespace = 'cartItem' AND next_value < (SELECT COALESCE(MAX(id), 10000) FROM cart_item);

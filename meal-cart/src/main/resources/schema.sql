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

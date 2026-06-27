CREATE TABLE IF NOT EXISTS user_account (
  id BIGINT PRIMARY KEY,
  phone VARCHAR(32) NOT NULL,
  nickname VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  UNIQUE KEY uk_user_account_phone (phone)
);

CREATE TABLE IF NOT EXISTS user_address (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  contact_name VARCHAR(64) NOT NULL,
  contact_phone VARCHAR(32) NOT NULL,
  detail VARCHAR(255) NOT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  INDEX idx_user_address_user_id (user_id)
);

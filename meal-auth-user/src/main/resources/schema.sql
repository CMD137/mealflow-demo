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
  is_default BOOLEAN NOT NULL DEFAULT FALSE,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  INDEX idx_user_address_user_id (user_id)
);

CREATE TABLE IF NOT EXISTS auth_token (
  token VARCHAR(128) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  role_code VARCHAR(64) NOT NULL,
  merchant_id BIGINT NULL,
  expire_time TIMESTAMP NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  INDEX idx_auth_token_user_id (user_id),
  INDEX idx_auth_token_expire_time (expire_time)
);

CREATE TABLE IF NOT EXISTS merchant_employee (
  id BIGINT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role_code VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  UNIQUE KEY uk_merchant_employee_user (merchant_id, user_id),
  INDEX idx_merchant_employee_user_id (user_id)
);

CREATE TABLE IF NOT EXISTS merchant_role (
  role_code VARCHAR(64) PRIMARY KEY,
  role_name VARCHAR(64) NOT NULL,
  description VARCHAR(255) NOT NULL,
  builtin BOOLEAN NOT NULL DEFAULT FALSE,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS menu_permission (
  id BIGINT PRIMARY KEY,
  parent_id BIGINT NULL,
  menu_code VARCHAR(64) NOT NULL,
  menu_name VARCHAR(64) NOT NULL,
  path VARCHAR(128) NOT NULL,
  permission_code VARCHAR(64) NOT NULL,
  sort_order INT NOT NULL,
  visible BOOLEAN NOT NULL DEFAULT TRUE,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  UNIQUE KEY uk_menu_permission_code (menu_code),
  UNIQUE KEY uk_menu_permission_permission (permission_code),
  INDEX idx_menu_permission_parent (parent_id)
);

CREATE TABLE IF NOT EXISTS role_permission (
  role_code VARCHAR(64) NOT NULL,
  permission_code VARCHAR(64) NOT NULL,
  create_time TIMESTAMP NOT NULL,
  PRIMARY KEY (role_code, permission_code)
);

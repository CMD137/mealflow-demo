-- MealFlow 核心表结构草案
-- 说明：
-- 1. 本文件是开发建表第一参考。
-- 2. 枚举字段用 TINYINT 存储，具体数值以 docs/MealFlow-status-codes.md 为准。
-- 3. 不同服务真实落地时应拆到各自逻辑库，这里集中展示便于评审。

CREATE TABLE queue_ticket (
  id BIGINT PRIMARY KEY,
  ticket_no VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  cart_snapshot JSON NOT NULL,
  price_snapshot JSON NOT NULL,
  submit_snapshot JSON NOT NULL,
  status TINYINT NOT NULL,
  priority INT NOT NULL DEFAULT 0,
  queue_score BIGINT NOT NULL,
  ahead_count_snapshot INT NOT NULL DEFAULT 0,
  estimated_wait_seconds INT NOT NULL DEFAULT 0,
  order_id BIGINT DEFAULT NULL,
  ready_time DATETIME DEFAULT NULL,
  processing_time DATETIME DEFAULT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  last_error VARCHAR(512) DEFAULT NULL,
  expire_time DATETIME NOT NULL,
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_ticket_no(ticket_no),
  KEY idx_merchant_status_score(merchant_id, status, queue_score),
  KEY idx_status_ready_time(status, ready_time),
  KEY idx_status_processing_time(status, processing_time),
  KEY idx_user_create_time(user_id, create_time)
);

CREATE TABLE capacity_token (
  id BIGINT PRIMARY KEY,
  request_id VARCHAR(128) DEFAULT NULL,
  merchant_id BIGINT NOT NULL,
  ticket_id BIGINT DEFAULT NULL,
  order_id BIGINT DEFAULT NULL,
  status TINYINT NOT NULL,
  expire_time DATETIME NOT NULL,
  release_reason VARCHAR(64) DEFAULT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_request_id(request_id),
  UNIQUE KEY uk_ticket_id(ticket_id),
  UNIQUE KEY uk_order_id(order_id),
  KEY idx_merchant_status(merchant_id, status),
  KEY idx_status_expire(status, expire_time)
);

CREATE TABLE local_event (
  event_id BIGINT PRIMARY KEY,
  event_key VARCHAR(128) NOT NULL,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id BIGINT NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  event_version INT NOT NULL DEFAULT 1,
  payload JSON NOT NULL,
  status TINYINT NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_time DATETIME DEFAULT NULL,
  locked_by VARCHAR(128) DEFAULT NULL,
  locked_until DATETIME DEFAULT NULL,
  last_error VARCHAR(512) DEFAULT NULL,
  sent_time DATETIME DEFAULT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_event_key(event_key),
  KEY idx_status_next_retry_time(status, next_retry_time),
  KEY idx_locked_until(locked_until)
);

CREATE TABLE consumer_record (
  id BIGINT PRIMARY KEY,
  event_id BIGINT NOT NULL,
  event_key VARCHAR(128) NOT NULL,
  consumer_group VARCHAR(128) NOT NULL,
  status TINYINT NOT NULL,
  locked_by VARCHAR(128) DEFAULT NULL,
  locked_until DATETIME DEFAULT NULL,
  error_msg VARCHAR(512) DEFAULT NULL,
  consume_time DATETIME DEFAULT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_event_consumer(event_id, consumer_group),
  UNIQUE KEY uk_event_key_consumer(event_key, consumer_group),
  KEY idx_consumer_locked_until(consumer_group, status, locked_until)
);

CREATE TABLE idempotent_request (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  request_id VARCHAR(128) NOT NULL,
  biz_type VARCHAR(64) NOT NULL,
  biz_id BIGINT DEFAULT NULL,
  status TINYINT NOT NULL,
  response_snapshot JSON DEFAULT NULL,
  expire_time DATETIME NOT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_user_request_biz(user_id, request_id, biz_type),
  KEY idx_status_expire(status, expire_time)
);

CREATE TABLE stock_reservation (
  id BIGINT PRIMARY KEY,
  request_id VARCHAR(128) NOT NULL,
  user_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  ticket_id BIGINT DEFAULT NULL,
  order_id BIGINT DEFAULT NULL,
  quantity INT NOT NULL,
  status TINYINT NOT NULL,
  expire_time DATETIME NOT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_request_sku(request_id, sku_id),
  UNIQUE KEY uk_ticket_sku(ticket_id, sku_id),
  UNIQUE KEY uk_order_sku(order_id, sku_id),
  KEY idx_order_id(order_id),
  KEY idx_user_merchant_sku_status(user_id, merchant_id, sku_id, status),
  KEY idx_status_expire(status, expire_time)
);

CREATE TABLE voucher_lock (
  id BIGINT PRIMARY KEY,
  request_id VARCHAR(128) NOT NULL,
  user_id BIGINT NOT NULL,
  user_voucher_id BIGINT NOT NULL,
  ticket_id BIGINT DEFAULT NULL,
  order_id BIGINT DEFAULT NULL,
  status TINYINT NOT NULL,
  lock_expire_time DATETIME NOT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_request_voucher(request_id, user_voucher_id),
  UNIQUE KEY uk_ticket_voucher(ticket_id, user_voucher_id),
  UNIQUE KEY uk_order_voucher(order_id, user_voucher_id),
  KEY idx_ticket_id(ticket_id),
  KEY idx_user_voucher(user_voucher_id),
  KEY idx_status_expire(status, lock_expire_time)
);

CREATE TABLE voucher_claim (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  voucher_id BIGINT NOT NULL,
  status TINYINT NOT NULL,
  last_error VARCHAR(512) DEFAULT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_user_voucher_claim(user_id, voucher_id),
  KEY idx_status_update_time(status, update_time)
);

-- 现有订单表演进字段，真实执行时按当前 orders 表结构生成 ALTER。
-- ALTER TABLE orders
--   ADD COLUMN queue_ticket_id BIGINT DEFAULT NULL,
--   ADD COLUMN refund_status TINYINT NOT NULL DEFAULT 0,
--   ADD COLUMN version INT NOT NULL DEFAULT 0,
--   ADD UNIQUE KEY uk_queue_ticket_id(queue_ticket_id);

-- 用户券包兜底一人一券。
-- ALTER TABLE user_voucher
--   ADD UNIQUE KEY uk_user_voucher(user_id, voucher_id);

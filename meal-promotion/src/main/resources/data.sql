INSERT IGNORE INTO voucher (id, name, type, discount_cent, stock, status, scope, merchant_id, start_time, end_time)
VALUES (1000, '午高峰秒杀券', 'SECKILL', 500, 100, 'ACTIVE', 'PLATFORM', NULL, '2020-01-01 00:00:00', '2099-12-31 23:59:59');

INSERT IGNORE INTO user_voucher (id, user_id, voucher_id, status, create_time, update_time)
VALUES (300, 100, 1000, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO voucher_claim
  (event_key, user_id, voucher_id, user_voucher_id, status, create_time, update_time)
VALUES
  ('seckill:1000:100', 100, 1000, 300, 'CLAIMED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

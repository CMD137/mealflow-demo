INSERT IGNORE INTO voucher (id, name, type, discount_cent, stock, status, start_time, end_time)
VALUES (1000, 'Lunch Peak Seckill Coupon', 'SECKILL', 500, 100, 'ACTIVE', CURRENT_TIMESTAMP, NULL);

INSERT IGNORE INTO user_voucher (id, user_id, voucher_id, status, create_time, update_time)
VALUES (300, 100, 1000, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO voucher (id, discount_cent, stock)
VALUES (1000, 500, 100);

INSERT IGNORE INTO user_voucher (id, user_id, voucher_id, status, create_time, update_time)
VALUES (300, 100, 1000, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO user_account (id, phone, nickname, status, create_time, update_time)
VALUES
  (100, '13800000000', 'Demo User', 'NORMAL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (101, '13800000001', 'Queue User A', 'VIP', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (102, '13800000002', 'Queue User B', 'NORMAL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO user_address (id, user_id, contact_name, contact_phone, detail, create_time, update_time)
VALUES
  (20, 100, 'Demo Contact', '13800000000', 'Tech Park Building 1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (21, 101, 'Queue Contact', '13800000001', 'Startup Road No.2', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

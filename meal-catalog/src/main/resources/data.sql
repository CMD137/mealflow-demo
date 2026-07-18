INSERT IGNORE INTO category(id, merchant_id, name, sort_order, status, create_time, update_time)
VALUES
  (1, 10, '盖饭', 10, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (2, 10, '饮品', 20, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO sku(id, merchant_id, name, price_cent, stock)
VALUES
  (1, 10, '招牌牛肉饭', 2800, 50),
  (2, 10, '香煎鸡腿饭', 2600, 50),
  (3, 10, '冰柠檬茶', 800, 100);

INSERT IGNORE INTO category(id, merchant_id, name, sort_order, status, create_time, update_time)
VALUES
  (1, 10, '盖饭', 10, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (2, 10, '饮品', 20, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO sku(id, merchant_id, category_id, name, description, image_url, price_cent, stock, status)
VALUES
  (1, 10, 1, '招牌牛肉饭', '午高峰招牌牛肉饭', '', 2800, 50, 'ON_SHELF'),
  (2, 10, 1, '香煎鸡腿饭', '香煎鸡腿盖饭', '', 2600, 50, 'ON_SHELF'),
  (3, 10, 2, '冰柠檬茶', '冰爽柠檬茶', '', 800, 100, 'ON_SHELF');

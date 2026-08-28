INSERT IGNORE INTO category(id, merchant_id, name, sort_order, status, create_time, update_time)
VALUES
  (1, 10, '盖饭', 10, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (2, 10, '饮品', 20, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (3, 11, '轻食主餐', 10, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (4, 11, '沙拉与饮品', 20, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO sku(id, merchant_id, category_id, name, description, image_url, price_cent, stock, status)
VALUES
  (1, 10, 1, '招牌牛肉饭', '午高峰招牌牛肉饭', '', 2800, 50, 'ON_SHELF'),
  (2, 10, 1, '香煎鸡腿饭', '香煎鸡腿盖饭', '', 2600, 50, 'ON_SHELF'),
  (3, 10, 2, '冰柠檬茶', '冰爽柠檬茶', '', 800, 100, 'ON_SHELF'),
  (4, 10, 2, '支付宝沙箱测试商品', '仅用于支付宝沙箱支付、退款与查询联调', '', 1, 10000, 'ON_SHELF'),
  (5, 11, 3, '香煎鸡胸藜麦碗', '鸡胸肉、藜麦与时蔬组合', '', 3200, 40, 'ON_SHELF'),
  (6, 11, 3, '鲜虾全麦意面', '鲜虾搭配全麦意面和番茄酱', '', 3000, 40, 'ON_SHELF'),
  (7, 11, 4, '牛油果鸡蛋沙拉', '牛油果、鸡蛋与生菜沙拉', '', 2600, 50, 'ON_SHELF'),
  (8, 11, 4, '无糖美式咖啡', '现磨无糖美式咖啡', '', 1000, 80, 'ON_SHELF');

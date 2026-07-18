INSERT IGNORE INTO merchant (id, name, business_status, base_capacity, manual_factor, create_time, update_time)
VALUES
  (10, 'MealFlow 牛肉饭', 'OPEN', 1, 1.0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (11, 'MealFlow 轻食铺', 'OPEN', 3, 1.0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO category(id, merchant_id, name, sort_order, status, create_time, update_time)
VALUES
  (1, 10, 'Rice Bowls', 10, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (2, 10, 'Drinks', 20, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO sku(id, merchant_id, name, price_cent, stock)
VALUES
  (1, 10, 'Signature Beef Rice', 2800, 50),
  (2, 10, 'Grilled Chicken Rice', 2600, 50),
  (3, 10, 'Iced Lemon Tea', 800, 100);

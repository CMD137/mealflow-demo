INSERT IGNORE INTO category(id, merchant_id, name, sort_order, status, create_time, update_time)
VALUES
  (1, 10, 'Rice Bowls', 10, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (2, 10, 'Drinks', 20, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO sku(id, merchant_id, category_id, name, description, image_url, price_cent, stock, status)
VALUES
  (1, 10, 1, 'Signature Beef Rice', 'Beef rice bowl for lunch peak', '', 2800, 50, 'ON_SHELF'),
  (2, 10, 1, 'Grilled Chicken Rice', 'Grilled chicken rice bowl', '', 2600, 50, 'ON_SHELF'),
  (3, 10, 2, 'Iced Lemon Tea', 'Cold lemon tea', '', 800, 100, 'ON_SHELF');

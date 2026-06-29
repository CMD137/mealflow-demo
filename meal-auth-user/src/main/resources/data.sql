INSERT IGNORE INTO user_account (id, phone, nickname, status, create_time, update_time)
VALUES
  (100, '13800000000', 'Demo User', 'NORMAL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (101, '13800000001', 'Queue User A', 'VIP', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (102, '13800000002', 'Queue User B', 'NORMAL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO user_address (id, user_id, contact_name, contact_phone, detail, create_time, update_time)
VALUES
  (20, 100, 'Demo Contact', '13800000000', 'Tech Park Building 1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (21, 101, 'Queue Contact', '13800000001', 'Startup Road No.2', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO merchant_employee (id, merchant_id, user_id, role_code, status, create_time, update_time)
VALUES
  (1, 10, 100, 'MERCHANT_ADMIN', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO merchant_role (role_code, role_name, description, builtin, create_time, update_time)
VALUES
  ('CUSTOMER', 'Customer', 'Customer app user', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('MERCHANT_ADMIN', 'Merchant Admin', 'Merchant owner with full back-office access', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('STORE_STAFF', 'Store Staff', 'Store employee who can operate fulfillment and catalog', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO menu_permission
  (id, parent_id, menu_code, menu_name, path, permission_code, sort_order, visible, create_time, update_time)
VALUES
  (1, NULL, 'dashboard', 'Dashboard', '/admin/dashboard', 'MERCHANT_MANAGE', 10, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (2, NULL, 'merchant', 'Merchant Settings', '/admin/merchant', 'MERCHANT_MANAGE', 20, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (3, NULL, 'catalog', 'Catalog Management', '/admin/catalog', 'CATALOG_MANAGE', 30, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (4, NULL, 'fulfillment', 'Fulfillment Workbench', '/admin/fulfillment', 'FULFILLMENT_OPERATE', 40, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (5, NULL, 'operations', 'Operations Console', '/admin/operations', 'INTERNAL_OPERATE', 50, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (6, 5, 'outbox', 'Outbox Events', '/admin/operations/outbox', 'INTERNAL_OPERATE', 51, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (7, 5, 'consumer-records', 'Consumer Records', '/admin/operations/consumer-records', 'INTERNAL_OPERATE', 52, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (8, NULL, 'notify', 'Notifications', '/admin/notify', 'NOTIFY_READ', 60, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO role_permission (role_code, permission_code, create_time)
VALUES
  ('CUSTOMER', 'USER_READ', CURRENT_TIMESTAMP),
  ('CUSTOMER', 'CART_WRITE', CURRENT_TIMESTAMP),
  ('CUSTOMER', 'ORDER_WRITE', CURRENT_TIMESTAMP),
  ('CUSTOMER', 'PAYMENT_WRITE', CURRENT_TIMESTAMP),
  ('CUSTOMER', 'VOUCHER_USE', CURRENT_TIMESTAMP),
  ('CUSTOMER', 'NOTIFY_READ', CURRENT_TIMESTAMP),
  ('MERCHANT_ADMIN', 'USER_READ', CURRENT_TIMESTAMP),
  ('MERCHANT_ADMIN', 'CART_WRITE', CURRENT_TIMESTAMP),
  ('MERCHANT_ADMIN', 'ORDER_WRITE', CURRENT_TIMESTAMP),
  ('MERCHANT_ADMIN', 'PAYMENT_WRITE', CURRENT_TIMESTAMP),
  ('MERCHANT_ADMIN', 'VOUCHER_USE', CURRENT_TIMESTAMP),
  ('MERCHANT_ADMIN', 'NOTIFY_READ', CURRENT_TIMESTAMP),
  ('MERCHANT_ADMIN', 'MERCHANT_MANAGE', CURRENT_TIMESTAMP),
  ('MERCHANT_ADMIN', 'CATALOG_MANAGE', CURRENT_TIMESTAMP),
  ('MERCHANT_ADMIN', 'FULFILLMENT_OPERATE', CURRENT_TIMESTAMP),
  ('MERCHANT_ADMIN', 'INTERNAL_OPERATE', CURRENT_TIMESTAMP),
  ('STORE_STAFF', 'USER_READ', CURRENT_TIMESTAMP),
  ('STORE_STAFF', 'ORDER_WRITE', CURRENT_TIMESTAMP),
  ('STORE_STAFF', 'CATALOG_MANAGE', CURRENT_TIMESTAMP),
  ('STORE_STAFF', 'FULFILLMENT_OPERATE', CURRENT_TIMESTAMP),
  ('STORE_STAFF', 'NOTIFY_READ', CURRENT_TIMESTAMP);

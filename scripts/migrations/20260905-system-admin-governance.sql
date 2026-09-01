-- System administrator governance upgrade for databases initialized before 20260905.
-- Safe to run repeatedly. It preserves platform_admin membership and revokes, rather than upgrades,
-- legacy PLATFORM_ADMIN sessions so the new authority is only issued by a fresh login.

START TRANSACTION;

INSERT INTO merchant_role (role_code, role_name, description, builtin, create_time, update_time)
VALUES ('SYSTEM_ADMIN', 'System Admin', 'System governance administrator without merchant ownership', TRUE,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), description = VALUES(description), builtin = TRUE,
                        update_time = CURRENT_TIMESTAMP;

INSERT INTO role_permission (role_code, permission_code, create_time)
VALUES
  ('SYSTEM_ADMIN', 'PLATFORM_VOUCHER_MANAGE', CURRENT_TIMESTAMP),
  ('SYSTEM_ADMIN', 'SYSTEM_MERCHANT_READ', CURRENT_TIMESTAMP),
  ('SYSTEM_ADMIN', 'SYSTEM_MERCHANT_STATUS_WRITE', CURRENT_TIMESTAMP),
  ('SYSTEM_ADMIN', 'SYSTEM_USER_READ', CURRENT_TIMESTAMP),
  ('SYSTEM_ADMIN', 'SYSTEM_USER_STATUS_WRITE', CURRENT_TIMESTAMP),
  ('SYSTEM_ADMIN', 'SYSTEM_ORDER_READ', CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE create_time = create_time;

INSERT INTO menu_permission
  (id, parent_id, menu_code, menu_name, path, permission_code, sort_order, visible, create_time, update_time)
VALUES
  (9, NULL, 'system-merchants', 'Merchant Governance', '/admin/system/merchants', 'SYSTEM_MERCHANT_READ', 70,
   TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (10, NULL, 'system-users', 'User Governance', '/admin/system/users', 'SYSTEM_USER_READ', 80,
   TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (11, NULL, 'system-orders', 'Global Orders', '/admin/system/orders', 'SYSTEM_ORDER_READ', 90,
   TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), path = VALUES(path), permission_code = VALUES(permission_code),
                        sort_order = VALUES(sort_order), visible = TRUE, update_time = CURRENT_TIMESTAMP;

-- Never silently expand an existing session. Any legacy platform token must be discarded.
UPDATE auth_token
SET revoked = TRUE, update_time = CURRENT_TIMESTAMP
WHERE role_code = 'PLATFORM_ADMIN' AND revoked = FALSE AND expire_time > CURRENT_TIMESTAMP;

DELETE FROM role_permission WHERE role_code = 'PLATFORM_ADMIN';
DELETE FROM merchant_role WHERE role_code = 'PLATFORM_ADMIN';

-- Platform membership remains separate from merchant_employee. Rename the demo label only when present.
UPDATE user_account
SET nickname = 'System Admin', update_time = CURRENT_TIMESTAMP
WHERE phone = '13800000006' AND nickname = 'Platform Admin';

COMMIT;

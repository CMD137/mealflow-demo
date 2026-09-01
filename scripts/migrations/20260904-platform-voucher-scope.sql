-- Apply once to existing MealFlow MySQL databases before deploying the platform-voucher revision.
-- Run during a maintenance window: the first INSERT allocates one user id from business_sequence.
-- New databases already receive the final definitions from the module schema/data SQL files.

-- 1) Platform identity. It is intentionally not a merchant_employee row, so merchant ownership checks
-- cannot accidentally grant platform-wide capabilities.
CREATE TABLE platform_admin (
  user_id BIGINT PRIMARY KEY,
  status VARCHAR(32) NOT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL
);

INSERT IGNORE INTO merchant_role (role_code, role_name, description, builtin, create_time, update_time)
VALUES ('PLATFORM_ADMIN', 'Platform Admin', 'Platform-wide promotion administrator without merchant ownership',
        TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO role_permission (role_code, permission_code, create_time)
VALUES
  ('PLATFORM_ADMIN', 'USER_READ', CURRENT_TIMESTAMP),
  ('PLATFORM_ADMIN', 'PLATFORM_VOUCHER_MANAGE', CURRENT_TIMESTAMP);

-- Local/demo bootstrap account: phone 13800000006. Login still follows the project-wide OTP flow;
-- no password is introduced or stored. If this phone already exists, it is promoted idempotently instead.
SET @platform_admin_id := (
  SELECT GREATEST(
    (SELECT next_value FROM business_sequence WHERE namespace = 'userAccount'),
    (SELECT COALESCE(MAX(id), 0) FROM user_account)
  ) + 1
);
INSERT INTO user_account (id, phone, nickname, status, create_time, update_time)
SELECT @platform_admin_id, '13800000006', 'Platform Admin', 'NORMAL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM user_account WHERE phone = '13800000006');
UPDATE business_sequence
SET next_value = GREATEST(next_value, (SELECT id FROM user_account WHERE phone = '13800000006'))
WHERE namespace = 'userAccount';
INSERT IGNORE INTO platform_admin (user_id, status, create_time, update_time)
SELECT id, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM user_account WHERE phone = '13800000006';

-- 2) Voucher scope. Existing vouchers have no durable creator-merchant fact, so they remain PLATFORM
-- vouchers. This preserves historical customer entitlements instead of guessing a merchant and making an
-- already-claimed voucher unusable. Reclassify only vouchers whose original merchant is independently known:
-- UPDATE voucher SET scope = 'MERCHANT', merchant_id = <merchant id> WHERE id IN (...);
ALTER TABLE voucher
  ADD COLUMN scope VARCHAR(16) NOT NULL DEFAULT 'PLATFORM' AFTER status,
  ADD COLUMN merchant_id BIGINT NULL AFTER scope,
  ADD INDEX idx_voucher_scope_merchant_status (scope, merchant_id, status);

UPDATE voucher
SET scope = 'PLATFORM', merchant_id = NULL
WHERE scope IS NULL OR scope NOT IN ('PLATFORM', 'MERCHANT')
   OR (scope = 'PLATFORM' AND merchant_id IS NOT NULL)
   OR (scope = 'MERCHANT' AND merchant_id IS NULL);

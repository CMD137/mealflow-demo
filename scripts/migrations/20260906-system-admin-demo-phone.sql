-- Move the active local/demo SYSTEM_ADMIN account carrying the legacy demo phone
-- to its approved phone number. Safe to run repeatedly after
-- 20260905-system-admin-governance.sql. Membership remains in platform_admin;
-- this does not create a merchant employee or preserve old tokens.

START TRANSACTION;

UPDATE user_account AS u
JOIN platform_admin AS pa ON pa.user_id = u.id
SET u.phone = '17739819838', u.nickname = 'System Admin', u.update_time = CURRENT_TIMESTAMP
WHERE u.phone = '13800000006' AND pa.status = 'ACTIVE';

COMMIT;

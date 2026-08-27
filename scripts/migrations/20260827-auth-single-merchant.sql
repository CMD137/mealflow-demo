-- Run the diagnostic query first. Resolve every returned row before adding the constraint.
SELECT user_id, COUNT(*) AS employee_relations
FROM merchant_employee
GROUP BY user_id
HAVING COUNT(*) > 1;

-- After the query returns no rows, replace the former per-merchant uniqueness with the project rule:
-- one employee account belongs to one merchant only.
ALTER TABLE merchant_employee DROP INDEX uk_merchant_employee_user;
ALTER TABLE merchant_employee ADD UNIQUE KEY uk_merchant_employee_user_id (user_id);
ALTER TABLE merchant_employee DROP INDEX idx_merchant_employee_user_id;
ALTER TABLE merchant_employee ADD INDEX idx_merchant_employee_merchant_id (merchant_id);

-- Merchant administrators must not retain service-internal operations permissions.
DELETE FROM role_permission
WHERE role_code = 'MERCHANT_ADMIN' AND permission_code = 'INTERNAL_OPERATE';

DELETE FROM menu_permission WHERE permission_code = 'INTERNAL_OPERATE';

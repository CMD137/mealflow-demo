-- Apply once to an existing MealFlow database before deploying this revision.
ALTER TABLE customer_order
  ADD COLUMN contact_name VARCHAR(64) NOT NULL DEFAULT '' AFTER amount_cent,
  ADD COLUMN contact_phone VARCHAR(32) NOT NULL DEFAULT '' AFTER contact_name,
  ADD COLUMN delivery_address VARCHAR(255) NOT NULL DEFAULT '' AFTER contact_phone,
  ADD COLUMN payment_expire_time TIMESTAMP NULL AFTER delivery_address;

-- Historical pending orders are deliberately expired immediately: they have no trustworthy payment deadline.
UPDATE customer_order
SET payment_expire_time = CASE
  WHEN status = 'PENDING_PAYMENT' THEN CURRENT_TIMESTAMP
  ELSE create_time
END
WHERE payment_expire_time IS NULL;

ALTER TABLE customer_order
  MODIFY COLUMN payment_expire_time TIMESTAMP NOT NULL,
  ADD INDEX idx_customer_order_payment_expire (status, payment_expire_time);

ALTER TABLE voucher_lock
  ADD COLUMN expire_time TIMESTAMP NULL AFTER order_id;

UPDATE voucher_lock
SET expire_time = CASE
  WHEN status = 'LOCKED' THEN CURRENT_TIMESTAMP
  ELSE create_time
END
WHERE expire_time IS NULL;

ALTER TABLE voucher_lock
  MODIFY COLUMN expire_time TIMESTAMP NOT NULL,
  ADD INDEX idx_voucher_lock_status_expire (status, expire_time);

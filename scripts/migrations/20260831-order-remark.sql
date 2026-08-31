-- Apply once to existing MealFlow databases before deploying this revision.
-- Existing orders have no reliable historical customer remark, so they remain NULL.
ALTER TABLE customer_order
  ADD COLUMN remark VARCHAR(255) NULL AFTER delivery_address;

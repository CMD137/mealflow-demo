-- Queue capacity compatibility upgrade for existing MealFlow MySQL databases.
--
-- Run once after taking a database backup. New databases do not need this file:
-- meal-queue/src/main/resources/schema.sql already contains the final column.
--
-- This script targets the older merchant_queue_limit table that only had
-- merchant_id, limit_value, create_time and update_time.

ALTER TABLE merchant_queue_limit
  ADD COLUMN inflight_count INT NOT NULL DEFAULT 0 AFTER limit_value;

-- The stored count is a derived value. Rebuild it from the source-of-truth
-- capacity tokens so a restart neither loses nor overstates occupied capacity.
UPDATE merchant_queue_limit AS queue_limit
LEFT JOIN (
  SELECT merchant_id, COUNT(*) AS held_count
  FROM capacity_token
  WHERE status = 'HELD'
  GROUP BY merchant_id
) AS held_tokens ON held_tokens.merchant_id = queue_limit.merchant_id
SET queue_limit.inflight_count = COALESCE(held_tokens.held_count, 0),
    queue_limit.update_time = CURRENT_TIMESTAMP;

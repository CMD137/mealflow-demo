-- One-time compatibility upgrade for EXISTING MealFlow MySQL databases that were created before
-- the admin pagination / query optimization change. New databases do NOT need this file:
-- meal-order/src/main/resources/schema.sql already contains the final index definitions.
--
-- Run once after taking a database backup. MySQL does not support IF NOT EXISTS for ADD INDEX,
-- so re-running this file on an already-upgraded database fails with "Duplicate key name";
-- that is expected and safe (the index already exists).

-- 1) Merchant admin order query: WHERE merchant_id=? AND status=? AND create_time BETWEEN ...
--    Previously only single-column indexes (user_id / status) existed, so this query had to scan
--    and filesort a large slice of the table.
ALTER TABLE customer_order
  ADD INDEX idx_customer_order_merchant_status_time (merchant_id, status, create_time);

-- 2) Outbox dispatch scan: WHERE status IN ('NEW','FAILED') ORDER BY id LIMIT n
--    The (status, id) composite index lets MySQL satisfy both the filter and the sort from one
--    index instead of doing a filesort over the status slice.
ALTER TABLE order_local_event
  ADD INDEX idx_order_local_event_status_id (status, id);

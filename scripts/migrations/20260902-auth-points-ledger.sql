-- One-time compatibility upgrade for EXISTING MealFlow MySQL databases that predate the
-- points ledger change. New databases do NOT need this file:
-- meal-auth-user/src/main/resources/schema.sql already contains the final structure.
--
-- Run once after taking a database backup. MySQL does not support IF NOT EXISTS for
-- ADD COLUMN, so re-running on an already-upgraded database fails with "Duplicate column
-- name 'points'"; that is expected and safe.

-- 1) User points balance column (source of truth for total points).
ALTER TABLE user_account
  ADD COLUMN points INT NOT NULL DEFAULT 0 AFTER status;

-- 2) Points movement ledger. UNIQUE (user_id, biz_type, biz_key) makes sign-in rewards
--    exactly-once even under concurrent requests.
CREATE TABLE IF NOT EXISTS points_ledger (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  biz_type VARCHAR(32) NOT NULL,
  biz_key VARCHAR(64) NOT NULL,
  delta INT NOT NULL,
  balance_after INT NOT NULL,
  create_time TIMESTAMP NOT NULL,
  UNIQUE KEY uk_points_ledger_biz (user_id, biz_type, biz_key),
  INDEX idx_points_ledger_user_time (user_id, create_time)
);

-- 3) Ledger id source follows the project's business_sequence convention.
INSERT INTO business_sequence (namespace, next_value) VALUES ('pointsLedger', 1000)
ON DUPLICATE KEY UPDATE next_value = next_value;

-- NOTE: points earned before this migration lived only in Redis (sign:user:*:points) and are
-- NOT backfilled automatically. For a demo environment an empty ledger is acceptable; for a
-- real deployment, migrate Redis counters per user before switching reads to MySQL, e.g.:
--   INSERT INTO points_ledger (id, user_id, biz_type, biz_key, delta, balance_after, create_time)
--   SELECT <seq>, id, 'LEGACY', 'redis-migration', <redis_points>, <redis_points>, NOW()
--   FROM user_account WHERE <redis_points> > 0;

-- One-time MySQL 8 migration from the legacy synchronous voucher claim schema.
-- The original claim and retry tables are retained as *_legacy_20260825 archives.

RENAME TABLE voucher_claim TO voucher_claim_legacy_20260825;

CREATE TABLE voucher_claim (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_key VARCHAR(256) NOT NULL,
  user_id BIGINT NOT NULL,
  voucher_id BIGINT NOT NULL,
  user_voucher_id BIGINT NULL,
  status VARCHAR(32) NOT NULL,
  last_error VARCHAR(512) NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  UNIQUE KEY uk_voucher_claim_event_key (event_key),
  UNIQUE KEY uk_voucher_claim_user_voucher (user_id, voucher_id),
  INDEX idx_voucher_claim_status (status)
);

INSERT INTO voucher_claim (
  event_key, user_id, voucher_id, user_voucher_id, status, last_error, create_time, update_time
)
SELECT
  CONCAT('seckill:', legacy.voucher_id, ':', legacy.user_id),
  legacy.user_id,
  legacy.voucher_id,
  MAX(user_voucher.id),
  CASE WHEN MAX(user_voucher.id) IS NULL THEN 'SOLD_OUT' ELSE 'CLAIMED' END,
  CASE WHEN MAX(user_voucher.id) IS NULL THEN 'MIGRATED_WITHOUT_USER_VOUCHER' ELSE NULL END,
  MIN(legacy.create_time),
  MAX(legacy.update_time)
FROM voucher_claim_legacy_20260825 legacy
LEFT JOIN user_voucher
  ON user_voucher.user_id = legacy.user_id AND user_voucher.voucher_id = legacy.voucher_id
GROUP BY legacy.user_id, legacy.voucher_id;

RENAME TABLE voucher_claim_retry TO voucher_claim_retry_legacy_20260825;

CREATE TABLE voucher_claim_retry (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_key VARCHAR(256) NOT NULL,
  user_id BIGINT NOT NULL,
  voucher_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  last_error VARCHAR(512) NULL,
  next_retry_time TIMESTAMP NOT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  UNIQUE KEY uk_voucher_claim_retry_event (event_key),
  INDEX idx_voucher_claim_retry_status_time (status, next_retry_time)
);

INSERT INTO voucher_claim_retry (
  event_key, user_id, voucher_id, status, retry_count, last_error, next_retry_time, create_time, update_time
)
SELECT
  CONCAT('seckill:', voucher_id, ':', user_id),
  user_id,
  voucher_id,
  CASE WHEN status = 'REPAIRED' THEN 'RECOVERED' WHEN status = 'DEAD' THEN 'DEAD' ELSE 'RETRY' END,
  retry_count,
  last_error,
  next_retry_time,
  create_time,
  update_time
FROM voucher_claim_retry_legacy_20260825;

ALTER TABLE voucher MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE user_voucher MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE voucher_lock MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;

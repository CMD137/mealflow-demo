-- Backfill durable claim facts for historical seckill vouchers that already exist
-- in user wallets. Safe to rerun: both voucher_claim unique keys remain authoritative.
INSERT IGNORE INTO voucher_claim (
  event_key,
  user_id,
  voucher_id,
  user_voucher_id,
  status,
  last_error,
  create_time,
  update_time
)
SELECT
  CONCAT('seckill:', uv.voucher_id, ':', uv.user_id),
  uv.user_id,
  uv.voucher_id,
  uv.id,
  'CLAIMED',
  NULL,
  uv.create_time,
  CURRENT_TIMESTAMP
FROM user_voucher uv
JOIN voucher v ON v.id = uv.voucher_id AND v.type = 'SECKILL'
LEFT JOIN voucher_claim vc
  ON vc.event_key = CONCAT('seckill:', uv.voucher_id, ':', uv.user_id)
  OR (vc.user_id = uv.user_id AND vc.voucher_id = uv.voucher_id)
WHERE vc.id IS NULL;

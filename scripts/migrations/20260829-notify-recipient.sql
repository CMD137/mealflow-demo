-- 为已有本地 MySQL 数据库补齐商户级通知收件人字段。
-- 本脚本只需执行一次；执行前请确认 notify_message 尚未存在同名索引。
ALTER TABLE notify_message
  ADD COLUMN recipient_type VARCHAR(16) NOT NULL DEFAULT 'USER' AFTER user_id,
  ADD COLUMN recipient_id BIGINT NULL AFTER recipient_type;

UPDATE notify_message
SET recipient_type = 'USER', recipient_id = user_id
WHERE recipient_id IS NULL;

ALTER TABLE notify_message
  ADD INDEX idx_notify_message_recipient_time (recipient_type, recipient_id, create_time);

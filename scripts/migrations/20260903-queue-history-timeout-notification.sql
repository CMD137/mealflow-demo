-- Apply once to existing MealFlow MySQL databases before deploying this revision.
-- New databases already receive these definitions from meal-queue/src/main/resources/schema.sql.
-- Confirm with `SHOW INDEX FROM queue_ticket` before re-running: MySQL reports a duplicate key name
-- after a successful previous application, which is expected and safe.

ALTER TABLE queue_ticket
  ADD INDEX idx_queue_ticket_user_create (user_id, create_time);

CREATE TABLE queue_timeout_notification (
  ticket_id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  last_error VARCHAR(512) NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  INDEX idx_queue_timeout_notification_status_ticket (status, ticket_id)
);

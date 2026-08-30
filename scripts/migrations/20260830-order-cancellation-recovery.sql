-- Apply once to an existing MealFlow database before deploying this revision.
-- This must return no rows before adding the unique key:
-- SELECT queue_ticket_id, COUNT(*) FROM customer_order
-- WHERE queue_ticket_id IS NOT NULL GROUP BY queue_ticket_id HAVING COUNT(*) > 1;
ALTER TABLE customer_order
  ADD UNIQUE KEY uk_customer_order_queue_ticket_id (queue_ticket_id);

ALTER TABLE order_saga_step
  ADD COLUMN promoted_ticket_id BIGINT NULL AFTER last_error,
  ADD COLUMN promoted_capacity_token_id BIGINT NULL AFTER promoted_ticket_id;

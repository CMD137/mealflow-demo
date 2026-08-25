ALTER TABLE capacity_token ADD COLUMN IF NOT EXISTS released_ticket_id BIGINT NULL;
ALTER TABLE capacity_token ADD COLUMN IF NOT EXISTS released_capacity_token_id BIGINT NULL;

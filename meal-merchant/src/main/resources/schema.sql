CREATE TABLE IF NOT EXISTS merchant (
  id BIGINT PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  business_status VARCHAR(32) NOT NULL,
  base_capacity INT NOT NULL,
  manual_factor DOUBLE NOT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL
);

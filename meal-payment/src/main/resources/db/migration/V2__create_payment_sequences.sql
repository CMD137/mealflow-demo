CREATE TABLE IF NOT EXISTS business_sequence (
  namespace VARCHAR(64) PRIMARY KEY,
  next_value BIGINT NOT NULL
);

INSERT INTO business_sequence (namespace, next_value) VALUES ('paymentOrder', 10000)
ON DUPLICATE KEY UPDATE next_value = next_value;
INSERT INTO business_sequence (namespace, next_value) VALUES ('paymentLocalEvent', 10000)
ON DUPLICATE KEY UPDATE next_value = next_value;

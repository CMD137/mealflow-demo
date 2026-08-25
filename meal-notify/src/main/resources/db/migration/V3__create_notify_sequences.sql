CREATE TABLE IF NOT EXISTS business_sequence (namespace VARCHAR(64) PRIMARY KEY, next_value BIGINT NOT NULL);
INSERT INTO business_sequence (namespace, next_value) VALUES ('notifyMessage', 10000)
ON DUPLICATE KEY UPDATE next_value = next_value;
INSERT INTO business_sequence (namespace, next_value) VALUES ('notifyDelivery', 10000)
ON DUPLICATE KEY UPDATE next_value = next_value;
INSERT INTO business_sequence (namespace, next_value) VALUES ('notifyConsumerRecord', 10000)
ON DUPLICATE KEY UPDATE next_value = next_value;

UPDATE business_sequence SET next_value = (SELECT COALESCE(MAX(id), 10000) FROM notify_message)
WHERE namespace = 'notifyMessage' AND next_value < (SELECT COALESCE(MAX(id), 10000) FROM notify_message);
UPDATE business_sequence SET next_value = (SELECT COALESCE(MAX(id), 10000) FROM notify_delivery)
WHERE namespace = 'notifyDelivery' AND next_value < (SELECT COALESCE(MAX(id), 10000) FROM notify_delivery);
UPDATE business_sequence SET next_value = (SELECT COALESCE(MAX(id), 10000) FROM consumer_record)
WHERE namespace = 'notifyConsumerRecord' AND next_value < (SELECT COALESCE(MAX(id), 10000) FROM consumer_record);

CREATE TABLE IF NOT EXISTS business_sequence (
  namespace VARCHAR(64) PRIMARY KEY,
  next_value BIGINT NOT NULL
);

INSERT INTO business_sequence (namespace, next_value) VALUES ('supportQaLog', 10000)
ON DUPLICATE KEY UPDATE next_value = next_value;

CREATE TABLE IF NOT EXISTS meal_support_qa_log (
  id BIGINT PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  trace_id VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(32) NOT NULL,
  question TEXT NOT NULL,
  answer TEXT NOT NULL,
  used_tools VARCHAR(512) NULL,
  citations VARCHAR(1024) NULL,
  llm_elapsed_ms BIGINT NULL,
  tool_elapsed_ms BIGINT NULL,
  model_name VARCHAR(64) NULL,
  create_time TIMESTAMP NOT NULL,
  INDEX idx_support_qa_session (session_id),
  INDEX idx_support_qa_user_time (user_id, create_time)
);

UPDATE business_sequence SET next_value = (SELECT COALESCE(MAX(id), 10000) FROM meal_support_qa_log)
WHERE namespace = 'supportQaLog' AND next_value < (SELECT COALESCE(MAX(id), 10000) FROM meal_support_qa_log);

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS campus_event_vectors (
  id VARCHAR(128) PRIMARY KEY,
  event_id BIGINT NOT NULL,
  embedding vector(1536),
  title VARCHAR(255),
  summary TEXT,
  event_type VARCHAR(64),
  source_type VARCHAR(64),
  college VARCHAR(128),
  start_time TIMESTAMP,
  status VARCHAR(32),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_campus_event_vectors_event_id
  ON campus_event_vectors (event_id);

CREATE INDEX IF NOT EXISTS idx_campus_event_vectors_meta
  ON campus_event_vectors (event_type, status, start_time);


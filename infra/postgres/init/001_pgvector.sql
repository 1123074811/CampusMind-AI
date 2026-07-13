CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS campus_ai_vectors (
  id TEXT PRIMARY KEY,
  content TEXT,
  metadata JSON,
  embedding vector(384)
);

CREATE INDEX IF NOT EXISTS idx_campus_ai_vectors_embedding
  ON campus_ai_vectors USING HNSW (embedding vector_cosine_ops);

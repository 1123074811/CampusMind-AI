-- 旧维度向量无法复用于新的多语言模型。
-- 迁移保留旧表为 campus_ai_vectors_legacy_1536；新表需触发全量向量回填。
BEGIN;

DO $$
DECLARE
  current_dimensions INTEGER;
BEGIN
  SELECT atttypmod
    INTO current_dimensions
    FROM pg_attribute
   WHERE attrelid = 'campus_ai_vectors'::regclass
     AND attname = 'embedding';

  IF current_dimensions = 384 THEN
    RETURN;
  END IF;
  IF current_dimensions <> 1536 THEN
    RAISE EXCEPTION 'Unsupported embedding dimensions: %', current_dimensions;
  END IF;
  IF to_regclass('public.campus_ai_vectors_legacy_1536') IS NOT NULL THEN
    RAISE EXCEPTION 'Legacy vector table already exists; inspect it before retrying';
  END IF;

  ALTER TABLE campus_ai_vectors RENAME TO campus_ai_vectors_legacy_1536;
  ALTER INDEX IF EXISTS idx_campus_ai_vectors_embedding
    RENAME TO idx_campus_ai_vectors_embedding_legacy_1536;
  CREATE TABLE campus_ai_vectors (
    id TEXT PRIMARY KEY,
    content TEXT,
    metadata JSON,
    embedding vector(384)
  );
  CREATE INDEX idx_campus_ai_vectors_embedding
    ON campus_ai_vectors USING HNSW (embedding vector_cosine_ops);
END $$;

COMMIT;

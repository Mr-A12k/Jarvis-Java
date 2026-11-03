BEGIN;

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE jarvis.documents (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_name name NOT NULL DEFAULT session_user,
    file_name text NOT NULL,
    file_path text NOT NULL,
    content_hash text NOT NULL,
    status text NOT NULL,
    chunk_count int NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 1,
    created_at timestamptz DEFAULT now(),
    updated_at timestamptz DEFAULT now(),
    CONSTRAINT uq_documents_hash UNIQUE (owner_name, content_hash)
);

CREATE TABLE jarvis.document_chunks (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id uuid NOT NULL REFERENCES jarvis.documents(id) ON DELETE CASCADE,
    owner_name name NOT NULL DEFAULT session_user,
    chunk_index int NOT NULL,
    content text NOT NULL,
    embedding vector(384) NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz DEFAULT now()
);

CREATE INDEX ON jarvis.document_chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX ON jarvis.document_chunks (document_id, chunk_index);
CREATE INDEX ON jarvis.documents (owner_name, created_at DESC);

ALTER TABLE jarvis.documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE jarvis.documents FORCE ROW LEVEL SECURITY;
CREATE POLICY document_owner ON jarvis.documents USING (owner_name = session_user) WITH CHECK (owner_name = session_user);

ALTER TABLE jarvis.document_chunks ENABLE ROW LEVEL SECURITY;
ALTER TABLE jarvis.document_chunks FORCE ROW LEVEL SECURITY;
CREATE POLICY chunk_owner ON jarvis.document_chunks USING (owner_name = session_user) WITH CHECK (owner_name = session_user);

CREATE OR REPLACE FUNCTION jarvis.match_chunks(
    p_embedding vector(384),
    p_match_count int DEFAULT 5,
    p_similarity_threshold float8 DEFAULT 0.5
)
RETURNS TABLE (
    id uuid,
    document_id uuid,
    chunk_index int,
    content text,
    metadata jsonb,
    similarity float8,
    file_path text,
    file_name text
)
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, jarvis
AS $$
BEGIN
    RETURN QUERY
    SELECT
        c.id,
        c.document_id,
        c.chunk_index,
        c.content,
        c.metadata,
        (1 - (c.embedding OPERATOR(public.<=>) p_embedding))::float8 AS similarity,
        d.file_path,
        d.file_name
    FROM jarvis.document_chunks c
    JOIN jarvis.documents d ON d.id = c.document_id
    WHERE c.owner_name = session_user AND d.owner_name = session_user
      AND (1 - (c.embedding OPERATOR(public.<=>) p_embedding)) >= p_similarity_threshold
    ORDER BY c.embedding OPERATOR(public.<=>) p_embedding ASC
    LIMIT p_match_count;
END;
$$;

REVOKE ALL ON ALL FUNCTIONS IN SCHEMA jarvis FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA jarvis FROM PUBLIC;

COMMIT;


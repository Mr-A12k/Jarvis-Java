BEGIN;
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


COMMIT;

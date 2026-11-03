-- Run in pgAdmin connected to the dedicated JARVIS database.
-- Adds Biometric Profiles table, vector similarity indexes (HNSW), and matching functions
-- for Face Recognition (128-d embeddings) and Voice Identification (192-d acoustic embeddings).

BEGIN;

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS jarvis.biometric_profiles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_name name NOT NULL DEFAULT session_user,
    user_name text NOT NULL CHECK (length(btrim(user_name)) BETWEEN 1 AND 100),
    role text NOT NULL DEFAULT 'OWNER' CHECK (role IN ('OWNER', 'FAMILY', 'GUEST', 'ADMIN')),
    is_primary_user boolean NOT NULL DEFAULT true,
    face_enrolled boolean NOT NULL DEFAULT false,
    face_embedding vector(128),
    face_samples_count int NOT NULL DEFAULT 0,
    voice_enrolled boolean NOT NULL DEFAULT false,
    voice_embedding vector(192),
    voice_samples_count int NOT NULL DEFAULT 0,
    confidence_threshold float8 NOT NULL DEFAULT 0.80,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_biometric_user UNIQUE (owner_name, user_name)
);

CREATE INDEX IF NOT EXISTS idx_biometric_owner ON jarvis.biometric_profiles(owner_name, user_name);
CREATE INDEX IF NOT EXISTS idx_biometric_face ON jarvis.biometric_profiles USING hnsw (face_embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_biometric_voice ON jarvis.biometric_profiles USING hnsw (voice_embedding vector_cosine_ops);

ALTER TABLE jarvis.biometric_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE jarvis.biometric_profiles FORCE ROW LEVEL SECURITY;

CREATE POLICY biometric_profile_owner ON jarvis.biometric_profiles
    USING (owner_name = session_user)
    WITH CHECK (owner_name = session_user);

-- Upsert biometric enrollment for a user
CREATE OR REPLACE FUNCTION jarvis.biometric_profile_upsert(
    p_user_name text,
    p_role text,
    p_is_primary boolean,
    p_face_embedding vector(128),
    p_voice_embedding vector(192),
    p_metadata jsonb DEFAULT '{}'::jsonb
)
RETURNS jarvis.biometric_profiles
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, jarvis
AS $$
DECLARE
    result jarvis.biometric_profiles;
BEGIN
    INSERT INTO jarvis.biometric_profiles (
        owner_name,
        user_name,
        role,
        is_primary_user,
        face_enrolled,
        face_embedding,
        face_samples_count,
        voice_enrolled,
        voice_embedding,
        voice_samples_count,
        metadata,
        updated_at
    )
    VALUES (
        session_user,
        p_user_name,
        COALESCE(p_role, 'OWNER'),
        COALESCE(p_is_primary, true),
        (p_face_embedding IS NOT NULL),
        p_face_embedding,
        CASE WHEN p_face_embedding IS NOT NULL THEN 1 ELSE 0 END,
        (p_voice_embedding IS NOT NULL),
        p_voice_embedding,
        CASE WHEN p_voice_embedding IS NOT NULL THEN 1 ELSE 0 END,
        COALESCE(p_metadata, '{}'::jsonb),
        now()
    )
    ON CONFLICT (owner_name, user_name) DO UPDATE SET
        role = EXCLUDED.role,
        is_primary_user = EXCLUDED.is_primary_user,
        face_enrolled = CASE WHEN EXCLUDED.face_embedding IS NOT NULL THEN true ELSE jarvis.biometric_profiles.face_enrolled END,
        face_embedding = COALESCE(EXCLUDED.face_embedding, jarvis.biometric_profiles.face_embedding),
        face_samples_count = CASE WHEN EXCLUDED.face_embedding IS NOT NULL THEN jarvis.biometric_profiles.face_samples_count + 1 ELSE jarvis.biometric_profiles.face_samples_count END,
        voice_enrolled = CASE WHEN EXCLUDED.voice_embedding IS NOT NULL THEN true ELSE jarvis.biometric_profiles.voice_enrolled END,
        voice_embedding = COALESCE(EXCLUDED.voice_embedding, jarvis.biometric_profiles.voice_embedding),
        voice_samples_count = CASE WHEN EXCLUDED.voice_embedding IS NOT NULL THEN jarvis.biometric_profiles.voice_samples_count + 1 ELSE jarvis.biometric_profiles.voice_samples_count END,
        metadata = jarvis.biometric_profiles.metadata || EXCLUDED.metadata,
        updated_at = now()
    RETURNING * INTO result;

    RETURN result;
END;
$$;

-- Match face embedding against enrolled profiles using cosine distance
CREATE OR REPLACE FUNCTION jarvis.biometric_match_face(
    p_embedding vector(128),
    p_similarity_threshold float8 DEFAULT 0.80
)
RETURNS TABLE (
    id uuid,
    user_name text,
    role text,
    similarity float8,
    is_primary_user boolean
)
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, jarvis
AS $$
BEGIN
    RETURN QUERY
    SELECT
        b.id,
        b.user_name,
        b.role,
        (1 - (b.face_embedding OPERATOR(public.<=>) p_embedding))::float8 AS similarity,
        b.is_primary_user
    FROM jarvis.biometric_profiles b
    WHERE b.owner_name = session_user
      AND b.face_enrolled = true
      AND (1 - (b.face_embedding OPERATOR(public.<=>) p_embedding)) >= p_similarity_threshold
    ORDER BY b.face_embedding OPERATOR(public.<=>) p_embedding ASC
    LIMIT 1;
END;
$$;

-- Match voice acoustic embedding against enrolled profiles using cosine distance
CREATE OR REPLACE FUNCTION jarvis.biometric_match_voice(
    p_embedding vector(192),
    p_similarity_threshold float8 DEFAULT 0.78
)
RETURNS TABLE (
    id uuid,
    user_name text,
    role text,
    similarity float8,
    is_primary_user boolean
)
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, jarvis
AS $$
BEGIN
    RETURN QUERY
    SELECT
        b.id,
        b.user_name,
        b.role,
        (1 - (b.voice_embedding OPERATOR(public.<=>) p_embedding))::float8 AS similarity,
        b.is_primary_user
    FROM jarvis.biometric_profiles b
    WHERE b.owner_name = session_user
      AND b.voice_enrolled = true
      AND (1 - (b.voice_embedding OPERATOR(public.<=>) p_embedding)) >= p_similarity_threshold
    ORDER BY b.voice_embedding OPERATOR(public.<=>) p_embedding ASC
    LIMIT 1;
END;
$$;

REVOKE ALL ON ALL FUNCTIONS IN SCHEMA jarvis FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA jarvis FROM PUBLIC;

COMMIT;

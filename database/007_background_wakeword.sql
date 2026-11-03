-- Run in pgAdmin connected to the dedicated JARVIS database.
-- Adds Background Wake-Word configuration, wake detection event audit log,
-- and management functions for native system tray immersion.

BEGIN;

CREATE TABLE IF NOT EXISTS jarvis.wakeword_config (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_name name NOT NULL DEFAULT session_user,
    enabled boolean NOT NULL DEFAULT true,
    engine text NOT NULL DEFAULT 'openwakeword' CHECK (engine IN ('openwakeword', 'silero_vad', 'acoustic')),
    model_name text NOT NULL DEFAULT 'hey_jarvis',
    sensitivity float8 NOT NULL DEFAULT 0.50 CHECK (sensitivity BETWEEN 0.05 AND 0.99),
    vad_threshold float8 NOT NULL DEFAULT 0.50 CHECK (vad_threshold BETWEEN 0.05 AND 0.99),
    auto_popup boolean NOT NULL DEFAULT true,
    chime_sound boolean NOT NULL DEFAULT true,
    audio_device_name text DEFAULT 'Default System Input',
    bring_to_front boolean NOT NULL DEFAULT true,
    start_with_windows boolean NOT NULL DEFAULT false,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_wakeword_owner UNIQUE (owner_name)
);

CREATE TABLE IF NOT EXISTS jarvis.wakeword_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_name name NOT NULL DEFAULT session_user,
    wake_word text NOT NULL DEFAULT 'hey_jarvis',
    confidence float8 NOT NULL DEFAULT 0.50,
    engine text NOT NULL DEFAULT 'openwakeword',
    audio_duration_ms int DEFAULT 1280,
    foreground_app text DEFAULT 'desktop',
    action_taken text NOT NULL DEFAULT 'POPUP_AND_LISTEN',
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_wakeword_events_owner ON jarvis.wakeword_events(owner_name, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_wakeword_events_word ON jarvis.wakeword_events(wake_word);

ALTER TABLE jarvis.wakeword_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE jarvis.wakeword_config FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS wakeword_config_owner ON jarvis.wakeword_config;
CREATE POLICY wakeword_config_owner ON jarvis.wakeword_config
    USING (owner_name = session_user)
    WITH CHECK (owner_name = session_user);

ALTER TABLE jarvis.wakeword_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE jarvis.wakeword_events FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS wakeword_events_owner ON jarvis.wakeword_events;
CREATE POLICY wakeword_events_owner ON jarvis.wakeword_events
    USING (owner_name = session_user)
    WITH CHECK (owner_name = session_user);

-- Function: Retrieve active user wake-word configuration (or initialize defaults)
CREATE OR REPLACE FUNCTION jarvis.wakeword_config_get()
RETURNS jarvis.wakeword_config
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, jarvis
AS $$
DECLARE
    cfg jarvis.wakeword_config;
BEGIN
    SELECT * INTO cfg FROM jarvis.wakeword_config WHERE owner_name = session_user;
    IF NOT FOUND THEN
        INSERT INTO jarvis.wakeword_config (owner_name)
        VALUES (session_user)
        RETURNING * INTO cfg;
    END IF;
    RETURN cfg;
END;
$$;

-- Function: Upsert wake-word settings
CREATE OR REPLACE FUNCTION jarvis.wakeword_config_upsert(
    p_enabled boolean DEFAULT true,
    p_engine text DEFAULT 'openwakeword',
    p_model_name text DEFAULT 'hey_jarvis',
    p_sensitivity float8 DEFAULT 0.50,
    p_vad_threshold float8 DEFAULT 0.50,
    p_auto_popup boolean DEFAULT true,
    p_chime_sound boolean DEFAULT true,
    p_audio_device_name text DEFAULT 'Default System Input',
    p_bring_to_front boolean DEFAULT true,
    p_start_with_windows boolean DEFAULT false,
    p_metadata jsonb DEFAULT '{}'::jsonb
)
RETURNS jarvis.wakeword_config
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, jarvis
AS $$
DECLARE
    result jarvis.wakeword_config;
BEGIN
    INSERT INTO jarvis.wakeword_config (
        owner_name,
        enabled,
        engine,
        model_name,
        sensitivity,
        vad_threshold,
        auto_popup,
        chime_sound,
        audio_device_name,
        bring_to_front,
        start_with_windows,
        metadata,
        updated_at
    )
    VALUES (
        session_user,
        COALESCE(p_enabled, true),
        COALESCE(p_engine, 'openwakeword'),
        COALESCE(p_model_name, 'hey_jarvis'),
        COALESCE(p_sensitivity, 0.50),
        COALESCE(p_vad_threshold, 0.50),
        COALESCE(p_auto_popup, true),
        COALESCE(p_chime_sound, true),
        COALESCE(p_audio_device_name, 'Default System Input'),
        COALESCE(p_bring_to_front, true),
        COALESCE(p_start_with_windows, false),
        COALESCE(p_metadata, '{}'::jsonb),
        now()
    )
    ON CONFLICT (owner_name)
    DO UPDATE SET
        enabled = EXCLUDED.enabled,
        engine = EXCLUDED.engine,
        model_name = EXCLUDED.model_name,
        sensitivity = EXCLUDED.sensitivity,
        vad_threshold = EXCLUDED.vad_threshold,
        auto_popup = EXCLUDED.auto_popup,
        chime_sound = EXCLUDED.chime_sound,
        audio_device_name = EXCLUDED.audio_device_name,
        bring_to_front = EXCLUDED.bring_to_front,
        start_with_windows = EXCLUDED.start_with_windows,
        metadata = EXCLUDED.metadata,
        updated_at = now()
    RETURNING * INTO result;

    RETURN result;
END;
$$;

-- Function: Log a background wake-word trigger event
CREATE OR REPLACE FUNCTION jarvis.wakeword_event_log(
    p_wake_word text DEFAULT 'hey_jarvis',
    p_confidence float8 DEFAULT 0.50,
    p_engine text DEFAULT 'openwakeword',
    p_audio_duration_ms int DEFAULT 1280,
    p_foreground_app text DEFAULT 'desktop',
    p_action_taken text DEFAULT 'POPUP_AND_LISTEN',
    p_metadata jsonb DEFAULT '{}'::jsonb
)
RETURNS jarvis.wakeword_events
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, jarvis
AS $$
DECLARE
    entry jarvis.wakeword_events;
BEGIN
    INSERT INTO jarvis.wakeword_events (
        owner_name,
        wake_word,
        confidence,
        engine,
        audio_duration_ms,
        foreground_app,
        action_taken,
        metadata,
        created_at
    )
    VALUES (
        session_user,
        COALESCE(p_wake_word, 'hey_jarvis'),
        COALESCE(p_confidence, 0.50),
        COALESCE(p_engine, 'openwakeword'),
        COALESCE(p_audio_duration_ms, 1280),
        COALESCE(p_foreground_app, 'desktop'),
        COALESCE(p_action_taken, 'POPUP_AND_LISTEN'),
        COALESCE(p_metadata, '{}'::jsonb),
        now()
    )
    RETURNING * INTO entry;

    RETURN entry;
END;
$$;

-- Function: Read recent wake-word events
CREATE OR REPLACE FUNCTION jarvis.wakeword_event_read(
    p_limit int DEFAULT 50,
    p_offset int DEFAULT 0
)
RETURNS SETOF jarvis.wakeword_events
LANGUAGE sql
SECURITY INVOKER
SET search_path = pg_catalog, jarvis
STABLE
AS $$
    SELECT *
    FROM jarvis.wakeword_events
    WHERE owner_name = session_user
    ORDER BY created_at DESC
    LIMIT LEAST(GREATEST(p_limit, 1), 200)
    OFFSET GREATEST(p_offset, 0);
$$;

-- Function: Aggregate wake-word statistics
CREATE OR REPLACE FUNCTION jarvis.wakeword_stats_get()
RETURNS TABLE (
    total_wakes bigint,
    wakes_today bigint,
    average_confidence float8,
    primary_engine text,
    last_wake_at timestamptz
)
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, jarvis
STABLE
AS $$
BEGIN
    RETURN QUERY
    SELECT
        COUNT(*)::bigint AS total_wakes,
        COUNT(*) FILTER (WHERE created_at >= date_trunc('day', now()))::bigint AS wakes_today,
        COALESCE(AVG(confidence)::float8, 0.0) AS average_confidence,
        COALESCE(
            (SELECT engine FROM jarvis.wakeword_events WHERE owner_name = session_user GROUP BY engine ORDER BY COUNT(*) DESC LIMIT 1),
            'openwakeword'
        ) AS primary_engine,
        MAX(created_at) AS last_wake_at
    FROM jarvis.wakeword_events
    WHERE owner_name = session_user;
END;
$$;

COMMIT;

-- Run in pgAdmin Query Tool connected to the dedicated JARVIS database.
-- Migration 008: Autonomous Coding Agent Engine, Long-Running Sessions, and File Diffs.
-- Adds jarvis.coding_sessions, jarvis.coding_steps, and jarvis.coding_file_diffs tables
-- with Row Level Security (RLS), indexes, and stored procedures for autonomous code engineering.

BEGIN;

CREATE SCHEMA IF NOT EXISTS jarvis;

-- 1. Coding Sessions Table (Supports multi-hour long tasks & resume)
CREATE TABLE IF NOT EXISTS jarvis.coding_sessions (
    id text PRIMARY KEY,
    owner_name name NOT NULL DEFAULT session_user,
    title text NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 500),
    objective text NOT NULL CHECK (length(btrim(objective)) BETWEEN 1 AND 10000),
    workspace_root text NOT NULL,
    model text NOT NULL DEFAULT 'qwen2.5-coder:1.5b',
    search_provider text NOT NULL DEFAULT 'AUTO',
    status text NOT NULL CHECK (status IN (
        'INITIALIZING', 'INDEXING', 'PLANNING', 'RUNNING', 
        'WAITING_APPROVAL', 'TESTING', 'PAUSED', 'COMPLETED', 
        'FAILED', 'CANCELLED'
    )),
    total_turns integer NOT NULL DEFAULT 0,
    files_modified text[] NOT NULL DEFAULT '{}',
    current_checkpoint jsonb NOT NULL DEFAULT '{}'::jsonb,
    final_summary text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz
);

-- 2. Coding Discrete Execution Steps Table (Turn-by-turn ReAct Loop Audit)
CREATE TABLE IF NOT EXISTS jarvis.coding_steps (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id text NOT NULL REFERENCES jarvis.coding_sessions(id) ON DELETE CASCADE,
    step_index integer NOT NULL,
    step_type text NOT NULL CHECK (step_type IN (
        'THOUGHT', 'CODEBASE_SEARCH', 'WEB_RESEARCH', 'READ_FILE', 
        'WRITE_FILE', 'EDIT_FILE', 'RUN_COMMAND', 'TEST_RUN', 
        'SELF_CORRECTION', 'SYNTHESIS'
    )),
    action_tool text,
    tool_input jsonb NOT NULL DEFAULT '{}'::jsonb,
    tool_output text,
    thought text,
    status text NOT NULL CHECK (status IN (
        'PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'WAITING_APPROVAL', 'SKIPPED'
    )),
    error_message text,
    duration_ms bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    CONSTRAINT uq_coding_step UNIQUE (session_id, step_index)
);

-- 3. Coding File Diffs & Rollback Snapshot Table
CREATE TABLE IF NOT EXISTS jarvis.coding_file_diffs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id text NOT NULL REFERENCES jarvis.coding_sessions(id) ON DELETE CASCADE,
    step_index integer NOT NULL,
    file_path text NOT NULL,
    change_type text NOT NULL CHECK (change_type IN ('CREATE', 'MODIFY', 'DELETE')),
    original_content text, -- Stores exact prior content for 100% reliable 1-click rollback
    new_content text,
    diff_unified text,
    status text NOT NULL DEFAULT 'APPLIED' CHECK (status IN ('PROPOSED', 'APPLIED', 'REVERTED', 'REJECTED')),
    created_at timestamptz NOT NULL DEFAULT now()
);

-- 4. High-Performance Query Indexes
CREATE INDEX IF NOT EXISTS idx_coding_sessions_owner ON jarvis.coding_sessions(owner_name, created_at DESC, id);
CREATE INDEX IF NOT EXISTS idx_coding_steps_session ON jarvis.coding_steps(session_id, step_index);
CREATE INDEX IF NOT EXISTS idx_coding_file_diffs_session ON jarvis.coding_file_diffs(session_id, file_path);

-- 5. Stored Procedures / Helper Functions
CREATE OR REPLACE FUNCTION jarvis.coding_session_upsert(
    p_id text,
    p_title text,
    p_objective text,
    p_workspace_root text,
    p_model text,
    p_search_provider text,
    p_status text,
    p_total_turns integer DEFAULT 0,
    p_files_modified text[] DEFAULT '{}',
    p_checkpoint jsonb DEFAULT '{}'::jsonb,
    p_final_summary text DEFAULT NULL
)
RETURNS jarvis.coding_sessions
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_record jarvis.coding_sessions;
BEGIN
    INSERT INTO jarvis.coding_sessions (
        id, title, objective, workspace_root, model, search_provider, status,
        total_turns, files_modified, current_checkpoint, final_summary,
        updated_at, completed_at
    )
    VALUES (
        p_id, p_title, p_objective, p_workspace_root, p_model, COALESCE(p_search_provider, 'AUTO'), p_status,
        p_total_turns, p_files_modified, p_checkpoint, p_final_summary,
        now(),
        CASE WHEN p_status IN ('COMPLETED', 'FAILED', 'CANCELLED') THEN now() ELSE NULL END
    )
    ON CONFLICT (id) DO UPDATE SET
        status = EXCLUDED.status,
        total_turns = EXCLUDED.total_turns,
        files_modified = EXCLUDED.files_modified,
        current_checkpoint = EXCLUDED.current_checkpoint,
        final_summary = COALESCE(EXCLUDED.final_summary, jarvis.coding_sessions.final_summary),
        updated_at = now(),
        completed_at = CASE WHEN EXCLUDED.status IN ('COMPLETED', 'FAILED', 'CANCELLED') THEN now() ELSE jarvis.coding_sessions.completed_at END
    RETURNING * INTO v_record;

    RETURN v_record;
END;
$$;

CREATE OR REPLACE FUNCTION jarvis.coding_step_log(
    p_session_id text,
    p_step_index integer,
    p_step_type text,
    p_action_tool text,
    p_tool_input jsonb,
    p_thought text,
    p_status text,
    p_tool_output text DEFAULT NULL,
    p_error_message text DEFAULT NULL,
    p_duration_ms bigint DEFAULT NULL
)
RETURNS jarvis.coding_steps
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_record jarvis.coding_steps;
BEGIN
    INSERT INTO jarvis.coding_steps (
        session_id, step_index, step_type, action_tool, tool_input,
        thought, status, tool_output, error_message, duration_ms,
        completed_at
    )
    VALUES (
        p_session_id, p_step_index, p_step_type, p_action_tool, p_tool_input,
        p_thought, p_status, p_tool_output, p_error_message, p_duration_ms,
        CASE WHEN p_status IN ('SUCCESS', 'FAILED', 'SKIPPED') THEN now() ELSE NULL END
    )
    ON CONFLICT (session_id, step_index) DO UPDATE SET
        status = EXCLUDED.status,
        tool_output = COALESCE(EXCLUDED.tool_output, jarvis.coding_steps.tool_output),
        error_message = COALESCE(EXCLUDED.error_message, jarvis.coding_steps.error_message),
        duration_ms = COALESCE(EXCLUDED.duration_ms, jarvis.coding_steps.duration_ms),
        completed_at = CASE WHEN EXCLUDED.status IN ('SUCCESS', 'FAILED', 'SKIPPED') THEN now() ELSE jarvis.coding_steps.completed_at END
    RETURNING * INTO v_record;

    RETURN v_record;
END;
$$;

CREATE OR REPLACE FUNCTION jarvis.coding_diff_record(
    p_session_id text,
    p_step_index integer,
    p_file_path text,
    p_change_type text,
    p_original_content text,
    p_new_content text,
    p_diff_unified text,
    p_status text DEFAULT 'APPLIED'
)
RETURNS jarvis.coding_file_diffs
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_record jarvis.coding_file_diffs;
BEGIN
    INSERT INTO jarvis.coding_file_diffs (
        session_id, step_index, file_path, change_type,
        original_content, new_content, diff_unified, status
    )
    VALUES (
        p_session_id, p_step_index, p_file_path, p_change_type,
        p_original_content, p_new_content, p_diff_unified, p_status
    )
    RETURNING * INTO v_record;

    RETURN v_record;
END;
$$;

-- Enable Row Level Security (RLS)
ALTER TABLE jarvis.coding_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE jarvis.coding_steps ENABLE ROW LEVEL SECURITY;
ALTER TABLE jarvis.coding_file_diffs ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS coding_sessions_owner_policy ON jarvis.coding_sessions;
CREATE POLICY coding_sessions_owner_policy ON jarvis.coding_sessions
    FOR ALL USING (owner_name = session_user);

COMMIT;

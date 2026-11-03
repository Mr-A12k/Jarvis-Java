-- Run in pgAdmin or psql connected to the dedicated JARVIS database.
-- Adds Desktop GUI Agent Automation schema:
-- 1. jarvis.desktop_workflows: saved macro sequences, trigger phrases, action payloads
-- 2. jarvis.desktop_action_audit: audit trail of executed mouse, keyboard, and window operations
-- 3. jarvis.desktop_custom_apps: user-registered custom application shortcuts and paths
-- 4. Stored procedures for recording execution and logging GUI actions

BEGIN;

-- 1. Desktop Automation Workflows (Macros & Multi-step Sequences)
CREATE TABLE IF NOT EXISTS jarvis.desktop_workflows (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_name name NOT NULL DEFAULT session_user,
    name text NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 150),
    description text,
    trigger_pattern text,
    actions jsonb NOT NULL DEFAULT '[]'::jsonb,
    is_favorite boolean NOT NULL DEFAULT false,
    execution_count int NOT NULL DEFAULT 0,
    last_executed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_desktop_workflow_name UNIQUE (owner_name, name)
);

CREATE INDEX IF NOT EXISTS idx_desktop_workflows_owner ON jarvis.desktop_workflows(owner_name, name);
CREATE INDEX IF NOT EXISTS idx_desktop_workflows_fav ON jarvis.desktop_workflows(owner_name, is_favorite);

ALTER TABLE jarvis.desktop_workflows ENABLE ROW LEVEL SECURITY;
ALTER TABLE jarvis.desktop_workflows FORCE ROW LEVEL SECURITY;

CREATE POLICY desktop_workflows_owner ON jarvis.desktop_workflows
    USING (owner_name = session_user)
    WITH CHECK (owner_name = session_user);

-- 2. Desktop GUI Action Audit Trail
CREATE TABLE IF NOT EXISTS jarvis.desktop_action_audit (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_name name NOT NULL DEFAULT session_user,
    workflow_id uuid REFERENCES jarvis.desktop_workflows(id) ON DELETE SET NULL,
    action_type text NOT NULL CHECK (action_type IN (
        'MOUSE_MOVE', 'MOUSE_CLICK', 'MOUSE_DRAG', 'MOUSE_SCROLL',
        'TYPE_TEXT', 'KEY_PRESS', 'HOTKEY',
        'LAUNCH_APP', 'FOCUS_WINDOW', 'CLOSE_WINDOW', 'MINIMIZE_WINDOW', 'MAXIMIZE_WINDOW', 'SHOW_DESKTOP',
        'SEQUENCE_START', 'SEQUENCE_COMPLETE'
    )),
    target text,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    status text NOT NULL DEFAULT 'SUCCESS' CHECK (status IN ('SUCCESS', 'FAILED', 'BLOCKED', 'CANCELLED')),
    duration_ms int DEFAULT 0,
    error_message text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_desktop_audit_owner_time ON jarvis.desktop_action_audit(owner_name, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_desktop_audit_action ON jarvis.desktop_action_audit(action_type);

ALTER TABLE jarvis.desktop_action_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE jarvis.desktop_action_audit FORCE ROW LEVEL SECURITY;

CREATE POLICY desktop_audit_owner ON jarvis.desktop_action_audit
    USING (owner_name = session_user)
    WITH CHECK (owner_name = session_user);

-- 3. Custom Registered Desktop Applications
CREATE TABLE IF NOT EXISTS jarvis.desktop_custom_apps (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_name name NOT NULL DEFAULT session_user,
    app_name text NOT NULL CHECK (length(btrim(app_name)) BETWEEN 1 AND 100),
    category text NOT NULL DEFAULT 'Custom',
    executable_path text NOT NULL,
    arguments text,
    working_dir text,
    icon text DEFAULT 'AppWindow',
    voice_triggers text[] DEFAULT '{}'::text[],
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_desktop_custom_app UNIQUE (owner_name, app_name)
);

CREATE INDEX IF NOT EXISTS idx_desktop_custom_apps_owner ON jarvis.desktop_custom_apps(owner_name, app_name);

ALTER TABLE jarvis.desktop_custom_apps ENABLE ROW LEVEL SECURITY;
ALTER TABLE jarvis.desktop_custom_apps FORCE ROW LEVEL SECURITY;

CREATE POLICY desktop_custom_apps_owner ON jarvis.desktop_custom_apps
    USING (owner_name = session_user)
    WITH CHECK (owner_name = session_user);

-- 4. Upsert Desktop Workflow Function
CREATE OR REPLACE FUNCTION jarvis.desktop_workflow_upsert(
    p_name text,
    p_description text,
    p_trigger_pattern text,
    p_actions jsonb,
    p_is_favorite boolean DEFAULT false
)
RETURNS jarvis.desktop_workflows
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, jarvis
AS $$
DECLARE
    result jarvis.desktop_workflows;
BEGIN
    INSERT INTO jarvis.desktop_workflows (
        owner_name,
        name,
        description,
        trigger_pattern,
        actions,
        is_favorite,
        updated_at
    )
    VALUES (
        session_user,
        p_name,
        p_description,
        p_trigger_pattern,
        COALESCE(p_actions, '[]'::jsonb),
        COALESCE(p_is_favorite, false),
        now()
    )
    ON CONFLICT (owner_name, name) DO UPDATE SET
        description = EXCLUDED.description,
        trigger_pattern = EXCLUDED.trigger_pattern,
        actions = EXCLUDED.actions,
        is_favorite = EXCLUDED.is_favorite,
        updated_at = now()
    RETURNING * INTO result;

    RETURN result;
END;
$$;

-- 5. Record Workflow Execution Function
CREATE OR REPLACE FUNCTION jarvis.desktop_workflow_record_execution(
    p_workflow_id uuid,
    p_duration_ms int DEFAULT 0,
    p_status text DEFAULT 'SUCCESS'
)
RETURNS void
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, jarvis
AS $$
BEGIN
    UPDATE jarvis.desktop_workflows
    SET execution_count = execution_count + 1,
        last_executed_at = now(),
        updated_at = now()
    WHERE id = p_workflow_id AND owner_name = session_user;
END;
$$;

-- 6. Log Desktop Action Function
CREATE OR REPLACE FUNCTION jarvis.desktop_log_action(
    p_action_type text,
    p_target text,
    p_payload jsonb DEFAULT '{}'::jsonb,
    p_status text DEFAULT 'SUCCESS',
    p_duration_ms int DEFAULT 0,
    p_error text DEFAULT NULL,
    p_workflow_id uuid DEFAULT NULL
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, jarvis
AS $$
DECLARE
    new_id uuid;
BEGIN
    INSERT INTO jarvis.desktop_action_audit (
        owner_name,
        workflow_id,
        action_type,
        target,
        payload,
        status,
        duration_ms,
        error_message,
        created_at
    )
    VALUES (
        session_user,
        p_workflow_id,
        p_action_type,
        p_target,
        COALESCE(p_payload, '{}'::jsonb),
        COALESCE(p_status, 'SUCCESS'),
        COALESCE(p_duration_ms, 0),
        p_error,
        now()
    )
    RETURNING id INTO new_id;

    RETURN new_id;
END;
$$;

-- Seed default initial workflow templates
INSERT INTO jarvis.desktop_workflows (owner_name, name, description, trigger_pattern, actions, is_favorite)
VALUES
(
    session_user,
    'Quick Meeting Notes in Notepad',
    'Launches Notepad and prepares a formatted meeting notes header',
    'open notepad and take meeting notes',
    '[
        {"type": "LAUNCH_APP", "target": "notepad.exe", "delayMs": 800},
        {"type": "FOCUS_WINDOW", "target": "Notepad", "delayMs": 400},
        {"type": "TYPE_TEXT", "text": "=== JARVIS MEETING NOTES ===\nDate: "},
        {"type": "TYPE_TEXT", "text": "Attendees: Team\nTopic: Project Review\n\nNotes:\n- "},
        {"type": "KEY_PRESS", "key": "ENTER"}
    ]'::jsonb,
    true
),
(
    session_user,
    'Developer Workspace Setup',
    'Brings VS Code to the front, opens the integrated terminal, and shows status',
    'setup developer workspace',
    '[
        {"type": "LAUNCH_APP", "target": "code", "delayMs": 1000},
        {"type": "FOCUS_WINDOW", "target": "Visual Studio Code", "delayMs": 500},
        {"type": "HOTKEY", "hotkey": "Ctrl+`", "delayMs": 600}
    ]'::jsonb,
    true
),
(
    session_user,
    'Show Desktop & Clear Workspace',
    'Minimizes all active foreground windows to show clean desktop',
    'show desktop',
    '[
        {"type": "SHOW_DESKTOP", "delayMs": 300}
    ]'::jsonb,
    false
),
(
    session_user,
    'Save Active Document',
    'Sends Ctrl+S hotkey to save current active file or document',
    'save active file',
    '[
        {"type": "HOTKEY", "hotkey": "Ctrl+S", "delayMs": 200}
    ]'::jsonb,
    true
)
ON CONFLICT (owner_name, name) DO NOTHING;

COMMIT;

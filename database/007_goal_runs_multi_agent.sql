-- Run in pgAdmin Query Tool connected to the dedicated JARVIS database.
-- Migration 007: Autonomous Goal Planner with Multi-Agent Parallel Task Execution.
-- Adds jarvis.goal_runs and jarvis.goal_steps tables with Row Level Security (RLS),
-- indexes, and stored procedures for sub-agents (Researcher, Coder, Validator, System Analyst).

BEGIN;

CREATE SCHEMA IF NOT EXISTS jarvis;

-- 1. Autonomous Goal Runs Table
CREATE TABLE IF NOT EXISTS jarvis.goal_runs (
    id text PRIMARY KEY,
    owner_name name NOT NULL DEFAULT session_user,
    title text NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 500),
    objective text NOT NULL CHECK (length(btrim(objective)) BETWEEN 1 AND 4000),
    execution_mode text NOT NULL DEFAULT 'MULTI_AGENT_PARALLEL' CHECK (execution_mode IN ('SEQUENTIAL', 'MULTI_AGENT_PARALLEL')),
    active_agents text[] NOT NULL DEFAULT '{}',
    status text NOT NULL CHECK (status IN ('PLANNING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'WAITING_APPROVAL')),
    final_report text,
    version integer NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz
);

-- 2. Multi-Agent Discrete Plan Steps Table
CREATE TABLE IF NOT EXISTS jarvis.goal_steps (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    goal_id text NOT NULL REFERENCES jarvis.goal_runs(id) ON DELETE CASCADE,
    step_index int NOT NULL,
    stage int NOT NULL DEFAULT 1,
    assigned_agent text NOT NULL CHECK (assigned_agent IN ('RESEARCHER', 'CODER', 'VALIDATOR', 'SYSTEM_ANALYST', 'COORDINATOR', 'GENERAL')),
    agent_name text NOT NULL,
    title text NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 500),
    action_type text NOT NULL,
    input_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    status text NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'WAITING_APPROVAL')),
    output text,
    error text,
    duration_ms bigint,
    dependencies int[] NOT NULL DEFAULT '{}',
    parallel_group int NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    CONSTRAINT uq_goal_step UNIQUE (goal_id, step_index)
);

-- 3. High Performance Query Indexes
CREATE INDEX IF NOT EXISTS idx_goal_runs_owner ON jarvis.goal_runs(owner_name, created_at DESC, id);
CREATE INDEX IF NOT EXISTS idx_goal_steps_goal ON jarvis.goal_steps(goal_id, step_index);
CREATE INDEX IF NOT EXISTS idx_goal_steps_agent ON jarvis.goal_steps(assigned_agent);
CREATE INDEX IF NOT EXISTS idx_goal_steps_stage ON jarvis.goal_steps(stage);

-- 4. Row Level Security Policies
ALTER TABLE jarvis.goal_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE jarvis.goal_runs FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE schemaname = 'jarvis' AND tablename = 'goal_runs' AND policyname = 'goal_run_owner'
    ) THEN
        CREATE POLICY goal_run_owner ON jarvis.goal_runs
            USING (owner_name = session_user)
            WITH CHECK (owner_name = session_user);
    END IF;
END;
$$;

ALTER TABLE jarvis.goal_steps ENABLE ROW LEVEL SECURITY;
ALTER TABLE jarvis.goal_steps FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE schemaname = 'jarvis' AND tablename = 'goal_steps' AND policyname = 'goal_step_owner'
    ) THEN
        CREATE POLICY goal_step_owner ON jarvis.goal_steps
            USING (
                EXISTS (
                    SELECT 1 FROM jarvis.goal_runs g
                    WHERE g.id = goal_id AND g.owner_name = session_user
                )
            )
            WITH CHECK (
                EXISTS (
                    SELECT 1 FROM jarvis.goal_runs g
                    WHERE g.id = goal_id AND g.owner_name = session_user
                )
            );
    END IF;
END;
$$;

-- 5. Stored Procedures and Functions

-- Upsert Goal Run
CREATE OR REPLACE FUNCTION jarvis.goal_run_upsert(
    p_id text,
    p_title text,
    p_objective text,
    p_execution_mode text,
    p_active_agents text[],
    p_status text,
    p_final_report text DEFAULT NULL
)
RETURNS jarvis.goal_runs
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, jarvis
AS $$
DECLARE
    result jarvis.goal_runs;
BEGIN
    INSERT INTO jarvis.goal_runs (
        id, owner_name, title, objective, execution_mode, active_agents, status, final_report, completed_at
    )
    VALUES (
        p_id, session_user, p_title, p_objective,
        COALESCE(p_execution_mode, 'MULTI_AGENT_PARALLEL'),
        COALESCE(p_active_agents, '{}'),
        p_status,
        p_final_report,
        CASE WHEN p_status IN ('COMPLETED', 'FAILED', 'CANCELLED') THEN now() ELSE NULL END
    )
    ON CONFLICT (id) DO UPDATE SET
        title = EXCLUDED.title,
        objective = EXCLUDED.objective,
        execution_mode = EXCLUDED.execution_mode,
        active_agents = EXCLUDED.active_agents,
        status = EXCLUDED.status,
        final_report = COALESCE(EXCLUDED.final_report, jarvis.goal_runs.final_report),
        version = jarvis.goal_runs.version + 1,
        completed_at = CASE WHEN EXCLUDED.status IN ('COMPLETED', 'FAILED', 'CANCELLED') THEN now() ELSE jarvis.goal_runs.completed_at END
    RETURNING * INTO result;

    RETURN result;
END;
$$;

-- Upsert Goal Step for Sub-Agents
CREATE OR REPLACE FUNCTION jarvis.goal_step_upsert(
    p_goal_id text,
    p_step_index int,
    p_stage int,
    p_assigned_agent text,
    p_agent_name text,
    p_title text,
    p_action_type text,
    p_input_json jsonb,
    p_status text,
    p_output text DEFAULT NULL,
    p_error text DEFAULT NULL,
    p_duration_ms bigint DEFAULT NULL,
    p_dependencies int[] DEFAULT '{}',
    p_parallel_group int DEFAULT 1
)
RETURNS jarvis.goal_steps
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, jarvis
AS $$
DECLARE
    result jarvis.goal_steps;
BEGIN
    INSERT INTO jarvis.goal_steps (
        goal_id, step_index, stage, assigned_agent, agent_name, title, action_type,
        input_json, status, output, error, duration_ms, dependencies, parallel_group,
        completed_at
    )
    VALUES (
        p_goal_id, p_step_index, COALESCE(p_stage, 1), p_assigned_agent, p_agent_name,
        p_title, p_action_type, COALESCE(p_input_json, '{}'::jsonb), p_status,
        p_output, p_error, p_duration_ms, COALESCE(p_dependencies, '{}'), COALESCE(p_parallel_group, 1),
        CASE WHEN p_status IN ('COMPLETED', 'FAILED') THEN now() ELSE NULL END
    )
    ON CONFLICT (goal_id, step_index) DO UPDATE SET
        stage = EXCLUDED.stage,
        assigned_agent = EXCLUDED.assigned_agent,
        agent_name = EXCLUDED.agent_name,
        title = EXCLUDED.title,
        action_type = EXCLUDED.action_type,
        input_json = EXCLUDED.input_json,
        status = EXCLUDED.status,
        output = EXCLUDED.output,
        error = EXCLUDED.error,
        duration_ms = EXCLUDED.duration_ms,
        dependencies = EXCLUDED.dependencies,
        parallel_group = EXCLUDED.parallel_group,
        completed_at = CASE WHEN EXCLUDED.status IN ('COMPLETED', 'FAILED') THEN now() ELSE jarvis.goal_steps.completed_at END
    RETURNING * INTO result;

    RETURN result;
END;
$$;

-- Read Goal Runs for Authenticated User
CREATE OR REPLACE FUNCTION jarvis.goal_run_read(
    p_limit int DEFAULT 20,
    p_offset int DEFAULT 0
)
RETURNS SETOF jarvis.goal_runs
LANGUAGE sql
SECURITY INVOKER
SET search_path = pg_catalog, jarvis
AS $$
    SELECT * FROM jarvis.goal_runs
    WHERE owner_name = session_user
    ORDER BY created_at DESC
    LIMIT LEAST(p_limit, 100) OFFSET GREATEST(p_offset, 0);
$$;

-- Read Plan Steps for a Specific Goal
CREATE OR REPLACE FUNCTION jarvis.goal_steps_read(
    p_goal_id text
)
RETURNS SETOF jarvis.goal_steps
LANGUAGE sql
SECURITY INVOKER
SET search_path = pg_catalog, jarvis
AS $$
    SELECT s.* FROM jarvis.goal_steps s
    JOIN jarvis.goal_runs g ON g.id = s.goal_id
    WHERE s.goal_id = p_goal_id AND g.owner_name = session_user
    ORDER BY s.step_index ASC;
$$;

-- Delete a Goal Run and Cascade Delete its Steps
CREATE OR REPLACE FUNCTION jarvis.goal_run_delete(
    p_id text
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, jarvis
AS $$
BEGIN
    DELETE FROM jarvis.goal_runs
    WHERE id = p_id AND owner_name = session_user;
    RETURN FOUND;
END;
$$;

COMMIT;

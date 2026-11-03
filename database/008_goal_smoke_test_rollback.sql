-- Run in pgAdmin connected to the dedicated JARVIS database.
-- Migration 008: Smoke test for jarvis.goal_runs and jarvis.goal_steps with automatic rollback.
-- Verifies multi-agent parallel goal storage, step upserts, and read queries.

BEGIN;

DO $$
DECLARE
    v_goal jarvis.goal_runs;
    v_step1 jarvis.goal_steps;
    v_step2 jarvis.goal_steps;
    v_step3 jarvis.goal_steps;
    v_read_count int;
BEGIN
    -- 1. Create a goal run in MULTI_AGENT_PARALLEL mode
    v_goal := jarvis.goal_run_upsert(
        'goal-smoke-test-001',
        'Multi-Agent Smoke Test Pipeline',
        'Develop REST API with Researcher, Coder, and Validator parallel agents',
        'MULTI_AGENT_PARALLEL',
        ARRAY['Researcher Agent', 'Coder Agent', 'Validator Agent'],
        'RUNNING'
    );
    ASSERT v_goal.id = 'goal-smoke-test-001', 'Goal ID mismatch';
    ASSERT v_goal.execution_mode = 'MULTI_AGENT_PARALLEL', 'Execution mode mismatch';

    -- 2. Stage 1 Parallel Sub-Agent Steps: Researcher + Coder
    v_step1 := jarvis.goal_step_upsert(
        'goal-smoke-test-001', 1, 1, 'RESEARCHER', 'Researcher Agent',
        'Investigate REST API architecture specifications', 'RESEARCH_WEB', '{"query":"spring webflux rest"}'::jsonb,
        'COMPLETED', 'Found architecture guidelines and reactive patterns.', NULL, 120, '{}', 1
    );

    v_step2 := jarvis.goal_step_upsert(
        'goal-smoke-test-001', 2, 1, 'CODER', 'Coder Agent',
        'Implement reactive controller and service classes', 'CODE_GENERATE', '{"type":"controller"}'::jsonb,
        'COMPLETED', 'Generated Spring Boot reactive controller.', NULL, 210, '{}', 1
    );

    -- 3. Stage 2 Sub-Agent Step: Validator
    v_step3 := jarvis.goal_step_upsert(
        'goal-smoke-test-001', 3, 2, 'VALIDATOR', 'Validator Agent',
        'Validate code syntax and generate unit test assertions', 'CODE_VALIDATE', '{"target":"controller"}'::jsonb,
        'COMPLETED', 'All test assertions and syntax checks passed.', NULL, 95, ARRAY[1, 2], 2
    );

    ASSERT v_step1.assigned_agent = 'RESEARCHER', 'Step 1 agent mismatch';
    ASSERT v_step2.assigned_agent = 'CODER', 'Step 2 agent mismatch';
    ASSERT v_step3.assigned_agent = 'VALIDATOR', 'Step 3 agent mismatch';

    -- 4. Verify Read Functions
    SELECT count(*) INTO v_read_count FROM jarvis.goal_steps_read('goal-smoke-test-001');
    ASSERT v_read_count = 3, 'Expected 3 steps in smoke test goal run';

    PERFORM * FROM jarvis.goal_run_read(10, 0);

    RAISE NOTICE 'Multi-agent goal runs smoke test completed successfully with 3 parallel sub-agents verified.';
END;
$$;

ROLLBACK;

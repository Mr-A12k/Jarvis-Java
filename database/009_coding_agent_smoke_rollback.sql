-- Run in pgAdmin connected to the dedicated JARVIS database.
-- Migration 009: Smoke test for jarvis.coding_sessions, jarvis.coding_steps, and jarvis.coding_file_diffs.
-- Verifies autonomous coding agent storage, step logging, file diff tracking, and automatic rollback.

BEGIN;

DO $$
DECLARE
    v_session jarvis.coding_sessions;
    v_step1 jarvis.coding_steps;
    v_step2 jarvis.coding_steps;
    v_diff jarvis.coding_file_diffs;
    v_step_count int;
BEGIN
    -- 1. Create a coding session
    v_session := jarvis.coding_session_upsert(
        'coding-smoke-test-001',
        'Implement LRU Cache in Java',
        'Create LRUCache with O(1) operations and comprehensive unit tests',
        'd:/Jarvis/jarvis-ai',
        'qwen2.5-coder:1.5b',
        'AUTO',
        'RUNNING',
        0,
        ARRAY['backend/src/main/java/com/jarvis/common/LRUCache.java'],
        '{"stage":"implementation"}'::jsonb,
        NULL
    );
    ASSERT v_session.id = 'coding-smoke-test-001', 'Session ID mismatch';
    ASSERT v_session.model = 'qwen2.5-coder:1.5b', 'Model mismatch';

    -- 2. Step 1: Codebase Search
    v_step1 := jarvis.coding_step_log(
        'coding-smoke-test-001', 1, 'CODEBASE_SEARCH', 'workspace_search',
        '{"pattern":"LRUCache"}'::jsonb,
        'Searching codebase for any pre-existing cache implementations...',
        'SUCCESS', 'Found 0 matches. Safe to create new implementation.', NULL, 45
    );
    ASSERT v_step1.step_index = 1, 'Step 1 index mismatch';

    -- 3. Step 2: Write File
    v_step2 := jarvis.coding_step_log(
        'coding-smoke-test-001', 2, 'WRITE_FILE', 'create_file',
        '{"path":"backend/src/main/java/com/jarvis/common/LRUCache.java"}'::jsonb,
        'Creating LRUCache with LinkedHashMap backing and synchronized access...',
        'SUCCESS', 'File created successfully (45 lines written).', NULL, 80
    );
    ASSERT v_step2.step_index = 2, 'Step 2 index mismatch';

    -- 4. Record a file diff
    v_diff := jarvis.coding_diff_record(
        'coding-smoke-test-001', 2,
        'backend/src/main/java/com/jarvis/common/LRUCache.java',
        'CREATE',
        NULL,
        'package com.jarvis.common; public class LRUCache {}',
        '--- /dev/null\n+++ b/LRUCache.java\n@@ -0,0 +1,1 @@\n+package com.jarvis.common; public class LRUCache {}',
        'APPLIED'
    );
    ASSERT v_diff.file_path = 'backend/src/main/java/com/jarvis/common/LRUCache.java', 'Diff file path mismatch';

    -- 5. Verify Step Count
    SELECT count(*) INTO v_step_count FROM jarvis.coding_steps WHERE session_id = 'coding-smoke-test-001';
    ASSERT v_step_count = 2, 'Expected 2 steps in smoke test session';

    RAISE NOTICE 'Autonomous coding agent smoke test completed successfully with 2 steps and 1 file diff verified.';
END;
$$;

ROLLBACK;

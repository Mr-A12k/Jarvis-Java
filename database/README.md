# pgAdmin functions for JARVIS

Prepared SQL; not yet executed against your database. The UI does not yet persist through these functions.

1. Create a dedicated `jarvis` database and non-superuser owner login in pgAdmin. Enter passwords locally.
2. Connect Query Tool to that database as its owner. Open `001_tables_and_functions.sql` and execute once. It intentionally fails rather than overwriting an existing `jarvis` schema.
3. Refresh Schemas > jarvis > Tables / Functions.
4. Open and execute `002_smoke_test_rollback.sql` in a development database. Sample records roll back; sequence numbers may advance.
5. Put the connection URL, username and password in ignored `../.env`. Do not paste credentials into chat.

Example calls:

```sql
SELECT * FROM jarvis.memory_create('Answer style','Prefer concise answers');
SELECT * FROM jarvis.memory_read(30,0);
SELECT * FROM jarvis.conversation_create('Planning');
SELECT * FROM jarvis.conversation_read(30,0);
-- Copy the actual UUID and current version from the results:
-- SELECT * FROM jarvis.memory_update('actual-uuid',1,'Answer style','Detailed answers');
-- SELECT jarvis.memory_delete('actual-uuid',2);
-- SELECT * FROM jarvis.conversation_update('actual-uuid',1,'New title');
-- SELECT * FROM jarvis.message_append('actual-uuid','user','Hello');
-- SELECT * FROM jarvis.message_read('actual-uuid',0,50);
-- SELECT jarvis.conversation_delete('actual-uuid',3);

-- Smart Home & IoT Automation:
-- SELECT * FROM jarvis.smart_device_list();
-- SELECT * FROM jarvis.smart_device_update_state('switch.desk_power_strip', 'off', '{"power_watts": 0.0}'::jsonb);
-- SELECT * FROM jarvis.smart_device_update_state('light.room_lighting', 'on', '{"brightness": 15, "color": "#3b82f6"}'::jsonb);
-- SELECT * FROM jarvis.smart_scene_list();
-- SELECT * FROM jarvis.smarthome_config_get();

-- Autonomous Goal Planner & Multi-Agent Parallel Task Execution (Migration 007):
-- SELECT * FROM jarvis.goal_run_upsert('goal-001', 'Build Reactive REST API', 'Implement REST API in Java', 'MULTI_AGENT_PARALLEL', ARRAY['Researcher Agent', 'Coder Agent', 'Validator Agent'], 'RUNNING');
-- SELECT * FROM jarvis.goal_step_upsert('goal-001', 1, 1, 'RESEARCHER', 'Researcher Agent', 'Research API specs', 'RESEARCH_WEB', '{"query":"spring webflux"}'::jsonb, 'COMPLETED', 'API specs found', NULL, 120, '{}', 1);
-- SELECT * FROM jarvis.goal_run_read(20, 0);
-- SELECT * FROM jarvis.goal_steps_read('goal-001');

```

Updates and deletes require the current version; concurrent changes fail instead of being silently overwritten. Appending a message increments its conversation version. Deleting a conversation deletes its messages, not saved memories. Deletes are permanent after commit: review the selected row and use BEGIN/ROLLBACK while experimenting.

Functions use SECURITY INVOKER, fixed search paths, validated table constraints and no dynamic SQL. Ownership comes from PostgreSQL session_user, not a caller-provided owner parameter. RLS is enabled and forced; superusers/BYPASSRLS can still bypass it. PUBLIC receives no privileges. Run as the dedicated schema owner; privileges for another runtime role must be explicitly configured.

This is database-login isolation, not application-account isolation. A shared backend login represents one database user. Multi-user web authentication and app-account mapping must be implemented before connecting multiple application users to these functions. No model is permitted to execute arbitrary SQL.

pgvector is not needed for these relational tables; it remains required for document RAG. This script is not automatically run by the application or Flyway yet.

### Migrations
- `001_tables_and_functions.sql`: Core relational memory, conversations, and versioned message history.
- `002_smoke_test_rollback.sql`: Test script validating isolation and rollback safety.
- `003_pgvector_rag.sql`: pgvector tables for semantic document retrieval and embedding cosine similarity.
- `004_vector_search_fix.sql`: Distance function optimizations for HNSW indexing.
- `005_vector_smoke_rollback.sql`: Test script validating vector embeddings.
- `006_biometric_profiles.sql`: Biometric face recognition vectors (128-d) and voice acoustic prints (192-d).
- `007_goal_runs_multi_agent.sql`: Autonomous Goal Planner with Multi-Agent Parallel Task Execution (Researcher, Coder, Validator, System Analyst), parallel stages, RLS, and stored procedures.
- `008_goal_smoke_test_rollback.sql`: Test script validating multi-agent goal storage, step upserts, and rollback safety.

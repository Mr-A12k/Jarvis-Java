# PostgreSQL persistence API

Configure `JARVIS_DB_URL` (JDBC URL), `JARVIS_DB_USER`, and `JARVIS_DB_PASSWORD` in the ignored `.env`, then restart. No credentials are supplied by default. Install `database/001_tables_and_functions.sql` once in a dedicated empty database, using the same dedicated login as the backend. Do not use a superuser for the app. Existing schemas are not automatically modified.

All routes below use `/api/v1/database`. Default local mode requires the `X-Jarvis-Local: 1` header from a loopback client (the UI adds it automatically). Optional token mode requires the bearer token.

| Route                                                                | Operation                                                                                                                     |
| -------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| GET `/status`                                                        | Configured/ready; verifies the schema when configured                                                                         |
| GET `/memories` or `/conversations`                                  | List with `limit` 1–100 and `offset` 0–100000                                                                                 |
| POST `/memories`                                                     | Create `{title,content}`                                                                                                      |
| POST `/conversations`                                                | Create `{title}`                                                                                                              |
| PUT `/memories/{id}`                                                 | Update `{version,title,content}`                                                                                              |
| PUT `/conversations/{id}`                                            | Rename `{version,title}`                                                                                                      |
| DELETE `/memories/{id}?version=N` or `/conversations/{id}?version=N` | Delete with version guard; deleting a conversation also deletes its messages                                                  |
| POST `/snapshots`                                                    | Atomically save `{title,messages:[{role,content}]}`; up to 100 user/assistant messages, subject to the 64 KiB HTTP body limit |
| GET `/conversations/{id}/messages?after=0&limit=50`                  | Messages in ID order; use last ID as next cursor                                                                              |

Snapshot saves create a new conversation each time. Do not automatically retry a save after an ambiguous network failure; list conversations first. A returned version includes message insertions. Missing/stale updates return 409, absent configuration or database failure returns 503, invalid data returns 400. SQL and credentials are never returned in errors. JDBC runs off the HTTP event loop, with two concurrent operations and connection/query timeouts.

The chat history drawer saves and reopens explicit snapshots. Saving more than 100 messages fails visibly without truncation. PostgreSQL memories are included in ordinary local chat (up to ten records, clipped to 150 UTF-8 bytes total, plus up to 50 bytes of local notes). They are treated as untrusted context, not system instructions. File-memory JSON remains separate. GET `/memories/export` exports the current database user's memories; the memory page provides a JSON download button.

For an existing pgvector installation, apply `database/004_vector_search_fix.sql` once. It fixes distance-operator resolution without replacing tables. `database/005_vector_smoke_rollback.sql` verifies known-vector matching, negative matching, and cascade deletion, then rolls back all changes. The vector schema currently expects the 384-dimensional `all-minilm:22m` model.

## Autonomous Goal Planner & Multi-Agent Parallel Execution (Migration 007)

Apply `database/007_goal_runs_multi_agent.sql` to add relational tables and stored procedures for autonomous multi-agent task execution. `database/008_goal_smoke_test_rollback.sql` provides smoke testing and rollback verification.

### Tables
- **`jarvis.goal_runs`**: Session records for multi-step goals. Stores `id`, `owner_name`, `title`, `objective`, `execution_mode` (`MULTI_AGENT_PARALLEL`), `active_agents`, `status` (`PLANNING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`), `final_report`, `created_at`, `completed_at`.
- **`jarvis.goal_steps`**: Discrete action steps assigned to parallel sub-agents. Stores `id`, `goal_id`, `step_index`, `stage`, `assigned_agent` (`RESEARCHER`, `CODER`, `VALIDATOR`, `SYSTEM_ANALYST`, `COORDINATOR`), `agent_name`, `title`, `action_type`, `input_json`, `status`, `output`, `error`, `duration_ms`, `dependencies`, `parallel_group`.

### Stored Procedures
- `jarvis.goal_run_upsert(p_id, p_title, p_objective, p_execution_mode, p_active_agents, p_status, p_final_report)`: Upserts a goal run with atomic status transitions.
- `jarvis.goal_step_upsert(p_goal_id, p_step_index, p_stage, p_assigned_agent, p_agent_name, p_title, p_action_type, p_input_json, p_status, p_output, p_error, p_duration_ms, p_dependencies, p_parallel_group)`: Upserts a sub-agent task step.
- `jarvis.goal_run_read(p_limit, p_offset)`: Reads recent goal runs scoped to `session_user`.
- `jarvis.goal_steps_read(p_goal_id)`: Reads all plan steps for a goal run ordered by `step_index`.
- `jarvis.goal_run_delete(p_id)`: Cascading delete for a goal run and its sub-agent steps.

Validation: `./scripts/test-database.ps1 -Backend` creates a separate temporary PostgreSQL cluster on port 15432, applies schema and rollback smoke tests, runs real JDBC persistence tests and HTTP authentication/validation tests, then stops the cluster. It does not touch the primary database.

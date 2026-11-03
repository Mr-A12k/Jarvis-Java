# Architecture and phased implementation

Preserve the legacy Python prototype; build the requested stack as a sibling application. The backend owns authorization and resource limits. Spring AI sits behind `AiProvider`; prompts, orchestration, transport, and health remain separate. React will use ordinary REST plus authenticated fetch-based SSE. PostgreSQL will own durable user-scoped relational records and pgvector chunks; Ollama owns local inference.

1. **Local chat**: Java 21, Boot 3.5.x, AI 1.1.x, local model abstraction, deadlines, single-flight inference, safe failures, authenticated APIs. Validate automated tests and real Ollama inference before marking the phase complete.
2. **React workspace**: TypeScript/Vite/Tailwind; streaming chat, abort, health, responsive navigation. Visual thesis: calm graphite surfaces with one cyan accent. Content: primary chat workspace, navigation, secondary model status. Interaction: subtle panel entrance, streaming text, clear focus/hover transitions; reduced-motion support. No inactive controls for unimplemented features.
3. **Persistence**: PostgreSQL, JPA/Flyway, server-owned user IDs, paginated conversations and messages, separate memory clearing. Replace the initial single-user token scheme with user-bound authentication before claiming multi-user support.
4. **RAG**: validated document formats; bounded extraction and chunking; model-specific embeddings; pgvector with owner/document filters; upload states and content-hash deduplication; transactional delete/re-index; source/page/chunk metadata; evidence snippets and empty-result behavior. Verify a small known-answer dataset and cross-user negatives. Keep ingestion and chat mutually bounded to avoid simultaneous model residency.
5. **MCP**: opt-in Spring AI client configuration, tested stdio/HTTP transports, JSON-schema validation, timeouts, audit events, and explicit approval records for consequential tools. Never import another agent's credentials or assume its servers are accessible. Only configuration section names were inspected: Codex has node_repl and rea; they are not configured as JARVIS servers.
6. **Live research**: user-configured search provider/key; evidence links and timestamped results; no implicit upload of private document text. Separately retrieve document and web evidence.
7. **Shopping research**: criteria and budget parsing, real supported source results, comparison with unknown fields explicitly missing; link-based handoff when checkout integration is absent.
8. **Automation**: Playwright for Java, domain/directory/command allowlists, approvals bound to exact action payloads, one-time execution, expiry, audit. User handles credentials, CAPTCHA, OTP and payment. No generic LLM-to-shell bridge.
9. **Voice, memory and scheduler**: opt-in push-to-talk and speech output with text fallback; explicit relational memories with export/delete; scheduler with allowlisted action types and execution history. Never retry a purchase automatically.
10. **Zero Prebuilt Answers & Model Deep-Thinking**: Prohibition of static answers; mandatory DeepSeek-style reasoning trace (`<think>` blocks); autonomous live web retrieval and continuous learning for ungrounded knowledge; transparent real-time thought-process audio/text narration.
11. **Dynamic Multi-Lingual Intelligence & Strict Language Mirroring**: Strict language mirroring (English queries get 100% English replies and reasoning; Tamil queries get native Tamil script and reasoning); dynamic auto-switching via voice/text commands ("switch to English", "speak in Tamil", "தமிழில் பேசு"); cross-lingual prompt isolation; high-priority Tamil support with phonetic mapping.
12. **Omnipresent Voice & Text Command Control**: Unified dispatch of all assistant capabilities (biometric face/voice authentication, live hardware telemetry, web search, browser automation, goal planning, and language transitions) via direct voice or text commands without UI navigation locks.
13. **DeepSeek-R1 Cognitive Reasoning & Dynamic MoSM Task Router**: Specialized cognitive reasoning routing (`SegmentRouter.java`), temperature 0.2 calibration, strict scratchpad `<think>...</think>` tags for internal step-by-step logic, riddles, math proofs, and algorithmic puzzles, dynamic `/api/v1/chat/segments` introspection, and frontend model badge/thought-accordion integration.

## External requirements

- Local chat: Ollama and an installed local chat model; no API key.
- RAG/history: PostgreSQL with pgvector plus a separate local embedding model. No cloud embedding fallback.
- MCP: explicitly configured compatible servers; any credentials supplied externally, never hardcoded.
- Web research: search API key and network access. No fake search results.
- Shopping: supported retailer APIs/integrations or manual product-link handoff.
- Voice: supported browser, microphone permission, user awareness that browser speech recognition may use the browser vendor's online service.

## Validation gates

Each phase must pass its relevant automated tests and report which external integration checks actually ran. A missing prerequisite is not a passing integration. Phase 1 has no database dependency by design, so local chat remains available before the heavier persistence stack is introduced. Do not call the full initial release complete until every item in the supplied definition of done has been verified.

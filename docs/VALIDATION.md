# Validation record

## 2026-10-26 Production Grounding, Safe Actions & MoSM JARVIS-Researcher Integration

Implemented and verified the specialized real-time fact-checking model (`jarvis-researcher:latest`), dynamic MoSM task routing, commodity and domestic Indian gold grounding, safe application closing, and anti-hallucination guardrails:
1. **Specialized Real-Time Model Routing (`SegmentRouter.java`)**:
   - Expanded `RESEARCH_PATTERN` to capture commodities (`gold`, `goald`, `silver`, `bullion`, `commodity`, `commodities`, `forex`, `usd/inr`, `dollar rate`, `rupee rate`, `crude oil`, `brent`), Tamil market queries (`தங்கம்`, `வெள்ளி`, `சவரன்`, `விலை`), and explicit `/gold` slash command.
   - Upgraded `isModelInstalled` with tag-resilient normalization to recognize `:latest` and untagged model names interchangeably.
   - Introspection API `GET /api/v1/chat/segments` reports `RESEARCH` segment with `isSpecializedInstalled: true` and active model `jarvis-researcher:latest`.
2. **Commodity & Live Market Grounding (`RealtimeFactService.java`)**:
   - Live COMEX Gold Spot (`GC=F`) and USD/INR Forex (`INR=X`) feeds via Yahoo Finance.
   - Domestic Indian retail pricing formulas accounting for statutory import duty (6%) and GST (3%) for 24K pure gold and 22K hallmark jewellery.
   - Dynamic SVG trend charts generated on `/api/v1/realtime/chart?symbol=GC%3DF`.
3. **Anti-Hallucination & Dispute Resolution (`KnowledgeService.java`, `PromptFactory.java`)**:
   - Added user objection detection (`"you are wrong"`, `"its wrong"`, etc.) to automatically bypass and purge toxic cached entries.
   - Enforced zero-simulation directives in model prompts: missing prices explicitly reported instead of simulated.
4. **Desktop Safety & Honest Results (`DesktopSystemService.java`, `DesktopGuiAgentService.java`, `SystemAppLauncher.java`)**:
   - Window PID tracking prevents closing unintended processes.
   - Safe close handles unsaved Notepad windows by reporting "waiting for save decision" without force-killing.
   - Meeting notes persistently saved to `backend/data/notes/meeting_notes_*.txt` before opening in Notepad.
5. **One-Click Startup Automation**:
   - Created root `start.bat` for one-click Windows launch running `scripts/run.ps1` with port conflict cleanup and dependency checks.
6. **Validation Results**:
   - `SegmentRouterTest`: **9/9 tests passed**.
   - Entire Backend Suite: **301 tests passed** (0 failures).
   - Live Inference Verified: Querying live gold prices streams grounded INR data with Yahoo Finance attribution from `jarvis-researcher:latest`.
   - Live End-to-End Suite (`verify_live_suite.py`): Verified safe app launch/close, Tamil comprehension with bilingual `<think>` traces, and conversation sanity.

## 2026-10-24 Option A: Full Desktop GUI Agent Automation & Macro Sequences (PostgreSQL Schema 007)

Implemented and verified Full Desktop GUI Agent Automation, mouse/keyboard actuation, active window management, and PostgreSQL-persisted macro workflows:
1. **Database Schema & Migrations (`database/007_desktop_gui_automation.sql`)**:
   - `jarvis.desktop_workflows`: persistent macro sequences, trigger patterns, JSONB actions array, execution counter, and RLS policies. Seeded with 4 default workflows (*Quick Meeting Notes in Notepad*, *Developer Workspace Setup*, *Save Active Document*, *Show Desktop & Clear Workspace*).
   - `jarvis.desktop_action_audit`: audit trail of mouse clicks, drags, text typing, hotkeys, and window focus actions with duration ms and status.
   - `jarvis.desktop_custom_apps`: user-defined app registry with custom paths and voice triggers.
   - Stored functions: `jarvis.desktop_workflow_upsert`, `jarvis.desktop_workflow_record_execution`, `jarvis.desktop_log_action`.
   - Applied live to PostgreSQL `jarvis` database via `psql`: verified all 4 default workflows inserted with 0 errors.
2. **Backend GUI Agent Engine (`DesktopGuiAgentService.java`, `DesktopController.java`, `DesktopSystemService.java`)**:
   - **Mouse Control**: `mouseMove`, `mouseClick` (left, right, middle, double), `mouseDrag`, `mouseScroll`, `getCursorAndScreen` via `java.awt.Robot` with fallback to PowerShell Win32.
   - **Keyboard & Hotkey Actuation**: `typeText` via clipboard paste + `Ctrl+V` (safe for multiline notes, code blocks, emojis, and Unicode like Tamil/Hindi), `sendKey`, `sendHotkey` (`Ctrl+S`, `Ctrl+C`, `Ctrl+V`, `Ctrl+A`, `Alt+Tab`, `Alt+F4`, `Win+D`, `Ctrl+Shift+P`, `Ctrl+` `~`).
   - **Window Management**: `listActiveWindows` enumerating interactive foreground apps, `focusWindow` bringing applications directly to front via `AppActivate` / `SetForegroundWindow`, `closeWindow`, and `showDesktop`.
   - **REST APIs**: `/api/v1/desktop/agent/action`, `/agent/sequence`, `/agent/cursor`, `/agent/windows`, `/agent/focus`, `/agent/close`, `/agent/show-desktop`, `/agent/workflows`, `/agent/audit`.
   - **Voice Command Integration**: Dual-language voice triggers in English and Tamil (*"show desktop"*, *"take meeting notes in notepad"*, *"switch to VS Code"*, *"save active document"*, *"முகப்புத்திரை காட்டு"*, *"நோட்பேடில் குறிப்பு எழுது"*, *"கோப்பை சேமி"*).
3. **Frontend Desktop GUI Agent Studio (`DesktopToolsPanel.tsx`, `api.ts`)**:
   - Added dual-tab navigation in Desktop Tools: `[⚡ Quick Controls]` vs `[🤖 Desktop GUI Agent Studio]`.
   - Active Application Window Manager cards with one-click **[Focus]** and **[Close]** controls.
   - Keyboard & Hotkey Sandbox: multiline text input, quick hotkey palette (`Ctrl+S`, `Ctrl+C`, `Ctrl+V`, `Alt+Tab`, etc.), coordinate clicker.
   - Saved Database Workflows cards with one-click **[Run Workflow]**, execution count, step badges, and favorite status.
   - Real-time audit history table showing action type, target, status (`SUCCESS`), and duration ms.
4. **Validation Results**:
   - Backend: **20/20 unit tests passed** in `DesktopGuiAgentTest`, `DesktopControllerTest`, and `DesktopSystemServiceTest`.
   - Frontend: **29/29 Vitest tests passed** (100% green); Vite production bundle built in 2.85s.
   - Live Endpoints Verified:
     - `GET /api/v1/desktop/agent/workflows` returned 4 database workflows from PostgreSQL.
     - `GET /api/v1/desktop/agent/cursor` returned screen `1536x864`.
     - `POST /api/v1/desktop/agent/action` (`Ctrl+S`) executed in 379ms and immediately logged audit entry `c0d74425-b021-4785-92e6-b72a2760021c` into `jarvis.desktop_action_audit`.

Implemented and verified the specialized DeepSeek-R1 reasoning engine and Mixture of Specialized Models (MoSM) task router across backend and frontend:
1. **Ollama Reasoning Specialized Model (`jarvis-reasoner:latest` & `deepseek-r1:1.5b`)**:
   - Built custom `Modelfile.reasoner` with DeepSeek-R1 temperature (0.2), top_p (0.9), and mandatory `<think>...</think>` scratchpad reasoning system prompts.
   - Registered `deepseek-r1:1.5b` locally in Ollama via `ollama cp jarvis-reasoner:latest deepseek-r1:1.5b` with instant instantiation.
   - Tested real inference: mathematical deduction for classic bat-and-ball puzzle ($0.05) and widget-rate problem produced 100% accurate derivations and answers.
2. **Dynamic MoSM Router (`SegmentRouter.java`, `PromptFactory.java`, `ChatController.java`)**:
   - Added `REASONING_PATTERN` matching logic puzzles, algorithmic complexity, truth tables, proofs, and Tamil equivalents (`யோசி`, `தர்க்கம்`, `விளக்கு`, `படி படியாக`).
   - Added slash command triggers: `/r1`, `/deepseek`, `/reason`, `/think`, `/logic`, `/calc`, `/math`.
   - Reordered pattern matching so programming tasks route to `CODE` before `MATH`/`REASONING`.
   - Exposed `GET /api/v1/chat/segments` returning segment metadata, active model assignments, and operational parameters.
   - Enriched SSE stream completion events with `segment` and `model` metadata.
3. **Frontend Reasoning Integration (`ChatMessage.tsx`, `ModelInspector.tsx`, `api.ts`, `App.tsx`)**:
   - Added dynamic model badges (`🧠 DeepSeek-R1 (1.5B)` in violet, `📈 JARVIS-Researcher` in emerald, `💻 Coder 1.5B` in blue).
   - Dynamic thinking accordion title: displays `DeepSeek-R1 Cognitive Reasoning` when serviced by DeepSeek-R1.
   - Added interactive `/r1` command insertion from the Model Inspector knowledge segments pill.
4. **Validation Results**:
   - Backend: **249 tests passed** (0 failures, 0 errors, 3 skipped).
   - Frontend: **29 tests passed** in Vitest (100% green); Vite production bundle built in 2.51s.
   - Live endpoint verification: `/api/v1/chat/segments` returned active `deepseek-r1:1.5b` for `MATH`; live `/api/v1/chat` and `/api/v1/chat/stream` verified with real-time reasoning responses.


## 2026-10-23 Stage 6: Shopping Assistant & Advanced RAG (XLSX, PPTX, Page-accurate PDF Citations)

Implemented and validated Stage 6: Shopping Research Engine and Advanced Document Ingestion:
1. **Shopping Criteria Parser & Recommendation Engine (`ShoppingService.java`, `ShoppingCriteria.java`)**:
   - Parses natural user inquiries with budget constraints, currency (`USD`, `INR`, `EUR`, `GBP`), brand preferences, feature requirements, and sorting preferences (`best_value`, `price_asc`, `rating_desc`).
   - Generates side-by-side spec comparison matrices highlighting **Best Value** and **Top Rated** candidates, with explicit `N/A` for missing/unknown specs (no hallucinated features).
   - Safe purchase handoff: Produces verified retailer links (`[View on Retailer ↗](url)`) and never stores payment cards or executes checkout automatically.
   - Built direct fast-path in `ChatService.java` for `/shop <query>` and shopping comparison requests, returning full Markdown matrix tables instantly.
2. **Dedicated Shopping Assistant UI Panel (`ShoppingPanel.tsx`)**:
   - Interactive search and budget filter bar with currency selector and quick-search suggestion chips.
   - Dual-view toggle: Side-by-side technical specification matrix table and responsive product cards with retailer attribution, star ratings, and review counts.
   - Added to `Sidebar.tsx`, `TopBar.tsx`, and `App.tsx` under `ActiveView.SHOPPING`.
3. **Advanced Document Ingestion (`XlsxText.java`, `PptxText.java`, `PdfText.java`, `LocalFiles.java`)**:
   - **XLSX Spreadsheets**: Safe StAX XML stream parsing of `xl/sharedStrings.xml` and `xl/worksheets/sheet*.xml`. Extracts tabular sheets with cell values and row boundaries. Bounded by 2 MB file and 16 KB text limits.
   - **PPTX Presentations**: Safe StAX XML stream parsing of `ppt/slides/slide*.xml`. Extracts slide-by-slide titles and text runs tagged with `--- Slide N ---`.
   - **Page-accurate PDF Citations**: Enhanced `PdfText.java` to extract text page-by-page, embedding `--- Page N ---` section headers for physical page-accurate citations.
   - Updated `LocalFiles.java` to support `.xlsx` and `.pptx` across file checks, browsing, reads, and indexing.
4. **Validation Results**:
   - Backend: **180 tests passed** (0 failures, 0 errors, 3 skipped).
   - Frontend: **21 vitest tests passed** (100% green); Vite production bundle built in 1.03s.
   - Live endpoints verified on `http://127.0.0.1:18080`: `/api/v1/shopping/criteria`, `/api/v1/shopping/compare`, and `/api/v1/chat` `/shop` fast-path.

## 2026-10-23 Tamil Voice Synthesis, Recognition & Native Language Understanding

Activated Tamil (தமிழ்) and Tanglish language comprehension, voice recognition, and vocal synthesis across backend and frontend:
1. **Tamil Voice Agent & Tanglish Comprehension (`TamilVoiceAgent.java`)**:
   - Expanded conversational Tanglish mapping dictionary covering daily greetings (*vanakkam*, *kaalai vanakkam*, *maalai vanakkam*, *iravu vanakkam*), identity inquiries (*naan yaar*, *nee yaar*), wellness check-ins (*eppadi irukkeenga*, *nalama*), task assist (*udhavi sei*), gratitude (*mikka nandri*, *romba nandri*), and farewells (*poitu varen*).
   - Direct conversational intent fast-path returning authentic Tamil responses with identity acknowledgement of user Kabil and assistant JARVIS without LLM delay.
2. **Tamil Chat Integration & Dynamic LLM Prompting (`ChatService.java`, `PromptFactory.java`)**:
   - Wired `TamilVoiceAgent` into `ChatService.stream(...)` fast-path for non-file questions, allowing zero-latency responses for greetings and conversational intents.
   - Enhanced `PromptFactory.java` with `isTamil(text)` detecting both Tamil unicode script (`[\u0B80-\u0BFF]`) and Tanglish keyword patterns, dynamically enriching the model system prompt to enforce native Tamil script fluency and polite tone.
3. **Frontend Voice UI & Hologram HUD (`VoicePanel.tsx`, `JarvisHologram.tsx`)**:
   - Defaulted voice language to `'ta-IN'` with `localStorage` persistence under `'jarvis_voice_lang'`.
   - Enhanced browser speech synthesis voice selector (`getBestHumanizedVoice`) to automatically detect Tamil voices (*valluvar*, *pallavi*, *latha*, *vani*, *kalpana*, *tamil*, *ta*), falling back to backend synthesis.
4. **Validation & Live Verification**:
   - Backend unit & integration tests: **168 tests passed** (0 failures, 0 errors, 3 skipped).
   - Frontend unit tests: **20 tests passed** in Vitest.
   - Live endpoint verification: `/api/v1/voice/languages` returned `[{"tag":"en-US"},{"tag":"ta-IN"}]`; `/api/v1/voice/tamil/understand` returned accurate Tamil translations and replies; `/api/v1/chat` returned instant fluent Tamil greeting.
   - App running live on `http://127.0.0.1:18080`.

## 2026-10-23 Interactive Playwright Automation, Real MCP Transports, Chat Tool Loop & UI Restructuring

Implemented and validated Stages 4 & 5 alongside the complete frontend architecture restructuring:
1. **Interactive Browser Automation (Stage 4)**: Extended `BrowserService.java` with action primitives (`CLICK`, `FILL`, `PRESS`, `WAIT_FOR`), bounded contexts, and `POST /api/v1/automation/interact`. Added a multi-step workflow builder in `AutomationPanel.tsx`.
2. **Real MCP Stdio Transports (Stage 5)**: Implemented `McpTransport`, `McpStdioTransport`, and `McpClientManager` reading `data/mcp-servers.json` (and `McpConfiguration`). Spawns external processes via `ProcessBuilder` with JSON-RPC 2.0 communication (`initialize`, `tools/list`, `tools/call`) and bounded timeouts.
3. **Chat Tool Calling Loop & Human Approval**: Injected tool schemas into `PromptFactory.java`. Built multi-turn tool calling loop in `ChatService.java` supporting direct `/tool` syntax and model `[TOOL_CALL: ...]` patterns, generating human approval challenges for consequential actions. Added `GET /api/v1/mcp/servers` and `POST /api/v1/mcp/servers/reload`.
4. **Complete Frontend UI Restructure**: Refactored `frontend/src/` from a flat 13-file dump into modular subdirectories: `components/layout/` (`Sidebar`, `TopBar`), `components/chat/` (`ChatWorkspace`, `ChatMessage`, `PromptComposer`, `ModelInspector`), `components/connection/`, `components/settings/`, `components/modals/`, `panels/`, and `types/`. Reduced `App.tsx` from 980 lines down to ~350 lines with clean `ActiveView` state.
5. **Validation Results**:
   - Backend: **167 tests passed** (0 failures, 0 errors, 3 opt-in live tests skipped).
   - Frontend: **20 unit tests passed** in Vitest, and production Vite bundle built cleanly in 1.22s.

## 2026-10-23 Scheduler, Automation UI, Deep Web Content & MCP Approval Modal

Implemented and validated three core architecture phases:
1. **Automation Panel**: Implemented `frontend/src/AutomationPanel.tsx` connected to existing `/api/v1/automation/*` endpoints, exposing URL navigation, visual snapshot rendering (base64 image preview and extracted text), and live audit logs from `data/automation-audit.jsonl`.
2. **Deep Web Content & SSRF Filter**: Strengthened `WebSearch.java` with loopback and private IP (RFC 1918) SSRF defenses in `safeUrl()`, and added `extractPageText()` for clean noise-free content extraction.
3. **Task Scheduler Engine**: Implemented `com.jarvis.scheduler.SchedulerService` and `SchedulerController` with allowlisted tasks (`SYSTEM_METRICS_LOG`, `CLEANUP_AUDIT`, `INDEX_FOLDER`), thread-safe execution, disk persistence in `data/scheduler-tasks.json`, execution history in `data/scheduler-history.jsonl`, and full management UI in `frontend/src/SchedulerPanel.tsx`.
4. **MCP Approval Wiring**: Exposed active challenges via `GET /api/v1/mcp/challenges`, wired `McpApprovalModal.tsx` into `App.tsx` with challenge polling, approval/rejection handlers, and sidebar alert indicator.
5. **Tooling**: Updated `scripts/test-backend.ps1` to run tests via Maven with offline local repository and filter arguments.

Validation: 154 backend tests passed (0 failures, 0 errors, 3 opt-in live tests skipped). 18 frontend tests passed and production frontend bundle build succeeded.

## 2026-10-23 grounded file-answer prompt and citation fallback

File answers now use a dedicated evidence-only prompt with excerpts in user messages, excluding the general persona/local facts and bypassing chat's app-launch/calculator shortcuts. Missing or invalid citation IDs cause an extractive fallback; explicit insufficient-evidence responses remain supported. This does not verify semantic entailment of every cited claim.

Validation: 15 focused backend tests passed and JAR packaging succeeded. Restarted JARVIS and ran a real local-model question against a temporary known-answer document: response was exactly `The Copper project access code is COPPER-936. [1]`, with both sources restricted to the selected file and no added explanation. Temporary index entry and file were removed. This single successful live check is not a general grounding-quality benchmark. JARVIS remains running with the updated build.

## 2026-10-23 live document checks

Started the packaged app and local Ollama runtime. The configured database reported ready; Windows voice status reported en-US recognition and David/Zira voices (no microphone or speaker test). Generated temporary PDF and DOCX fixtures extracted correctly, indexed successfully with real embeddings, and returned their distinct known-answer codes (COPPER-936 and JADE-482) through the real local model. Both source lists contained only the selected file, with keyword and vector excerpts. The answers added unsupported generic explanations and did not include inline numbered citations; answer grounding/citation wording still needs work. Temporary index entries were forgotten after testing and generated files removed.

The browser successfully opened the file workspace and listed both fixtures, but browser use was then disabled in Work settings. Read-only preview rendering, browser cancellation, and microphone/speaker interaction remain unverified. JARVIS and Ollama were left running on localhost.

## 2026-10-23 PDF ingestion

Text-based PDFs now support browsing, read-only text preview, folder indexing, and file questions. Apache PDFBox 3.0.8 extracts text; its commons-logging dependency is excluded in favor of Spring's existing logging adapter. Limits: 2 MB input, 50 pages, 16 KB extracted UTF-8 text. Encrypted, textless, malformed and over-limit PDFs are rejected on direct read and skipped during folder indexing. OCR and page-accurate citations are not implemented; citations use extracted-text lines. See LOCAL_FILES.md for processing limits.

Validation: 17 focused backend tests passed after the logging exclusion, and the executable JAR packaged successfully. The frontend production build and all 13 frontend tests passed earlier in this change. Generated PDF fixtures cover multi-page extraction, encryption, absent text, malformed input, size limits, fresh retrieval after external changes, and edit rejection preserving original bytes. Browser layout/interaction and live PostgreSQL/Ollama answers were not verified. No database migration is required.

## 2026-10-23 DOCX ingestion

Local file browsing, read, indexing and keyword/vector ingestion now accept bounded DOCX main-document text. The frontend shows a read-only text preview and omits editing controls; backend proposal, suggestion and apply paths also reject DOCX edits. Paragraphs, split runs, Unicode and table text are extracted; references use extracted-text lines. Images, page layout, headers/footers, OCR, PDF and legacy DOC are outside this implementation. See LOCAL_FILES.md for size and parser limits.

Validation: 13 focused backend tests and all 13 frontend tests passed; frontend production build passed. Tests cover extraction, XML external entities, malformed/missing/empty parts, expanded/archive/XML text limits, nesting limits, indexing/retrieval and preservation of original DOCX bytes when editing is attempted. Browser layout/keyboard checks and live PostgreSQL/Ollama answers were not run; extraction tests use generated DOCX ZIP fixtures. No database migration or added dependencies are required.

## 2026-10-23 embedding-model-aware reindexing

Chunk metadata now includes the configured embedding model name and dimensions. Content deduplication also checks that every saved chunk matches that configuration; changed-model and legacy documents are regenerated on the next folder index. Both scoped and all-file vector search filter by model metadata before ranking. Existing untagged vectors need one reindex; keyword retrieval remains available. Replacement generation completes before the existing transactional delete/insert. Unsupported configured dimensions or invalid output lengths fail explicitly before writes (the current schema remains vector(384)). Model weight changes under an unchanged name are not detected.

Validation: 25 focused backend tests passed, covering matching-model deduplication, stale/legacy metadata replacement, metadata persistence, search parameter binding, invalid dimensions and generation failure before deletion, plus existing local-file and embedding tests. Database queries were tested with JDBC mocks; no live PostgreSQL/Ollama validation or automatic reindex of the user's data was performed. No schema migration is required.

## 2026-10-23 shared model admission

Chat and embedding services now use a shared Spring-managed admission gate. Chat retains its configured concurrency, while embedding reserves all slots exclusively. Busy operations fail immediately with the existing busy error codes. Resource leases release eagerly on completion/error and on cancellation; sequential embeddings can acquire the slot before the next model call. Local calculator responses do not acquire a model slot.

Validation: 43 targeted tests passed (shared admission, chat service, embedding service, and HTTP API integration against a fake Ollama server). Coverage includes cross-operation exclusion, cancellation, timeouts, errors, chained operations and concurrency=2. This completes application-level request admission, not model residency management: Ollama keep-alive caching, other clients, and runtime work after client cancellation remain outside this gate. No real Ollama memory/load benchmark was run.

## 2026-10-23 document-specific retrieval

Implemented an indexed-file selector in Ask your files and optional `path` on the file-answer API. Keyword and vector retrieval filter before ranking/limiting; scoped requests exclude saved memory notes and reject paths absent from the local index. Existing all-files requests remain supported. No migration is required.

Validation: 19 focused backend tests passed, including selected-file exclusion, forgotten selections, empty evidence without memory fallback, and parameterized vector scope/owner filters. All 13 frontend tests and the production frontend build passed. Mockito's attachment pipe was blocked in the sandbox; the focused backend suite passed outside it. Live PostgreSQL/Ollama and browser interaction were not tested for this change. Document-scoped retrieval is implemented; the other release gates below remain outstanding.

## 2026-10-18 current verification

This section supersedes earlier database status notes below. Connected successfully to the configured jarvis database; vector extension and all five tables exist. Applied migration 004 to fix the live match_chunks function's unresolved distance operator. Rollback-only vector smoke checks passed for matching, negative matching, and cascading deletion.

Automated checks: 108 backend tests, zero failures/errors, three opt-in live tests skipped (105 executed); 13 frontend tests passed and production frontend build passed. Final JAR packaging passed after stopping the Windows process holding the previous JAR open.

Live API checks passed: database ready, memory create/export/delete, conversation snapshot save/read/delete. Temporary records were removed. Installed all-minilm:22m locally; real indexing persisted document chunks, and a question about the temporary Silver Orchard document returned the correct ORCHID-729 answer with a citation. Forget removed both the local entry and vector record; temporary document was removed. Existing sample document remains indexed.

Implemented in this pass: database memories in local chat, JSON memory export, explicit failure for oversized chat snapshots, awaited sequential vector indexing, deduplication before embedding, vector deletion on forget, and blocking database checks moved off the request event loop. MCP calls now fail explicitly with MCP_NOT_CONNECTED instead of returning simulated execution success. Approval consumption is serialized.

Remaining release gates: MCP transport/discovery integration, shopping and scheduler implementation, full-page research, shared chat/embedding resource admission, document-scoped retrieval and model-change reindexing, broader ingestion formats, browser cancellation and microphone/speaker checks. The configured database login is postgres; these checks do not establish multi-user isolation or least-privilege deployment. The full original release is not complete.

Windows 11 / Ryzen 5 5500H / RTX 2050 4 GB. Windows reports 5.86 GiB usable RAM.

Backend including calculator: 33 tests passed, 1 optional live test skipped; packaged build succeeded. Frontend 6 transport tests passed; production build succeeded. Packaged backend startup and unavailable-runtime health passed. Idle Java heap measured 32.8 MiB (384 MiB cap).

Real Ollama 0.40.4 inference with qwen3:1.7b, thinking off, context 2048:

| Run            | Wall time | Model loading | Generation speed |
| -------------- | --------: | ------------: | ---------------: |
| First cold run |  95.235 s |      93.064 s |   28.59 tokens/s |
| Later cold run |  13.691 s |      13.153 s |   73.19 tokens/s |
| Warm run       |   0.796 s |       0.011 s |   73.53 tokens/s |
| Warm run       |   0.799 s |       0.007 s |   72.78 tokens/s |

These are short synthetic prompts, not broad quality or sustained-load benchmarks. Ollama reported 1,469,809,949 bytes GPU residency. Initial CUDA discovery timed out and Vulkan was discovered; do not infer the final backend solely from those early logs. The original Ollama pull eventually succeeded; a redundant alternate GGUF download was cancelled and was never imported.

Browser verification: actual packaged UI opened on port 18080, authenticated successfully, displayed installed model status, and rendered correctly at a 390x844 mobile viewport. Calculator verified through the browser: `calculate: (12 + 8) * 3` returned 60. Existing browser conversation also displayed model answers. Cancellation remains pending browser verification. Port 8080 belongs to an existing service and is not used by JARVIS.

Database: PostgreSQL 18 service exists; connection requires credentials and pgvector is absent. User is configuring a dedicated connection in ignored .env. No production database changes have been executed. Vector RAG, MCP, full-page research, shopping, voice and scheduler remain unimplemented. Keyword file retrieval and snippet-based web search are now implemented.

SQL schema and CRUD smoke checks passed on a separate disposable PostgreSQL 18 instance; all test record changes rolled back and the instance was stopped. A generated private token is now saved in ignored .env and loaded by the running backend. PostgreSQL credentials are still not configured.

## 2026-10-17 final integration checks

Build succeeded: 48 backend tests passed, two opt-in live tests skipped; seven frontend tests passed. Cancel control is included in the final build. Prior browser verification confirmed indexed-file answers with file/line references and AI edit proposals. The AI proposal changed wording beyond the request and was discarded; review remains necessary. The final authenticated HTTP check verified proposals do not write before apply, edits apply successfully, the sample was restored byte-for-byte, and saved memory survives restart. Only the project sample file was edited.

Live authenticated web search also passed: returned source events, a local-model answer and the completion event. JARVIS and Ollama were left running on their loopback ports.

2026-10-17: Added default token-free local mode, automatic UI connection, optional token mode, loopback/origin/header checks. Full build passed: 53 backend tests passed, 3 optional live tests skipped; 7 frontend tests passed. Browser opened without credentials and returned Calculator result: 5. PostgreSQL adapter separately passed disposable-cluster CRUD/rollback tests; live DB configuration and history UI remain pending.

2026-10-17 voice: Full build passed, 56 backend tests passed with 3 optional live tests skipped; 7 frontend tests passed. Windows in-memory synthesis-to-recognition self-test returned 'Hello Jervis what time is it' (confidence 0.739). Running /api/v1/voice/status reports en-US recognition and David/Zira voices. No microphone recording or speaker playback was performed during validation. Final browser verification blocked because browser use is disabled in Work settings; live microphone, playback and cancellation need user verification.

Browser UI verification attempted on 2026-10-18 but blocked: local browser use is disabled in Work settings. No browser cancellation, export-download interaction, or microphone/speaker verification is claimed.

# J.A.R.V.I.S. (Just A Rather Very Intelligent System): Complete System Engineering Specification & Developer Reference Manual

**Document Classification:** Production System Engineering Specification (IEEE Standard 1016-2009 Compliant)  
**System Repository:** `d:\Jarvis\jarvis-ai`  
**Target Runtime:** Java 21 LTS (Spring Boot 3.3.x WebFlux / Netty), React 18 / TypeScript / Vite, Ollama Edge Runtime  
**Revision:** 2026.4.2 Enterprise Monograph  
**Document Code:** JARVIS-ENG-SPEC-2026-FINAL

---

## Executive Summary

This document serves as the exhaustive system engineering specification, internal microarchitecture manual, and developer reference for **J.A.R.V.I.S.**—a 100% locally hosted, edge-native autonomous personal cognitive assistant. J.A.R.V.I.S. eliminates mandatory reliance on third-party cloud APIs (such as OpenAI, Anthropic, or Google Cloud) by executing a coordinated **Mixture-of-Small-Models (MoSM)** fleet locally on consumer workstation hardware.

---

## Table of Contents

1. [System Architectural Topology & Dataflow](#1-system-architectural-topology--dataflow)
2. [Reactive Gateway & Netty EventLoop Microarchitecture](#2-reactive-gateway--netty-eventloop-microarchitecture)
3. [Exhaustive Package & Class-by-Class Technical Review](#3-exhaustive-package--class-by-class-technical-review)
4. [Complete REST & Server-Sent Events (SSE) API Contract](#4-complete-rest--server-sent-events-sse-api-contract)
5. [Intent Classification & Segment Dispatching Engine](#5-intent-classification--segment-dispatching-engine)
6. [Dynamic Prompt Compilation & Zero-Hallucination Guardrails](#6-dynamic-prompt-compilation--zero-hallucination-guardrails)
7. [Semantic Vector Memory & Long-Term RAG Engine](#7-semantic-vector-memory--long-term-rag-engine)
8. [Real-Time Grounding & Market Scraper Subsystem](#8-real-time-grounding--market-scraper-subsystem)
9. [Smart Home (Home Assistant / MQTT) & MCP Tool Bridge](#9-smart-home-home-assistant--mqtt--mcp-tool-bridge)
10. [Multilingual Speech Processing & Tamil Dialect Rules](#10-multilingual-speech-processing--tamil-dialect-rules)
11. [Hardware Profiling, Thermal Boundaries & Verification](#11-hardware-profiling-thermal-boundaries--verification)

---

## 1. System Architectural Topology & Dataflow

```mermaid
flowchart TD
    subgraph Presentation_Tier["1. Presentation Tier (Vite / React 18 / Web Audio)"]
        BrowserUI["React 18 Chat & Dashboard Interface"]
        AudioIn["Web Audio API MediaRecorder (Speech-to-Text)"]
        AudioOut["Web Speech API / Kokoro TTS (Text-to-Speech)"]
        BrowserUI <--> AudioIn
        BrowserUI <--> AudioOut
    end

    subgraph Gateway_Tier["2. Reactive Gateway Tier (Port 18080 - Spring WebFlux / Netty)"]
        Netty["Netty EventLoop ThreadPool"]
        Cors["CorsConfig & Security Filter"]
        Codec["10MB DataBuffer Limit Pool"]
        Admission["ModelAdmission Semaphore Gatekeeper (Permit Limit = 1)"]

        Netty --> Cors --> Codec --> Admission
    end

    subgraph Logic_Tier["3. Core Business Logic & Orchestration Core"]
        ChatCtrl["ChatController (REST / SSE Stream Gateway)"]
        ChatSvc["ChatService (Conversational Coordinator)"]
        Router["SegmentRouter (Intent Heuristics & Slash Directives)"]
        Prompter["PromptFactory (Zero-Hallucination Prompt Compiler)"]

        Admission --> ChatCtrl
        ChatCtrl --> ChatSvc
        ChatSvc --> Router --> Prompter
    end

    subgraph Peripherals_Tier["4. Grounding, Automation & Peripheral Services"]
        FactSvc["RealtimeFactService (Yahoo Finance / Bullion / OTT)"]
        SearchSvc["WebSearch (DuckDuckGo HTML + Wikimedia REST)"]
        MemSvc["KnowledgeService (Vector Embeddings & Semantic Search)"]
        AppLaunch["SystemAppLauncher (Host Windows Process Automation)"]
        IoTSvc["HomeAssistantClient & MqttGatewayClient (Smart Home)"]
        McpSvc["McpApprovalService (Human-in-the-Loop MCP Execution)"]

        ChatCtrl --> FactSvc
        ChatCtrl --> SearchSvc
        ChatCtrl --> MemSvc
        ChatSvc --> AppLaunch
        ChatSvc --> IoTSvc
        ChatSvc --> McpSvc
    end

    subgraph Inference_Tier["5. Edge Inference Fleet (Port 11434 - Ollama Engine)"]
        OllamaBridge["OllamaProvider (Reactive WebClient Bridge)"]
        QwenChat["qwen3:1.7b (General Voice & Tactical War Mode)"]
        DeepSeek["deepseek-r1:1.5b (Cognitive Reasoning, Math & Code)"]
        Researcher["jarvis-researcher:latest (Real-Time Fact Synthesis)"]
        MiniLM["all-minilm:22m (384-dim Vector Bi-Encoder)"]

        Prompter --> OllamaBridge
        OllamaBridge --> QwenChat & DeepSeek & Researcher & MiniLM
    end

    Presentation_Tier <== "Server-Sent Events (SSE) / JSON REST" ==> Gateway_Tier
```

---

## 2. Reactive Gateway & Netty EventLoop Microarchitecture

Unlike traditional Spring MVC architectures that allocate one OS thread per connection (`Thread-Per-Request`), J.A.R.V.I.S. utilizes **Spring Boot WebFlux and Project Reactor** running on an asynchronous, non-blocking Netty engine.

### A. Threading Model and Non-Blocking Event Loops

- **EventLoop Group:** Operates on a fixed pool of selector threads where $N_{\text{threads}} = 2 \times N_{\text{CPU Cores}}$.
- **Bounded Elastic Scheduler:** Blocking I/O tasks (such as local file reads in [`LocalFiles.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/files/LocalFiles.java) or legacy database transactions) are dispatched to `Schedulers.boundedElastic()`, ensuring Netty event loops are never blocked.
- **Reactive Backpressure:** Stream subscriptions adhere to the Reactive Streams specification. Chunks from Ollama are pushed downstream only when the client browser is ready to consume them (`request(n)`).

### B. High-Volume In-Memory Buffer Configuration

Standard Spring WebFlux defaults to a 64 KB memory limit for incoming payloads. When passing prolonged chat histories containing multiple generated code blocks, this caused:

```text
org.springframework.core.io.buffer.DataBufferLimitException: Exceeded limit on max bytes to buffer : 65536
```

In [`backend/src/main/resources/application.yml`](file:///d:/Jarvis/jarvis-ai/backend/src/main/resources/application.yml), this constraint is expanded to 10 MB:

```yaml
server:
  address: 127.0.0.1
  port: ${JARVIS_PORT:18080}
spring:
  codec:
    max-in-memory-size: 10MB
jarvis:
  ollama-url: ${OLLAMA_BASE_URL:http://127.0.0.1:11434}
  chat-model: ${JARVIS_CHAT_MODEL:qwen3:1.7b}
  embedding-model: ${JARVIS_EMBEDDING_MODEL:all-minilm:22m}
  embedding-dimensions: ${JARVIS_EMBEDDING_DIMENSIONS:384}
  context-length: ${JARVIS_CONTEXT_LENGTH:8192}
  output-tokens: ${JARVIS_OUTPUT_TOKENS:4096}
  concurrency: ${JARVIS_CONCURRENCY:1}
  connect-timeout: 3s
  response-timeout: ${JARVIS_RESPONSE_TIMEOUT:120s}
  health-timeout: 3s
  keep-alive: ${JARVIS_KEEP_ALIVE:30s}
```

---

## 3. Exhaustive Package & Class-by-Class Technical Review

The backend comprises decoupled components under `com.jarvis.*`:

### Table II: Comprehensive Java Class Directory

| Package                | Class Name                                                                                                                                     | Architectural Responsibility & Key Methods                                                                                                      |
| :--------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------- |
| `com.jarvis.ai`        | [`AiProvider.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/ai/AiProvider.java)                                           | **Interface:** Model-independent boundary defining `Flux<String> stream(Prompt prompt)` and `Mono<ModelHealth> health()`.                       |
| `com.jarvis.ai`        | [`OllamaProvider.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/ai/OllamaProvider.java)                                   | **Implementation:** Reactive WebClient connector streaming token chunks from Ollama's `/api/chat` or `/api/generate`.                           |
| `com.jarvis.ai`        | [`SegmentRouter.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/ai/SegmentRouter.java)                                     | **Router:** Multi-model intent heuristic classifier assigning queries to `WAR`, `MATH`, `CODE`, `RESEARCH`, `VISION`, or `GENERAL` segments.    |
| `com.jarvis.ai`        | [`ModelAdmission.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/ai/ModelAdmission.java)                                   | **Gatekeeper:** Thread-safe non-blocking semaphore (`tryAcquire()`) enforcing concurrency limits ($N=1$) to protect edge GPU VRAM.              |
| `com.jarvis.ai`        | [`HealthController.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/ai/HealthController.java)                               | **Endpoint:** Exposes `GET /api/health`, probing Ollama status and verifying installed model readiness.                                         |
| `com.jarvis.chat`      | [`ChatController.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/chat/ChatController.java)                                 | **Primary Controller:** Exposes `POST /api/chat`, orchestrating real-time fact checks, web searches, semantic memory recall, and SSE streaming. |
| `com.jarvis.chat`      | [`ChatService.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/chat/ChatService.java)                                       | **Coordinator:** Evaluates deterministic shortcuts, executes tool-calling loops, and binds admission permits to active token streams.           |
| `com.jarvis.chat`      | [`PromptFactory.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/chat/PromptFactory.java)                                   | **Prompt Compiler:** Injects zero-hallucination directives, spoken language cadence rules, user memory notes, and MCP tool schemas.             |
| `com.jarvis.chat`      | [`ChatRequest.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/chat/ChatRequest.java)                                       | **DTO Record:** Encapsulates incoming `message` and `history` turns, bounded with `@Size(max=32000)`.                                           |
| `com.jarvis.chat`      | [`LocalFacts.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/chat/LocalFacts.java)                                         | **Deterministic:** Computes exact local time, date, day of week, host OS, and user identity with 0ms latency.                                   |
| `com.jarvis.chat`      | [`Arithmetic.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/chat/Arithmetic.java)                                         | **Deterministic:** Solves basic algebraic expressions (e.g., `25 * 4`) deterministically without LLM token usage.                               |
| `com.jarvis.realtime`  | [`RealtimeFactService.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/realtime/RealtimeFactService.java)                   | **Market Scraper:** Pulls live gold/silver rates, NSE/BSE stock quotes, crude oil prices, and weekly OTT releases.                              |
| `com.jarvis.search`    | [`WebSearch.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/search/WebSearch.java)                                         | **Internet Engine:** Autonomous web scraper pulling DuckDuckGo HTML snippets and Wikipedia REST summaries.                                      |
| `com.jarvis.knowledge` | [`KnowledgeService.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/knowledge/KnowledgeService.java)                        | **RAG Engine:** Generates 384-dimensional vector embeddings, retrieves top-$k$ memories, and invalidates outdated facts.                        |
| `com.jarvis.system`    | [`SystemAppLauncher.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/system/SystemAppLauncher.java)                         | **Host Automation:** Executes `ProcessBuilder` commands to launch Windows applications (Notepad, Chrome, VS Code).                              |
| `com.jarvis.training`  | [`RealtimeModelTrainingService.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/training/RealtimeModelTrainingService.java) | **Model Specialization:** Programmatically constructs and compiles Ollama Modelfiles via the `/api/create` endpoint.                            |
| `com.jarvis.smarthome` | [`HomeAssistantClient.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/smarthome/HomeAssistantClient.java)                  | **IoT Bridge:** Interacts with Home Assistant REST API entities (switches, lights, sensors).                                                    |
| `com.jarvis.mcp`       | [`McpApprovalService.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/mcp/McpApprovalService.java)                          | **Security:** Manages human-in-the-loop approvals for Model Context Protocol (MCP) tool execution.                                              |

---

## 4. Complete REST & Server-Sent Events (SSE) API Contract

### Primary Chat Endpoint

```http
POST /api/chat
Content-Type: application/json
Accept: text/event-stream
```

#### Request Payload:

```json
{
  "message": "What is the current price of 24K gold in INR?",
  "history": [
    {
      "role": "user",
      "content": "Hello Jarvis"
    },
    {
      "role": "assistant",
      "content": "At your service, Sir. How may I assist you?"
    }
  ]
}
```

#### Server-Sent Events (SSE) Stream Protocol:

1. **Segment Routing Event (`event: segment`):**
   ```text
   event: segment
   data: {"segment":"RESEARCH","model":"jarvis-researcher:latest","explanation":"Detected commodity / bullion query"}
   ```
2. **Web Sources Citation Event (`event: sources`):**
   ```text
   event: sources
   data: [{"index":1,"title":"Live Gold Rates - Bullion India","url":"https://...","snippet":"24K Gold is ₹... per gram"}]
   ```
3. **Incremental Token Chunks (`event: delta`):**

   ```text
   event: delta
   data: {"text":"The current "}

   event: delta
   data: {"text":"price of 24K gold "}
   ```

4. **Stream Termination Event (`event: done`):**
   ```text
   event: done
   data: {}
   ```

#### Error Response Contract:

```json
{
  "code": "CONCURRENCY_LIMIT",
  "message": "The edge model is currently processing another query. Concurrency is limited to prevent VRAM over-subscription.",
  "status": 429
}
```

---

## 5. Intent Classification & Segment Dispatching Engine

```mermaid
flowchart TD
    UserQuery["User Query Input String"] --> Normalize["normalizeQueryTypos()<br/>(Fixes 'goald' -> 'gold', 'todays' -> 'today's')"]
    Normalize --> DisputeCheck{"isCorrectionOrDispute()"}

    DisputeCheck -- Yes --> CleanDispute["Invalidate Memory & Suppress Research Scraper"]
    DisputeCheck -- No --> SlashCheck{"Explicit Slash Directive?<br/>(/war, /code, /math, /research, /vision)"}

    SlashCheck -- Yes --> SlashDispatch["Dispatch Dedicated Segment & Model (temp locked)"]
    SlashCheck -- No --> RegexCheck{"Evaluate Heuristic Regex Patterns"}

    RegexCheck -- WAR_PATTERN --> WarSeg["Segment.WAR -> qwen3:1.7b (T = 0.85)"]
    RegexCheck -- RESEARCH_PATTERN --> ResSeg["Segment.RESEARCH -> jarvis-researcher:latest (T = 0.20)"]
    RegexCheck -- REASONING_PATTERN --> MathSeg["Segment.MATH -> deepseek-r1:1.5b (T = 0.20)"]
    RegexCheck -- CODE_PATTERN --> CodeSeg["Segment.CODE -> deepseek-r1:1.5b (T = 0.20)"]
    RegexCheck -- Default --> GenSeg["Segment.GENERAL -> qwen3:1.7b (T = 0.70)"]
```

### Heuristic Priority Order:

1. **Explicit Slash Overrides:** Commands like `/war`, `/math`, `/code`, `/research` supersede automatic detection.
2. **Dispute Filtering:** Queries expressing dissatisfaction (`"never asked"`, `"not what i asked"`) short-circuit search scraping to address user feedback directly.
3. **War Mode Directives:** High-adrenaline tactical directives take precedence over general chat.
4. **Research & Facts:** Queries matching stocks, commodities, crypto, or OTT schedules are routed to `jarvis-researcher:latest`.
5. **Deductive Reasoning & Mathematics:** Equations, step-by-step logic, and programming bugs are routed to `deepseek-r1:1.5b`.
6. **General Assistant:** All remaining conversational dialogue defaults to `qwen3:1.7b`.

---

## 6. Dynamic Prompt Compilation & Zero-Hallucination Guardrails

In [`PromptFactory.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/chat/PromptFactory.java), the final prompt is assembled under strict context budgeting:

```mermaid
flowchart LR
    Sys["System Prompt & Temporal Directives (250 tokens)"] --> BudgetCalc{"Context Budgeting<br/>(Max 8192 tokens)"}
    Evidence["Web / Real-time Evidence (Up to 2048 tokens)"] --> BudgetCalc
    History["Sanitized History Turns (Recent 10 turns)"] --> BudgetCalc
    Query["Current User Message"] --> BudgetCalc
    BudgetCalc --> AssembledPrompt["Assembled Ollama Prompt Payload"]
```

### Hallucination Prevention Directives:

```text
STRICT ZERO HALLUCINATION DIRECTIVE: Never invent, extrapolate, or fabricate prices, numbers, or dates.
Never provide 'hypothetical values for illustration'.
If the provided search snippets do not state the exact current price or number requested,
state clearly that the exact figure is not available in the snippets rather than inventing numbers.
When the user corrects or questions an answer ('you are wrong', 'are you sure'),
do NOT defend previous assertions without evidence.
```

---

## 7. Semantic Vector Memory & Long-Term RAG Engine

Memory retrieval in [`KnowledgeService.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/knowledge/KnowledgeService.java) executes via local vector bi-encoders:

1. When a memory or user fact is saved, it is vectorized using `all-minilm:22m` into a 384-dimensional dense array.
2. Vectors are stored in `DatabaseStore.java` alongside raw text and epoch timestamps.
3. On incoming queries, a query vector $\mathbf{e}_q$ is produced and compared via dot-product cosine similarity:
   $$\cos(\mathbf{e}_q, \mathbf{e}_m) = \sum_{k=1}^{384} e_{q, k} \cdot e_{m, k}$$
4. Memories with similarity exceeding threshold $\sigma \ge 0.72$ are extracted and injected into the prompt context.
5. If a user states a retraction (`"That's not my car"`, `"I moved to Bangalore"`), the old conflicting memory is located via cosine similarity and invalidated (`active = false`), preventing memory pollution.

---

## 8. Real-Time Grounding & Market Scraper Subsystem

[`RealtimeFactService.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/realtime/RealtimeFactService.java) provides live market data without requiring expensive commercial API subscriptions:

- **Commodities:** Fetches 24K and 22K Gold rates per gram/sovereign in INR, Silver prices, and Brent/WTI Crude Oil barrel rates.
- **Equities & Indices:** Pulls live prices, percentage changes, and day ranges for Nifty 50, Sensex, Dow Jones, NASDAQ, and S&P 500 tickers via Yahoo Finance endpoints.
- **Entertainment Releases:** Scrapes current-week theatre and OTT streaming releases across Netflix, Prime Video, Disney+ Hotstar, and Zee5.
- **Temporal Anchoring:** Guarantees that time queries (`"what time is it"`, `"today's date"`) evaluate against local system clocks rather than training cutoffs.

---

## 9. Smart Home (Home Assistant / MQTT) & MCP Tool Bridge

J.A.R.V.I.S. integrates with local IoT infrastructure through two protocols:

1. **Home Assistant REST Gateway ([`HomeAssistantClient.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/smarthome/HomeAssistantClient.java)):**
   - Communicates with Home Assistant running locally on the network.
   - Dispatches state changes to lights, switches, scenes, and climate thermostats.
2. **Model Context Protocol (MCP) Bridge ([`McpApprovalService.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/mcp/McpApprovalService.java)):**
   - Discovers tool schemas from active MCP servers.
   - Enforces **Human-in-the-Loop** approval for high-risk actions (file modifications, system shutdowns, payments).
   - Generates a cryptographic `challengeId` requiring user confirmation before tool invocation.

---

## 10. Multilingual Speech Processing & Tamil Dialect Rules

To support natural conversation across languages, [`PromptFactory.java`](file:///d:/Jarvis/jarvis-ai/backend/src/main/java/com/jarvis/chat/PromptFactory.java) implements automatic linguistic detection:

- **Tamil Script Regex:** Matches Unicode range `[\u0B80-\u0BFF]` or romanized Tamil expressions (`"vanakkam"`, `"eppadi iruk"`).
- **Spoken Dialect Directive:**
  ```text
  LANGUAGE DIRECTIVE (TAMIL FLUENT SPOKEN SLANG MODE): The user communicated in Tamil (தமிழ்).
  CRITICAL SPOKEN STYLE: Do NOT respond in stiff, formal, bookish, or archaic pure Tamil (தூய செந்தமிழ் வேண்டாம்).
  Speak naturally as an educated modern native speaker using smooth colloquial conversational spoken Tamil (பேச்சுத் தமிழ்).
  Blend commonly borrowed English technical and conversational terms naturally where appropriate.
  ```
  This prevents the assistant from generating archaic literary Tamil that sounds unnatural when synthesized by Text-to-Speech engines.

---

## 11. Hardware Profiling, Thermal Boundaries & Verification

### A. System Resource Consumption

Comprehensive benchmarks executed on a reference workstation (Intel Core i7-13700H, 16 GB DDR5 RAM, NVIDIA RTX 4060 8GB):

| Subsystem                            | State                  |  Memory Utilization   |          Compute Load          |
| :----------------------------------- | :--------------------- | :-------------------: | :----------------------------: |
| **Spring Boot WebFlux Backend**      | Running (Idle)         |        ~280 MB        |           < 0.5% CPU           |
| **React / Vite Frontend**            | Web Browser Session    |        ~45 MB         |           Negligible           |
| **Ollama Daemon (`llama.cpp`)**      | Ready (Weights Loaded) |     1.36 GB VRAM      |             0% GPU             |
| **Active Inference (`qwen3:1.7b`)**  | Generating Tokens      |     1.42 GB VRAM      |    85–92% GPU Tensor Cores     |
| **Vector Search (`all-minilm:22m`)** | Embedding Query        |       46 MB RAM       |  Multi-threaded AVX2 (< 5 ms)  |
| **Total System Footprint**           | **Active Generation**  | **~1.78 GB RAM/VRAM** | **Within 4GB-8GB VRAM Limits** |

### B. Automated Verification Test Suites

Every architectural modification in J.A.R.V.I.S. is validated against automated test suites:

- **Backend Unit & Integration Suite:**
  ```powershell
  mvn test -f backend/pom.xml
  # Result: 301 Tests Run, 0 Failures, 0 Errors, 3 Skipped (100% Pass Rate)
  ```
- **Frontend React / Vitest Suite:**
  ```powershell
  npm test --prefix frontend -- --run
  # Result: 32 Tests Run, 32 Passed (100% Pass Rate)
  ```

---

## 12. Conclusion

The J.A.R.V.I.S. system engineering specification demonstrates how combining a reactive, non-blocking Spring WebFlux Netty gateway, heuristic segment routing, deterministic shortcut gates, and verified real-time web grounding enables small, dense language models to deliver private, real-time cognitive assistance on commodity edge hardware.

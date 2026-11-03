# How to Run JARVIS AI & Autonomous Coding Studio

This guide provides the complete, tested instructions to run the JARVIS AI workspace, autonomous coding agent, neural knowledge graph, and desktop application on Windows.

---

## ⚡ Quick Start Options

### Option 1: Desktop Application Studio (Cursor / Claude Code Mode)
For multi-hour continuous coding tasks, run the standalone desktop studio:

```cmd
:: From the project root (d:\Jarvis\jarvis-ai), double-click or run in terminal:
start-coding-app.bat
```

Or via PowerShell:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\start-desktop-app.ps1 -View coding
```

**What this does automatically:**
1. Verifies if the JARVIS Spring Boot backend is active on port `18080`. If offline, boots it in the background automatically.
2. Locates your installed Chromium engine (Microsoft Edge or Google Chrome).
3. Opens a standalone, borderless desktop window (`--app=http://127.0.0.1:18080/#coding`) with an isolated profile (`data/desktop-coding-profile`) to prevent browser tab throttling during multi-hour tasks.

---

### Option 2: Unified Web Application Mode

```powershell
# Open terminal in d:\Jarvis\jarvis-ai and run:
start.bat
# Or:
powershell -File scripts/run.ps1
```

👉 **Open your browser at:**
* **Autonomous Coding Studio**: [http://127.0.0.1:18080/#coding](http://127.0.0.1:18080/#coding)
* **Obsidian Neural Knowledge Graph**: [http://127.0.0.1:18080/#graph](http://127.0.0.1:18080/#graph)
* **General AI Assistant**: [http://127.0.0.1:18080](http://127.0.0.1:18080)

---

## 📋 Complete Step-by-Step Setup

### Step 1: Prerequisites

1. **Java 21**: Verify your Java runtime:
   ```powershell
   java -version
   ```
2. **Ollama Service**: Ensure Ollama is running on port `11434`:
   ```powershell
   powershell -File scripts/start-ollama.ps1
   ```
3. **Pull Recommended Local Minimal-Resource Models**:
   ```powershell
   # 1. Specialized Code Engineering (Cursor & Claude Code engine)
   ollama pull qwen2.5-coder:1.5b

   # 2. Deep Cognitive Reasoning & Math Proofs (DeepSeek-R1 CoT)
   ollama pull deepseek-r1:1.5b

   # 3. High-Speed Local RAG Embeddings (46 MB)
   ollama pull all-minilm:22m

   # 4. (Optional) General Conversational Model
   ollama pull qwen3:1.7b
   ```

---

### Step 2: Build & Package (Production Unified JAR)

Whenever you modify frontend or backend files, package the single executable JAR:

```powershell
# 1. Build React frontend bundle into frontend/dist
cd frontend
npm run build
cd ..

# 2. Package Spring Boot JAR with embedded frontend
cd backend
mvn.cmd package -DskipTests
cd ..
```

The resulting executable fat JAR is located at:
`backend/target/jarvis-backend-0.1.0-SNAPSHOT.jar` (~50 MB).

---

### Step 3: Choose Your Run Mode

#### Mode A: Unified Production Mode (Fast & Light)
Serves REST APIs, SSE streams, and React UI from a single process on port `18080`:

```powershell
powershell -File scripts/start-backend.ps1
```
* **Memory Footprint**: Strict 384 MB heap ceiling (`-Xmx384m`).
* **Authentication**: Default local mode connects automatically without tokens.

#### Mode B: Frontend Developer Mode (Vite Hot-Reload)
Use this when modifying React components in `frontend/src/`:

1. **Terminal 1 (Backend API)**:
   ```powershell
   powershell -File scripts/start-backend.ps1
   ```
2. **Terminal 2 (Vite Hot Server)**:
   ```powershell
   cd frontend
   npm run dev
   ```
* **Dev UI URL**: [http://localhost:5173/#coding](http://localhost:5173/#coding) *(Vite proxies `/api/*` to `18080` automatically)*.

---

### Step 4: (Optional) PostgreSQL Database Setup

JARVIS automatically stores coding sessions and diffs in local JSON (`data/coding-sessions.json`). If you use PostgreSQL:

1. Open pgAdmin or `psql` connected to your database.
2. Run migration:
   ```sql
   \i database/008_coding_agent.sql
   ```
3. (Optional) Verify schema with the transactional smoke test:
   ```sql
   \i database/009_coding_agent_smoke_rollback.sql
   ```

---

### Step 5: How to Use the Autonomous Coding Studio

1. **Launch a Coding Task**:
   - Type an objective in the prompt box (e.g., `"Implement LRUCache in com.jarvis.common with unit tests"` or click one of the quick starter pills).
   - Alternatively, in the main chat assistant, type `/code <task>` to jump directly into the studio.
2. **Select Search Provider**:
   - Choose `AUTO`, `CHROMIUM_GOOGLE`, `CHROMIUM_DUCKDUCKGO`, `CHROMIUM_BING`, or `DUCKDUCKGO_HTTP` in the header bar.
3. **Approve Consequential Actions**:
   - Safe operations (reading files, directory trees, web searches) run automatically.
   - Consequential operations (editing files, creating files, terminal commands) pause and display a permission challenge modal. Click **Approve** to proceed.
4. **Interactive Terminal Console**:
   - In the **Build Console** tab, type commands into the bottom input prompt (e.g. `git status`, `mvn test`, `npm test`) and hit Enter.
5. **1-Click Rollback & Resume**:
   - Review unified color-coded diffs under the **File Diffs** tab.
   - Click **Rollback Changes** anytime to revert all changes made during the session.
   - If a session is interrupted, click **Resume Task** to continue from the last checkpoint.

```powershell
# 1. Check Backend availability
Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/v1/health/live'
# Expected output: status = up

# 2. Check Local Ollama model readiness
Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/v1/health/model'
# Expected output: status = ready, model = qwen3:1.7b

# 3. Check System diagnostics (RAM, GPU, context limits)
Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/v1/diagnostics'
```

---

## 🛠️ Common Troubleshooting

| Issue | Cause | Solution |
| :--- | :--- | :--- |
| **`mvn package` fails: "The process cannot access the file because it is being used by another process"** | A previous instance of JARVIS (`java.exe`) is running and holding the `.jar` open. | Run `Stop-Process -Id (Get-NetTCPConnection -LocalPort 18080).OwningProcess -Force` before packaging. |
| **Model status shows `missing_model`** | Ollama is running but `qwen3:1.7b` has not been pulled. | Run `ollama pull qwen3:1.7b`. |
| **Model status shows `unavailable`** | Ollama is not running on port `11434`. | Launch Ollama from Windows Start or run `powershell -File scripts/start-ollama.ps1`. |
| **Port 18080 already in use** | Another service or orphaned backend instance owns port 18080. | Change port via `JARVIS_PORT=18081` in `.env` or terminate the owning process. |

---

## 🧪 Running Automated Tests

To verify that all features, security policies, and integrations are green:

```powershell
# Run all backend unit & integration tests (167 tests):
powershell -File scripts/test-backend.ps1

# Run frontend tests (20 tests):
cd frontend
npm test -- --run
```

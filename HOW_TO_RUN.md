# How to Run JARVIS AI Properly

This guide provides the complete, tested steps to run the JARVIS AI workspace on your local Windows system from the beginning, without port conflicts, JAR locking errors, or model repetition.

---

## ⚡ Quick Start (Single-Command Run)

### Method A: One-Click Windows Launcher (Fastest)
Simply double-click `start.bat` in the project root (`D:\Jarvis\jarvis-ai\start.bat`), or run:
```cmd
start.bat
```

### Method B: PowerShell Direct Execution
To automatically terminate any stale instances on port 18080, rebuild with latest changes, and start the application:

```powershell
# Open PowerShell in D:\Jarvis\jarvis-ai and run:
powershell -File scripts/run.ps1 -Build
```

👉 **Open your browser at:** [http://127.0.0.1:18080](http://127.0.0.1:18080)

---

## 📋 Complete Step-by-Step Instructions (From the Beginning)

### Step 1: Ensure Prerequisites & Background Services Are Ready

1. **JDK 21**:
   Verify Java 21 is reachable:
   ```powershell
   & 'D:\Tools\jdk-21\bin\java.exe' -version
   ```

2. **Ollama Service**:
   Ensure Ollama is running and listening on port `11434`:
   ```powershell
   # If not already running from your Windows tray, start it:
   powershell -File scripts/start-ollama.ps1
   ```

3. **Required Local Models**:
   Ensure the required local models are pulled in Ollama:
   ```powershell
   # Chat model (1.7B parameter fast local model)
   ollama pull qwen3:1.7b

   # Embedding model (lightweight 46MB model for RAG & vector memory)
   ollama pull all-minilm:22m
   ```

---

### Step 2: Clean Up Any Stale Processes on Port 18080

If an older Java process is running in the background, Windows will lock the `.jar` file (causing Maven build errors) or fail with `Port 18080 was already in use`. Run this cleanup command first:

```powershell
# Stop all processes currently holding port 18080:
$conflicts = Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue
if ($conflicts) {
    $pids = $conflicts | Select-Object -ExpandProperty OwningProcess -Unique | Where-Object { $_ -gt 0 }
    foreach ($p in $pids) {
        Write-Host "Releasing port 18080 (killing PID $p)..."
        Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Milliseconds 600
}
```

---

### Step 3: Build & Package the Application

Whenever you make frontend or backend code modifications, rebuild the artifacts:

```powershell
# 1. Build the React frontend production bundle:
cd frontend
npm run build
cd ..

# 2. Package the unified backend executable JAR:
cd backend
mvn.cmd package -DskipTests
cd ..
```

The resulting unified bundle will be placed at:
`backend/target/jarvis-backend-0.1.0-SNAPSHOT.jar`

---

### Step 4: Start the JARVIS Server

#### Option A: Unified Production Mode (Recommended)
The Spring Boot backend serves both the **REST API** and the compiled **React Web UI** as a single lightweight process on port `18080`:

```powershell
powershell -File scripts/start-backend.ps1
```

* **Web UI URL**: [http://127.0.0.1:18080](http://127.0.0.1:18080)
* **API Health Check**: [http://127.0.0.1:18080/api/v1/health/live](http://127.0.0.1:18080/api/v1/health/live)
* **Memory Footprint**: Default 512 MB heap ceiling (`-Xmx512m`) with 128 MB direct memory.
* **Authentication**: Default local mode automatically connects via loopback security headers.

#### Option B: Developer Mode (Vite Hot-Reload)
Use this mode when you are actively writing code in `frontend/src/` and want instant hot-module replacement in the browser:

1. **Terminal 1 (Backend API)**:
   ```powershell
   powershell -File scripts/start-backend.ps1
   ```
2. **Terminal 2 (Vite Dev Server)**:
   ```powershell
   cd frontend
   npm run dev
   ```
* **Dev UI URL**: [http://localhost:5173](http://localhost:5173) *(Vite proxies `/api/*` calls to `18080` automatically)*.

---

### Step 5: Verify System Health

In your browser or PowerShell, confirm that all services report healthy status:

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

## 🖥️ Desktop Application Control (Launch & Close)

JARVIS features native, bidirectional application management for Windows. It remembers the last opened application and executes closing commands without falling through to LLM hallucination:

### 1. English Commands
| Action | Example Commands |
| :--- | :--- |
| **Launch Specific App** | `"open notepad"`, `"launch chrome"`, `"start calculator"`, `"open vs code"`, `"run spotify"` |
| **Close Specific App** | `"close notepad"`, `"exit chrome"`, `"kill calculator"`, `"close vs code"`, `"quit spotify"` |
| **Close Last Opened App** | `"close it"`, `"close the opened application"`, `"close the app"`, `"close active window"` |

### 2. Tamil Commands (தமிழ் கட்டளைகள்)
| செயல்பாடு | மாதிரி கட்டளைகள் |
| :--- | :--- |
| **பயன்பாடு திறக்க** | `"நோட்பேடை திற"`, `"குரோமை திற"`, `"கால்குலேட்டரை திற"`, `"குரோம் திற"` |
| **பயன்பாடு மூட** | `"நோட்பேடை மூடு"`, `"குரோமை மூடு"`, `"கால்குலேட்டரை மூடு"`, `"மூடு"`, `"மூடவும்"` |
| **திறக்கப்பட்ட பயன்பாடு மூட** | `"அதை மூடு"`, `"பயன்பாட்டை மூடு"`, `"திறக்கப்பட்ட பயன்பாட்டை மூடு"` |

---

## 🛡️ Stability & Repetition Prevention (How It Works)

1. **No Phantom MCP Tool Hallucinations**:
   - In earlier configurations, dummy MCP tools (`execute_system_command`, `delete_resource`) were advertised to the LLM even when no MCP server was connected.
   - When users asked to close an app, the 1.7B model outputted `[TOOL_CALL: execute_system_command {"command":"notepad","args":["--close"]}]`. Because the chat endpoint streams plain text, the tool was never run, the app never closed, and the tool call accumulated in chat history—causing the model to repeat it continuously.
   - **Resolution**: `ChatService` and `McpApprovalService` now filter discovery via `discoverActiveTools()`, ensuring tools are only advertised when real MCP servers are connected. In addition, `PromptFactory` automatically sanitizes any historical `[TOOL_CALL: ...]` artifacts.

2. **Repetition Penalty Control**:
   - `OllamaChatOptions` now enforces `.repeatPenalty(1.15)` and `.repeatLastN(64)`.
   - This eliminates repetitive token loops, repeated phrases, and conversational echo loops in small models (`qwen3:1.7b` and `deepseek-r1:1.5b`).

3. **Deterministic App Closing**:
   - `SystemAppLauncher`, `DesktopSystemService`, and `ChatService` now intercept close commands (`close`, `exit`, `quit`, `kill`, `மூடு`) directly before the request reaches the LLM.
   - Graceful window termination (`$p.CloseMainWindow()`) with forceful process fallback (`Stop-Process -Force` / `taskkill`) ensures applications close reliably on Windows 10 and 11.

---

## 🛠️ Common Troubleshooting

| Issue | Cause | Solution |
| :--- | :--- | :--- |
| **`mvn package` fails: "The process cannot access the file because it is being used by another process"** | A previous instance of JARVIS (`java.exe`) is running and holding the `.jar` open. | Run `Stop-Process -Id (Get-NetTCPConnection -LocalPort 18080).OwningProcess -Force` before packaging. |
| **Port 18080 already in use** | A previous background or orphaned backend instance owns port 18080. | The `scripts/start-backend.ps1` and `scripts/run.ps1` scripts now automatically clear this. Or run the cleanup command in Step 2. |
| **Model repeating the same output continuously** | History contains unexecuted tool call strings or repetition penalty was disabled. | Resolved with `.repeatPenalty(1.15)` and active MCP tool filtering. Restart the conversation or open a fresh chat session. |
| **Application not closing when requested** | Close command was previously missing from launcher/dispatcher. | Fully resolved! Use `"close notepad"`, `"close it"`, or `"நோட்பேடை மூடு"`. |
| **Low-Memory / Paging File Warning on Windows** | Host Windows commit charge exceeded 90% with low pagefile. | Close background applications (e.g. idle browser tabs or heavy games) to keep free memory above 1.5 GB. |
| **Model status shows `missing_model`** | Ollama is running but `qwen3:1.7b` has not been pulled. | Run `ollama pull qwen3:1.7b`. |
| **Model status shows `unavailable`** | Ollama is not running on port `11434`. | Launch Ollama from Windows Start or run `powershell -File scripts/start-ollama.ps1`. |

---

## 🧪 Running Automated Tests

To verify that all features, security policies, and application launchers are green:

```powershell
# Run all backend unit & integration tests (301 tests passing):
powershell -File scripts/test-backend.ps1

# Run frontend test suite (32 tests passing):
cd frontend
npm test -- --run
```

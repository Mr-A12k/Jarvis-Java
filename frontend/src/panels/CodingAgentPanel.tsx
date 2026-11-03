import React, { useEffect, useState, useRef } from "react";
import {
  Code2,
  Play,
  RotateCcw,
  CheckCircle2,
  XCircle,
  Clock,
  Loader2,
  FileCode,
  Folder,
  ChevronRight,
  ChevronDown,
  Terminal,
  Search,
  Globe,
  Sparkles,
  AlertTriangle,
  RefreshCw,
  Copy,
  Check,
  Undo2,
  Layers,
  Cpu,
  Brain,
  History,
  Share2,
} from "lucide-react";
import {
  getApi,
  startCodingSession,
  listCodingSessions,
  getCodingSession,
  cancelCodingSession,
  resumeCodingSession,
  rollbackCodingSession,
  getWorkspaceTree,
  readWorkspaceFile,
  executeWorkspaceCommand,
  getSearchProviders,
  setSearchProvider,
  type CodingSession,
  type CodingStep,
  type CodingDiff,
  type WorkspaceFileNode,
  type FileReadData,
  type SearchProviderInfo,
} from "@/api";

interface CodingAgentPanelProps {
  token: string;
  initialTask?: string;
  onNavigate?: (view: any) => void;
}

const STARTER_PROMPTS = [
  { label: "⚡ Create REST Controller", text: "Create a reactive Spring Boot REST controller in com.jarvis.api with CRUD endpoints and validation" },
  { label: "🧪 Generate Unit Tests", text: "Write comprehensive JUnit 5 unit tests for MathUtils asserting boundary cases" },
  { label: "🛠️ Fix Compiler Bug", text: "Inspect active compile errors, diagnose the root cause, and apply a surgical fix" },
  { label: "🚀 Implement LRU Cache", text: "Implement an O(1) concurrent LRUCache class in com.jarvis.common with unit tests" },
];

export default function CodingAgentPanel({ token, initialTask, onNavigate }: CodingAgentPanelProps) {
  const [objective, setObjective] = useState(initialTask || "");

  useEffect(() => {
    const handleTrigger = (e: any) => {
      if (e.detail?.task) {
        setObjective(e.detail.task);
      }
    };
    window.addEventListener("jarvis_seed_coding_task", handleTrigger);
    return () => window.removeEventListener("jarvis_seed_coding_task", handleTrigger);
  }, []);
  const [selectedModel, setSelectedModel] = useState("qwen2.5-coder:1.5b");
  const [installedModels, setInstalledModels] = useState<string[]>([]);
  const [activeSession, setActiveSession] = useState<CodingSession | null>(null);
  const [sessions, setSessions] = useState<CodingSession[]>([]);
  const [loading, setLoading] = useState(false);
  const [starting, setStarting] = useState(false);
  const [activeTab, setActiveTab] = useState<"diffs" | "terminal" | "learning">("diffs");

  // Search providers
  const [searchProviders, setSearchProviders] = useState<SearchProviderInfo[]>([]);
  const [selectedProvider, setSelectedProvider] = useState<string>("AUTO");

  // File tree
  const [fileTree, setFileTree] = useState<WorkspaceFileNode | null>(null);
  const [treeSearch, setTreeSearch] = useState("");
  const [selectedFile, setSelectedFile] = useState<string | null>(null);
  const [fileContent, setFileContent] = useState<FileReadData | null>(null);
  const [fileLoading, setFileLoading] = useState(false);
  const [expandedFolders, setExpandedFolders] = useState<Record<string, boolean>>({});

  // Rolling log buffer for terminal console
  const [terminalLogs, setTerminalLogs] = useState<string[]>([]);
  const [rollingDiffs, setRollingDiffs] = useState<CodingDiff[]>([]);
  const [copied, setCopied] = useState(false);
  const [customCmd, setCustomCmd] = useState("");
  const [runningCmd, setRunningCmd] = useState(false);

  // Poll active session for updates if running
  useEffect(() => {
    void loadSessions();
    void loadWorkspaceTree();
    void loadSearchProviders();
    void loadInstalledModels();
  }, [token]);

  async function loadInstalledModels() {
    try {
      const data = await getApi<{ installedModels?: string[] }>("segments", token);
      if (data && Array.isArray(data.installedModels) && data.installedModels.length > 0) {
        setInstalledModels(data.installedModels);
        if (!data.installedModels.includes(selectedModel)) {
          const coder = data.installedModels.find((m) => m.includes("coder"));
          if (coder) setSelectedModel(coder);
          else if (data.installedModels[0]) setSelectedModel(data.installedModels[0]);
        }
      }
    } catch {}
  }

  useEffect(() => {
    let interval: any = null;
    if (activeSession && (activeSession.status === "RUNNING" || activeSession.status === "INITIALIZING" || activeSession.status === "INDEXING")) {
      interval = setInterval(() => {
        void refreshActiveSession(activeSession.id);
      }, 2000);
    }
    return () => {
      if (interval) clearInterval(interval);
    };
  }, [activeSession?.id, activeSession?.status]);

  async function loadSessions() {
    try {
      const list = await listCodingSessions(token);
      setSessions(list);
      if (list.length > 0 && !activeSession) {
        setActiveSession(list[0]);
        setRollingDiffs(list[0].diffs || []);
      }
    } catch (e) {
      console.warn("Failed to load coding sessions:", e);
    }
  }

  async function loadWorkspaceTree() {
    try {
      const tree = await getWorkspaceTree("", 3, token);
      setFileTree(tree);
      // Auto-expand root
      if (tree && tree.name) {
        setExpandedFolders({ [tree.name]: true, "backend": true, "frontend": true });
      }
    } catch (e) {
      console.warn("Failed to load workspace tree:", e);
    }
  }

  async function loadSearchProviders() {
    try {
      const providers = await getSearchProviders(token);
      setSearchProviders(providers);
      const active = providers.find((p) => p.active);
      if (active) setSelectedProvider(active.id);
    } catch (e) {
      console.warn("Failed to load search providers:", e);
    }
  }

  async function refreshActiveSession(id: string) {
    try {
      const updated = await getCodingSession(id, token);
      setActiveSession(updated);
      setRollingDiffs(updated.diffs || []);
    } catch (e) {
      console.warn("Refresh session failed:", e);
    }
  }

  async function handleStartSession() {
    if (!objective.trim()) return;
    setStarting(true);
    setTerminalLogs([`🚀 Launching Autonomous Coding Mission...`, `Target Model: ${selectedModel}`, `Search Engine: ${selectedProvider}`]);
    try {
      const session = await startCodingSession(objective.trim(), selectedModel, selectedProvider, token);
      setActiveSession(session);
      setSessions([session, ...sessions.filter((s) => s.id !== session.id)]);
      setRollingDiffs([]);
      setObjective("");
    } catch (e: any) {
      alert("Failed to start coding session: " + e.message);
    } finally {
      setStarting(false);
    }
  }

  async function handleCancelSession() {
    if (!activeSession) return;
    try {
      await cancelCodingSession(activeSession.id, token);
      await refreshActiveSession(activeSession.id);
    } catch (e: any) {
      alert("Failed to cancel session: " + e.message);
    }
  }

  async function handleResumeSession() {
    if (!activeSession) return;
    try {
      await resumeCodingSession(activeSession.id, token);
      await refreshActiveSession(activeSession.id);
    } catch (e: any) {
      alert("Failed to resume session: " + e.message);
    }
  }

  async function handleRollback() {
    if (!activeSession) return;
    if (!confirm("Are you sure you want to revert all file changes made in this session?")) return;
    try {
      await rollbackCodingSession(activeSession.id, token);
      await refreshActiveSession(activeSession.id);
      await loadWorkspaceTree();
      alert("✅ All file changes successfully rolled back!");
    } catch (e: any) {
      alert("Rollback failed: " + e.message);
    }
  }

  async function handleManualCommandSubmit(e: React.FormEvent) {
    e.preventDefault();
    const cmd = customCmd.trim();
    if (!cmd || runningCmd) return;
    setRunningCmd(true);
    setTerminalLogs((prev) => [...prev, `$ ${cmd}`]);
    setCustomCmd("");
    try {
      const res = await executeWorkspaceCommand(cmd, undefined, 60, token);
      const outputLines: string[] = [];
      if (res.stdout) {
        outputLines.push(...res.stdout.split("\n"));
      }
      if (res.stderr) {
        outputLines.push(...res.stderr.split("\n").map((l) => `[STDERR] ${l}`));
      }
      if (res.exitCode !== 0) {
        outputLines.push(`[Process exited with code ${res.exitCode}]`);
      }
      setTerminalLogs((prev) => [...prev, ...outputLines]);
    } catch (err: any) {
      setTerminalLogs((prev) => [...prev, `[ERROR] ${err.message}`]);
    } finally {
      setRunningCmd(false);
    }
  }

  async function handleFileClick(path: string) {
    setSelectedFile(path);
    setFileLoading(true);
    try {
      const data = await readWorkspaceFile(path, undefined, undefined, token);
      setFileContent(data);
    } catch (e) {
      console.warn("Failed to read file:", e);
    } finally {
      setFileLoading(false);
    }
  }

  function toggleFolder(folderPath: string) {
    setExpandedFolders((prev) => ({ ...prev, [folderPath]: !prev[folderPath] }));
  }

  function handleProviderChange(e: React.ChangeEvent<HTMLSelectElement>) {
    const val = e.target.value;
    setSelectedProvider(val);
    void setSearchProvider(val, token);
  }

  function copyDiffText(diffText: string) {
    void navigator.clipboard.writeText(diffText);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }

  return (
    <div className="h-full flex flex-col bg-[#0b0f17] text-slate-200 overflow-hidden font-sans">
      {/* Top Header */}
      <header className="px-6 py-3.5 border-b border-slate-800/80 bg-[#0d131f]/90 flex items-center justify-between shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-emerald-500/10 border border-emerald-500/30 flex items-center justify-center text-emerald-400">
            <Code2 size={19} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-sm font-semibold tracking-wide text-white uppercase">JARVIS Developer Studio</h1>
              <span className="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-mono">
                AUTONOMOUS PAIR PROGRAMMER
              </span>
            </div>
            <p className="text-xs text-slate-400">
              Workspace: <code className="text-cyan-400 bg-slate-900 px-1 py-0.5 rounded text-[11px]">d:\Jarvis\jarvis-ai</code>
            </p>
          </div>
        </div>

        {/* Global Controls & Selectors */}
        <div className="flex items-center gap-3">
          {/* Search Engine Selector */}
          <div className="flex items-center gap-1.5 bg-slate-900 border border-slate-800 px-2.5 py-1 rounded-md text-xs">
            <Globe size={13} className="text-cyan-400 shrink-0" />
            <span className="text-slate-400 text-[11px]">Search:</span>
            <select
              value={selectedProvider}
              onChange={handleProviderChange}
              className="bg-transparent text-slate-200 text-xs focus:outline-none cursor-pointer"
            >
              {searchProviders.map((p) => (
                <option key={p.id} value={p.id} className="bg-slate-900 text-slate-200">
                  {p.displayName}
                </option>
              ))}
            </select>
          </div>

          {/* Model Selector */}
          <div className="flex items-center gap-1.5 bg-slate-900 border border-slate-800 px-2.5 py-1 rounded-md text-xs">
            <Cpu size={13} className="text-purple-400 shrink-0" />
            <span className="text-slate-400 text-[11px]">Model:</span>
            <select
              value={selectedModel}
              onChange={(e) => setSelectedModel(e.target.value)}
              className="bg-transparent text-slate-200 text-xs focus:outline-none cursor-pointer"
            >
              {installedModels.length > 0 ? (
                installedModels.map((m) => (
                  <option key={m} value={m} className="bg-slate-900 text-slate-200">
                    {m.includes("coder") ? "💻 " : m.includes("r1") ? "🧠 " : "⚡ "} {m}
                  </option>
                ))
              ) : (
                <>
                  <option value="qwen2.5-coder:1.5b" className="bg-slate-900 text-slate-200">
                    💻 Qwen 2.5 Coder (1.5B)
                  </option>
                  <option value="deepseek-r1:1.5b" className="bg-slate-900 text-slate-200">
                    🧠 DeepSeek-R1 CoT (1.5B)
                  </option>
                  <option value="qwen3:1.7b" className="bg-slate-900 text-slate-200">
                    ⚡ Qwen 3 General (1.7B)
                  </option>
                </>
              )}
            </select>
          </div>

          {activeSession && (
            <button
              onClick={handleRollback}
              title="1-Click Rollback all file changes made in this session"
              className="px-2.5 py-1 rounded bg-amber-500/10 hover:bg-amber-500/20 text-amber-400 border border-amber-500/30 text-xs flex items-center gap-1.5 transition-colors"
            >
              <Undo2 size={13} />
              <span>Rollback Changes</span>
            </button>
          )}

          {onNavigate && (
            <button
              onClick={() => onNavigate("graph")}
              title="Open Obsidian-Style Neural Knowledge Graph"
              className="px-2.5 py-1 rounded bg-purple-500/10 hover:bg-purple-500/20 text-purple-400 border border-purple-500/30 text-xs flex items-center gap-1.5 transition-colors"
            >
              <Share2 size={13} />
              <span>Neural Graph</span>
            </button>
          )}
        </div>
      </header>

      {/* 3-Pane IDE Layout */}
      <div className="flex-1 flex overflow-hidden">
        {/* Left Pane: Workspace File Tree Explorer */}
        <aside className="w-64 border-r border-slate-800/80 bg-[#0c101a] flex flex-col shrink-0">
          <div className="p-3 border-b border-slate-800/60 flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-300 uppercase tracking-wider flex items-center gap-1.5">
              <Folder size={14} className="text-cyan-400" /> Files
            </span>
            <button
              onClick={loadWorkspaceTree}
              className="p-1 hover:bg-slate-800 rounded text-slate-400 hover:text-slate-200 transition-colors"
              title="Refresh File Tree"
            >
              <RefreshCw size={12} />
            </button>
          </div>

          <div className="p-2 border-b border-slate-800/40">
            <div className="flex items-center gap-1.5 px-2 py-1 bg-slate-900 border border-slate-800 rounded text-xs text-slate-400">
              <Search size={12} />
              <input
                type="text"
                placeholder="Filter files..."
                value={treeSearch}
                onChange={(e) => setTreeSearch(e.target.value)}
                className="w-full bg-transparent focus:outline-none text-slate-200 text-xs placeholder:text-slate-500"
              />
            </div>
          </div>

          {/* Tree View */}
          <div className="flex-1 overflow-y-auto p-2 text-xs font-mono space-y-0.5">
            {fileTree ? (
              <FileTreeNode
                node={fileTree}
                depth={0}
                filter={treeSearch}
                expanded={expandedFolders}
                onToggle={toggleFolder}
                onSelectFile={handleFileClick}
                selectedPath={selectedFile}
              />
            ) : (
              <div className="p-4 text-center text-slate-500">Loading workspace files...</div>
            )}
          </div>
        </aside>

        {/* Center Pane: Agent Activity, Prompt Composer & ReAct Feed */}
        <main className="flex-1 flex flex-col min-w-0 border-r border-slate-800/80 bg-[#090d14]">
          {/* Prompt Composer & Starter Chips */}
          <div className="p-4 border-b border-slate-800/80 bg-[#0d131f]/60 shrink-0">
            <div className="flex gap-2 mb-2.5">
              <textarea
                value={objective}
                onChange={(e) => setObjective(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) {
                    e.preventDefault();
                    void handleStartSession();
                  }
                }}
                rows={2}
                placeholder="Describe your coding task, bug fix, or feature (e.g. 'Create an LRUCache class with unit tests and verify with Maven')... [Ctrl+Enter to Run]"
                className="flex-1 bg-slate-950 border border-slate-800 rounded-lg p-2.5 text-xs text-slate-100 placeholder:text-slate-500 focus:outline-none focus:border-cyan-500/50 resize-none font-mono"
              />
              <button
                disabled={starting || !objective.trim()}
                onClick={handleStartSession}
                className="px-4 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-40 text-white rounded-lg font-semibold text-xs flex flex-col items-center justify-center gap-1 shrink-0 transition-colors shadow-lg shadow-emerald-950/40"
              >
                {starting ? <Loader2 size={16} className="animate-spin" /> : <Play size={16} />}
                <span>Execute</span>
              </button>
            </div>

            {/* Quick Starters */}
            <div className="flex items-center gap-1.5 flex-wrap">
              <span className="text-[11px] text-slate-500 font-medium mr-1">Starters:</span>
              {STARTER_PROMPTS.map((starter, i) => (
                <button
                  key={i}
                  onClick={() => setObjective(starter.text)}
                  className="px-2 py-0.5 rounded bg-slate-900 hover:bg-slate-800 border border-slate-800/80 text-[11px] text-slate-300 hover:text-cyan-300 transition-colors"
                >
                  {starter.label}
                </button>
              ))}
            </div>
          </div>

          {/* Active Session Status & Execution Feed */}
          <div className="flex-1 overflow-y-auto p-4 space-y-4">
            {activeSession ? (
              <div>
                {/* Session Header Card */}
                <div className="p-3.5 rounded-lg border border-slate-800 bg-[#0e1422] mb-4">
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-semibold text-white">{activeSession.title}</span>
                      <span
                        className={`text-[10px] px-2 py-0.5 rounded font-mono font-bold ${
                          activeSession.status === "COMPLETED"
                            ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                            : activeSession.status === "RUNNING" || activeSession.status === "INDEXING"
                            ? "bg-cyan-500/10 text-cyan-400 border border-cyan-500/20 animate-pulse"
                            : "bg-amber-500/10 text-amber-400 border border-amber-500/20"
                        }`}
                      >
                        {activeSession.status}
                      </span>
                    </div>

                    {activeSession.status === "RUNNING" && (
                      <button
                        onClick={handleCancelSession}
                        className="px-2 py-0.5 rounded bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 border border-rose-500/30 text-xs flex items-center gap-1 transition-colors"
                      >
                        <XCircle size={12} />
                        <span>Cancel Task</span>
                      </button>
                    )}

                    {(activeSession.status === "CANCELLED" || activeSession.status === "PAUSED") && (
                      <button
                        onClick={handleResumeSession}
                        className="px-2 py-0.5 rounded bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 text-xs flex items-center gap-1 transition-colors"
                      >
                        <Play size={12} />
                        <span>Resume Task</span>
                      </button>
                    )}
                  </div>

                  <p className="text-xs text-slate-400 font-mono mb-2">{activeSession.objective}</p>

                  <div className="flex items-center gap-4 text-[11px] text-slate-500">
                    <span>Model: <strong className="text-slate-300 font-mono">{activeSession.model}</strong></span>
                    <span>Turns: <strong className="text-slate-300 font-mono">{activeSession.totalTurns}</strong></span>
                    <span>Files Modified: <strong className="text-slate-300 font-mono">{activeSession.filesModified?.length || 0}</strong></span>
                  </div>
                </div>

                {/* Turn-by-Turn ReAct Stream */}
                <div className="space-y-3">
                  {activeSession.steps && activeSession.steps.map((step) => (
                    <div key={step.index} className="border border-slate-800/80 rounded-lg bg-[#0c111c] overflow-hidden">
                      <div className="px-3.5 py-2 bg-slate-900/60 border-b border-slate-800/60 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className="w-5 h-5 rounded-full bg-cyan-500/10 border border-cyan-500/30 text-cyan-400 text-[10px] font-bold flex items-center justify-center font-mono">
                            {step.index}
                          </span>
                          <span className="text-xs font-semibold text-slate-200 font-mono">{step.type}</span>
                          <span className="text-[11px] text-slate-400 font-mono">{step.action}</span>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="text-[10px] text-slate-500 flex items-center gap-1">
                            <Clock size={11} /> {step.durationMs}ms
                          </span>
                          <span
                            className={`text-[10px] px-1.5 py-0.5 rounded font-mono ${
                              step.status === "SUCCESS"
                                ? "text-emerald-400 bg-emerald-500/10"
                                : "text-amber-400 bg-amber-500/10"
                            }`}
                          >
                            {step.status}
                          </span>
                        </div>
                      </div>

                      <div className="p-3 text-xs space-y-2">
                        {/* DeepSeek-style Cognitive Reasoning Accordion */}
                        {step.thought && (
                          <div className="p-2.5 rounded bg-purple-950/20 border border-purple-500/20 text-purple-200/90 text-[11px]">
                            <div className="flex items-center gap-1.5 font-semibold text-purple-400 mb-1">
                              <Brain size={13} />
                              <span>Cognitive Reasoning &amp; Trace</span>
                            </div>
                            <p className="whitespace-pre-wrap">{step.thought}</p>
                          </div>
                        )}

                        {step.output && (
                          <div className="p-2 rounded bg-slate-950 border border-slate-900 font-mono text-[11px] text-slate-300 max-h-48 overflow-y-auto whitespace-pre-wrap">
                            {step.output}
                          </div>
                        )}
                      </div>
                    </div>
                  ))}
                </div>

                {/* Final Deliverable Summary */}
                {activeSession.finalSummary && (
                  <div className="mt-4 p-4 rounded-lg bg-emerald-950/20 border border-emerald-500/30 text-xs">
                    <div className="flex items-center gap-2 text-emerald-400 font-semibold mb-2">
                      <CheckCircle2 size={16} />
                      <span>Deliverable Summary</span>
                    </div>
                    <div className="text-slate-300 whitespace-pre-wrap font-mono">
                      {activeSession.finalSummary}
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <div className="h-full flex flex-col items-center justify-center text-center p-8 text-slate-500">
                <Code2 size={36} className="text-slate-600 mb-3" />
                <h3 className="text-sm font-semibold text-slate-300 mb-1">Ready for Autonomous Coding</h3>
                <p className="text-xs text-slate-500 max-w-sm mb-4">
                  Select a starter above or enter a prompt to begin an autonomous coding task. JARVIS will explore the codebase, plan surgical edits, execute tests, and self-correct.
                </p>
              </div>
            )}
          </div>
        </main>

        {/* Right Pane: Tabbed Diffs, Terminal Console, and File Content */}
        <aside className="w-96 border-l border-slate-800/80 bg-[#0c101a] flex flex-col shrink-0">
          {/* Tabs */}
          <div className="flex border-b border-slate-800/80 bg-[#090d14] p-1 gap-1 text-xs font-medium">
            <button
              onClick={() => setActiveTab("diffs")}
              className={`flex-1 py-1.5 rounded text-center transition-colors flex items-center justify-center gap-1.5 ${
                activeTab === "diffs" ? "bg-slate-800 text-cyan-400 font-semibold" : "text-slate-400 hover:text-slate-200"
              }`}
            >
              <Layers size={13} />
              <span>Diffs ({rollingDiffs.length})</span>
            </button>
            <button
              onClick={() => setActiveTab("terminal")}
              className={`flex-1 py-1.5 rounded text-center transition-colors flex items-center justify-center gap-1.5 ${
                activeTab === "terminal" ? "bg-slate-800 text-purple-400 font-semibold" : "text-slate-400 hover:text-slate-200"
              }`}
            >
              <Terminal size={13} />
              <span>Build Console</span>
            </button>
          </div>

          {/* Tab Content */}
          <div className="flex-1 overflow-y-auto p-3 text-xs font-mono">
            {activeTab === "diffs" ? (
              <div className="space-y-4">
                {rollingDiffs.length > 0 ? (
                  rollingDiffs.map((diff, i) => (
                    <div key={i} className="border border-slate-800 rounded bg-[#080c14] overflow-hidden">
                      <div className="px-2.5 py-1.5 bg-slate-900/80 border-b border-slate-800 flex items-center justify-between text-[11px]">
                        <span className="text-slate-300 font-semibold truncate">{diff.filePath}</span>
                        <div className="flex items-center gap-2">
                          <span
                            className={`px-1.5 py-0.2 rounded font-mono ${
                              diff.changeType === "CREATE" ? "bg-emerald-500/10 text-emerald-400" : "bg-cyan-500/10 text-cyan-400"
                            }`}
                          >
                            {diff.changeType}
                          </span>
                          <button
                            onClick={() => copyDiffText(diff.unifiedDiff)}
                            className="text-slate-400 hover:text-white"
                            title="Copy Diff"
                          >
                            {copied ? <Check size={12} className="text-emerald-400" /> : <Copy size={12} />}
                          </button>
                        </div>
                      </div>
                      <div className="p-2 text-[11px] overflow-x-auto whitespace-pre leading-relaxed">
                        {diff.unifiedDiff.split("\n").map((line, idx) => {
                          if (line.startsWith("+")) {
                            return <div key={idx} className="text-emerald-400 bg-emerald-950/20">{line}</div>;
                          }
                          if (line.startsWith("-")) {
                            return <div key={idx} className="text-rose-400 bg-rose-950/20">{line}</div>;
                          }
                          if (line.startsWith("@@")) {
                            return <div key={idx} className="text-cyan-400 bg-cyan-950/20">{line}</div>;
                          }
                          return <div key={idx} className="text-slate-400">{line}</div>;
                        })}
                      </div>
                    </div>
                  ))
                ) : selectedFile && fileContent ? (
                  <div className="border border-slate-800 rounded bg-[#080c14]">
                    <div className="px-2.5 py-1.5 bg-slate-900/80 border-b border-slate-800 text-[11px] text-cyan-400 font-semibold truncate">
                      {selectedFile} ({fileContent.totalLines} lines)
                    </div>
                    <div className="p-2 text-[11px] overflow-x-auto whitespace-pre leading-relaxed text-slate-300">
                      {fileContent.content}
                    </div>
                  </div>
                ) : (
                  <div className="p-6 text-center text-slate-500">
                    <p>No file diffs generated yet.</p>
                    <p className="text-[11px] text-slate-600 mt-1">Select a file from the explorer on the left to preview it.</p>
                  </div>
                )}
              </div>
            ) : (
              /* Terminal Console */
              <div className="flex flex-col h-full justify-between">
                <div className="space-y-1 overflow-y-auto flex-1">
                  {terminalLogs.length > 0 ? (
                    terminalLogs.map((logLine, idx) => (
                      <div key={idx} className="text-[11px] leading-relaxed break-all">
                        {logLine.includes("[STDERR]") ? (
                          <span className="text-rose-400">{logLine}</span>
                        ) : logLine.includes("BUILD SUCCESS") ? (
                          <span className="text-emerald-400 font-bold">{logLine}</span>
                        ) : (
                          <span className="text-slate-300">{logLine}</span>
                        )}
                      </div>
                    ))
                  ) : (
                    <div className="p-6 text-center text-slate-500">Terminal is quiet. Launch a task or run a command below.</div>
                  )}
                </div>

                <form onSubmit={handleManualCommandSubmit} className="mt-2 flex items-center gap-2 border-t border-slate-800 pt-2 shrink-0">
                  <span className="text-emerald-400 font-mono text-xs select-none">$</span>
                  <input
                    type="text"
                    value={customCmd}
                    onChange={(e) => setCustomCmd(e.target.value)}
                    placeholder="Run command (e.g. git status, mvn test, npm test)..."
                    disabled={runningCmd}
                    className="flex-1 bg-transparent text-xs font-mono text-slate-200 outline-none placeholder:text-slate-600"
                  />
                  <button
                    type="submit"
                    disabled={runningCmd || !customCmd.trim()}
                    className="px-2 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-mono disabled:opacity-40"
                  >
                    {runningCmd ? <Loader2 size={12} className="animate-spin" /> : "Run"}
                  </button>
                </form>
              </div>
            )}
          </div>
        </aside>
      </div>
    </div>
  );
}

interface TreeNodeProps {
  node: WorkspaceFileNode;
  depth: number;
  filter: string;
  expanded: Record<string, boolean>;
  onToggle: (path: string) => void;
  onSelectFile: (path: string) => void;
  selectedPath: string | null;
}

function FileTreeNode({ node, depth, filter, expanded, onToggle, onSelectFile, selectedPath }: TreeNodeProps) {
  const isExpanded = expanded[node.path] || false;
  const isSelected = selectedPath === node.path;

  // Filter check
  if (filter && !node.isDirectory && !node.name.toLowerCase().includes(filter.toLowerCase())) {
    return null;
  }

  return (
    <div>
      <div
        onClick={() => {
          if (node.isDirectory) onToggle(node.path);
          else onSelectFile(node.path);
        }}
        style={{ paddingLeft: `${depth * 12 + 6}px` }}
        className={`flex items-center gap-1.5 py-1 px-2 rounded cursor-pointer select-none transition-colors ${
          isSelected
            ? "bg-cyan-500/20 text-cyan-300 font-semibold"
            : "hover:bg-slate-800/60 text-slate-400 hover:text-slate-200"
        }`}
      >
        {node.isDirectory ? (
          <>
            {isExpanded ? <ChevronDown size={12} className="text-slate-500" /> : <ChevronRight size={12} className="text-slate-500" />}
            <Folder size={13} className="text-amber-400/80 shrink-0" />
          </>
        ) : (
          <>
            <span className="w-3" />
            <FileCode size={13} className="text-cyan-400/80 shrink-0" />
          </>
        )}
        <span className="truncate">{node.name}</span>
      </div>

      {node.isDirectory && isExpanded && node.children && (
        <div>
          {node.children.map((child, idx) => (
            <FileTreeNode
              key={idx}
              node={child}
              depth={depth + 1}
              filter={filter}
              expanded={expanded}
              onToggle={onToggle}
              onSelectFile={onSelectFile}
              selectedPath={selectedPath}
            />
          ))}
        </div>
      )}
    </div>
  );
}

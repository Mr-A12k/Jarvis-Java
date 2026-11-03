import React, { useEffect, useState, useMemo } from "react";
import {
  Target,
  Play,
  RotateCcw,
  CheckCircle2,
  XCircle,
  Clock,
  Loader2,
  FileText,
  Search,
  ShoppingBag,
  Cpu,
  Sparkles,
  AlertTriangle,
  RefreshCw,
  Copy,
  Check,
  Code2,
  ShieldCheck,
  Zap,
  Bot,
  Layers,
  Filter,
  Activity,
  Terminal,
} from "lucide-react";
import {
  startGoal,
  listGoals,
  getGoal,
  cancelGoal,
  type GoalRun,
  type PlanStep,
} from "@/api";

interface GoalPlannerPanelProps {
  token: string;
}

const SUB_AGENT_ROSTER = [
  {
    role: "RESEARCHER",
    name: "Researcher Agent",
    tamilName: "ஆராய்ச்சி முகவர்",
    icon: Search,
    color: "cyan",
    desc: "Gathers web intelligence, technical specs, market pricing and documents.",
  },
  {
    role: "CODER",
    name: "Coder Agent",
    tamilName: "நிரலாக்க முகவர்",
    icon: Code2,
    color: "emerald",
    desc: "Architects modules, writes production classes, and implements algorithms.",
  },
  {
    role: "VALIDATOR",
    name: "Validator Agent",
    tamilName: "சரிபார்ப்பு முகவர்",
    icon: ShieldCheck,
    color: "amber",
    desc: "Validates syntax, derives unit test suites, audits edge cases & security.",
  },
  {
    role: "SYSTEM_ANALYST",
    name: "System Analyst Agent",
    tamilName: "கணினி ஆய்வாளர்",
    icon: Cpu,
    color: "sky",
    desc: "Audits hardware compute, RAM, GPU, storage and workspace dependencies.",
  },
  {
    role: "COORDINATOR",
    name: "Lead Coordinator",
    tamilName: "தலைமை ஒருங்கிணைப்பாளர்",
    icon: Target,
    color: "purple",
    desc: "Synthesizes multi-agent deliverables into verified strategic reports.",
  },
];

const STARTER_GOALS = [
  {
    label: "🛠️ Multi-Agent REST API",
    text: "Build reactive REST API controller and service in Java with unit test assertions",
  },
  {
    label: "🛒 Multi-Agent Product Discovery",
    text: "Research mechanical keyboards under ₹4000 and compare features across retailers",
  },
  {
    label: "💻 Hardware & Security Audit",
    text: "Audit system hardware and RAM memory diagnostics with security validation",
  },
  {
    label: "🤖 Deep Tech Investigation",
    text: "Deep research on recent autonomous multi-agent reasoning architectures and benchmarks",
  },
  {
    label: "📁 Workspace Codebase Review",
    text: "Search workspace documents and analyze codebase architecture for system notes",
  },
  {
    label: "🇮🇳 பல முகவர்கள்: மென்பொருள் உருவாக்கம்",
    text: "புதிய ரியாக்டிவ் ஏபிஐ செயலியை உருவாக்கி, குறியீட்டை சரிபார்த்து சோதனை அறிக்கையை தயாரி",
  },
];

export default function GoalPlannerPanel({ token }: GoalPlannerPanelProps) {
  const [goals, setGoals] = useState<GoalRun[]>([]);
  const [selectedGoalId, setSelectedGoalId] = useState<string | null>(null);
  const [selectedGoal, setSelectedGoal] = useState<GoalRun | null>(null);
  const [objectiveInput, setObjectiveInput] = useState("");
  const [executionMode, setExecutionMode] = useState<"MULTI_AGENT_PARALLEL" | "SEQUENTIAL">("MULTI_AGENT_PARALLEL");
  const [agentFilter, setAgentFilter] = useState<string>("ALL");
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [copied, setCopied] = useState(false);

  // Poll active goals
  useEffect(() => {
    void fetchGoals();
  }, [token]);

  // Periodic polling if selected goal or any goal is running
  useEffect(() => {
    const hasRunning = goals.some((g) => g.status === "RUNNING" || g.status === "PLANNING");
    if (!hasRunning && selectedGoal?.status !== "RUNNING") return;

    const interval = setInterval(() => {
      void refreshSelectedAndList();
    }, 1500);

    return () => clearInterval(interval);
  }, [goals, selectedGoalId, selectedGoal?.status]);

  async function fetchGoals() {
    setLoading(true);
    setError("");
    try {
      const list = await listGoals(token);
      setGoals(list);
      if (list.length > 0 && !selectedGoalId) {
        setSelectedGoalId(list[0].id);
        setSelectedGoal(list[0]);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load goals");
    } finally {
      setLoading(false);
    }
  }

  async function refreshSelectedAndList() {
    try {
      const list = await listGoals(token);
      setGoals(list);
      if (selectedGoalId) {
        const current = await getGoal(token, selectedGoalId);
        setSelectedGoal(current);
      }
    } catch {
      // Ignore polling errors
    }
  }

  async function handleSelectGoal(id: string) {
    setSelectedGoalId(id);
    setError("");
    try {
      const goal = await getGoal(token, id);
      setSelectedGoal(goal);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to fetch goal details");
    }
  }

  async function handleStartGoal(e?: React.FormEvent) {
    if (e) e.preventDefault();
    if (!objectiveInput.trim()) return;

    setSubmitting(true);
    setError("");
    try {
      const run = await startGoal(token, objectiveInput.trim(), executionMode);
      setObjectiveInput("");
      await fetchGoals();
      setSelectedGoalId(run.id);
      setSelectedGoal(run);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to start goal");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleCancelGoal(id: string) {
    try {
      await cancelGoal(token, id);
      await refreshSelectedAndList();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to cancel goal");
    }
  }

  function handleCopyReport() {
    if (!selectedGoal?.finalReport) return;
    void navigator.clipboard.writeText(selectedGoal.finalReport);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }

  function getAgentMeta(role?: string) {
    const found = SUB_AGENT_ROSTER.find((r) => r.role === role);
    return (
      found || {
        role: "RESEARCHER",
        name: "Researcher Agent",
        tamilName: "ஆராய்ச்சி முகவர்",
        icon: Bot,
        color: "cyan",
        desc: "Autonomous Sub-Agent",
      }
    );
  }

  function getStepIcon(actionType: string) {
    switch (actionType) {
      case "CODE_GENERATE":
        return <Code2 className="h-4 w-4 text-emerald-400" />;
      case "CODE_VALIDATE":
      case "SECURITY_AUDIT":
      case "FACT_VERIFY":
        return <ShieldCheck className="h-4 w-4 text-amber-400" />;
      case "SHOPPING_SEARCH":
        return <ShoppingBag className="h-4 w-4 text-emerald-400" />;
      case "RESEARCH_WEB":
        return <Search className="h-4 w-4 text-cyan-400" />;
      case "FILE_SEARCH":
        return <FileText className="h-4 w-4 text-indigo-400" />;
      case "SYSTEM_METRICS":
        return <Cpu className="h-4 w-4 text-sky-400" />;
      default:
        return <Sparkles className="h-4 w-4 text-purple-400" />;
    }
  }

  function getAgentBadge(role?: string, name?: string) {
    const meta = getAgentMeta(role);
    const Icon = meta.icon;
    const displayName = name || meta.name;

    switch (meta.color) {
      case "emerald":
        return (
          <span className="inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-0.5 rounded-full bg-emerald-950/70 border border-emerald-700/80 text-emerald-300">
            <Icon className="h-3.5 w-3.5 text-emerald-400" /> {displayName}
          </span>
        );
      case "amber":
        return (
          <span className="inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-0.5 rounded-full bg-amber-950/70 border border-amber-700/80 text-amber-300">
            <Icon className="h-3.5 w-3.5 text-amber-400" /> {displayName}
          </span>
        );
      case "sky":
        return (
          <span className="inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-0.5 rounded-full bg-sky-950/70 border border-sky-700/80 text-sky-300">
            <Icon className="h-3.5 w-3.5 text-sky-400" /> {displayName}
          </span>
        );
      case "purple":
        return (
          <span className="inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-0.5 rounded-full bg-purple-950/70 border border-purple-700/80 text-purple-300">
            <Icon className="h-3.5 w-3.5 text-purple-400" /> {displayName}
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-0.5 rounded-full bg-cyan-950/70 border border-cyan-700/80 text-cyan-300">
            <Icon className="h-3.5 w-3.5 text-cyan-400" /> {displayName}
          </span>
        );
    }
  }

  function getStatusBadge(status: string) {
    switch (status) {
      case "COMPLETED":
        return (
          <span className="inline-flex items-center gap-1 text-[11px] font-medium text-emerald-400 bg-emerald-950/70 border border-emerald-800/80 px-2 py-0.5 rounded-full">
            <CheckCircle2 className="h-3 w-3" /> Completed
          </span>
        );
      case "RUNNING":
        return (
          <span className="inline-flex items-center gap-1 text-[11px] font-medium text-cyan-400 bg-cyan-950/70 border border-cyan-800/80 px-2 py-0.5 rounded-full animate-pulse">
            <Loader2 className="h-3 w-3 animate-spin" /> In Progress
          </span>
        );
      case "FAILED":
        return (
          <span className="inline-flex items-center gap-1 text-[11px] font-medium text-rose-400 bg-rose-950/70 border border-rose-800/80 px-2 py-0.5 rounded-full">
            <XCircle className="h-3 w-3" /> Failed
          </span>
        );
      case "CANCELLED":
        return (
          <span className="inline-flex items-center gap-1 text-[11px] font-medium text-zinc-400 bg-zinc-800 border border-zinc-700 px-2 py-0.5 rounded-full">
            Cancelled
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1 text-[11px] font-medium text-zinc-400 bg-zinc-900 border border-zinc-800 px-2 py-0.5 rounded-full">
            <Clock className="h-3 w-3" /> Pending
          </span>
        );
    }
  }

  // Active steps stats
  const completedStepsCount =
    selectedGoal?.steps.filter((s) => s.status === "COMPLETED").length || 0;
  const runningStepsCount =
    selectedGoal?.steps.filter((s) => s.status === "RUNNING").length || 0;
  const totalStepsCount = selectedGoal?.steps.length || 0;
  const progressPercent =
    totalStepsCount > 0 ? Math.round((completedStepsCount / totalStepsCount) * 100) : 0;

  // Group steps by stage
  const groupedSteps = useMemo(() => {
    if (!selectedGoal) return [];
    const stagesMap = new Map<number, PlanStep[]>();
    for (const step of selectedGoal.steps) {
      const stageNum = step.stage || 1;
      if (!stagesMap.has(stageNum)) {
        stagesMap.set(stageNum, []);
      }
      stagesMap.get(stageNum)!.push(step);
    }
    return Array.from(stagesMap.entries()).sort(([a], [b]) => a - b);
  }, [selectedGoal]);

  // Filtered steps
  const filteredSteps = useMemo(() => {
    if (!selectedGoal) return [];
    if (agentFilter === "ALL") return selectedGoal.steps;
    return selectedGoal.steps.filter((s) => s.assignedAgent === agentFilter);
  }, [selectedGoal, agentFilter]);

  return (
    <div className="flex-1 overflow-y-auto p-6 max-w-7xl mx-auto space-y-6">
      {/* Top Header */}
      <div className="flex items-center justify-between border-b border-zinc-800 pb-4">
        <div>
          <span className="eyebrow flex items-center gap-1.5 text-cyan-400">
            <Zap className="h-3.5 w-3.5 fill-cyan-400" /> MULTI-AGENT PARALLEL TASK EXECUTION
          </span>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-100 flex items-center gap-2">
            <Target className="text-cyan-400 h-6 w-6" />
            தானியங்கி பல முகவர் திட்டமிடுபவர் (Multi-Agent Goal Planner)
          </h1>
          <p className="text-sm text-zinc-400">
            Decomposes complex objectives into discrete stages executed simultaneously by specialized parallel sub-agents (Researcher, Coder, Validator, and System Analyst).
          </p>
        </div>

        <button
          onClick={() => void fetchGoals()}
          className="flex items-center gap-1.5 text-xs bg-zinc-900 border border-zinc-800 rounded-lg px-3 py-1.5 text-zinc-300 hover:text-cyan-400 transition"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`} /> Refresh
        </button>
      </div>

      {/* Sub-Agent Squad Capabilities Roster */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
        {SUB_AGENT_ROSTER.map((agent) => {
          const Icon = agent.icon;
          return (
            <div
              key={agent.role}
              className="bg-zinc-900/50 border border-zinc-800/80 rounded-xl p-3.5 space-y-1.5 transition hover:border-zinc-700"
            >
              <div className="flex items-center gap-2">
                <div className={`p-1.5 rounded-lg bg-${agent.color}-950/80 border border-${agent.color}-800/60`}>
                  <Icon className={`h-4 w-4 text-${agent.color}-400`} />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="text-xs font-semibold text-zinc-200 truncate">{agent.name}</div>
                  <div className="text-[10px] text-zinc-500 truncate">{agent.tamilName}</div>
                </div>
              </div>
              <p className="text-[11px] text-zinc-400 line-clamp-2 leading-relaxed">{agent.desc}</p>
            </div>
          );
        })}
      </div>

      {/* Error alert */}
      {error && (
        <div className="bg-rose-950/40 border border-rose-800/80 rounded-xl p-3 text-sm text-rose-300 flex items-center gap-2">
          <AlertTriangle className="h-4 w-4 text-rose-400 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Goal Creator Box */}
      <div className="bg-zinc-900/60 border border-zinc-800 rounded-xl p-5 space-y-4">
        <form onSubmit={handleStartGoal} className="space-y-3">
          <div className="flex items-center justify-between">
            <label className="text-xs font-semibold text-zinc-300 flex items-center gap-2">
              <Sparkles className="h-4 w-4 text-cyan-400" />
              Define Target Objective (இலக்கை உள்ளிடவும்)
            </label>

            <div className="flex items-center gap-2">
              <span className="text-[11px] text-zinc-500">Pipeline:</span>
              <button
                type="button"
                onClick={() =>
                  setExecutionMode((m) =>
                    m === "MULTI_AGENT_PARALLEL" ? "SEQUENTIAL" : "MULTI_AGENT_PARALLEL"
                  )
                }
                className={`text-[11px] px-2.5 py-1 rounded-md border font-medium flex items-center gap-1.5 transition ${
                  executionMode === "MULTI_AGENT_PARALLEL"
                    ? "bg-cyan-950/80 border-cyan-700/80 text-cyan-300"
                    : "bg-zinc-800 border-zinc-700 text-zinc-400"
                }`}
              >
                <Zap className="h-3 w-3 fill-current" />
                {executionMode === "MULTI_AGENT_PARALLEL"
                  ? "Multi-Agent Parallel Concurrency"
                  : "Sequential Pipeline"}
              </button>
            </div>
          </div>

          <div className="flex gap-2">
            <textarea
              value={objectiveInput}
              onChange={(e) => setObjectiveInput(e.target.value)}
              placeholder="Enter your objective in English or Tamil (e.g. 'Build reactive REST API in Java with unit tests' or 'புதிய ரியாக்டிவ் ஏபிஐ செயலியை உருவாக்கி, குறியீட்டை சரிபார்த்து சோதனை அறிக்கையை தயாரி')..."
              rows={2}
              className="flex-1 bg-zinc-950 border border-zinc-800 rounded-lg p-3 text-sm text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-cyan-500 resize-none"
            />
            <button
              type="submit"
              disabled={submitting || !objectiveInput.trim()}
              className="bg-cyan-500 hover:bg-cyan-400 disabled:opacity-50 text-black font-semibold text-sm px-6 rounded-lg transition flex items-center justify-center gap-2 shrink-0"
            >
              {submitting ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" /> Planning…
                </>
              ) : (
                <>
                  <Play className="h-4 w-4" /> Start Multi-Agent Goal (தொடங்கு)
                </>
              )}
            </button>
          </div>

          {/* Quick Starters */}
          <div className="flex flex-wrap items-center gap-2 pt-1">
            <span className="text-[11px] text-zinc-500">Quick Starters:</span>
            {STARTER_GOALS.map((starter, i) => (
              <button
                key={i}
                type="button"
                onClick={() => setObjectiveInput(starter.text)}
                className="text-xs bg-zinc-800/70 hover:bg-zinc-800 text-zinc-300 hover:text-cyan-300 border border-zinc-700/60 rounded-md px-2.5 py-1 transition"
              >
                {starter.label}
              </button>
            ))}
          </div>
        </form>
      </div>

      {/* Dual Layout: Goals List & Selected Goal Execution Workspace */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column: Recent Goal Runs */}
        <div className="bg-zinc-900/60 border border-zinc-800 rounded-xl p-4 space-y-3">
          <div className="flex items-center justify-between border-b border-zinc-800/80 pb-2">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-zinc-400">
              Recent Goals ({goals.length})
            </h2>
          </div>

          <div className="space-y-2 max-h-[600px] overflow-y-auto pr-1">
            {goals.length === 0 ? (
              <div className="text-center py-8 text-xs text-zinc-500 space-y-1">
                <Target className="h-8 w-8 text-zinc-700 mx-auto" />
                <p>No goals initiated yet.</p>
                <p className="text-[11px] text-zinc-600">Enter an objective above to begin.</p>
              </div>
            ) : (
              goals.map((g) => {
                const isSelected = g.id === selectedGoalId;
                const completed = g.steps.filter((s) => s.status === "COMPLETED").length;
                return (
                  <div
                    key={g.id}
                    onClick={() => void handleSelectGoal(g.id)}
                    className={`p-3 rounded-lg border text-left cursor-pointer transition ${
                      isSelected
                        ? "bg-cyan-950/30 border-cyan-800/60 text-zinc-100"
                        : "bg-zinc-950/40 border-zinc-800/60 hover:bg-zinc-800/40 text-zinc-300"
                    }`}
                  >
                    <div className="flex items-center justify-between mb-1.5">
                      <span className="text-[11px] font-mono text-zinc-400">{g.id}</span>
                      {getStatusBadge(g.status)}
                    </div>
                    <div className="font-semibold text-sm line-clamp-1 mb-1">{g.title}</div>
                    <div className="text-xs text-zinc-400 line-clamp-1 mb-2">{g.objective}</div>

                    <div className="flex items-center justify-between text-[11px] text-zinc-500">
                      <span className="flex items-center gap-1">
                        <Zap className="h-3 w-3 text-cyan-400" />
                        {completed}/{g.steps.length} steps
                      </span>
                      <span>{new Date(g.createdAt).toLocaleTimeString()}</span>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* Right Column: Active Multi-Agent Goal Execution Workspace */}
        <div className="lg:col-span-2 space-y-5">
          {selectedGoal ? (
            <>
              {/* Selected Goal Header Card */}
              <div className="bg-zinc-900/60 border border-zinc-800 rounded-xl p-5 space-y-4">
                <div className="flex items-start justify-between">
                  <div className="space-y-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="text-xs font-mono text-zinc-400">{selectedGoal.id}</span>
                      {getStatusBadge(selectedGoal.status)}
                      <span className="inline-flex items-center gap-1 text-[11px] font-mono font-medium px-2 py-0.5 rounded-full bg-cyan-950/70 border border-cyan-700/70 text-cyan-300">
                        <Zap className="h-3 w-3 fill-cyan-400" />
                        {selectedGoal.executionMode || "MULTI_AGENT_PARALLEL"}
                      </span>

                      {/* Live Parallel Concurrency Indicator */}
                      {runningStepsCount > 1 && (
                        <span className="inline-flex items-center gap-1.5 text-[11px] font-semibold px-2.5 py-0.5 rounded-full bg-emerald-950/90 border border-emerald-600 text-emerald-300 animate-pulse">
                          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping" />
                          ⚡ {runningStepsCount} Sub-Agents Running Simultaneously
                        </span>
                      )}
                      {runningStepsCount === 1 && (
                        <span className="inline-flex items-center gap-1.5 text-[11px] font-medium px-2.5 py-0.5 rounded-full bg-cyan-950/80 border border-cyan-700 text-cyan-300">
                          <Loader2 className="h-3 w-3 animate-spin text-cyan-400" />
                          1 Sub-Agent Executing
                        </span>
                      )}
                    </div>

                    <h2 className="text-xl font-bold text-zinc-100">{selectedGoal.title}</h2>
                    <p className="text-sm text-zinc-400 mt-1">{selectedGoal.objective}</p>
                  </div>

                  {selectedGoal.status === "RUNNING" && (
                    <button
                      onClick={() => void handleCancelGoal(selectedGoal.id)}
                      className="text-xs bg-rose-950/60 hover:bg-rose-900/80 text-rose-300 border border-rose-800 px-3 py-1.5 rounded-lg transition flex items-center gap-1.5"
                    >
                      <RotateCcw className="h-3.5 w-3.5" /> Cancel Goal
                    </button>
                  )}
                </div>

                {/* Active Sub-Agents Participating */}
                {selectedGoal.activeAgents && selectedGoal.activeAgents.length > 0 && (
                  <div className="pt-2 border-t border-zinc-800/80 flex flex-wrap items-center gap-2">
                    <span className="text-[11px] text-zinc-400 font-medium">Sub-Agent Squad:</span>
                    {selectedGoal.activeAgents.map((agentName, idx) => {
                      const matched = SUB_AGENT_ROSTER.find(
                        (r) => r.name.toLowerCase() === agentName.toLowerCase()
                      );
                      return (
                        <span key={idx}>
                          {getAgentBadge(matched?.role, agentName)}
                        </span>
                      );
                    })}
                  </div>
                )}

                {/* Progress Bar */}
                <div className="space-y-1.5">
                  <div className="flex justify-between text-xs text-zinc-400">
                    <span>Multi-Agent Milestones Progress</span>
                    <span className="font-mono">
                      {completedStepsCount}/{totalStepsCount} ({progressPercent}%)
                    </span>
                  </div>
                  <div className="w-full bg-zinc-950 h-2 rounded-full overflow-hidden border border-zinc-800">
                    <div
                      className="bg-cyan-500 h-full transition-all duration-500 rounded-full"
                      style={{ width: `${progressPercent}%` }}
                    />
                  </div>
                </div>
              </div>

              {/* Sub-Agent Filter Tabs */}
              <div className="flex items-center justify-between border-b border-zinc-800 pb-2">
                <div className="flex items-center gap-1.5">
                  <Filter className="h-3.5 w-3.5 text-zinc-400" />
                  <span className="text-xs font-semibold text-zinc-400 uppercase tracking-wider">
                    Filter by Sub-Agent:
                  </span>
                </div>

                <div className="flex flex-wrap gap-1.5">
                  <button
                    onClick={() => setAgentFilter("ALL")}
                    className={`text-xs px-2.5 py-1 rounded-md transition ${
                      agentFilter === "ALL"
                        ? "bg-zinc-800 text-zinc-100 font-semibold border border-zinc-700"
                        : "text-zinc-400 hover:text-zinc-200"
                    }`}
                  >
                    All Agents ({selectedGoal.steps.length})
                  </button>
                  {["RESEARCHER", "CODER", "VALIDATOR", "SYSTEM_ANALYST"].map((role) => {
                    const count = selectedGoal.steps.filter((s) => s.assignedAgent === role).length;
                    if (count === 0) return null;
                    const meta = getAgentMeta(role);
                    return (
                      <button
                        key={role}
                        onClick={() => setAgentFilter(role)}
                        className={`text-xs px-2.5 py-1 rounded-md transition ${
                          agentFilter === role
                            ? "bg-cyan-950 text-cyan-300 font-semibold border border-cyan-800"
                            : "text-zinc-400 hover:text-zinc-200"
                        }`}
                      >
                        {meta.name.split(" ")[0]} ({count})
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Staged Execution Breakdown */}
              <div className="space-y-4">
                {groupedSteps.map(([stageNum, stepsInStage]) => {
                  const visibleSteps =
                    agentFilter === "ALL"
                      ? stepsInStage
                      : stepsInStage.filter((s) => s.assignedAgent === agentFilter);
                  if (visibleSteps.length === 0) return null;

                  const isMultiParallel = stepsInStage.length > 1;

                  return (
                    <div key={stageNum} className="space-y-3">
                      {/* Stage Header */}
                      <div className="flex items-center justify-between bg-zinc-950/70 border border-zinc-800/80 px-3.5 py-2 rounded-lg">
                        <div className="flex items-center gap-2">
                          <Layers className="h-4 w-4 text-cyan-400" />
                          <span className="text-xs font-bold text-zinc-200">
                            Stage {stageNum}: {stageNum === 1 ? "Research & Architecture Phase" : stageNum === 2 ? "Implementation & Validation Phase" : "Coordinator Synthesis Phase"}
                          </span>
                        </div>

                        {isMultiParallel && (
                          <span className="inline-flex items-center gap-1 text-[11px] font-mono text-cyan-300 bg-cyan-950/70 border border-cyan-800/60 px-2 py-0.5 rounded-full">
                            <Zap className="h-3 w-3 fill-cyan-400" />
                            Parallel Stage ({stepsInStage.length} Sub-Agents)
                          </span>
                        )}
                      </div>

                      {/* Steps inside Stage */}
                      <div className="space-y-3 pl-2">
                        {visibleSteps.map((step) => {
                          const isDone = step.status === "COMPLETED";
                          const isRunning = step.status === "RUNNING";
                          return (
                            <div
                              key={step.stepIndex}
                              className={`border rounded-xl p-4 transition ${
                                isRunning
                                  ? "bg-cyan-950/20 border-cyan-700/80 ring-1 ring-cyan-500/30"
                                  : isDone
                                  ? "bg-zinc-900/40 border-zinc-800/80"
                                  : "bg-zinc-950/30 border-zinc-800/50"
                              }`}
                            >
                              <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
                                <div className="flex items-center gap-2.5">
                                  <span className="flex items-center justify-center w-6 h-6 rounded-full bg-zinc-800 text-xs font-mono text-zinc-300">
                                    {step.stepIndex}
                                  </span>
                                  {getAgentBadge(step.assignedAgent, step.agentName)}
                                  <div className="flex items-center gap-1.5 text-zinc-300">
                                    {getStepIcon(step.actionType)}
                                    <span className="text-xs font-mono text-zinc-400">
                                      ({step.actionType})
                                    </span>
                                  </div>
                                </div>

                                <div className="flex items-center gap-2">
                                  {step.durationMs != null && (
                                    <span className="text-[11px] font-mono text-zinc-500">
                                      {step.durationMs}ms
                                    </span>
                                  )}
                                  {getStatusBadge(step.status)}
                                </div>
                              </div>

                              <div className="text-sm font-semibold text-zinc-100 pl-8">
                                {step.title}
                              </div>

                              {/* Step output preview with code formatting */}
                              {step.output && (
                                <div className="mt-3 ml-8 pt-3 border-t border-zinc-800/80 text-xs text-zinc-300 font-mono bg-zinc-950/80 p-3.5 rounded-lg overflow-x-auto whitespace-pre-wrap max-h-64 border border-zinc-800/60 leading-relaxed">
                                  {step.output}
                                </div>
                              )}

                              {step.error && (
                                <div className="mt-2 ml-8 text-xs text-rose-400 bg-rose-950/40 p-2.5 rounded-lg border border-rose-900/60">
                                  Error: {step.error}
                                </div>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* Final Strategic Report Deliverable */}
              {selectedGoal.finalReport && (
                <div className="bg-zinc-900/70 border border-emerald-800/60 rounded-xl p-6 space-y-4">
                  <div className="flex items-center justify-between border-b border-zinc-800 pb-3">
                    <div className="flex items-center gap-2">
                      <Sparkles className="h-5 w-5 text-emerald-400" />
                      <h3 className="text-base font-bold text-zinc-100">
                        இறுதி பல முகவர்கள் உத்தி அறிக்கை (Multi-Agent Strategic Deliverable)
                      </h3>
                    </div>

                    <button
                      onClick={handleCopyReport}
                      className="flex items-center gap-1.5 text-xs bg-zinc-800 hover:bg-zinc-700 text-zinc-200 px-3 py-1.5 rounded-lg border border-zinc-700 transition"
                    >
                      {copied ? (
                        <>
                          <Check className="h-3.5 w-3.5 text-emerald-400" /> Copied!
                        </>
                      ) : (
                        <>
                          <Copy className="h-3.5 w-3.5" /> Copy Markdown
                        </>
                      )}
                    </button>
                  </div>

                  <div className="prose prose-invert prose-sm max-w-none text-zinc-200 whitespace-pre-wrap font-sans leading-relaxed">
                    {selectedGoal.finalReport}
                  </div>
                </div>
              )}
            </>
          ) : (
            <div className="bg-zinc-900/40 border border-zinc-800/60 rounded-xl p-12 text-center text-zinc-500 space-y-3">
              <Target className="h-12 w-12 text-zinc-700 mx-auto" />
              <div className="text-sm font-medium text-zinc-400">No Goal Selected</div>
              <p className="text-xs text-zinc-500 max-w-sm mx-auto">
                Select an existing goal from the left panel or initiate a new multi-agent autonomous objective above.
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

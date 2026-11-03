import { useState, useEffect } from "react";
import {
  Play,
  Pause,
  SkipForward,
  SkipBack,
  Square,
  Volume2,
  VolumeX,
  Volume1,
  Laptop,
  Search,
  ExternalLink,
  Sparkles,
  RefreshCw,
  Sliders,
  Check,
  AlertCircle,
  Terminal,
  Globe,
  Code2,
  Music,
  Tv,
  Calculator,
  FileText,
  Settings,
  FolderOpen,
  Palette,
  Camera,
  MessageCircle,
  Activity,
  Send,
  SlidersHorizontal,
  MousePointer,
  Command,
  Maximize2,
  Minimize2,
  X,
  PlayCircle,
  Layers,
  Monitor,
  Clock,
  Star,
  Trash2,
  Plus,
  Wand2,
  Save,
  Mouse,
  Keyboard,
  ShieldCheck,
  Flame,
} from "lucide-react";
import {
  controlDesktopMedia,
  getDesktopVolume,
  controlDesktopVolume,
  listDesktopApps,
  launchDesktopApp,
  executeDesktopCommand,
  executeDesktopGuiAction,
  executeDesktopGuiSequence,
  getDesktopCursorInfo,
  getDesktopActiveWindows,
  focusDesktopWindow,
  closeDesktopWindow,
  showDesktop,
  getDesktopWorkflows,
  saveDesktopWorkflow,
  deleteDesktopWorkflow,
  getDesktopAuditLogs,
  type VolumeStatus,
  type DesktopAppDefinition,
  type DesktopActionResponse,
  type DesktopGuiAction,
  type DesktopWorkflow,
  type DesktopAuditEntry,
  type ActiveWindowInfo,
  type CursorPositionInfo,
} from "@/api";

interface DesktopToolsPanelProps {
  token: string;
}

export default function DesktopToolsPanel({ token }: DesktopToolsPanelProps) {
  // Navigation Tabs: "quick" (Media, Volume, Apps) vs "agent" (Full GUI Agent Automation)
  const [activeTab, setActiveTab] = useState<"quick" | "agent">("quick");

  // Volume State
  const [volumeStatus, setVolumeStatus] = useState<VolumeStatus>({ volume: 50, isMuted: false });
  const [sliderValue, setSliderValue] = useState<number>(50);
  const [volumeBusy, setVolumeBusy] = useState(false);

  // Media State
  const [mediaBusy, setMediaBusy] = useState(false);
  const [mediaStatusMsg, setMediaStatusMsg] = useState<string | null>(null);

  // Apps State
  const [apps, setApps] = useState<DesktopAppDefinition[]>([]);
  const [appsLoading, setAppsLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedCategory, setSelectedCategory] = useState<string>("All");
  const [launchStatus, setLaunchStatus] = useState<{ id: string; success: boolean; msg: string } | null>(null);

  // Voice Simulation State
  const [activeLangTab, setActiveLangTab] = useState<"en" | "ta">("en");
  const [testingCmd, setTestingCmd] = useState<string | null>(null);
  const [simulatedReply, setSimulatedReply] = useState<string | null>(null);

  // GUI Agent State
  const [cursorInfo, setCursorInfo] = useState<CursorPositionInfo | null>(null);
  const [windows, setWindows] = useState<ActiveWindowInfo[]>([]);
  const [windowsLoading, setWindowsLoading] = useState(false);
  const [workflows, setWorkflows] = useState<DesktopWorkflow[]>([]);
  const [workflowsLoading, setWorkflowsLoading] = useState(false);
  const [auditLogs, setAuditLogs] = useState<DesktopAuditEntry[]>([]);
  const [runningWorkflowId, setRunningWorkflowId] = useState<string | null>(null);
  const [agentMsg, setAgentMsg] = useState<{ text: string; ok: boolean } | null>(null);

  // Sandbox inputs
  const [customText, setCustomText] = useState("");
  const [coordX, setCoordX] = useState<number>(500);
  const [coordY, setCoordY] = useState<number>(300);
  const [actionBusy, setActionBusy] = useState(false);

  // Load initial data
  useEffect(() => {
    fetchVolume();
    fetchApps();
    if (activeTab === "agent") {
      fetchAgentData();
    }
  }, [token, activeTab]);

  const fetchVolume = async () => {
    try {
      const status = await getDesktopVolume(token);
      setVolumeStatus(status);
      setSliderValue(status.volume);
    } catch {
      // Default fallback
    }
  };

  const fetchApps = async () => {
    setAppsLoading(true);
    try {
      const list = await listDesktopApps(token);
      setApps(list);
    } catch {
      // Empty fallback
    } finally {
      setAppsLoading(false);
    }
  };

  const fetchAgentData = async () => {
    setWindowsLoading(true);
    setWorkflowsLoading(true);
    try {
      const [cur, win, wfs, logs] = await Promise.allSettled([
        getDesktopCursorInfo(token),
        getDesktopActiveWindows(token),
        getDesktopWorkflows(token),
        getDesktopAuditLogs(token, 25),
      ]);
      if (cur.status === "fulfilled") setCursorInfo(cur.value);
      if (win.status === "fulfilled") setWindows(win.value);
      if (wfs.status === "fulfilled") setWorkflows(wfs.value);
      if (logs.status === "fulfilled") setAuditLogs(logs.value);
    } finally {
      setWindowsLoading(false);
      setWorkflowsLoading(false);
    }
  };

  // Media Handlers
  const handleMedia = async (action: "play_pause" | "next" | "prev" | "stop") => {
    setMediaBusy(true);
    try {
      const res = await controlDesktopMedia(action, token);
      setMediaStatusMsg(res.message);
      setTimeout(() => setMediaStatusMsg(null), 3500);
    } catch (e: unknown) {
      const err = e instanceof Error ? e.message : "Media action failed";
      setMediaStatusMsg(`Error: ${err}`);
      setTimeout(() => setMediaStatusMsg(null), 3500);
    } finally {
      setMediaBusy(false);
    }
  };

  // Volume Handlers
  const handleVolumeChange = async (newVal: number) => {
    setSliderValue(newVal);
    setVolumeBusy(true);
    try {
      const res = await controlDesktopVolume("set", { level: newVal }, token);
      setVolumeStatus(res);
    } catch {
      // ignore
    } finally {
      setVolumeBusy(false);
    }
  };

  const handleVolumeStep = async (direction: "up" | "down") => {
    setVolumeBusy(true);
    try {
      const res = await controlDesktopVolume(direction, { steps: 3 }, token);
      setVolumeStatus(res);
      setSliderValue(res.volume);
    } catch {
      // ignore
    } finally {
      setVolumeBusy(false);
    }
  };

  const handleToggleMute = async () => {
    setVolumeBusy(true);
    try {
      const res = await controlDesktopVolume("mute", {}, token);
      setVolumeStatus(res);
    } catch {
      // ignore
    } finally {
      setVolumeBusy(false);
    }
  };

  // App Launcher Handler
  const handleLaunchApp = async (target: string, id: string) => {
    setLaunchStatus({ id, success: true, msg: "Launching..." });
    try {
      const res = await launchDesktopApp(target, token);
      setLaunchStatus({ id, success: res.success, msg: res.message });
      setTimeout(() => setLaunchStatus(null), 4000);
    } catch (e: unknown) {
      const err = e instanceof Error ? e.message : "Launch failed";
      setLaunchStatus({ id, success: false, msg: err });
      setTimeout(() => setLaunchStatus(null), 4000);
    }
  };

  // Voice Command Simulator
  const handleTestCommand = async (commandText: string, lang: string) => {
    setTestingCmd(commandText);
    setSimulatedReply(null);
    try {
      const res = await executeDesktopCommand(commandText, lang, token);
      if (res.handled) {
        setSimulatedReply(res.reply);
        fetchVolume();
      } else {
        setSimulatedReply("Command not recognized as a direct desktop action.");
      }
    } catch (e: unknown) {
      const err = e instanceof Error ? e.message : "Execution failed";
      setSimulatedReply(`Error executing command: ${err}`);
    } finally {
      setTestingCmd(null);
    }
  };

  // GUI Agent Action Handlers
  const handleShowDesktop = async () => {
    setActionBusy(true);
    try {
      const res = await showDesktop(token);
      setAgentMsg({ text: res.message, ok: res.success });
      refreshAgentAudit();
    } catch (e: unknown) {
      setAgentMsg({ text: e instanceof Error ? e.message : "Failed to toggle desktop", ok: false });
    } finally {
      setActionBusy(false);
      setTimeout(() => setAgentMsg(null), 4000);
    }
  };

  const handleFocusWindow = async (target: string) => {
    setActionBusy(true);
    try {
      const res = await focusDesktopWindow(token, target);
      setAgentMsg({ text: res.message, ok: res.success });
      refreshAgentAudit();
    } catch (e: unknown) {
      setAgentMsg({ text: e instanceof Error ? e.message : "Failed to focus window", ok: false });
    } finally {
      setActionBusy(false);
      setTimeout(() => setAgentMsg(null), 4000);
    }
  };

  const handleCloseWindow = async (target: string) => {
    if (!confirm(`Are you sure you want to close window '${target}'?`)) return;
    setActionBusy(true);
    try {
      const res = await closeDesktopWindow(token, target);
      setAgentMsg({ text: res.message, ok: res.success });
      getDesktopActiveWindows(token).then(setWindows);
      refreshAgentAudit();
    } catch (e: unknown) {
      setAgentMsg({ text: e instanceof Error ? e.message : "Failed to close window", ok: false });
    } finally {
      setActionBusy(false);
      setTimeout(() => setAgentMsg(null), 4000);
    }
  };

  const handleHotkey = async (combo: string) => {
    setActionBusy(true);
    try {
      const res = await executeDesktopGuiAction(token, { type: "HOTKEY", hotkey: combo });
      setAgentMsg({ text: `Hotkey dispatched: ${combo}`, ok: res.success });
      refreshAgentAudit();
    } catch (e: unknown) {
      setAgentMsg({ text: e instanceof Error ? e.message : "Hotkey failed", ok: false });
    } finally {
      setActionBusy(false);
      setTimeout(() => setAgentMsg(null), 3000);
    }
  };

  const handleTypeText = async () => {
    if (!customText) return;
    setActionBusy(true);
    try {
      const res = await executeDesktopGuiAction(token, { type: "TYPE_TEXT", text: customText, delayMs: 50 });
      setAgentMsg({ text: res.message, ok: res.success });
      setCustomText("");
      refreshAgentAudit();
    } catch (e: unknown) {
      setAgentMsg({ text: e instanceof Error ? e.message : "Typing failed", ok: false });
    } finally {
      setActionBusy(false);
      setTimeout(() => setAgentMsg(null), 4000);
    }
  };

  const handleMouseClickAction = async (button: string) => {
    setActionBusy(true);
    try {
      const res = await executeDesktopGuiAction(token, { type: "MOUSE_CLICK", button, x: coordX, y: coordY });
      setAgentMsg({ text: res.message, ok: res.success });
      refreshAgentAudit();
    } catch (e: unknown) {
      setAgentMsg({ text: e instanceof Error ? e.message : "Click failed", ok: false });
    } finally {
      setActionBusy(false);
      setTimeout(() => setAgentMsg(null), 3000);
    }
  };

  const handleRunWorkflow = async (wf: DesktopWorkflow) => {
    setRunningWorkflowId(wf.id);
    try {
      const res = await executeDesktopGuiSequence(token, wf.id, wf.name, wf.actions);
      setAgentMsg({ text: res.message, ok: res.success });
      getDesktopWorkflows(token).then(setWorkflows);
      refreshAgentAudit();
    } catch (e: unknown) {
      setAgentMsg({ text: e instanceof Error ? e.message : "Workflow failed", ok: false });
    } finally {
      setRunningWorkflowId(null);
      setTimeout(() => setAgentMsg(null), 4500);
    }
  };

  const handleDeleteWorkflow = async (id: string, name: string) => {
    if (!confirm(`Delete workflow '${name}'?`)) return;
    try {
      await deleteDesktopWorkflow(token, id);
      getDesktopWorkflows(token).then(setWorkflows);
    } catch {
      // ignore
    }
  };

  const refreshAgentAudit = () => {
    getDesktopAuditLogs(token, 25).then(setAuditLogs).catch(() => {});
    getDesktopCursorInfo(token).then(setCursorInfo).catch(() => {});
  };

  // App icon renderer
  const renderAppIcon = (iconName: string) => {
    switch (iconName) {
      case "Globe": return <Globe className="w-5 h-5 text-blue-400" />;
      case "Code2": return <Code2 className="w-5 h-5 text-sky-400" />;
      case "Terminal": return <Terminal className="w-5 h-5 text-emerald-400" />;
      case "Music": return <Music className="w-5 h-5 text-green-400" />;
      case "PlaySquare":
      case "Tv": return <Tv className="w-5 h-5 text-red-400" />;
      case "Calculator": return <Calculator className="w-5 h-5 text-amber-400" />;
      case "FileText": return <FileText className="w-5 h-5 text-purple-400" />;
      case "Settings": return <Settings className="w-5 h-5 text-slate-300" />;
      case "FolderOpen": return <FolderOpen className="w-5 h-5 text-yellow-400" />;
      case "Palette": return <Palette className="w-5 h-5 text-pink-400" />;
      case "Camera": return <Camera className="w-5 h-5 text-indigo-400" />;
      case "MessageCircle": return <MessageCircle className="w-5 h-5 text-violet-400" />;
      case "Activity": return <Activity className="w-5 h-5 text-cyan-400" />;
      default: return <Laptop className="w-5 h-5 text-cyan-400" />;
    }
  };

  // Categories
  const categories = ["All", ...Array.from(new Set(apps.map((a) => a.category)))];

  const filteredApps = apps.filter((a) => {
    const matchesCat = selectedCategory === "All" || a.category === selectedCategory;
    const matchesQuery =
      a.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      a.command.toLowerCase().includes(searchQuery.toLowerCase()) ||
      a.description.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesCat && matchesQuery;
  });

  return (
    <div className="flex-1 overflow-y-auto px-4 py-6 md:px-8 space-y-6 text-slate-200">
      {/* Header Banner */}
      <div className="bg-gradient-to-r from-cyan-950/40 via-slate-900 to-slate-950 border border-cyan-800/40 rounded-2xl p-6 shadow-2xl relative overflow-hidden backdrop-blur-md">
        <div className="absolute top-0 right-0 p-8 opacity-10 pointer-events-none">
          <Laptop size={180} />
        </div>
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 relative z-10">
          <div>
            <div className="flex items-center gap-2 mb-2 flex-wrap">
              <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold uppercase tracking-wider bg-cyan-500/20 text-cyan-300 border border-cyan-500/30">
                Host OS Telemetry &amp; Actuators
              </span>
              <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold tracking-wider bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                Win32 &amp; Robot Online
              </span>
              <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold tracking-wider bg-purple-500/20 text-purple-300 border border-purple-500/30">
                PostgreSQL RLS Persisted
              </span>
            </div>
            <h1 className="text-2xl md:text-3xl font-bold tracking-tight text-white flex items-center gap-2">
              <Laptop className="text-cyan-400" /> Desktop System Tools &amp; GUI Agent
            </h1>
            <p className="text-sm text-slate-400 mt-1 max-w-2xl">
              Unified Windows desktop automation: hardware media keys, master volume, application launching, autonomous mouse/keyboard actuation, active window switcher, and database-persisted macro workflows.
            </p>
          </div>

          <div className="flex items-center gap-2 flex-wrap">
            <button
              onClick={() => {
                fetchVolume();
                fetchApps();
                if (activeTab === "agent") fetchAgentData();
              }}
              className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-slate-800/80 hover:bg-slate-700 border border-slate-700/60 text-xs font-medium text-slate-300 transition"
            >
              <RefreshCw size={14} className={appsLoading || windowsLoading ? "animate-spin" : ""} /> Refresh System
            </button>
          </div>
        </div>

        {/* Tab Navigation Pill Selector */}
        <div className="flex items-center gap-3 mt-6 pt-4 border-t border-slate-800/80">
          <button
            onClick={() => setActiveTab("quick")}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold uppercase tracking-wider transition ${
              activeTab === "quick"
                ? "bg-cyan-500 text-slate-950 shadow-lg shadow-cyan-500/30 font-bold"
                : "bg-slate-800/60 text-slate-400 hover:text-white hover:bg-slate-800"
            }`}
          >
            <Sliders size={15} /> Quick Controls (Media &amp; Apps)
          </button>
          <button
            onClick={() => setActiveTab("agent")}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold uppercase tracking-wider transition ${
              activeTab === "agent"
                ? "bg-gradient-to-r from-purple-500 to-indigo-600 text-white shadow-lg shadow-purple-500/30 font-bold"
                : "bg-slate-800/60 text-slate-400 hover:text-white hover:bg-slate-800"
            }`}
          >
            <MousePointer size={15} /> Desktop GUI Agent Studio
            <span className="px-1.5 py-0.5 rounded bg-purple-400/20 text-[10px] text-purple-300 border border-purple-400/30">
              New
            </span>
          </button>
        </div>
      </div>

      {/* Global Agent Notification Toast */}
      {agentMsg && (
        <div
          className={`px-4 py-3 rounded-xl border text-xs flex items-center justify-between shadow-xl animate-fadeIn ${
            agentMsg.ok
              ? "bg-emerald-950/80 border-emerald-500/40 text-emerald-200"
              : "bg-red-950/80 border-red-500/40 text-red-200"
          }`}
        >
          <div className="flex items-center gap-2">
            {agentMsg.ok ? <Sparkles size={16} className="text-emerald-400" /> : <AlertCircle size={16} className="text-red-400" />}
            <span className="font-medium">{agentMsg.text}</span>
          </div>
          <button onClick={() => setAgentMsg(null)} className="text-slate-400 hover:text-white">
            <X size={14} />
          </button>
        </div>
      )}

      {/* ========================================================================= */}
      {/* TAB 1: QUICK CONTROLS (Media, Volume, Apps, Voice Sim)                    */}
      {/* ========================================================================= */}
      {activeTab === "quick" && (
        <>
          {/* Grid: Media & Volume Controllers */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Card 1: Windows Media Controller */}
            <div className="bg-slate-900/80 border border-cyan-800/30 rounded-2xl p-6 shadow-xl backdrop-blur-md flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2.5">
                    <div className="w-10 h-10 rounded-xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
                      <Play size={20} />
                    </div>
                    <div>
                      <h2 className="text-lg font-semibold text-white">Windows Media Control</h2>
                      <p className="text-xs text-slate-400">Spotify, YouTube, VLC, and Windows Media Player</p>
                    </div>
                  </div>
                  <span className="text-xs text-slate-500 font-mono">VK_MEDIA_*</span>
                </div>

                {/* Media Action Feedback Banner */}
                {mediaStatusMsg && (
                  <div className="mb-4 px-3 py-2 rounded-lg bg-cyan-950/60 border border-cyan-500/30 text-xs text-cyan-200 flex items-center gap-2 animate-fadeIn">
                    <Sparkles size={14} className="text-cyan-400" />
                    <span>{mediaStatusMsg}</span>
                  </div>
                )}

                {/* Tactile Media Controls HUD */}
                <div className="grid grid-cols-4 gap-3 py-4">
                  <button
                    disabled={mediaBusy}
                    onClick={() => handleMedia("prev")}
                    className="flex flex-col items-center justify-center gap-1.5 p-4 rounded-xl bg-slate-800/60 hover:bg-slate-700/80 active:bg-cyan-950/60 border border-slate-700/60 hover:border-cyan-500/50 transition group"
                    title="Previous Track (VK_MEDIA_PREV_TRACK)"
                  >
                    <SkipBack size={22} className="text-slate-300 group-hover:text-cyan-400 transition" />
                    <span className="text-[11px] font-medium text-slate-400 group-hover:text-white">Prev</span>
                  </button>

                  <button
                    disabled={mediaBusy}
                    onClick={() => handleMedia("play_pause")}
                    className="col-span-2 flex flex-col items-center justify-center gap-1.5 p-4 rounded-xl bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 active:scale-[0.98] border border-cyan-400/40 shadow-lg shadow-cyan-900/30 transition group"
                    title="Play / Pause Toggle (VK_MEDIA_PLAY_PAUSE)"
                  >
                    <div className="flex items-center gap-2 text-white">
                      <Play size={22} fill="currentColor" />
                      <Pause size={22} fill="currentColor" />
                    </div>
                    <span className="text-xs font-semibold text-white uppercase tracking-wider">Play / Pause</span>
                  </button>

                  <button
                    disabled={mediaBusy}
                    onClick={() => handleMedia("next")}
                    className="flex flex-col items-center justify-center gap-1.5 p-4 rounded-xl bg-slate-800/60 hover:bg-slate-700/80 active:bg-cyan-950/60 border border-slate-700/60 hover:border-cyan-500/50 transition group"
                    title="Next Track (VK_MEDIA_NEXT_TRACK)"
                  >
                    <SkipForward size={22} className="text-slate-300 group-hover:text-cyan-400 transition" />
                    <span className="text-[11px] font-medium text-slate-400 group-hover:text-white">Next</span>
                  </button>
                </div>
              </div>

              <div className="flex items-center justify-between pt-4 border-t border-slate-800/80 text-xs text-slate-400">
                <span className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" /> Global System Hook Active
                </span>
                <button
                  disabled={mediaBusy}
                  onClick={() => handleMedia("stop")}
                  className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-red-950/40 hover:bg-red-900/60 border border-red-500/30 text-red-300 transition"
                >
                  <Square size={13} fill="currentColor" /> Stop Playback
                </button>
              </div>
            </div>

            {/* Card 2: Master Volume Controller */}
            <div className="bg-slate-900/80 border border-cyan-800/30 rounded-2xl p-6 shadow-xl backdrop-blur-md flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2.5">
                    <div className="w-10 h-10 rounded-xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
                      {volumeStatus.isMuted ? <VolumeX size={20} /> : volumeStatus.volume > 50 ? <Volume2 size={20} /> : <Volume1 size={20} />}
                    </div>
                    <div>
                      <h2 className="text-lg font-semibold text-white">Master Hardware Volume</h2>
                      <p className="text-xs text-slate-400">Global Windows Audio Endpoints</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className={`px-2 py-0.5 rounded text-xs font-mono font-bold ${volumeStatus.isMuted ? "bg-red-500/20 text-red-400 border border-red-500/40" : "bg-cyan-500/20 text-cyan-300 border border-cyan-500/40"}`}>
                      {volumeStatus.isMuted ? "MUTED" : `${sliderValue}%`}
                    </span>
                  </div>
                </div>

                <div className="py-4 space-y-4">
                  <div className="flex items-center gap-4">
                    <button
                      disabled={volumeBusy}
                      onClick={handleToggleMute}
                      className={`p-3 rounded-xl border transition ${
                        volumeStatus.isMuted
                          ? "bg-red-950/60 border-red-500/50 text-red-400 hover:bg-red-900/60"
                          : "bg-slate-800/60 border-slate-700/60 text-slate-300 hover:text-white hover:bg-slate-700"
                      }`}
                      title={volumeStatus.isMuted ? "Unmute Volume" : "Mute Volume"}
                    >
                      {volumeStatus.isMuted ? <VolumeX size={20} /> : <Volume2 size={20} />}
                    </button>

                    <div className="flex-1 relative flex items-center">
                      <input
                        type="range"
                        min="0"
                        max="100"
                        value={sliderValue}
                        disabled={volumeBusy}
                        onChange={(e) => setSliderValue(Number(e.target.value))}
                        onMouseUp={(e) => handleVolumeChange(Number((e.target as HTMLInputElement).value))}
                        onTouchEnd={(e) => handleVolumeChange(Number((e.target as HTMLInputElement).value))}
                        className="w-full h-2.5 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-cyan-400 focus:outline-none"
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-4 gap-2 pt-2">
                    {[25, 50, 75, 100].map((preset) => (
                      <button
                        key={preset}
                        disabled={volumeBusy}
                        onClick={() => handleVolumeChange(preset)}
                        className={`py-2 rounded-lg text-xs font-medium border transition ${
                          sliderValue === preset && !volumeStatus.isMuted
                            ? "bg-cyan-500/20 border-cyan-400 text-cyan-300"
                            : "bg-slate-800/40 border-slate-700/60 text-slate-400 hover:text-white hover:bg-slate-700"
                        }`}
                      >
                        {preset}%
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              <div className="flex items-center justify-between pt-4 border-t border-slate-800/80">
                <button
                  disabled={volumeBusy}
                  onClick={() => handleVolumeStep("down")}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-800/80 hover:bg-slate-700 text-xs font-medium text-slate-300 transition"
                >
                  <Volume1 size={14} /> Step Down (-6%)
                </button>
                <button
                  disabled={volumeBusy}
                  onClick={() => handleVolumeStep("up")}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-800/80 hover:bg-slate-700 text-xs font-medium text-slate-300 transition"
                >
                  <Volume2 size={14} /> Step Up (+6%)
                </button>
              </div>
            </div>
          </div>

          {/* Section: Application Launcher Catalog */}
          <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-6 shadow-xl backdrop-blur-md space-y-6">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
              <div>
                <h2 className="text-xl font-bold text-white flex items-center gap-2">
                  <Laptop className="text-cyan-400" /> Windows Application Launcher
                </h2>
                <p className="text-xs text-slate-400 mt-0.5">
                  Click to launch instantly or trigger with voice in English (*"Open Spotify"*) or Tamil (*"குரோம் திற"*).
                </p>
              </div>

              <div className="flex items-center gap-3">
                <div className="relative">
                  <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                  <input
                    type="text"
                    placeholder="Search apps or tools..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="pl-9 pr-4 py-1.5 rounded-xl bg-slate-950/80 border border-slate-700/80 text-xs text-slate-200 focus:outline-none focus:border-cyan-500 w-48 md:w-64"
                  />
                </div>
              </div>
            </div>

            {/* Category Filter Chips */}
            <div className="flex items-center gap-2 overflow-x-auto pb-1 scrollbar-none">
              {categories.map((cat) => (
                <button
                  key={cat}
                  onClick={() => setSelectedCategory(cat)}
                  className={`px-3 py-1.5 rounded-xl text-xs font-medium transition whitespace-nowrap ${
                    selectedCategory === cat
                      ? "bg-cyan-500 text-slate-950 font-semibold shadow-md shadow-cyan-500/20"
                      : "bg-slate-800/60 text-slate-400 hover:text-slate-200 hover:bg-slate-800"
                  }`}
                >
                  {cat}
                </button>
              ))}
            </div>

            {/* App Launch Grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
              {filteredApps.map((app) => (
                <div
                  key={app.id}
                  className="p-4 rounded-xl bg-slate-950/50 hover:bg-slate-800/40 border border-slate-800/80 hover:border-cyan-500/40 transition group flex flex-col justify-between"
                >
                  <div className="flex items-start gap-3 mb-3">
                    <div className="p-2.5 rounded-xl bg-slate-900 border border-slate-800 group-hover:border-cyan-500/40 transition">
                      {renderAppIcon(app.icon)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <h3 className="text-sm font-semibold text-white truncate group-hover:text-cyan-300 transition">
                        {app.name}
                      </h3>
                      <p className="text-[11px] text-slate-400 line-clamp-1">{app.description}</p>
                    </div>
                  </div>

                  <div className="flex items-center justify-between pt-2 border-t border-slate-900">
                    <span className="text-[10px] font-mono text-slate-500 truncate">{app.category}</span>
                    <button
                      disabled={launchStatus?.id === app.id}
                      onClick={() => handleLaunchApp(app.command, app.id)}
                      className="flex items-center gap-1.5 px-3 py-1 rounded-lg bg-slate-800 hover:bg-cyan-600 active:bg-cyan-700 text-xs font-medium text-slate-300 hover:text-white transition"
                    >
                      {launchStatus?.id === app.id ? (
                        <>
                          <RefreshCw size={11} className="animate-spin text-cyan-300" />
                          <span>Launching...</span>
                        </>
                      ) : (
                        <>
                          <ExternalLink size={12} />
                          <span>Launch</span>
                        </>
                      )}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Section: Dual-Language Voice Command Simulator */}
          <div className="bg-slate-900/80 border border-cyan-800/30 rounded-2xl p-6 shadow-xl backdrop-blur-md space-y-4">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
              <div>
                <h2 className="text-lg font-bold text-white flex items-center gap-2">
                  <Sparkles className="text-cyan-400" /> Voice Command Execution Simulator
                </h2>
                <p className="text-xs text-slate-400 mt-0.5">
                  Test spoken English and Tamil desktop commands directly to verify regex parsing and actuation.
                </p>
              </div>

              <div className="flex items-center gap-2 p-1 rounded-xl bg-slate-950 border border-slate-800 text-xs">
                <button
                  onClick={() => setActiveLangTab("en")}
                  className={`px-3 py-1 rounded-lg font-medium transition ${
                    activeLangTab === "en" ? "bg-cyan-500 text-slate-950 font-bold" : "text-slate-400 hover:text-white"
                  }`}
                >
                  English Commands
                </button>
                <button
                  onClick={() => setActiveLangTab("ta")}
                  className={`px-3 py-1 rounded-lg font-medium transition ${
                    activeLangTab === "ta" ? "bg-cyan-500 text-slate-950 font-bold" : "text-slate-400 hover:text-white"
                  }`}
                >
                  தமிழ் கட்டளைகள் (Tamil)
                </button>
              </div>
            </div>

            {simulatedReply && (
              <div className="p-4 rounded-xl bg-slate-950/80 border border-cyan-500/40 text-xs text-slate-200 animate-fadeIn space-y-1">
                <div className="font-semibold text-cyan-400 uppercase tracking-wider text-[11px]">JARVIS Response</div>
                <div className="font-sans whitespace-pre-wrap">{simulatedReply}</div>
              </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-2">
              <div className="p-4 rounded-xl bg-slate-950/60 border border-slate-800 flex flex-col justify-between">
                <div>
                  <div className="flex items-center gap-2 mb-3 text-cyan-400 font-semibold text-xs uppercase tracking-wider">
                    <Music size={14} /> {activeLangTab === "en" ? "Media Commands" : "இசை கட்டளைகள்"}
                  </div>
                  <ul className="space-y-2 text-xs">
                    {(activeLangTab === "en"
                      ? ["Jarvis, play music", "Pause playback", "Skip to next track", "Previous track"]
                      : ["பாட்டைப் போடு", "பாட்டை நிறுத்து", "அடுத்த பாடல்", "முந்தைய பாடல்"]
                    ).map((cmd) => (
                      <li key={cmd} className="flex items-center justify-between group">
                        <span className="text-slate-300 font-mono text-[11px]">"{cmd}"</span>
                        <button
                          disabled={testingCmd === cmd}
                          onClick={() => handleTestCommand(cmd, activeLangTab)}
                          className="px-2 py-0.5 rounded bg-slate-800 hover:bg-cyan-600 text-[10px] text-slate-300 hover:text-white transition"
                        >
                          {testingCmd === cmd ? "Running..." : "Test"}
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>

              <div className="p-4 rounded-xl bg-slate-950/60 border border-slate-800 flex flex-col justify-between">
                <div>
                  <div className="flex items-center gap-2 mb-3 text-cyan-400 font-semibold text-xs uppercase tracking-wider">
                    <Volume2 size={14} /> {activeLangTab === "en" ? "Volume Controls" : "ஒலியளவு கட்டுப்பாடுகள்"}
                  </div>
                  <ul className="space-y-2 text-xs">
                    {(activeLangTab === "en"
                      ? ["Jarvis, volume up", "Make it quieter", "Set volume to 75%", "Mute audio"]
                      : ["சத்தத்தை கூட்டு", "சத்தத்தை குறை", "சத்தத்தை 50 ஆக வை", "மியூட் செய்"]
                    ).map((cmd) => (
                      <li key={cmd} className="flex items-center justify-between group">
                        <span className="text-slate-300 font-mono text-[11px]">"{cmd}"</span>
                        <button
                          disabled={testingCmd === cmd}
                          onClick={() => handleTestCommand(cmd, activeLangTab)}
                          className="px-2 py-0.5 rounded bg-slate-800 hover:bg-cyan-600 text-[10px] text-slate-300 hover:text-white transition"
                        >
                          {testingCmd === cmd ? "Running..." : "Test"}
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>

              <div className="p-4 rounded-xl bg-slate-950/60 border border-slate-800 flex flex-col justify-between">
                <div>
                  <div className="flex items-center gap-2 mb-3 text-cyan-400 font-semibold text-xs uppercase tracking-wider">
                    <Laptop size={14} /> {activeLangTab === "en" ? "App Launch Commands" : "செயலிகள் திறக்கும் கட்டளை"}
                  </div>
                  <ul className="space-y-2 text-xs">
                    {(activeLangTab === "en"
                      ? ["Open Spotify", "Launch Google Chrome", "Start Calculator", "Open Visual Studio Code"]
                      : ["ஸ்பாட்டிஃபை திற", "குரோம் திற", "கால்குலேட்டரை திற", "நோட்பேட் திற"]
                    ).map((cmd) => (
                      <li key={cmd} className="flex items-center justify-between group">
                        <span className="text-slate-300 font-mono text-[11px]">"{cmd}"</span>
                        <button
                          disabled={testingCmd === cmd}
                          onClick={() => handleTestCommand(cmd, activeLangTab)}
                          className="px-2 py-0.5 rounded bg-slate-800 hover:bg-cyan-600 text-[10px] text-slate-300 hover:text-white transition"
                        >
                          {testingCmd === cmd ? "Running..." : "Test"}
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            </div>
          </div>
        </>
      )}

      {/* ========================================================================= */}
      {/* TAB 2: FULL DESKTOP GUI AGENT STUDIO (Windows, Mouse, Keyboard, Workflows)*/}
      {/* ========================================================================= */}
      {activeTab === "agent" && (
        <div className="space-y-6">
          {/* Sub-Bar: Quick OS Actions & Telemetry HUD */}
          <div className="p-4 rounded-2xl bg-slate-900/90 border border-purple-500/30 shadow-xl flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-xl bg-purple-500/10 text-purple-400 border border-purple-500/30">
                <Monitor size={20} />
              </div>
              <div>
                <div className="text-xs font-semibold text-white flex items-center gap-2">
                  <span>Display Resolution:</span>
                  <span className="font-mono text-purple-300">
                    {cursorInfo ? `${cursorInfo.screenWidth} × ${cursorInfo.screenHeight}` : "1920 × 1080"}
                  </span>
                </div>
                <div className="text-[11px] text-slate-400 flex items-center gap-2">
                  <span>Cursor:</span>
                  <span className="font-mono text-cyan-300">
                    ({cursorInfo ? cursorInfo.x : 0}, {cursorInfo ? cursorInfo.y : 0})
                  </span>
                  <span>· Database Schema:</span>
                  <span className="font-mono text-emerald-300">jarvis.desktop_*</span>
                </div>
              </div>
            </div>

            <div className="flex items-center gap-2 flex-wrap">
              <button
                disabled={actionBusy}
                onClick={handleShowDesktop}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-purple-600 border border-slate-700 hover:border-purple-500 text-xs font-medium text-slate-200 transition"
                title="Minimizes all windows to display desktop (Win+D)"
              >
                <Minimize2 size={13} /> Show Desktop (Win+D)
              </button>
              <button
                disabled={actionBusy}
                onClick={() => handleHotkey("Ctrl+S")}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-emerald-600 border border-slate-700 hover:border-emerald-500 text-xs font-medium text-slate-200 transition"
                title="Save current active document (Ctrl+S)"
              >
                <Save size={13} /> Save Active (Ctrl+S)
              </button>
              <button
                disabled={actionBusy}
                onClick={() => handleHotkey("Alt+Tab")}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-cyan-600 border border-slate-700 hover:border-cyan-500 text-xs font-medium text-slate-200 transition"
                title="Switch active window (Alt+Tab)"
              >
                <Layers size={13} /> Alt + Tab
              </button>
            </div>
          </div>

          {/* Section: Live Active Window Manager */}
          <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-6 shadow-xl backdrop-blur-md space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-lg font-bold text-white flex items-center gap-2">
                  <Layers className="text-purple-400" /> Active Application Windows
                </h2>
                <p className="text-xs text-slate-400 mt-0.5">
                  Foreground interactive window handles. Click <strong>Focus</strong> to bring directly to front.
                </p>
              </div>
              <button
                disabled={windowsLoading}
                onClick={() => getDesktopActiveWindows(token).then(setWindows)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs text-slate-300 transition"
              >
                <RefreshCw size={13} className={windowsLoading ? "animate-spin" : ""} /> Refresh Windows
              </button>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
              {windows.map((win, idx) => (
                <div
                  key={`${win.processName}-${idx}`}
                  className="p-3.5 rounded-xl bg-slate-950/70 border border-slate-800 hover:border-purple-500/50 transition flex flex-col justify-between group"
                >
                  <div>
                    <div className="flex items-center justify-between mb-1.5">
                      <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-purple-950/60 text-purple-300 border border-purple-500/30 truncate max-w-[120px]">
                        {win.processName}
                      </span>
                      {win.processId > 0 && <span className="text-[10px] text-slate-500 font-mono">PID {win.processId}</span>}
                    </div>
                    <div className="text-xs font-semibold text-white line-clamp-2 group-hover:text-purple-300 transition mb-3">
                      {win.title}
                    </div>
                  </div>

                  <div className="flex items-center gap-2 pt-2 border-t border-slate-900">
                    <button
                      disabled={actionBusy}
                      onClick={() => handleFocusWindow(win.title || win.processName)}
                      className="flex-1 flex items-center justify-center gap-1 py-1 rounded-lg bg-purple-900/40 hover:bg-purple-600 border border-purple-500/30 text-xs text-purple-200 hover:text-white transition"
                    >
                      <Maximize2 size={12} /> Focus
                    </button>
                    <button
                      disabled={actionBusy}
                      onClick={() => handleCloseWindow(win.title || win.processName)}
                      className="p-1 rounded-lg bg-red-950/40 hover:bg-red-900/60 border border-red-500/30 text-red-400 transition"
                      title="Close window"
                    >
                      <X size={14} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Section: Mouse & Keyboard Sandbox */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Keyboard & Hotkey Sandbox */}
            <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-6 shadow-xl backdrop-blur-md space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-bold text-white flex items-center gap-2">
                  <Keyboard className="text-cyan-400" /> Keyboard &amp; Text Actuation
                </h3>
                <span className="text-[10px] font-mono text-cyan-400">Unicode &amp; Multiline Safe</span>
              </div>

              <div className="space-y-3">
                <textarea
                  rows={3}
                  value={customText}
                  onChange={(e) => setCustomText(e.target.value)}
                  placeholder="Type any text or code to insert into the currently focused window..."
                  className="w-full p-3 rounded-xl bg-slate-950 border border-slate-800 text-xs text-slate-200 focus:outline-none focus:border-cyan-500 font-mono"
                />

                <div className="flex items-center justify-between">
                  <button
                    disabled={actionBusy || !customText.trim()}
                    onClick={handleTypeText}
                    className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 disabled:opacity-50 text-xs font-semibold text-white transition shadow-lg shadow-cyan-900/20"
                  >
                    <Send size={13} /> Type into Active Window
                  </button>
                  <button
                    onClick={() => setCustomText("=== JARVIS MEETING NOTES ===\nDate: " + new Date().toLocaleDateString() + "\n- Discuss architecture")}
                    className="text-[11px] text-slate-400 hover:text-cyan-300 underline"
                  >
                    Load template
                  </button>
                </div>
              </div>

              {/* Hotkey Chips Palette */}
              <div className="pt-2 border-t border-slate-800/80 space-y-2">
                <div className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">Quick Hotkey Palette</div>
                <div className="flex flex-wrap gap-1.5">
                  {[
                    "Ctrl+S",
                    "Ctrl+C",
                    "Ctrl+V",
                    "Ctrl+A",
                    "Ctrl+Shift+P",
                    "Ctrl+`",
                    "Alt+Tab",
                    "Alt+F4",
                    "Win+D",
                    "Win+R",
                    "Enter",
                    "Escape",
                  ].map((combo) => (
                    <button
                      key={combo}
                      disabled={actionBusy}
                      onClick={() => handleHotkey(combo)}
                      className="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-cyan-600 border border-slate-700/60 hover:border-cyan-400 text-xs font-mono text-slate-300 hover:text-white transition"
                    >
                      {combo}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {/* Mouse & Coordinate Control */}
            <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-6 shadow-xl backdrop-blur-md space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-bold text-white flex items-center gap-2">
                  <Mouse className="text-purple-400" /> Mouse Positioning &amp; Clicks
                </h3>
                <span className="text-[10px] font-mono text-purple-400">AWT Robot Hardware</span>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-[11px] text-slate-400 font-mono block mb-1">X Coordinate (px)</label>
                  <input
                    type="number"
                    value={coordX}
                    onChange={(e) => setCoordX(Number(e.target.value))}
                    className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-xs font-mono text-white focus:outline-none focus:border-purple-500"
                  />
                </div>
                <div>
                  <label className="text-[11px] text-slate-400 font-mono block mb-1">Y Coordinate (px)</label>
                  <input
                    type="number"
                    value={coordY}
                    onChange={(e) => setCoordY(Number(e.target.value))}
                    className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-xs font-mono text-white focus:outline-none focus:border-purple-500"
                  />
                </div>
              </div>

              <div className="grid grid-cols-3 gap-2">
                <button
                  disabled={actionBusy}
                  onClick={() => handleMouseClickAction("left")}
                  className="py-2.5 rounded-xl bg-purple-900/40 hover:bg-purple-600 border border-purple-500/30 text-xs font-medium text-purple-200 hover:text-white transition"
                >
                  Left Click
                </button>
                <button
                  disabled={actionBusy}
                  onClick={() => handleMouseClickAction("double")}
                  className="py-2.5 rounded-xl bg-indigo-900/40 hover:bg-indigo-600 border border-indigo-500/30 text-xs font-medium text-indigo-200 hover:text-white transition"
                >
                  Double Click
                </button>
                <button
                  disabled={actionBusy}
                  onClick={() => handleMouseClickAction("right")}
                  className="py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 border border-slate-700 text-xs font-medium text-slate-300 hover:text-white transition"
                >
                  Right Click
                </button>
              </div>

              <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between text-xs text-slate-400">
                <span>Move &amp; Click at current position:</span>
                <button
                  disabled={actionBusy}
                  onClick={() => executeDesktopGuiAction(token, { type: "MOUSE_MOVE", x: coordX, y: coordY })}
                  className="px-3 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 text-[11px] transition"
                >
                  Move Cursor Only
                </button>
              </div>
            </div>
          </div>

          {/* Section: Saved Workflows & Macro Sequences (Database Persisted) */}
          <div className="bg-slate-900/80 border border-purple-800/30 rounded-2xl p-6 shadow-xl backdrop-blur-md space-y-4">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
              <div>
                <h2 className="text-lg font-bold text-white flex items-center gap-2">
                  <Wand2 className="text-purple-400" /> Database Macro Workflows
                </h2>
                <p className="text-xs text-slate-400 mt-0.5">
                  Multi-action sequences persisted in PostgreSQL table <code className="text-purple-300">jarvis.desktop_workflows</code>.
                </p>
              </div>
              <button
                disabled={workflowsLoading}
                onClick={() => getDesktopWorkflows(token).then(setWorkflows)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs text-slate-300 transition"
              >
                <RefreshCw size={13} className={workflowsLoading ? "animate-spin" : ""} /> Reload Workflows
              </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {workflows.map((wf) => (
                <div
                  key={wf.id}
                  className="p-5 rounded-2xl bg-slate-950/70 border border-slate-800 hover:border-purple-500/40 transition flex flex-col justify-between group space-y-3"
                >
                  <div>
                    <div className="flex items-center justify-between mb-1.5">
                      <div className="flex items-center gap-2">
                        {wf.isFavorite && <Star size={14} className="text-amber-400 fill-amber-400" />}
                        <h3 className="text-sm font-bold text-white group-hover:text-purple-300 transition">
                          {wf.name}
                        </h3>
                      </div>
                      <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-slate-800 text-slate-400">
                        Run count: {wf.executionCount}
                      </span>
                    </div>

                    <p className="text-xs text-slate-400 mb-2">{wf.description}</p>

                    {wf.triggerPattern && (
                      <div className="text-[11px] font-mono text-cyan-300 bg-cyan-950/40 border border-cyan-800/30 px-2 py-1 rounded-lg inline-block mb-3">
                        Voice: "{wf.triggerPattern}"
                      </div>
                    )}

                    {/* Action Step Badges */}
                    <div className="flex flex-wrap gap-1 mb-2">
                      {wf.actions.map((act, i) => (
                        <span
                          key={i}
                          className="px-2 py-0.5 rounded bg-slate-900 border border-slate-800 text-[10px] font-mono text-purple-300"
                        >
                          {act.type} {act.target ? `(${act.target})` : act.hotkey ? `(${act.hotkey})` : ""}
                        </span>
                      ))}
                    </div>
                  </div>

                  <div className="flex items-center justify-between pt-3 border-t border-slate-900">
                    <span className="text-[10px] text-slate-500 font-mono">
                      {wf.actions.length} automated steps
                    </span>
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => handleDeleteWorkflow(wf.id, wf.name)}
                        className="p-1.5 rounded-lg bg-slate-800 hover:bg-red-950 text-slate-400 hover:text-red-400 transition"
                        title="Delete workflow"
                      >
                        <Trash2 size={13} />
                      </button>
                      <button
                        disabled={runningWorkflowId === wf.id}
                        onClick={() => handleRunWorkflow(wf)}
                        className="flex items-center gap-1.5 px-4 py-1.5 rounded-xl bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 disabled:opacity-50 text-xs font-semibold text-white transition shadow-lg shadow-purple-900/30"
                      >
                        {runningWorkflowId === wf.id ? (
                          <>
                            <RefreshCw size={12} className="animate-spin text-purple-200" />
                            <span>Executing...</span>
                          </>
                        ) : (
                          <>
                            <PlayCircle size={14} />
                            <span>Run Workflow</span>
                          </>
                        )}
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Section: Live Execution Audit Trail */}
          <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-6 shadow-xl backdrop-blur-md space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-sm font-bold text-white flex items-center gap-2">
                  <Clock className="text-cyan-400" /> GUI Action Audit History
                </h3>
                <p className="text-xs text-slate-400 mt-0.5">
                  Logged into <code className="text-cyan-300">jarvis.desktop_action_audit</code> &amp; <code className="text-cyan-300">data/desktop-audit.jsonl</code>.
                </p>
              </div>
              <button
                onClick={refreshAgentAudit}
                className="flex items-center gap-1 text-xs text-slate-400 hover:text-white"
              >
                <RefreshCw size={12} /> Refresh
              </button>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-slate-800 text-slate-400 font-mono text-[11px]">
                    <th className="pb-2">Timestamp</th>
                    <th className="pb-2">Action</th>
                    <th className="pb-2">Target / Detail</th>
                    <th className="pb-2">Status</th>
                    <th className="pb-2 text-right">Duration</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 font-mono text-[11px]">
                  {auditLogs.slice(0, 10).map((log, idx) => (
                    <tr key={log.id || idx} className="hover:bg-slate-800/30">
                      <td className="py-2 text-slate-500">{log.createdAt ? log.createdAt.replace("T", " ").substring(0, 19) : "Just now"}</td>
                      <td className="py-2 text-cyan-300 font-semibold">{log.actionType}</td>
                      <td className="py-2 text-slate-300 truncate max-w-xs">{log.target || JSON.stringify(log.payload)}</td>
                      <td className="py-2">
                        <span
                          className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                            log.status === "SUCCESS"
                              ? "bg-emerald-500/20 text-emerald-400"
                              : "bg-red-500/20 text-red-400"
                          }`}
                        >
                          {log.status}
                        </span>
                      </td>
                      <td className="py-2 text-right text-slate-400">{log.durationMs}ms</td>
                    </tr>
                  ))}
                  {auditLogs.length === 0 && (
                    <tr>
                      <td colSpan={5} className="py-4 text-center text-slate-500">
                        No desktop agent actions executed yet.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

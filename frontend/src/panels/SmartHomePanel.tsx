import { useState, useEffect } from "react";
import {
  Home,
  Power,
  Lightbulb,
  Sliders,
  Tv,
  Briefcase,
  Moon,
  Coffee,
  Gamepad2,
  RefreshCw,
  Settings,
  Zap,
  Flame,
  Thermometer,
  Activity,
  Send,
  CheckCircle2,
  AlertCircle,
  Wifi,
  WifiOff,
  Radio,
  SlidersHorizontal,
  ChevronRight,
  Sun,
  Eye,
  X,
  Volume2,
} from "lucide-react";
import {
  getSmartHomeStatus,
  listSmartDevices,
  executeSmartDeviceAction,
  listSmartScenes,
  activateSmartScene,
  getSmartHomeConfig,
  updateSmartHomeConfig,
  syncSmartHomeEntities,
  getSmartHomeEvents,
  sendSmartHomeCommand,
  type SmartDevice,
  type SmartScene,
  type SmartHomeConfig,
  type SmartHomeStatus,
  type SmartHomeEvent,
} from "@/api";
import { playJarvisSound } from "@/utils/audioEffects";

interface SmartHomePanelProps {
  token: string;
}

export default function SmartHomePanel({ token }: SmartHomePanelProps) {
  const [status, setStatus] = useState<SmartHomeStatus | null>(null);
  const [devices, setDevices] = useState<SmartDevice[]>([]);
  const [scenes, setScenes] = useState<SmartScene[]>([]);
  const [events, setEvents] = useState<SmartHomeEvent[]>([]);
  const [config, setConfig] = useState<SmartHomeConfig | null>(null);

  const [loading, setLoading] = useState(true);
  const [actionBusy, setActionBusy] = useState<string | null>(null);
  const [activeSceneId, setActiveSceneId] = useState<string | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);

  // Command prompt testing
  const [commandText, setCommandText] = useState("");
  const [commandBusy, setCommandBusy] = useState(false);
  const [commandResponse, setCommandResponse] = useState<string | null>(null);

  // Filter / Category tab
  const [activeCategory, setActiveCategory] = useState<"ALL" | "LIGHTS" | "SWITCHES" | "CLIMATE">("ALL");

  useEffect(() => {
    loadAll();
    const interval = setInterval(() => {
      refreshDataSilently();
    }, 6000);
    return () => clearInterval(interval);
  }, [token]);

  const loadAll = async () => {
    try {
      setLoading(true);
      const [st, devs, scns, evts, cfg] = await Promise.all([
        getSmartHomeStatus(token).catch(() => null),
        listSmartDevices(token).catch(() => []),
        listSmartScenes(token).catch(() => []),
        getSmartHomeEvents(token).catch(() => []),
        getSmartHomeConfig(token).catch(() => null),
      ]);
      setStatus(st);
      setDevices(devs);
      setScenes(scns);
      setEvents(evts);
      setConfig(cfg);
    } catch (e) {
      console.error("Failed to load smart home state:", e);
    } finally {
      setLoading(false);
    }
  };

  const refreshDataSilently = async () => {
    try {
      const [st, devs, evts] = await Promise.all([
        getSmartHomeStatus(token).catch(() => null),
        listSmartDevices(token).catch(() => []),
        getSmartHomeEvents(token).catch(() => []),
      ]);
      if (st) setStatus(st);
      if (devs.length) setDevices(devs);
      if (evts.length) setEvents(evts);
    } catch {}
  };

  const handleToggleDevice = async (device: SmartDevice) => {
    try {
      playJarvisSound("action");
      setActionBusy(device.entityId);
      const newAction = device.state === "on" ? "turn_off" : "turn_on";

      // Optimistic update
      setDevices((prev) =>
        prev.map((d) =>
          d.entityId === device.entityId
            ? {
                ...d,
                state: newAction === "turn_on" ? "on" : "off",
                attributes: {
                  ...d.attributes,
                  power_watts: newAction === "turn_on" ? 42.5 : 0.0,
                },
              }
            : d
        )
      );

      const updated = await executeSmartDeviceAction(device.entityId, newAction, {}, token);
      setDevices((prev) => prev.map((d) => (d.entityId === updated.entityId ? updated : d)));
      playJarvisSound("confirm");
      refreshDataSilently();
    } catch (e) {
      console.error("Failed to toggle device:", e);
      loadAll();
    } finally {
      setActionBusy(null);
    }
  };

  const handleBrightnessChange = async (device: SmartDevice, value: number) => {
    try {
      // Optimistic
      setDevices((prev) =>
        prev.map((d) =>
          d.entityId === device.entityId
            ? {
                ...d,
                state: value > 0 ? "on" : "off",
                attributes: { ...d.attributes, brightness: value },
              }
            : d
        )
      );

      const updated = await executeSmartDeviceAction(
        device.entityId,
        "set_brightness",
        { brightness: value },
        token
      );
      setDevices((prev) => prev.map((d) => (d.entityId === updated.entityId ? updated : d)));
    } catch (e) {
      console.error("Failed to set brightness:", e);
    }
  };

  const handleColorChange = async (device: SmartDevice, colorHex: string) => {
    try {
      playJarvisSound("action");
      setDevices((prev) =>
        prev.map((d) =>
          d.entityId === device.entityId
            ? {
                ...d,
                state: "on",
                attributes: { ...d.attributes, color: colorHex },
              }
            : d
        )
      );
      const updated = await executeSmartDeviceAction(
        device.entityId,
        "set_color",
        { color: colorHex },
        token
      );
      setDevices((prev) => prev.map((d) => (d.entityId === updated.entityId ? updated : d)));
    } catch (e) {
      console.error("Failed to change color:", e);
    }
  };

  const handleSceneTrigger = async (scene: SmartScene) => {
    try {
      playJarvisSound("activate");
      setActiveSceneId(scene.id);
      setActionBusy(scene.id);

      await activateSmartScene(scene.id, token);
      playJarvisSound("confirm");
      await loadAll();
    } catch (e) {
      console.error("Failed to activate scene:", e);
    } finally {
      setActionBusy(null);
      setTimeout(() => setActiveSceneId(null), 1500);
    }
  };

  const handleRunCommand = async (cmdToRun?: string) => {
    const text = (cmdToRun || commandText).trim();
    if (!text) return;
    try {
      playJarvisSound("action");
      setCommandBusy(true);
      setCommandResponse(null);

      const isTamil = /[\u0B80-\u0BFF]/.test(text);
      const res = await sendSmartHomeCommand(text, isTamil, token);

      if (res.matched && res.response) {
        setCommandResponse(res.response);
        playJarvisSound("confirm");
        loadAll();
      } else {
        setCommandResponse(res.message || "Command not recognized as a smart home action.");
      }
    } catch (e: any) {
      setCommandResponse(`Error executing command: ${e.message}`);
    } finally {
      setCommandBusy(false);
    }
  };

  const handleSaveConfig = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!config) return;
    try {
      playJarvisSound("action");
      const updated = await updateSmartHomeConfig(config, token);
      setConfig(updated);
      setSettingsOpen(false);
      playJarvisSound("confirm");
      loadAll();
    } catch (e) {
      console.error("Failed to save config:", e);
    }
  };

  const handleSyncEntities = async () => {
    try {
      playJarvisSound("action");
      const res = await syncSmartHomeEntities(token);
      alert(`Synchronized ${res.syncedCount} entities from Home Assistant.`);
      loadAll();
    } catch (e: any) {
      alert(`Sync failed: ${e.message}`);
    }
  };

  // Filtered devices
  const filteredDevices = devices.filter((d) => {
    if (activeCategory === "LIGHTS") return d.type === "LIGHT";
    if (activeCategory === "SWITCHES") return d.type === "SWITCH";
    if (activeCategory === "CLIMATE") return d.type === "CLIMATE" || d.type === "SENSOR";
    return true;
  });

  const totalWatts = devices
    .filter((d) => d.state === "on" && d.attributes?.power_watts)
    .reduce((sum, d) => sum + Number(d.attributes.power_watts || 0), 0);

  const getSceneIcon = (iconName: string) => {
    switch (iconName) {
      case "film":
        return <Tv size={20} className="text-blue-400" />;
      case "briefcase":
        return <Briefcase size={20} className="text-emerald-400" />;
      case "moon":
        return <Moon size={20} className="text-indigo-400" />;
      case "coffee":
        return <Coffee size={20} className="text-amber-400" />;
      case "gamepad-2":
        return <Gamepad2 size={20} className="text-purple-400" />;
      default:
        return <Sliders size={20} className="text-cyan-400" />;
    }
  };

  return (
    <div className="flex-1 overflow-y-auto bg-[#080d14] text-slate-100 p-6 space-y-6">
      {/* 1. Header HUD */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-cyan-900/30 pb-5">
        <div>
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-xl bg-cyan-950/60 border border-cyan-500/30 text-cyan-400 shadow-[0_0_15px_rgba(6,182,212,0.15)]">
              <Home size={24} />
            </div>
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
                SMART HOME &amp; IoT GATEWAY
                <span className="text-xs px-2.5 py-0.5 rounded-full bg-cyan-900/40 border border-cyan-500/40 text-cyan-300 font-mono">
                  v2.0
                </span>
              </h1>
              <p className="text-xs text-slate-400 font-mono">
                HOME ASSISTANT REST/WS · LOCAL MQTT BROKER · LOW-LATENCY EMBEDDED SANDBOX
              </p>
            </div>
          </div>
        </div>

        {/* Telemetry Chips & Action Buttons */}
        <div className="flex flex-wrap items-center gap-2.5">
          <div className="px-3 py-1.5 rounded-lg bg-slate-900/80 border border-slate-700/60 flex items-center gap-2 text-xs font-mono">
            <span
              className={`w-2 h-2 rounded-full ${
                status?.simulationMode ? "bg-amber-400 animate-pulse" : "bg-emerald-400"
              }`}
            />
            <span className="text-slate-300">
              MODE:{" "}
              <strong className={status?.simulationMode ? "text-amber-300" : "text-emerald-300"}>
                {status?.simulationMode ? "LOCAL SANDBOX" : "LIVE NETWORK"}
              </strong>
            </span>
          </div>

          <div className="px-3 py-1.5 rounded-lg bg-slate-900/80 border border-slate-700/60 flex items-center gap-2 text-xs font-mono">
            {status?.homeAssistantConnected ? (
              <Wifi size={14} className="text-emerald-400" />
            ) : (
              <WifiOff size={14} className="text-slate-500" />
            )}
            <span className="text-slate-300">
              HA:{" "}
              <strong className={status?.homeAssistantConnected ? "text-emerald-300" : "text-slate-400"}>
                {status?.homeAssistantConnected ? "CONNECTED" : "STANDBY"}
              </strong>
            </span>
          </div>

          <div className="px-3 py-1.5 rounded-lg bg-slate-900/80 border border-slate-700/60 flex items-center gap-2 text-xs font-mono">
            <Radio
              size={14}
              className={status?.mqttConnected ? "text-cyan-400" : "text-slate-500"}
            />
            <span className="text-slate-300">
              MQTT:{" "}
              <strong className={status?.mqttConnected ? "text-cyan-300" : "text-slate-400"}>
                {status?.mqttConnected ? "ONLINE (1883)" : "SIMULATED"}
              </strong>
            </span>
          </div>

          <div className="px-3 py-1.5 rounded-lg bg-slate-900/80 border border-amber-500/30 flex items-center gap-2 text-xs font-mono text-amber-300">
            <Zap size={14} className="text-amber-400" />
            <span>
              LOAD: <strong>{totalWatts.toFixed(1)} W</strong>
            </span>
          </div>

          <button
            onClick={() => loadAll()}
            disabled={loading}
            className="p-2 rounded-lg bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-300 hover:text-white transition-colors"
            title="Refresh All Devices"
          >
            <RefreshCw size={16} className={loading ? "animate-spin text-cyan-400" : ""} />
          </button>

          <button
            onClick={() => setSettingsOpen(true)}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-cyan-950/60 hover:bg-cyan-900/70 border border-cyan-500/40 text-cyan-300 hover:text-cyan-200 text-xs font-mono transition-colors"
          >
            <Settings size={14} /> Integrations
          </button>
        </div>
      </div>

      {/* 2. Interactive Voice & Natural Language Command Console */}
      <div className="p-4 rounded-xl bg-slate-900/70 border border-cyan-900/40 shadow-lg space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-xs font-mono text-cyan-400 font-semibold uppercase tracking-wider">
            <Volume2 size={15} /> Natural Speech &amp; Voice Commands (Dual-Language English &amp; தமிழ்)
          </div>
          <span className="text-[11px] text-slate-400 font-mono">Try speaking or click any pre-calibrated chip</span>
        </div>

        {/* Suggestion Chips */}
        <div className="flex flex-wrap gap-2">
          {[
            "Jarvis, set room lighting to Movie mode",
            "Jarvis, turn off the desk power strip",
            "Jarvis, turn on desk power strip",
            "Jarvis, dim room lighting to 20%",
            "Jarvis, what is the smart home status?",
            "அறை விளக்குகளை மூவி மோடுக்கு மாற்று",
            "டெஸ்க் பவர் ஸ்ட்ரிப்பை ஆஃப் செய்",
            "டெஸ்க் பவர் ஸ்ட்ரிப்பை ஆன் செய்",
          ].map((chip) => (
            <button
              key={chip}
              onClick={() => {
                setCommandText(chip);
                handleRunCommand(chip);
              }}
              className="text-xs px-2.5 py-1 rounded-md bg-slate-800/90 hover:bg-cyan-950/70 border border-slate-700/70 hover:border-cyan-500/50 text-slate-300 hover:text-cyan-300 font-mono transition-all text-left"
            >
              💬 {chip}
            </button>
          ))}
        </div>

        {/* Command Input Bar */}
        <div className="flex gap-2">
          <input
            type="text"
            value={commandText}
            onChange={(e) => setCommandText(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleRunCommand()}
            placeholder='e.g., "Jarvis, set room lighting to Movie mode" or "டெஸ்க் பவர் ஸ்ட்ரிப்பை ஆஃப் செய்"...'
            className="flex-1 bg-slate-950/80 border border-slate-700 focus:border-cyan-500 rounded-lg px-3.5 py-2 text-sm text-white focus:outline-none font-mono"
          />
          <button
            onClick={() => handleRunCommand()}
            disabled={commandBusy || !commandText.trim()}
            className="flex items-center gap-1.5 px-4 py-2 rounded-lg bg-cyan-600 hover:bg-cyan-500 disabled:opacity-50 text-white font-mono text-xs font-semibold transition-all shadow-[0_0_12px_rgba(6,182,212,0.3)]"
          >
            {commandBusy ? (
              <RefreshCw size={14} className="animate-spin" />
            ) : (
              <Send size={14} />
            )}
            Execute
          </button>
        </div>

        {/* Verbal Output Card */}
        {commandResponse && (
          <div className="p-3.5 rounded-lg bg-slate-950/90 border border-cyan-800/50 text-xs font-mono space-y-2 animate-in fade-in duration-200">
            <div className="flex items-center justify-between text-cyan-400 font-semibold">
              <span>JARVIS VERBAL OUTPUT &amp; LOGIC</span>
              <button
                onClick={() => setCommandResponse(null)}
                className="text-slate-400 hover:text-white"
              >
                <X size={14} />
              </button>
            </div>
            <div className="whitespace-pre-wrap leading-relaxed text-slate-200 bg-slate-900/60 p-3 rounded border border-slate-800">
              {commandResponse}
            </div>
          </div>
        )}
      </div>

      {/* 3. Quick Scene Macro Automation Buttons */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-xs font-mono font-bold text-slate-300 uppercase tracking-wider flex items-center gap-2">
            <Tv size={15} className="text-cyan-400" /> Multi-Device Smart Scenes &amp; Macros
          </h2>
          <span className="text-[11px] text-slate-400 font-mono">
            Instant simultaneous device orchestration
          </span>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-3">
          {scenes.map((scene) => {
            const isActive = activeSceneId === scene.id;
            return (
              <button
                key={scene.id}
                onClick={() => handleSceneTrigger(scene)}
                disabled={actionBusy === scene.id}
                className={`p-3.5 rounded-xl border transition-all text-left flex flex-col justify-between group relative overflow-hidden ${
                  isActive
                    ? "bg-cyan-950/80 border-cyan-400 shadow-[0_0_20px_rgba(6,182,212,0.3)] scale-[1.02]"
                    : "bg-slate-900/80 hover:bg-slate-850 border-slate-800 hover:border-cyan-500/40"
                }`}
              >
                <div className="flex items-center justify-between mb-2">
                  <div className="p-2 rounded-lg bg-slate-950/80 border border-slate-800 group-hover:border-cyan-500/40">
                    {getSceneIcon(scene.icon)}
                  </div>
                  {actionBusy === scene.id && (
                    <RefreshCw size={14} className="animate-spin text-cyan-400" />
                  )}
                </div>

                <div>
                  <div className="font-bold text-sm text-white group-hover:text-cyan-300 flex items-center gap-1.5">
                    {scene.name}
                  </div>
                  <div className="text-[11px] text-slate-400 line-clamp-2 mt-1 leading-snug">
                    {scene.description}
                  </div>
                </div>

                <div className="mt-3 pt-2 border-t border-slate-800/80 flex items-center justify-between text-[10px] font-mono text-cyan-400/80">
                  <span>{scene.actions.length} ACTIONS</span>
                  <span className="opacity-0 group-hover:opacity-100 transition-opacity">
                    ACTIVATE ↗
                  </span>
                </div>
              </button>
            );
          })}
        </div>
      </div>

      {/* 4. Category Tabs */}
      <div className="flex items-center gap-2 border-b border-slate-800 pb-2 text-xs font-mono">
        {[
          { id: "ALL", label: `ALL DEVICES (${devices.length})` },
          { id: "LIGHTS", label: `LIGHTS (${devices.filter((d) => d.type === "LIGHT").length})` },
          { id: "SWITCHES", label: `SWITCHES & STRIPS (${devices.filter((d) => d.type === "SWITCH").length})` },
          { id: "CLIMATE", label: `CLIMATE & SENSORS (${devices.filter((d) => d.type === "CLIMATE" || d.type === "SENSOR").length})` },
        ].map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveCategory(tab.id as any)}
            className={`px-3 py-1.5 rounded-lg transition-colors ${
              activeCategory === tab.id
                ? "bg-cyan-500/20 text-cyan-300 border border-cyan-500/40"
                : "text-slate-400 hover:text-slate-200 hover:bg-slate-900"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* 5. Device Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {filteredDevices.map((device) => {
          const isOn = device.state === "on" || device.state === "cool";
          const isBusy = actionBusy === device.entityId;

          return (
            <div
              key={device.entityId}
              className={`p-4 rounded-xl border transition-all flex flex-col justify-between ${
                isOn
                  ? "bg-slate-900/90 border-cyan-500/40 shadow-[0_0_15px_rgba(6,182,212,0.08)]"
                  : "bg-slate-900/50 border-slate-800 opacity-90"
              }`}
            >
              {/* Device Header */}
              <div>
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2.5">
                    <div
                      className={`p-2.5 rounded-xl border ${
                        isOn
                          ? "bg-cyan-500/20 border-cyan-400/50 text-cyan-300 shadow-[0_0_12px_rgba(6,182,212,0.2)]"
                          : "bg-slate-950 border-slate-800 text-slate-500"
                      }`}
                    >
                      {device.type === "LIGHT" ? (
                        <Lightbulb size={20} />
                      ) : device.type === "SWITCH" ? (
                        <Zap size={20} />
                      ) : device.type === "CLIMATE" ? (
                        <Thermometer size={20} />
                      ) : (
                        <Activity size={20} />
                      )}
                    </div>
                    <div>
                      <div className="font-bold text-white text-sm flex items-center gap-2">
                        {device.name}
                        <span className="text-[10px] px-1.5 py-0.2 rounded bg-slate-800 border border-slate-700 text-slate-400 font-mono">
                          {device.room}
                        </span>
                      </div>
                      <div className="text-[11px] font-mono text-slate-400">
                        {device.entityId}
                      </div>
                    </div>
                  </div>

                  {/* Toggle Button */}
                  {device.type !== "SENSOR" && (
                    <button
                      onClick={() => handleToggleDevice(device)}
                      disabled={isBusy}
                      className={`p-2.5 rounded-xl border transition-all ${
                        isOn
                          ? "bg-emerald-500/20 border-emerald-500/60 text-emerald-300 shadow-[0_0_12px_rgba(16,185,129,0.3)] hover:bg-emerald-500/30"
                          : "bg-slate-950 border-slate-700 text-slate-400 hover:text-white hover:bg-slate-800"
                      }`}
                      title={isOn ? "Power OFF" : "Power ON"}
                    >
                      {isBusy ? (
                        <RefreshCw size={16} className="animate-spin text-cyan-400" />
                      ) : (
                        <Power size={16} />
                      )}
                    </button>
                  )}
                </div>

                {/* Specific Device Controls */}
                {/* A. Light: Brightness & Color Chips */}
                {device.type === "LIGHT" && (
                  <div className="space-y-3 pt-2 border-t border-slate-800/80">
                    <div className="space-y-1">
                      <div className="flex justify-between text-xs font-mono text-slate-300">
                        <span>BRIGHTNESS</span>
                        <span className="text-cyan-400 font-bold">
                          {device.attributes?.brightness || 0}%
                        </span>
                      </div>
                      <input
                        type="range"
                        min="0"
                        max="100"
                        value={device.attributes?.brightness || 0}
                        onChange={(e) =>
                          handleBrightnessChange(device, parseInt(e.target.value))
                        }
                        className="w-full accent-cyan-400 h-1.5 bg-slate-950 rounded-lg cursor-pointer"
                      />
                    </div>

                    {/* Quick Color Presets */}
                    <div className="flex items-center gap-2 pt-1">
                      <span className="text-[10px] font-mono text-slate-400">PRESET:</span>
                      {[
                        { name: "Indigo", hex: "#3b82f6" },
                        { name: "Amber", hex: "#f59e0b" },
                        { name: "Violet", hex: "#a855f7" },
                        { name: "White", hex: "#f8fafc" },
                        { name: "Warm", hex: "#ffe8b8" },
                      ].map((c) => (
                        <button
                          key={c.hex}
                          onClick={() => handleColorChange(device, c.hex)}
                          style={{ backgroundColor: c.hex }}
                          className={`w-5 h-5 rounded-full border border-slate-600 hover:scale-110 transition-transform ${
                            device.attributes?.color === c.hex ? "ring-2 ring-cyan-400" : ""
                          }`}
                          title={c.name}
                        />
                      ))}
                    </div>
                  </div>
                )}

                {/* B. Switch: Power Watts & Outlets */}
                {device.type === "SWITCH" && (
                  <div className="space-y-2.5 pt-2 border-t border-slate-800/80">
                    <div className="flex items-center justify-between text-xs font-mono">
                      <span className="text-slate-400">CURRENT DRAW:</span>
                      <span className="text-amber-400 font-bold flex items-center gap-1">
                        <Zap size={13} />
                        {Number(device.attributes?.power_watts || 0).toFixed(1)} W
                      </span>
                    </div>

                    <div className="flex flex-wrap gap-1.5 pt-1">
                      {["MacBook/PC", "Monitor 1", "Monitor 2", "Phone Charger"].map(
                        (outlet, idx) => (
                          <span
                            key={outlet}
                            className={`text-[10px] font-mono px-2 py-0.5 rounded border ${
                              isOn
                                ? "bg-cyan-950/40 border-cyan-500/30 text-cyan-300"
                                : "bg-slate-950 border-slate-800 text-slate-500"
                            }`}
                          >
                            OUTLET {idx + 1}: {outlet}
                          </span>
                        )
                      )}
                    </div>
                  </div>
                )}

                {/* C. Climate & Thermostat */}
                {device.type === "CLIMATE" && (
                  <div className="space-y-2.5 pt-2 border-t border-slate-800/80">
                    <div className="flex items-center justify-between text-xs font-mono">
                      <span className="text-slate-400">ROOM TEMPERATURE:</span>
                      <span className="text-emerald-400 font-bold">
                        {device.attributes?.current_temperature || 24.2}°C
                      </span>
                    </div>

                    <div className="flex items-center justify-between text-xs font-mono">
                      <span className="text-slate-400">TARGET TEMP:</span>
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => {
                            const cur = Number(device.attributes?.target_temperature || 22.0);
                            executeSmartDeviceAction(
                              device.entityId,
                              "set_temperature",
                              { temperature: cur - 0.5 },
                              token
                            ).then(() => loadAll());
                          }}
                          className="px-2 py-0.5 rounded bg-slate-800 border border-slate-700 hover:bg-slate-700 text-white font-bold"
                        >
                          -
                        </button>
                        <span className="text-cyan-400 font-bold">
                          {device.attributes?.target_temperature || 22.0}°C
                        </span>
                        <button
                          onClick={() => {
                            const cur = Number(device.attributes?.target_temperature || 22.0);
                            executeSmartDeviceAction(
                              device.entityId,
                              "set_temperature",
                              { temperature: cur + 0.5 },
                              token
                            ).then(() => loadAll());
                          }}
                          className="px-2 py-0.5 rounded bg-slate-800 border border-slate-700 hover:bg-slate-700 text-white font-bold"
                        >
                          +
                        </button>
                      </div>
                    </div>
                  </div>
                )}

                {/* D. Sensor Telemetry */}
                {device.type === "SENSOR" && (
                  <div className="space-y-1.5 pt-2 border-t border-slate-800/80 text-xs font-mono">
                    <div className="flex justify-between">
                      <span className="text-slate-400">STATE:</span>
                      <span className="text-emerald-400 font-bold">{device.state}</span>
                    </div>
                    {device.attributes?.humidity && (
                      <div className="flex justify-between">
                        <span className="text-slate-400">HUMIDITY:</span>
                        <span className="text-cyan-400 font-bold">
                          {device.attributes.humidity}%
                        </span>
                      </div>
                    )}
                    {device.attributes?.battery_pct && (
                      <div className="flex justify-between">
                        <span className="text-slate-400">BATTERY:</span>
                        <span className="text-slate-300 font-bold">
                          {device.attributes.battery_pct}%
                        </span>
                      </div>
                    )}
                  </div>
                )}
              </div>

              {/* Card Footer */}
              <div className="mt-3 pt-2.5 border-t border-slate-800/60 flex items-center justify-between text-[10px] font-mono text-slate-500">
                <span>PROTOCOL: {device.protocol}</span>
                <span className="flex items-center gap-1">
                  <span
                    className={`w-1.5 h-1.5 rounded-full ${
                      isOn ? "bg-emerald-400 animate-ping" : "bg-slate-600"
                    }`}
                  />
                  {device.state.toUpperCase()}
                </span>
              </div>
            </div>
          );
        })}
      </div>

      {/* 6. Live Activity & Audit Event Log */}
      <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-xs font-mono font-bold text-slate-300 uppercase tracking-wider">
            <Activity size={15} className="text-cyan-400" /> Recent Smart Home Telemetry &amp; Action Log
          </div>
          <span className="text-[11px] text-slate-500 font-mono">
            {events.length} events logged
          </span>
        </div>

        <div className="max-h-52 overflow-y-auto space-y-1.5 pr-1 font-mono text-xs">
          {events.length === 0 ? (
            <div className="text-slate-500 text-center py-4">No recent smart home actions logged.</div>
          ) : (
            events.slice(0, 15).map((ev) => (
              <div
                key={ev.id}
                className="p-2 rounded bg-slate-950/70 border border-slate-800/70 flex items-center justify-between gap-3"
              >
                <div className="flex items-center gap-2 overflow-hidden">
                  <CheckCircle2 size={13} className="text-emerald-400 shrink-0" />
                  <span className="text-cyan-400 font-semibold shrink-0">[{ev.source}]</span>
                  <span className="text-slate-200 font-semibold shrink-0">{ev.action}</span>
                  <span className="text-slate-400 truncate">{ev.message}</span>
                </div>
                <div className="text-[10px] text-slate-500 shrink-0">
                  {new Date(ev.timestamp).toLocaleTimeString()}
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      {/* 7. Settings & Integrations Modal */}
      {settingsOpen && config && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-cyan-800/60 rounded-2xl w-full max-w-xl p-6 shadow-2xl space-y-5 animate-in fade-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <h2 className="text-lg font-bold text-white flex items-center gap-2 font-mono">
                <Settings size={18} className="text-cyan-400" /> SMART HOME INTEGRATIONS
              </h2>
              <button
                onClick={() => setSettingsOpen(false)}
                className="text-slate-400 hover:text-white"
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSaveConfig} className="space-y-4 font-mono text-xs">
              {/* Simulation Mode Toggle */}
              <div className="p-3 rounded-xl bg-slate-950 border border-slate-800 flex items-center justify-between">
                <div>
                  <div className="font-bold text-white">Local Simulation Sandbox</div>
                  <div className="text-slate-400 text-[11px]">
                    Simulate virtual IoT devices without physical hardware
                  </div>
                </div>
                <input
                  type="checkbox"
                  checked={config.simulationMode}
                  onChange={(e) =>
                    setConfig({ ...config, simulationMode: e.target.checked })
                  }
                  className="w-5 h-5 accent-cyan-500 cursor-pointer"
                />
              </div>

              {/* Home Assistant Config */}
              <div className="p-3.5 rounded-xl bg-slate-950/60 border border-slate-800 space-y-3">
                <div className="flex items-center justify-between">
                  <div className="font-bold text-cyan-300 flex items-center gap-1.5">
                    <Wifi size={14} /> Home Assistant REST/WebSocket API
                  </div>
                  <label className="flex items-center gap-1.5 cursor-pointer text-slate-300">
                    <input
                      type="checkbox"
                      checked={config.haEnabled}
                      onChange={(e) => setConfig({ ...config, haEnabled: e.target.checked })}
                      className="accent-cyan-500"
                    />
                    Enable
                  </label>
                </div>

                <div className="space-y-1">
                  <label className="text-slate-400">Server URL:</label>
                  <input
                    type="text"
                    value={config.haBaseUrl}
                    onChange={(e) => setConfig({ ...config, haBaseUrl: e.target.value })}
                    placeholder="http://homeassistant.local:8123"
                    className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-1.5 text-white"
                  />
                </div>

                <div className="space-y-1">
                  <label className="text-slate-400">Long-Lived Access Token:</label>
                  <input
                    type="password"
                    value={config.haToken}
                    onChange={(e) => setConfig({ ...config, haToken: e.target.value })}
                    placeholder="eyJhbGciOiJIUzI1NiIsIn..."
                    className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-1.5 text-white"
                  />
                </div>

                <button
                  type="button"
                  onClick={handleSyncEntities}
                  className="px-3 py-1.5 rounded bg-slate-800 hover:bg-slate-700 border border-slate-700 text-cyan-300 text-xs flex items-center gap-1.5"
                >
                  <RefreshCw size={12} /> Sync Home Assistant Entities
                </button>
              </div>

              {/* MQTT Broker Config */}
              <div className="p-3.5 rounded-xl bg-slate-950/60 border border-slate-800 space-y-3">
                <div className="flex items-center justify-between">
                  <div className="font-bold text-cyan-300 flex items-center gap-1.5">
                    <Radio size={14} /> Local MQTT Broker (Mosquitto/EMQX)
                  </div>
                  <label className="flex items-center gap-1.5 cursor-pointer text-slate-300">
                    <input
                      type="checkbox"
                      checked={config.mqttEnabled}
                      onChange={(e) =>
                        setConfig({ ...config, mqttEnabled: e.target.checked })
                      }
                      className="accent-cyan-500"
                    />
                    Enable
                  </label>
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <div className="space-y-1">
                    <label className="text-slate-400">Broker URL:</label>
                    <input
                      type="text"
                      value={config.mqttBrokerUrl}
                      onChange={(e) =>
                        setConfig({ ...config, mqttBrokerUrl: e.target.value })
                      }
                      placeholder="tcp://localhost:1883"
                      className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-1.5 text-white"
                    />
                  </div>
                  <div className="space-y-1">
                    <label className="text-slate-400">Client ID:</label>
                    <input
                      type="text"
                      value={config.mqttClientId}
                      onChange={(e) =>
                        setConfig({ ...config, mqttClientId: e.target.value })
                      }
                      className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-1.5 text-white"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <div className="space-y-1">
                    <label className="text-slate-400">Username (optional):</label>
                    <input
                      type="text"
                      value={config.mqttUsername}
                      onChange={(e) =>
                        setConfig({ ...config, mqttUsername: e.target.value })
                      }
                      className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-1.5 text-white"
                    />
                  </div>
                  <div className="space-y-1">
                    <label className="text-slate-400">Password (optional):</label>
                    <input
                      type="password"
                      value={config.mqttPassword || ""}
                      onChange={(e) =>
                        setConfig({ ...config, mqttPassword: e.target.value })
                      }
                      className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-1.5 text-white"
                    />
                  </div>
                </div>
              </div>

              {/* Actions */}
              <div className="flex justify-end gap-2.5 pt-2">
                <button
                  type="button"
                  onClick={() => setSettingsOpen(false)}
                  className="px-4 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white font-bold"
                >
                  Save Integrations
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

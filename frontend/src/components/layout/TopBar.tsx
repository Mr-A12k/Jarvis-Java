import { memo } from "react";
import { ChevronRight, History, Menu, Sparkles, Radio } from "lucide-react";
import type { ActiveView } from "@/types/navigation";

interface TopBarProps {
  activeView: ActiveView;
  connected: boolean;
  ready: boolean;
  busy: boolean;
  voiceBusy: boolean;
  wakeWordActive?: boolean;
  onToggleWakeWord?: () => void;
  mobileNav: boolean;
  onToggleMobileNav: () => void;
  onOpenHud: () => void;
  onOpenSavedChats: () => void;
}

const VIEW_TITLES: Record<ActiveView, string> = {
  assistant: "Assistant",
  voice: "Voice & AI Personas",
  database_memory: "Database memory",
  files: "Files & memory",
  shopping: "Shopping Assistant",
  automation: "Browser automation",
  scheduler: "Task scheduler",
  settings: "Model & privacy",
  saved_chats: "Saved chats",
  biometrics: "Biometrics (Face & Voice ID)",
  goals: "Autonomous Goal Planner",
  desktop: "Desktop control",
  smarthome: "Smart Home",
  coding: "Coding Agent Studio",
  graph: "Neural Knowledge Graph",
};

const TopBar = memo(function TopBar({
  activeView,
  connected,
  ready,
  busy,
  voiceBusy,
  wakeWordActive = false,
  onToggleWakeWord,
  mobileNav,
  onToggleMobileNav,
  onOpenHud,
  onOpenSavedChats,
}: TopBarProps) {
  const currentTitle = VIEW_TITLES[activeView] || "Assistant";

  return (
    <header className="topbar">
      <div className="breadcrumb">
        <button
          className="icon-button mobile-menu"
          aria-label="Toggle navigation"
          aria-expanded={mobileNav}
          onClick={onToggleMobileNav}
        >
          <Menu size={18} />
        </button>
        <span>Workspace</span>
        <ChevronRight size={13} />
        <strong>{currentTitle}</strong>
      </div>
      <div className="topbar-actions">
        {connected && onToggleWakeWord && (
          <button
            className={`text-button wake-word-topbar-btn ${wakeWordActive ? "wake-active" : ""}`}
            onClick={onToggleWakeWord}
            title={
              wakeWordActive
                ? "Hands-Free Wake Word is ACTIVE ('Hey Jarvis') - Click to disable"
                : "Enable Hands-Free Wake Word ('Hey Jarvis')"
            }
          >
            <Radio size={14} className={wakeWordActive ? "wake-pulse-icon" : ""} />
            <span>{wakeWordActive ? "Wake-Word: ACTIVE" : "Wake-Word: OFF"}</span>
          </button>
        )}
        {connected && (
          <button
            className="text-button iron-man-btn"
            onClick={onOpenHud}
            title="Open Iron Man J.A.R.V.I.S. 3D Holographic HUD"
          >
            <Sparkles size={14} /> Iron Man HUD
          </button>
        )}
        {connected && (
          <button
            className="text-button"
            onClick={onOpenSavedChats}
            disabled={busy || voiceBusy}
            title="Saved chats"
          >
            <History size={14} /> Saved chats
          </button>
        )}
        <div className="connection-label">
          <span className={`status-dot ${ready ? "ready" : ""}`} />
          {connected
            ? ready
              ? "Model ready"
              : "Model unavailable"
            : "Not connected"}
        </div>
      </div>
    </header>
  );
});

export default TopBar;

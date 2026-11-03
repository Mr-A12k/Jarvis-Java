import { memo } from "react";
import {
  Bookmark,
  CalendarClock,
  Code2,
  Database,
  Globe,
  History,
  Home,
  Laptop,
  LogOut,
  MessageSquare,
  Plus,
  ScanFace,
  Share2,
  ShieldAlert,
  ShieldCheck,
  ShoppingBag,
  SlidersHorizontal,
  Target,
  Volume2,
} from "lucide-react";
import type { ActiveView } from "@/types/navigation";

interface SidebarProps {
  activeView: ActiveView;
  onSelectView: (view: ActiveView) => void;
  onNewChat: () => void;
  connected: boolean;
  busy: boolean;
  voiceBusy: boolean;
  mobileNav: boolean;
  onOpenSavedChats: () => void;
  pendingChallengeCount?: number;
  onOpenApproval?: () => void;
  localSession: boolean;
  onDisconnect: () => void;
}

const Sidebar = memo(function Sidebar({
  activeView,
  onSelectView,
  onNewChat,
  connected,
  busy,
  voiceBusy,
  mobileNav,
  onOpenSavedChats,
  pendingChallengeCount = 0,
  onOpenApproval,
  localSession,
  onDisconnect,
}: SidebarProps) {
  const disabledActions = !connected || busy || voiceBusy;

  return (
    <aside
      className={`sidebar ${mobileNav ? "open" : ""}`}
      aria-label="Main navigation"
    >
      <a className="brand" href="/" aria-label="JARVIS home">
        <span className="brand-mark">J</span>
        <span>
          JARVIS<small>PERSONAL WORKSPACE</small>
        </span>
      </a>

      <button
        className="new-chat"
        disabled={disabledActions}
        onClick={onNewChat}
      >
        <Plus size={17} /> New conversation <span>↗</span>
      </button>

      <div className="nav-caption">WORKSPACE</div>

      <button
        className={`nav-item ${activeView === "assistant" ? "selected" : ""}`}
        onClick={() => onSelectView("assistant")}
      >
        <MessageSquare size={17} /> Assistant{" "}
        {activeView === "assistant" && <span className="nav-dot" />}
      </button>

      <button
        className={`nav-item ${activeView === "voice" ? "selected" : ""}`}
        disabled={disabledActions}
        onClick={() => onSelectView("voice")}
      >
        <Volume2 size={17} /> Voice &amp; Personas{" "}
        {activeView === "voice" && <span className="nav-dot" />}
      </button>

      <button
        className={`nav-item ${activeView === "desktop" ? "selected" : ""}`}
        disabled={disabledActions}
        onClick={() => onSelectView("desktop")}
      >
        <Laptop size={17} /> Desktop tools{" "}
        {activeView === "desktop" && <span className="nav-dot" />}
      </button>

      <button
        className={`nav-item ${activeView === "smarthome" ? "selected" : ""}`}
        disabled={disabledActions}
        onClick={() => onSelectView("smarthome")}
      >
        <Home size={17} /> Smart Home &amp; IoT{" "}
        {activeView === "smarthome" && <span className="nav-dot" />}
      </button>

      <button
        className="nav-item"
        disabled={disabledActions}
        onClick={onOpenSavedChats}
      >
        <History size={17} /> Saved chats
      </button>

      <button
        className={`nav-item ${activeView === "database_memory" ? "selected" : ""}`}
        disabled={disabledActions}
        onClick={() => onSelectView("database_memory")}
      >
        <Database size={17} /> Database memory
      </button>

      <button
        className={`nav-item ${activeView === "files" ? "selected" : ""}`}
        disabled={disabledActions}
        onClick={() => onSelectView("files")}
      >
        <Bookmark size={17} /> Files &amp; memory
      </button>

      <button
        className={`nav-item ${activeView === "shopping" ? "selected" : ""}`}
        disabled={disabledActions}
        onClick={() => onSelectView("shopping")}
      >
        <ShoppingBag size={17} /> Shopping assistant
      </button>

      <button
        className={`nav-item ${activeView === "automation" ? "selected" : ""}`}
        disabled={disabledActions}
        onClick={() => onSelectView("automation")}
      >
        <Globe size={17} /> Browser automation
      </button>

      <button
        className={`nav-item ${activeView === "scheduler" ? "selected" : ""}`}
        disabled={disabledActions}
        onClick={() => onSelectView("scheduler")}
      >
        <CalendarClock size={17} /> Task scheduler
      </button>

      <button
        className={`nav-item ${activeView === "biometrics" ? "selected" : ""}`}
        disabled={disabledActions}
        onClick={() => onSelectView("biometrics")}
      >
        <ScanFace size={17} /> Face &amp; Voice ID
      </button>

      <button
        className={`nav-item ${activeView === "goals" ? "selected" : ""}`}
        disabled={disabledActions}
        onClick={() => onSelectView("goals")}
      >
        <Target size={17} /> Goal planner
      </button>

      <button
        className={`nav-item ${activeView === "coding" ? "selected" : ""}`}
        disabled={disabledActions}
        onClick={() => onSelectView("coding")}
      >
        <Code2 size={17} /> Coding Agent{" "}
        {activeView === "coding" && <span className="nav-dot" />}
      </button>

      <button
        className={`nav-item ${activeView === "graph" ? "selected" : ""}`}
        disabled={disabledActions}
        onClick={() => onSelectView("graph")}
      >
        <Share2 size={17} /> Neural Graph{" "}
        {activeView === "graph" && <span className="nav-dot" />}
      </button>

      {pendingChallengeCount > 0 && (
        <button
          className="nav-item action-needed"
          style={{
            color: "#fbbf24",
            borderColor: "rgba(245, 158, 11, 0.4)",
            background: "rgba(245, 158, 11, 0.12)",
          }}
          onClick={onOpenApproval}
        >
          <ShieldAlert size={17} /> Action approval ({pendingChallengeCount})
        </button>
      )}

      <button
        className={`nav-item ${activeView === "settings" ? "selected" : ""}`}
        onClick={() => onSelectView("settings")}
      >
        <SlidersHorizontal size={17} /> Model &amp; privacy
      </button>

      <div className="sidebar-note">
        <ShieldCheck size={19} />
        <strong>Local by design.</strong>
        <p>
          Answers run locally. Web search sends your current question to
          DuckDuckGo when enabled.
        </p>
      </div>

      <div className="sidebar-bottom">
        <span className="avatar">K</span>
        <div>
          Local workspace
          <small>
            {connected ? "Connected this session" : "Not connected"}
          </small>
        </div>
        {connected && (
          <button
            className="icon-button"
            title={
              localSession ? "Clear session" : "Disconnect and clear session"
            }
            disabled={voiceBusy}
            onClick={onDisconnect}
          >
            <LogOut size={16} />
          </button>
        )}
      </div>
    </aside>
  );
});

export default Sidebar;

import { useEffect, useRef, useState } from "react";
import { saveChat } from "@/chatHistory";
import { X } from "lucide-react";
import {
  approveMcpChallenge,
  getApi,
  getDatabaseStatus,
  listMcpChallenges,
  streamChat,
  streamVisionChat,
} from "@/api";
import type { DatabaseStatus, Diagnostics, Health } from "@/api";
import Sidebar from "@/components/layout/Sidebar";
import TopBar from "@/components/layout/TopBar";
import ConnectionScreen from "@/components/connection/ConnectionScreen";
import SettingsView from "@/components/settings/SettingsView";
import ChatWorkspace from "@/components/chat/ChatWorkspace";
import type { Message } from "@/components/chat/ChatMessage";
import AutomationPanel from "@/panels/AutomationPanel";
import SchedulerPanel from "@/panels/SchedulerPanel";
import FilesPanel from "@/panels/FilesPanel";
import DatabaseMemoriesPanel from "@/panels/DatabaseMemoriesPanel";
import ShoppingPanel from "@/panels/ShoppingPanel";
import BiometricsPanel from "@/panels/BiometricsPanel";
import GoalPlannerPanel from "@/panels/GoalPlannerPanel";
import VoicePanel from "@/panels/VoicePanel";
import DesktopToolsPanel from "@/panels/DesktopToolsPanel";
import SmartHomePanel from "@/panels/SmartHomePanel";
import CodingAgentPanel from "@/panels/CodingAgentPanel";
import NeuralGraphPanel from "@/panels/NeuralGraphPanel";
import { playJarvisSound } from "@/utils/audioEffects";
import ConversationsDrawer from "@/components/modals/ConversationsDrawer";
import McpApprovalModal from "@/components/modals/McpApprovalModal";
import type { McpChallenge } from "@/components/modals/McpApprovalModal";
import type { ActiveView } from "@/types/navigation";

const STARTERS = [
  "Explain a concept",
  "Help me write something",
  "Think through a problem",
];

export default function App() {
  const [token, setToken] = useState("");
  const [localSession, setLocalSession] = useState(false);
  const [checkingSession, setCheckingSession] = useState(true);
  const connected = localSession || !!token;
  const [candidate, setCandidate] = useState("");
  const [connecting, setConnecting] = useState(false);
  const [health, setHealth] = useState<Health | null>(null);
  const [diagnostics, setDiagnostics] = useState<Diagnostics | null>(null);
  const [error, setError] = useState("");
  const [draft, setDraft] = useState("");
  const [messages, setMessages] = useState<Message[]>([]);
  const [busy, setBusy] = useState(false);
  const [voiceBusy, setVoiceBusy] = useState(false);
  const [hudOpen, setHudOpen] = useState(false);
  const [webSearch, setWebSearch] = useState(false);
  const [attachedImage, setAttachedImage] = useState<string | null>(null);
  const [wakeWordActive, setWakeWordActive] = useState<boolean>(() => {
    try {
      return localStorage.getItem("jarvis_wakeword_enabled") === "true";
    } catch {
      return false;
    }
  });

  const toggleWakeWord = () => {
    const next = !wakeWordActive;
    setWakeWordActive(next);
    try {
      localStorage.setItem("jarvis_wakeword_enabled", String(next));
      window.dispatchEvent(new CustomEvent("jarvis_wakeword_toggled", { detail: next }));
    } catch {}
    if (next) {
      playJarvisSound("activate");
    } else {
      playJarvisSound("standby");
    }
  };

  useEffect(() => {
    const handleSync = (e: any) => {
      const next = e.detail !== undefined ? e.detail : localStorage.getItem("jarvis_wakeword_enabled") === "true";
      setWakeWordActive(next);
    };
    window.addEventListener("jarvis_wakeword_toggled", handleSync);
    return () => window.removeEventListener("jarvis_wakeword_toggled", handleSync);
  }, []);

  // Global Native System Tray Wake-Word SSE & URL Trigger
  useEffect(() => {
    // 1. Check for URL wake parameter (?wake=1 or #wake)
    try {
      const urlParams = new URLSearchParams(window.location.search);
      if (urlParams.get("wake") === "1" || window.location.hash.includes("wake")) {
        setHudOpen(true);
        playJarvisSound("activate");
        const cleanUrl = window.location.pathname;
        window.history.replaceState({}, document.title, cleanUrl);
        window.dispatchEvent(new CustomEvent("jarvis_native_wake", { detail: { source: "url" } }));
      }
    } catch {}

    // 2. Connect to Native Tray Wake-Word SSE Stream
    let eventSource: EventSource | null = null;
    try {
      eventSource = new EventSource("/api/v1/voice/wakeword/stream");
      eventSource.addEventListener("wake", (e: MessageEvent) => {
        try {
          const data = JSON.parse(e.data);
          setHudOpen(true);
          playJarvisSound("activate");
          window.dispatchEvent(new CustomEvent("jarvis_native_wake", { detail: data }));
        } catch {}
      });
      eventSource.onerror = () => {
        // Automatically reconnects on retry
      };
    } catch {}

    return () => {
      if (eventSource) {
        eventSource.close();
      }
    };
  }, []);

  // Structured active view state
  const [activeView, setActiveView] = useState<ActiveView>(() => {
    try {
      const hash = window.location.hash.replace("#", "").toLowerCase();
      if (hash === "coding" || hash === "graph") {
        return hash as ActiveView;
      }
    } catch {}
    return "assistant";
  });

  useEffect(() => {
    const handleHash = () => {
      try {
        const hash = window.location.hash.replace("#", "").toLowerCase();
        if (hash === "coding" || hash === "graph") {
          setActiveView(hash as ActiveView);
        }
      } catch {}
    };
    window.addEventListener("hashchange", handleHash);
    return () => window.removeEventListener("hashchange", handleHash);
  }, []);
  const [conversationsDrawerOpen, setConversationsDrawerOpen] = useState(false);
  const [activeConversationId, setActiveConversationId] = useState<string | undefined>(undefined);
  const [dbStatus, setDbStatus] = useState<DatabaseStatus | null>(null);
  const [pendingChallenge, setPendingChallenge] = useState<McpChallenge | null>(null);
  const [mobileNav, setMobileNav] = useState(false);

  const active = useRef<AbortController | null>(null);
  const bottom = useRef<HTMLDivElement>(null);
  const textarea = useRef<HTMLTextAreaElement>(null);

  useEffect(() => () => active.current?.abort(), []);

  useEffect(() => {
    if (!activeConversationId || !messages.length) return;
    try {
      saveChat(activeConversationId, messages);
    } catch {
      setError("Chat history could not be saved. Browser storage may be full or unavailable.");
    }
  }, [activeConversationId, messages]);

  useEffect(() => {
    if (!connected) return;
    const controller = new AbortController();
    const refresh = async () => {
      try {
        const [h, d, db, challenges] = await Promise.all([
          getApi<Health>("health/model", token, controller.signal),
          getApi<Diagnostics>("diagnostics", token, controller.signal),
          getDatabaseStatus(token, controller.signal).catch(() => ({
            configured: false,
            ready: false,
          })),
          listMcpChallenges(token, controller.signal).catch(() => []),
        ]);
        setHealth(h);
        setDiagnostics(d);
        setDbStatus(db);
        if (challenges && challenges.length > 0) {
          const first = challenges[0];
          setPendingChallenge({
            challengeId: first.challengeId,
            toolName: first.toolName,
            parameters: first.arguments || {},
            description: `Tool action: ${first.toolName}`,
            serverName: "system-core",
          });
        }
      } catch (e) {
        if (!controller.signal.aborted) {
          setHealth(null);
          setError(
            e instanceof Error ? e.message : "Backend connection failed.",
          );
        }
      }
    };
    void refresh();
    const timer = setInterval(() => void refresh(), 30000);
    return () => {
      controller.abort();
      clearInterval(timer);
    };
  }, [token, connected]);

  useEffect(() => {
    const controller = new AbortController();
    getApi<{ mode: string }>("session", "", controller.signal)
      .then((s) => {
        if (!controller.signal.aborted) setLocalSession(s.mode === "local");
      })
      .catch((e) => {
        if (!controller.signal.aborted)
          setError(
            e instanceof Error ? e.message : "Cannot connect to backend.",
          );
      })
      .finally(() => {
        if (!controller.signal.aborted) setCheckingSession(false);
      });
    return () => controller.abort();
  }, []);

  async function connect(e: React.FormEvent) {
    e.preventDefault();
    setConnecting(true);
    setError("");
    try {
      const h = await getApi<Health>("health/model", candidate.trim());
      setHealth(h);
      setToken(candidate.trim());
      setCandidate("");
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "Cannot connect. Check the backend is running.",
      );
    } finally {
      setConnecting(false);
    }
  }

  async function refresh() {
    setError("");
    try {
      setHealth(await getApi<Health>("health/model", token));
      setDiagnostics(await getApi<Diagnostics>("diagnostics", token));
      setDbStatus(
        await getDatabaseStatus(token).catch(() => ({
          configured: false,
          ready: false,
        })),
      );
      const challenges = await listMcpChallenges(token).catch(() => []);
      if (challenges && challenges.length > 0) {
        const first = challenges[0];
        setPendingChallenge({
          challengeId: first.challengeId,
          toolName: first.toolName,
          parameters: first.arguments || {},
          description: `Tool action: ${first.toolName}`,
          serverName: "system-core",
        });
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Cannot refresh model status.");
    }
  }

  async function handleApproveMcp(challengeId: string) {
    try {
      await approveMcpChallenge(token, challengeId, true);
      setPendingChallenge(null);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Failed to approve MCP challenge.",
      );
    }
  }

  async function handleRejectMcp(challengeId: string) {
    try {
      await approveMcpChallenge(token, challengeId, false);
      setPendingChallenge(null);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Failed to reject MCP challenge.",
      );
    }
  }

  async function send(e: React.FormEvent) {
    e.preventDefault();
    if (voiceBusy) return;
    const textToSend = draft.trim() || (attachedImage ? "Look at my screen and help me debug" : "");
    if (!textToSend) return;
    if (textToSend.startsWith("/code ") || textToSend.startsWith("/coding ")) {
      const task = textToSend.replace(/^\/(?:code|coding)\s+/, "").trim();
      setDraft("");
      setActiveView("coding");
      if (task) {
        setTimeout(() => {
          window.dispatchEvent(new CustomEvent("jarvis_seed_coding_task", { detail: { task } }));
        }, 100);
      }
      return;
    }
    if (textToSend === "/code" || textToSend === "/coding") {
      setDraft("");
      setActiveView("coding");
      return;
    }
    if (textToSend === "/graph" || textToSend.startsWith("/graph ")) {
      setDraft("");
      setActiveView("graph");
      return;
    }
    await sendText(textToSend, undefined, attachedImage).catch(() => {});
  }

  async function sendText(text: string, onDelta?: (delta: string) => void, image?: string | null): Promise<string> {
    if (!text || active.current || !connected)
      throw new Error("Chat is busy or disconnected.");
    if (webSearch && text.length > 300) {
      setError("Keep web search questions under 300 characters.");
      throw new Error("Keep web search questions under 300 characters.");
    }
    const currentImage = image !== undefined ? image : attachedImage;
    const controller = new AbortController();
    active.current = controller;
    if (!activeConversationId) setActiveConversationId(`local-${crypto.randomUUID()}`);
    const id = crypto.randomUUID();
    const history = messages
      .filter((m) => !m.interrupted && m.content)
      .map(({ role, content }) => ({ role, content }));
    setMessages((prev) => [
      ...prev,
      { role: "user", content: text, id: crypto.randomUUID(), imageUrl: currentImage || undefined },
      { role: "assistant", content: "", id },
    ]);
    setDraft("");
    setAttachedImage(null);
    setBusy(true);
    setError("");
    let answer = "";
    let renderedAnswer = "";
    let rafId: number | null = null;

    const flushUpdate = () => {
      if (renderedAnswer !== answer) {
        renderedAnswer = answer;
        setMessages((prev) =>
          prev.map((m) =>
            m.id === id ? { ...m, content: renderedAnswer } : m,
          ),
        );
      }
    };

    const scheduleUpdate = () => {
      if (rafId === null) {
        rafId = requestAnimationFrame(() => {
          rafId = null;
          flushUpdate();
        });
      }
    };

    const handleDelta = (delta: string) => {
      answer += delta;
      onDelta?.(delta);
      scheduleUpdate();
    };

    const isWarMode = (() => {
      try {
        return localStorage.getItem("jarvis_war_mode") === "true";
      } catch {
        return false;
      }
    })();
    const messageToSend = isWarMode && !text.startsWith("/") ? `/war ${text}` : text;
    try {
      if (currentImage) {
        await streamVisionChat(
          messageToSend,
          currentImage,
          history,
          token,
          controller.signal,
          handleDelta
        );
      } else {
        await streamChat(
          messageToSend,
          history,
          token,
          controller.signal,
          handleDelta,
          webSearch,
          (sources) =>
            setMessages((prev) =>
              prev.map((m) => (m.id === id ? { ...m, sources } : m)),
            ),
          (meta) =>
            setMessages((prev) =>
              prev.map((m) =>
                m.id === id ? { ...m, segment: meta.segment, model: meta.model } : m,
              ),
            ),
        );
      }
      if (rafId !== null) {
        cancelAnimationFrame(rafId);
        rafId = null;
      }
      flushUpdate();
      return answer;
    } catch (e) {
      if (rafId !== null) {
        cancelAnimationFrame(rafId);
        rafId = null;
      }
      flushUpdate();
      setMessages((prev) =>
        prev.map((m) => (m.id === id ? { ...m, interrupted: true } : m)),
      );
      if (!controller.signal.aborted)
        setError(e instanceof Error ? e.message : "The response failed.");
      throw e;
    } finally {
      if (rafId !== null) {
        cancelAnimationFrame(rafId);
        rafId = null;
      }
      flushUpdate();
      if (active.current === controller) {
        active.current = null;
        setBusy(false);
      }
      textarea.current?.focus();
    }
  }

  function disconnect() {
    active.current?.abort();
    setToken("");
    setMessages([]);
    setHealth(null);
    setDiagnostics(null);
    setError("");
    setActiveView("assistant");
    setConversationsDrawerOpen(false);
    setActiveConversationId(undefined);
  }

  function handleNewChat() {
    if (active.current || voiceBusy) return;
    setActiveConversationId(undefined);
    setMessages([]);
    setError("");
    setActiveView("assistant");
    setMobileNav(false);
    textarea.current?.focus();
  }

  const ready = health?.status === "ready";

  return (
    <div className="workspace">
      <Sidebar
        activeView={activeView}
        onSelectView={(v) => {
          setActiveView(v);
          setMobileNav(false);
        }}
        onNewChat={handleNewChat}
        connected={connected}
        busy={busy}
        voiceBusy={voiceBusy}
        mobileNav={mobileNav}
        onOpenSavedChats={() => {
          setConversationsDrawerOpen(true);
          setMobileNav(false);
        }}
        pendingChallengeCount={pendingChallenge ? 1 : 0}
        onOpenApproval={() => {}}
        localSession={localSession}
        onDisconnect={disconnect}
      />

      <main>
        <TopBar
          activeView={activeView}
          connected={connected}
          ready={ready}
          busy={busy}
          voiceBusy={voiceBusy}
          wakeWordActive={wakeWordActive}
          onToggleWakeWord={toggleWakeWord}
          mobileNav={mobileNav}
          onToggleMobileNav={() => setMobileNav(!mobileNav)}
          onOpenHud={() => setHudOpen(true)}
          onOpenSavedChats={() => setConversationsDrawerOpen(true)}
        />

        {error && (
          <div className="error-banner" role="alert">
            <span>{error}</span>
            <button
              className="icon-button"
              aria-label="Dismiss error"
              onClick={() => setError("")}
            >
              <X size={16} />
            </button>
          </div>
        )}

        {checkingSession ? (
          <section className="connection-screen">
            <p>Connecting to local JARVIS…</p>
          </section>
        ) : !connected ? (
          <ConnectionScreen
            candidate={candidate}
            onCandidateChange={setCandidate}
            onSubmit={connect}
            connecting={connecting}
          />
        ) : activeView === "automation" ? (
          <AutomationPanel token={token} />
        ) : activeView === "scheduler" ? (
          <SchedulerPanel token={token} />
        ) : activeView === "files" ? (
          <FilesPanel token={token} />
        ) : activeView === "database_memory" ? (
          <DatabaseMemoriesPanel token={token} />
        ) : activeView === "shopping" ? (
          <ShoppingPanel token={token} />
        ) : activeView === "biometrics" ? (
          <BiometricsPanel token={token} />
        ) : activeView === "goals" ? (
          <GoalPlannerPanel token={token} />
        ) : activeView === "desktop" ? (
          <DesktopToolsPanel token={token} />
        ) : activeView === "smarthome" ? (
          <SmartHomePanel token={token} />
        ) : activeView === "coding" ? (
          <CodingAgentPanel token={token} onNavigate={setActiveView} />
        ) : activeView === "graph" ? (
          <NeuralGraphPanel token={token} onNavigate={setActiveView} />
        ) : activeView === "voice" ? (
          <VoicePanel
            token={token}
            busy={busy}
            onAsk={sendText}
            onStop={() => active.current?.abort()}
            onActiveChange={setVoiceBusy}
            diagnostics={diagnostics}
            hudOpen={hudOpen}
            onHudOpenChange={setHudOpen}
            onNavigate={(v) => setActiveView(v)}
            mode="page"
          />
        ) : activeView === "settings" ? (
          <SettingsView
            dbStatus={dbStatus}
            health={health}
            diagnostics={diagnostics}
            localSession={localSession}
            voiceBusy={voiceBusy}
            onRefresh={() => void refresh()}
            onDisconnect={disconnect}
            onNavigate={(v) => setActiveView(v)}
          />
        ) : (
          <ChatWorkspace
            token={token}
            messages={messages}
            draft={draft}
            onDraftChange={setDraft}
            onSubmit={send}
            onStop={() => active.current?.abort()}
            onAsk={sendText}
            busy={busy}
            voiceBusy={voiceBusy}
            onVoiceBusyChange={setVoiceBusy}
            webSearch={webSearch}
            onWebSearchChange={setWebSearch}
            health={health}
            ready={ready}
            onRefreshHealth={() => void refresh()}
            diagnostics={diagnostics}
            hudOpen={hudOpen}
            onHudOpenChange={setHudOpen}
            onOpenSettings={() => setActiveView("settings")}
            onNavigate={(v) => setActiveView(v)}
            starters={STARTERS}
            bottomRef={bottom}
            textareaRef={textarea}
            attachedImage={attachedImage}
            onAttachedImageChange={setAttachedImage}
          />
        )}

        {/* Global Persistent Voice Controller & Hands-Free Wake-Word Assistant */}
        {connected && activeView !== "voice" && (
          <VoicePanel
            token={token}
            busy={busy}
            onAsk={sendText}
            onStop={() => active.current?.abort()}
            onActiveChange={setVoiceBusy}
            diagnostics={diagnostics}
            hudOpen={hudOpen}
            onHudOpenChange={setHudOpen}
            onNavigate={(v) => setActiveView(v)}
            mode="compact"
          />
        )}
      </main>

      {conversationsDrawerOpen && (
        <ConversationsDrawer
          token={token}
          busy={busy || voiceBusy}
          activeConversationId={activeConversationId}
          currentMessages={messages}
          onSelectConversation={(id, _title, turns) => {
            setActiveConversationId(id);
            setMessages(
              turns.map((t) => ({
                ...t,
                id: t.id || crypto.randomUUID(),
              })),
            );
            setActiveView("assistant");
            setConversationsDrawerOpen(false);
          }}
          onNewConversation={() => {
            setActiveConversationId(undefined);
            setMessages([]);
          }}
          onClose={() => setConversationsDrawerOpen(false)}
        />
      )}

      <McpApprovalModal
        isOpen={!!pendingChallenge}
        challenge={pendingChallenge}
        onApprove={handleApproveMcp}
        onReject={handleRejectMcp}
        onClose={() => setPendingChallenge(null)}
      />
    </div>
  );
}

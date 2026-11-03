import { ArrowUpRight, Check, LogOut, RefreshCw, SlidersHorizontal, Volume2 } from "lucide-react";
import type { DatabaseStatus, Diagnostics, Health } from "@/api";
import { useState, useEffect } from "react";

interface SettingsViewProps {
  dbStatus: DatabaseStatus | null;
  health: Health | null;
  diagnostics: Diagnostics | null;
  localSession: boolean;
  voiceBusy: boolean;
  onRefresh: () => void;
  onDisconnect: () => void;
  onNavigate?: (view: any) => void;
}

export default function SettingsView({
  dbStatus,
  health,
  diagnostics,
  localSession,
  voiceBusy,
  onRefresh,
  onDisconnect,
  onNavigate,
}: SettingsViewProps) {
  const [activePersona, setActivePersona] = useState(() => {
    try {
      return localStorage.getItem("jarvis_voice_persona") || "jarvis";
    } catch {
      return "jarvis";
    }
  });
  const [activeLang, setActiveLang] = useState(() => {
    try {
      return localStorage.getItem("jarvis_voice_lang") || "en-US";
    } catch {
      return "en-US";
    }
  });

  useEffect(() => {
    const handleVoiceUpdate = () => {
      try {
        setActivePersona(localStorage.getItem("jarvis_voice_persona") || "jarvis");
        setActiveLang(localStorage.getItem("jarvis_voice_lang") || "en-US");
      } catch {}
    };
    window.addEventListener("jarvis_voice_updated", handleVoiceUpdate);
    return () => window.removeEventListener("jarvis_voice_updated", handleVoiceUpdate);
  }, []);
  return (
    <section className="settings-page">
      <span className="eyebrow">WORKSPACE SETTINGS</span>
      <h1>Model &amp; privacy</h1>
      <p className="subtle">The current configuration of your local backend.</p>

      <div className="settings-section">
        <h2>PostgreSQL Persistence</h2>
        <p>
          <span className={`status-dot ${dbStatus?.ready ? "ready" : ""}`} />
          Status:{" "}
          {dbStatus?.ready
            ? "Connected & Schema Ready"
            : dbStatus?.configured
              ? "Configured (PostgreSQL service offline or missing schema)"
              : "Not Configured (Optional)"}
        </p>
        <p>
          <Check size={16} /> Row-Level Security (RLS) bound to session user
        </p>
        <p>
          <Check size={16} /> Atomic conversation snapshots &amp; pagination
        </p>
      </div>

      <div className="settings-section">
        <div className="section-heading">
          <h2>Local inference</h2>
          <button className="text-button" onClick={onRefresh}>
            <RefreshCw size={14} /> Refresh
          </button>
        </div>
        <dl>
          <div>
            <dt>Model</dt>
            <dd>{health?.model ?? "Unavailable"}</dd>
          </div>
          <div>
            <dt>Runtime</dt>
            <dd>Ollama · local computer</dd>
          </div>
          <div>
            <dt>Context window</dt>
            <dd>{diagnostics?.contextLength.toLocaleString() ?? "—"} tokens</dd>
          </div>
          <div>
            <dt>Response limit</dt>
            <dd>{diagnostics?.outputTokens ?? "—"} tokens</dd>
          </div>
          <div>
            <dt>Concurrent requests</dt>
            <dd>{diagnostics?.concurrency ?? "—"}</dd>
          </div>
          <div>
            <dt>System RAM</dt>
            <dd>
              {diagnostics?.ram
                ? `${diagnostics.ram.freeFormatted} free of ${diagnostics.ram.totalFormatted} (Used: ${diagnostics.ram.usedFormatted})`
                : "—"}
            </dd>
          </div>
          <div>
            <dt>GPU &amp; VRAM</dt>
            <dd>
              {diagnostics?.gpu
                ? `${diagnostics.gpu.name} · ${diagnostics.gpu.freeFormatted} free of ${diagnostics.gpu.totalFormatted}`
                : "—"}
            </dd>
          </div>
          <div>
            <dt>ROM / Storage</dt>
            <dd>
              {diagnostics?.rom
                ? `${diagnostics.rom.freeFormatted} free of ${diagnostics.rom.totalFormatted} (Used: ${diagnostics.rom.usedFormatted})`
                : "—"}
            </dd>
          </div>
          <div>
            <dt>Java heap</dt>
            <dd>
              {diagnostics
                ? `${Math.round(diagnostics.heapUsedBytes / 1048576)} / ${Math.round(diagnostics.heapMaxBytes / 1048576)} MB`
                : "—"}
            </dd>
          </div>
        </dl>
        <p className="config-hint">
          To change the model, set <code>JARVIS_CHAT_MODEL</code> in the backend
          terminal and restart the backend. Pull the model in Ollama first.
        </p>
      </div>

      <div className="settings-section">
        <div className="section-heading">
          <h2>Voice &amp; AI Personas</h2>
          {onNavigate && (
            <button className="text-button" onClick={() => onNavigate("voice")}>
              <SlidersHorizontal size={14} /> Open Voice Studio <ArrowUpRight size={13} />
            </button>
          )}
        </div>
        <p>
          Conversational audio synthesis, Marvel AI universe characters (Ultron, Friday, Edith, Jarvis, Karen, Jocasta), and speech recognition.
        </p>
        <dl>
          <div>
            <dt>Active Persona</dt>
            <dd style={{ textTransform: "capitalize", fontWeight: 600, color: "#38bdf8" }}>
              {activePersona.replace("_", " ").toUpperCase()}
            </dd>
          </div>
          <div>
            <dt>Speech Language</dt>
            <dd>{activeLang === "ta-IN" ? "தமிழ் (Tamil - India)" : "English (US)"}</dd>
          </div>
          <div>
            <dt>Engine Mode</dt>
            <dd>Local Offline Windows &amp; Browser Synthesis / F5-TTS Clones</dd>
          </div>
        </dl>
      </div>

      <div className="settings-section">
        <h2>Privacy</h2>
        <p>
          <Check size={16} /> Cloud fallback disabled
        </p>
        <p>
          <Check size={16} />{" "}
          {localSession
            ? "Token-free access on this computer"
            : "Connection token held only in memory"}
        </p>
        <p>
          <Check size={16} /> Conversations are not yet saved
        </p>
        <p>
          <Check size={16} /> Web search and reviewed local text-file edits
        </p>
      </div>

      <button
        className="text-button"
        disabled={voiceBusy}
        onClick={onDisconnect}
      >
        <LogOut size={16} />{" "}
        {localSession
          ? "Clear conversation"
          : "Disconnect and clear conversation"}
      </button>
    </section>
  );
}

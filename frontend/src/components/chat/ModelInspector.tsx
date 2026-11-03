import { ArrowUpRight, Cpu, RefreshCw, Sparkles, Volume2 } from "lucide-react";
import type { Diagnostics, Health } from "@/api";
import JarvisHologram from "@/JarvisHologram";
import { useEffect, useState, memo } from "react";

interface ModelInspectorProps {
  health: Health | null;
  diagnostics: Diagnostics | null;
  ready: boolean;
  busy: boolean;
  onRefresh: () => void;
  onExpandHud: () => void;
  onOpenSettings: () => void;
  onNavigate?: (view: any) => void;
  onSelectCommand?: (cmd: string) => void;
}

const ModelInspector = memo(function ModelInspector({
  health,
  diagnostics,
  ready,
  busy,
  onRefresh,
  onExpandHud,
  onOpenSettings,
  onNavigate,
  onSelectCommand,
}: ModelInspectorProps) {
  const [activePersona, setActivePersona] = useState(() => {
    try {
      return localStorage.getItem("jarvis_voice_persona") || "jarvis";
    } catch {
      return "jarvis";
    }
  });

  useEffect(() => {
    const handleVoiceUpdate = () => {
      try {
        setActivePersona(localStorage.getItem("jarvis_voice_persona") || "jarvis");
      } catch {}
    };
    window.addEventListener("jarvis_voice_updated", handleVoiceUpdate);
    return () => window.removeEventListener("jarvis_voice_updated", handleVoiceUpdate);
  }, []);
  return (
    <aside className="inspector" aria-label="Model details">
      <div className="inspector-title">
        <Cpu size={16} /> LOCAL ENGINE
        <button
          className="icon-button"
          onClick={onRefresh}
          aria-label="Refresh model health"
        >
          <RefreshCw size={13} />
        </button>
      </div>

      <div className="model-name">{health?.model ?? "Ollama"}</div>

      <div className="model-tag">
        <span className={`status-dot ${ready ? "ready" : ""}`} />
        {ready ? "Ready to respond" : "Needs attention"}
      </div>

      <div className="inspector-hologram-card">
        <JarvisHologram
          mode="embedded"
          state={busy ? "thinking" : "idle"}
          onExpand={onExpandHud}
          telemetry={{
            ram: diagnostics?.ram ? `${diagnostics.ram.freeFormatted} free` : undefined,
            gpu: diagnostics?.gpu ? `${diagnostics.gpu.name}` : undefined,
            rom: diagnostics?.rom ? `${diagnostics.rom.freeFormatted} free` : undefined,
            model: health?.model,
          }}
        />
      </div>

      <div className="inspector-rule" />

      <div className="segment-router-card">
        <div className="segment-card-title">
          <Sparkles size={13} style={{ color: "var(--accent, #34d399)" }} />
          <span>KNOWLEDGE SEGMENTS</span>
        </div>
        <div className="segment-pills">
          <button
            type="button"
            className="segment-pill"
            onClick={() => onSelectCommand?.("/r1")}
            title="Auto-routes to DeepSeek-R1 1.5B (Deep Cognitive Reasoning & Math Proofs)"
          >
            <span className="segment-icon">🧠</span>
            <div className="segment-info">
              <span className="segment-name">Reasoning &amp; Logic</span>
              <span className="segment-target" style={{ color: "#c084fc" }}>deepseek-r1:1.5b</span>
            </div>
          </button>
          <button
            type="button"
            className="segment-pill"
            onClick={() => onSelectCommand?.("/code")}
            title="Auto-routes to Qwen2.5-Coder 1.5B (Code Engineering)"
          >
            <span className="segment-icon">💻</span>
            <div className="segment-info">
              <span className="segment-name">Code Engineering</span>
              <span className="segment-target">qwen2.5-coder:1.5b</span>
            </div>
          </button>
          <button
            type="button"
            className="segment-pill"
            onClick={() => onSelectCommand?.("/research")}
            title="Auto-routes to jarvis-researcher:latest (Live Stock Markets, OTT Releases, Date/Time & Autonomous Training)"
          >
            <span className="segment-icon">📈</span>
            <div className="segment-info">
              <span className="segment-name">Realtime &amp; Facts</span>
              <span className="segment-target">jarvis-researcher</span>
            </div>
          </button>
          <button
            type="button"
            className="segment-pill"
            onClick={() => {
              if (onNavigate) {
                onNavigate("coding");
              } else {
                onSelectCommand?.("/code");
              }
            }}
            title="Open Autonomous Coding Agent Studio (Cursor / Claude Code mode)"
          >
            <span className="segment-icon">⚡</span>
            <div className="segment-info">
              <span className="segment-name">Coding Studio</span>
              <span className="segment-target" style={{ color: "#38bdf8" }}>autonomous agent</span>
            </div>
          </button>
          <button
            type="button"
            className="segment-pill"
            onClick={() => onNavigate?.("graph")}
            title="Open Obsidian-Style Neural Knowledge Graph"
          >
            <span className="segment-icon">🕸️</span>
            <div className="segment-info">
              <span className="segment-name">Neural Graph</span>
              <span className="segment-target" style={{ color: "#a855f7" }}>synapses view</span>
            </div>
          </button>
          <button
            type="button"
            className="segment-pill"
            onClick={() => {
              if (onNavigate) {
                onNavigate("voice");
              } else {
                onSelectCommand?.("/chat");
              }
            }}
            title="Open Iron Man Voice & AI Persona Studio"
          >
            <span className="segment-icon">🎙️</span>
            <div className="segment-info">
              <span className="segment-name">Voice &amp; Persona</span>
              <span className="segment-target" style={{ textTransform: "capitalize" }}>
                {activePersona.replace("_", " ")}
              </span>
            </div>
          </button>
        </div>
      </div>

      <div className="inspector-rule" />

      <div style={{ marginBottom: "16px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "6px" }}>
          <span style={{ fontSize: "11px", fontWeight: 600, color: "#94a3b8", display: "flex", alignItems: "center", gap: "6px" }}>
            <Volume2 size={13} style={{ color: "#38bdf8" }} /> ACTIVE VOICE
          </span>
          {onNavigate && (
            <button
              type="button"
              onClick={() => onNavigate("voice")}
              style={{ background: "none", border: "none", color: "#38bdf8", fontSize: "11px", cursor: "pointer", display: "flex", alignItems: "center", gap: "2px" }}
            >
              Studio <ArrowUpRight size={11} />
            </button>
          )}
        </div>
        <div
          onClick={() => onNavigate?.("voice")}
          style={{
            background: "rgba(56, 189, 248, 0.08)",
            border: "1px solid rgba(56, 189, 248, 0.25)",
            borderRadius: "6px",
            padding: "8px 10px",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            cursor: "pointer",
          }}
          title="Click to open Voice Persona Studio"
        >
          <span style={{ fontWeight: 600, fontSize: "12px", color: "#f1f5f9", textTransform: "capitalize" }}>
            {activePersona.replace("_", " ")}
          </span>
          <span style={{ fontSize: "10px", background: "rgba(56, 189, 248, 0.2)", color: "#38bdf8", padding: "1px 6px", borderRadius: "4px" }}>
            Active
          </span>
        </div>
      </div>

      <div className="inspector-rule" />

      <dl>
        <div>
          <dt>Context</dt>
          <dd>{diagnostics?.contextLength.toLocaleString() ?? "—"}</dd>
        </div>
        <div>
          <dt>Output limit</dt>
          <dd>{diagnostics?.outputTokens ?? "—"}</dd>
        </div>
        <div>
          <dt>RAM</dt>
          <dd>
            {diagnostics?.ram ? `${diagnostics.ram.freeFormatted} free` : "—"}
          </dd>
        </div>
        <div>
          <dt>GPU</dt>
          <dd title={diagnostics?.gpu?.name}>
            {diagnostics?.gpu ? `${diagnostics.gpu.freeFormatted} free` : "—"}
          </dd>
        </div>
        <div>
          <dt>ROM / Disk</dt>
          <dd>
            {diagnostics?.rom ? `${diagnostics.rom.freeFormatted} free` : "—"}
          </dd>
        </div>
        <div>
          <dt>Cloud fallback</dt>
          <dd>Off</dd>
        </div>
      </dl>

      <p className="inspector-note">
        One request at a time.
        <br />
        Built for your local hardware.
      </p>

      <div className="session-note">
        <span className="eyebrow">THIS SESSION</span>
        <p>Chat stays in this tab. Reloading or disconnecting clears it.</p>
        <button className="text-button" onClick={onOpenSettings}>
          Model &amp; privacy <ArrowUpRight size={13} />
        </button>
      </div>
    </aside>
  );
});

export default ModelInspector;

import { useState, useEffect, memo } from "react";
import type React from "react";
import { ArrowUp, Globe, Mic, ShieldCheck, Square, Flame, Monitor } from "lucide-react";
import type { Diagnostics, Health } from "@/api";
import { playJarvisSound } from "@/utils/audioEffects";
import { fileToBase64 } from "@/utils/screenCapture";
import ScreenCaptureBar from "./ScreenCaptureBar";
import ScreenCaptureModal from "@/components/modals/ScreenCaptureModal";

interface PromptComposerProps {
  token: string;
  draft: string;
  onDraftChange: (text: string) => void;
  onSubmit: (e: React.FormEvent) => void;
  onStop: () => void;
  onAsk: (text: string, onDelta?: (delta: string) => void) => Promise<string>;
  busy: boolean;
  voiceBusy: boolean;
  onVoiceBusyChange: (busy: boolean) => void;
  webSearch: boolean;
  onWebSearchChange: (enabled: boolean) => void;
  health: Health | null;
  ready: boolean;
  onRefreshHealth: () => void;
  diagnostics: Diagnostics | null;
  hudOpen: boolean;
  onHudOpenChange: (open: boolean) => void;
  onNavigate?: (view: any) => void;
  textareaRef: React.RefObject<HTMLTextAreaElement | null>;
  attachedImage?: string | null;
  onAttachedImageChange?: (image: string | null) => void;
}

const PromptComposer = memo(function PromptComposer({
  token,
  draft,
  onDraftChange,
  onSubmit,
  onStop,
  onAsk,
  busy,
  voiceBusy,
  onVoiceBusyChange,
  webSearch,
  onWebSearchChange,
  health,
  ready,
  onRefreshHealth,
  diagnostics,
  hudOpen,
  onHudOpenChange,
  onNavigate,
  textareaRef,
  attachedImage,
  onAttachedImageChange,
}: PromptComposerProps) {
  const [warMode, setWarMode] = useState<boolean>(() => {
    try {
      return localStorage.getItem("jarvis_war_mode") === "true";
    } catch {
      return false;
    }
  });
  const [screenModalOpen, setScreenModalOpen] = useState(false);

  useEffect(() => {
    const handleWarToggle = (e: any) => {
      const next = e.detail !== undefined ? e.detail : localStorage.getItem("jarvis_war_mode") === "true";
      setWarMode(next);
    };
    window.addEventListener("jarvis_war_mode_toggled", handleWarToggle);
    return () => window.removeEventListener("jarvis_war_mode_toggled", handleWarToggle);
  }, []);

  const handleWarToggle = () => {
    const next = !warMode;
    setWarMode(next);
    try {
      localStorage.setItem("jarvis_war_mode", String(next));
      window.dispatchEvent(new CustomEvent("jarvis_war_mode_toggled", { detail: next }));
    } catch {}
    if (next) {
      playJarvisSound("war_alert");
    } else {
      playJarvisSound("standby");
    }
  };

  const handleVoiceToggle = () => {
    if (voiceBusy) {
      window.dispatchEvent(new CustomEvent("jarvis_stop_voice"));
      onVoiceBusyChange(false);
    } else {
      window.dispatchEvent(new CustomEvent("jarvis_talk"));
    }
  };

  const handlePaste = async (e: React.ClipboardEvent) => {
    const items = e.clipboardData?.items;
    if (!items) return;
    for (let i = 0; i < items.length; i++) {
      if (items[i].type.indexOf("image") !== -1) {
        const file = items[i].getAsFile();
        if (file) {
          e.preventDefault();
          try {
            const dataUrl = await fileToBase64(file);
            onAttachedImageChange?.(dataUrl);
            if (!draft.trim()) {
              onDraftChange("Look at my screen and help me debug");
            }
            playJarvisSound("action");
          } catch {}
          break;
        }
      }
    }
  };

  return (
    <div className="composer-area" onPaste={handlePaste}>
      {health && !ready && (
        <div className="runtime-warning">
          {health.detail}{" "}
          <button onClick={onRefreshHealth}>Check again</button>
        </div>
      )}

      <form className="composer" onSubmit={onSubmit}>
        {attachedImage && onAttachedImageChange && (
          <ScreenCaptureBar
            imageDataUrl={attachedImage}
            onRemove={() => onAttachedImageChange(null)}
            onSelectPrompt={(p) => {
              onDraftChange(p);
              textareaRef.current?.focus();
            }}
          />
        )}

        <textarea
          ref={textareaRef}
          value={draft}
          onChange={(e) => onDraftChange(e.target.value)}
          placeholder={attachedImage ? "Ask JARVIS about your screen… (e.g. Look at my screen and help me debug)" : "Ask JARVIS anything… (Tip: Paste screenshot with Ctrl+V)"}
          rows={2}
          maxLength={webSearch ? 300 : 4000}
          aria-label="Message JARVIS"
          disabled={busy || voiceBusy}
          onKeyDown={(e) => {
            if (
              e.key === "Enter" &&
              !e.shiftKey &&
              !e.nativeEvent.isComposing
            ) {
              e.preventDefault();
              e.currentTarget.form?.requestSubmit();
            }
          }}
        />
        <div className="composer-bottom">
          <span className="composer-status">
            <span className="status-dot ready" /> Local chat{" "}
            <span className="divider">/</span> {health?.model ?? "Ollama"}
          </span>

          <div className="composer-quick-actions">
            <button
              type="button"
              className={`composer-tool-btn screen-btn ${attachedImage ? "active" : ""}`}
              onClick={() => setScreenModalOpen(true)}
              disabled={busy}
              title={attachedImage ? "Screen capture attached. Click to change." : "Look at my screen and help me debug"}
            >
              <Monitor size={13} className={attachedImage ? "text-cyan-400" : ""} />
              <span>{attachedImage ? "Screen Attached" : "Look at Screen"}</span>
            </button>

            <button
              type="button"
              className={`composer-tool-btn ${webSearch ? "active" : ""}`}
              onClick={() => onWebSearchChange(!webSearch)}
              disabled={busy}
              title={webSearch ? "Web search enabled (DuckDuckGo)" : "Enable Web search"}
            >
              <Globe size={13} />
              <span>Web search</span>
            </button>

            <button
              type="button"
              className={`composer-tool-btn war-btn ${warMode ? "active" : ""}`}
              onClick={handleWarToggle}
              disabled={busy}
              title={warMode ? "War Mode Engaged (House Party Combat Protocol)" : "Engage War Mode"}
            >
              <Flame size={13} className={warMode ? "war-flame-active" : ""} />
              <span>{warMode ? "War Mode" : "War Mode"}</span>
            </button>

            <button
              type="button"
              className={`composer-tool-btn mic-btn ${voiceBusy ? "recording" : ""}`}
              onClick={handleVoiceToggle}
              disabled={busy && !voiceBusy}
              title={voiceBusy ? "Stop voice listening/speaking" : "Talk to JARVIS (Voice input)"}
            >
              {voiceBusy ? <Square size={13} fill="currentColor" /> : <Mic size={14} />}
              <span>{voiceBusy ? "Stop" : "Voice"}</span>
            </button>

            {busy ? (
              <button
                type="button"
                className="send-button"
                aria-label="Stop response"
                onClick={onStop}
              >
                <Square size={15} fill="currentColor" />
              </button>
            ) : (
              <button
                className="send-button"
                aria-label="Send message"
                disabled={(!draft.trim() && !attachedImage) || voiceBusy}
              >
                <ArrowUp size={19} />
              </button>
            )}
          </div>
        </div>
      </form>

      <div className="composer-caption">
        <ShieldCheck size={12} /> Local inference. Answers can be wrong —
        verify important details.
        <span>Shift + Enter for a new line · Ctrl + V to paste screenshots</span>
      </div>

      <ScreenCaptureModal
        token={token}
        isOpen={screenModalOpen}
        onClose={() => setScreenModalOpen(false)}
        onImageSelected={(dataUrl, defaultPrompt) => {
          onAttachedImageChange?.(dataUrl);
          if (defaultPrompt && !draft.trim()) {
            onDraftChange(defaultPrompt);
          }
          playJarvisSound("action");
        }}
      />
    </div>
  );
});

export default PromptComposer;

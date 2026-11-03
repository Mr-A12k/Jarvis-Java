import { useEffect, useRef } from "react";
import type React from "react";
import { ArrowUpRight } from "lucide-react";
import type { Diagnostics, Health } from "@/api";
import JarvisHologram from "@/JarvisHologram";
import ChatMessage from "@/components/chat/ChatMessage";
import type { Message } from "@/components/chat/ChatMessage";
import PromptComposer from "@/components/chat/PromptComposer";
import ModelInspector from "@/components/chat/ModelInspector";

interface ChatWorkspaceProps {
  token: string;
  messages: Message[];
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
  onOpenSettings: () => void;
  onNavigate?: (view: any) => void;
  starters: string[];
  bottomRef: React.RefObject<HTMLDivElement | null>;
  textareaRef: React.RefObject<HTMLTextAreaElement | null>;
  attachedImage?: string | null;
  onAttachedImageChange?: (image: string | null) => void;
}

export default function ChatWorkspace({
  token,
  messages,
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
  onOpenSettings,
  onNavigate,
  starters,
  bottomRef,
  textareaRef,
  attachedImage,
  onAttachedImageChange,
}: ChatWorkspaceProps) {
  const messagesContainerRef = useRef<HTMLDivElement>(null);
  const isNearBottomRef = useRef(true);
  const scrollRafRef = useRef<number | null>(null);

  const handleScroll = () => {
    const el = messagesContainerRef.current;
    if (!el) return;
    const distanceToBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    // Pinned to bottom if within 100px
    isNearBottomRef.current = distanceToBottom < 100;
  };

  useEffect(() => {
    const el = messagesContainerRef.current;
    if (!el) return;

    if (isNearBottomRef.current) {
      if (scrollRafRef.current === null) {
        scrollRafRef.current = requestAnimationFrame(() => {
          scrollRafRef.current = null;
          if (messagesContainerRef.current) {
            messagesContainerRef.current.scrollTop = messagesContainerRef.current.scrollHeight;
          }
        });
      }
    }
  }, [messages]);

  useEffect(() => {
    return () => {
      if (scrollRafRef.current !== null) {
        cancelAnimationFrame(scrollRafRef.current);
      }
    };
  }, []);

  return (
    <div className="chat-layout">
      <section className="chat-workspace" aria-label="Conversation">
        <div
          ref={messagesContainerRef}
          onScroll={handleScroll}
          className="messages"
          aria-live="polite"
          aria-relevant="additions text"
        >
          {messages.length === 0 ? (
            <div className="empty-chat">
              <JarvisHologram
                mode="embedded"
                state={busy ? "thinking" : "idle"}
                onExpand={() => onHudOpenChange(true)}
                telemetry={{
                  ram: diagnostics?.ram
                    ? `${diagnostics.ram.freeFormatted} free`
                    : undefined,
                  gpu: diagnostics?.gpu ? `${diagnostics.gpu.name}` : undefined,
                  rom: diagnostics?.rom
                    ? `${diagnostics.rom.freeFormatted} free`
                    : undefined,
                  model: health?.model,
                }}
              />
              <span className="eyebrow">JARVIS / AT YOUR SERVICE</span>
              <h1>
                What are we
                <br />
                <span>working on?</span>
              </h1>
              <p>
                Ask a question, explore an idea, or work through what’s next.
              </p>
              <div className="starter-list">
                {starters.map((s) => (
                  <button
                    key={s}
                    onClick={() => {
                      onDraftChange(s + ": ");
                      textareaRef.current?.focus();
                    }}
                  >
                    {s}
                    <ArrowUpRight size={15} />
                  </button>
                ))}
              </div>
            </div>
          ) : (
            messages.map((m, idx) => (
              <ChatMessage
                key={m.id}
                message={m}
                isStreaming={busy && idx === messages.length - 1 && m.role === "assistant"}
              />
            ))
          )}
          <div ref={bottomRef} />
        </div>

        <PromptComposer
          token={token}
          draft={draft}
          onDraftChange={onDraftChange}
          onSubmit={onSubmit}
          onStop={onStop}
          onAsk={onAsk}
          busy={busy}
          voiceBusy={voiceBusy}
          onVoiceBusyChange={onVoiceBusyChange}
          webSearch={webSearch}
          onWebSearchChange={onWebSearchChange}
          health={health}
          ready={ready}
          onRefreshHealth={onRefreshHealth}
          diagnostics={diagnostics}
          hudOpen={hudOpen}
          onHudOpenChange={onHudOpenChange}
          onNavigate={onNavigate}
          textareaRef={textareaRef}
          attachedImage={attachedImage}
          onAttachedImageChange={onAttachedImageChange}
        />
      </section>

      <ModelInspector
        health={health}
        diagnostics={diagnostics}
        ready={ready}
        busy={busy}
        onRefresh={onRefreshHealth}
        onExpandHud={() => onHudOpenChange(true)}
        onOpenSettings={onOpenSettings}
        onNavigate={onNavigate}
        onSelectCommand={(cmd) => {
          onDraftChange(cmd + " ");
          textareaRef.current?.focus();
        }}
      />
    </div>
  );
}

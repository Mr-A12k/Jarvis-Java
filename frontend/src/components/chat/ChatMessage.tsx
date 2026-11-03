import { useState, useEffect, useRef, useMemo, memo } from "react";
import ReactMarkdown from "react-markdown";
import type { Components } from "react-markdown";
import remarkGfm from "remark-gfm";
import { Check, Copy, ChevronDown, ChevronRight, Brain, Globe } from "lucide-react";
import type { Source, Turn } from "@/api";
import CodeBlock from "./CodeBlock";

export type Message = Turn & {
  id: string;
  interrupted?: boolean;
  sources?: Source[];
  imageUrl?: string;
  segment?: string;
  model?: string;
};

interface ChatMessageProps {
  message: Message;
  isStreaming?: boolean;
}

const REMARK_PLUGINS = [remarkGfm];

function parseThinking(content: string) {
  if (!content) return { thinking: null, response: "", isComplete: false };
  // Guard against partial opening tag while streaming (e.g. "<th", "<think")
  if (content.startsWith("<") && !content.includes(">") && content.length < 8) {
    return { thinking: null, response: "", isComplete: false };
  }

  const thinkStart = content.indexOf("<think>");
  if (thinkStart === -1) {
    return { thinking: null, response: content, isComplete: true };
  }

  const thinkEnd = content.indexOf("</think>");
  const beforeThink = content.slice(0, thinkStart).trim();
  if (thinkEnd === -1) {
    // Thinking in progress
    const thinking = content.slice(thinkStart + 7).trim();
    return { thinking, response: beforeThink, isComplete: false };
  } else {
    // Thinking completed
    const thinking = content.slice(thinkStart + 7, thinkEnd).trim();
    const afterThink = content.slice(thinkEnd + 8).trim();
    const response = (beforeThink ? beforeThink + "\n\n" : "") + afterThink;
    return { thinking, response, isComplete: true };
  }
}

const ChatMessage = memo(function ChatMessage({ message, isStreaming = false }: ChatMessageProps) {
  const isAssistant = message.role === "assistant";
  const { thinking, response, isComplete } = parseThinking(message.content || "");

  // Thinking duration timer (DeepSeek / Claude 3.7 style)
  const [elapsedSec, setElapsedSec] = useState<number>(0);
  const startTimeRef = useRef<number | null>(null);
  const [copiedResponse, setCopiedResponse] = useState(false);

  // Auto-collapse when response starts arriving
  const [manualOpen, setManualOpen] = useState<boolean | null>(null);
  const [selectedImage, setSelectedImage] = useState<{ src: string; alt: string } | null>(null);

  useEffect(() => {
    if (thinking && !isComplete && isStreaming) {
      if (!startTimeRef.current) startTimeRef.current = Date.now();
      const interval = setInterval(() => {
        if (startTimeRef.current) {
          setElapsedSec(Math.max(1, Math.round((Date.now() - startTimeRef.current) / 1000)));
        }
      }, 500);
      return () => clearInterval(interval);
    }
  }, [!isComplete && isStreaming && !!thinking]);

  // If response has started and thinking is complete, default to collapsed unless user clicked
  const isAccordionOpen = manualOpen !== null ? manualOpen : !isComplete;

  const handleCopyMessage = async () => {
    try {
      await navigator.clipboard.writeText(response || message.content || "");
      setCopiedResponse(true);
      setTimeout(() => setCopiedResponse(false), 2000);
    } catch {
      // fallback
    }
  };

  const wordCount = thinking ? thinking.split(/\s+/).filter(Boolean).length : 0;

  // Memoize markdown components to avoid destroying & remounting the DOM tree on every token
  const markdownComponents = useMemo<Components>(
    () => ({
      pre({ children }) {
        return <>{children}</>;
      },
      code({ className, children, ...props }) {
        const match = /language-(\w+)/.exec(className || "");
        const content = String(children).replace(/\n$/, "");
        if (match || content.includes("\n")) {
          return (
            <CodeBlock
              language={match ? match[1] : "text"}
              code={content}
            />
          );
        }
        return (
          <code className="inline-code" {...props}>
            {children}
          </code>
        );
      },
      table({ children }) {
        return (
          <div className="markdown-table-wrapper">
            <table>{children}</table>
          </div>
        );
      },
      a({ href, children }) {
        return (
          <a href={href} target="_blank" rel="noopener noreferrer">
            {children}
          </a>
        );
      },
      img({ src, alt, ...props }) {
        return (
          <span className="message-image-container">
            <img
              src={src}
              alt={alt || "Visual representation"}
              className="message-image"
              loading="lazy"
              onClick={() => {
                if (src) setSelectedImage({ src, alt: alt || "Visual preview" });
              }}
              onError={(e) => {
                (e.target as HTMLElement).style.display = "none";
              }}
              {...props}
            />
            {alt && (
              <span className="message-image-caption">
                <span className="caption-badge">🖼️</span>
                <span>{alt}</span>
              </span>
            )}
          </span>
        );
      },
    }),
    [],
  );

  return (
    <article className={`message ${message.role}`}>
      <div className="message-author">
        {isAssistant ? (
          <>
            <span className="mini-mark">J</span>
            <span className="author-name">JARVIS</span>
            {message.model && (
              <span
                className="segment-chip"
                style={{
                  fontSize: "10px",
                  padding: "1px 7px",
                  borderRadius: "999px",
                  marginLeft: "8px",
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "4px",
                  background: message.model.includes("deepseek") || message.model.includes("reasoner")
                    ? "rgba(168, 85, 247, 0.15)"
                    : message.model.includes("researcher")
                    ? "rgba(16, 185, 129, 0.15)"
                    : message.model.includes("coder")
                    ? "rgba(59, 130, 246, 0.15)"
                    : "rgba(56, 189, 248, 0.12)",
                  color: message.model.includes("deepseek") || message.model.includes("reasoner")
                    ? "#c084fc"
                    : message.model.includes("researcher")
                    ? "#34d399"
                    : message.model.includes("coder")
                    ? "#60a5fa"
                    : "#38bdf8",
                  border: `1px solid ${message.model.includes("deepseek") || message.model.includes("reasoner") ? "rgba(168, 85, 247, 0.3)" : message.model.includes("researcher") ? "rgba(16, 185, 129, 0.3)" : "rgba(56, 189, 248, 0.25)"}`,
                  fontWeight: 500,
                  letterSpacing: "0.02em"
                }}
                title={`MoSM Segment: ${message.segment || "General"} | Served by ${message.model}`}
              >
                {message.model.includes("deepseek") || message.model.includes("reasoner") ? (
                  <>🧠 DeepSeek-R1 (1.5B)</>
                ) : message.model.includes("researcher") ? (
                  <>📈 JARVIS-Researcher</>
                ) : message.model.includes("coder") ? (
                  <>💻 Coder 1.5B</>
                ) : (
                  <>⚡ {message.model.split(":")[0]}</>
                )}
              </span>
            )}
          </>
        ) : (
          <>
            <span className="user-mark">Y</span>
            <span className="author-name">YOU</span>
          </>
        )}
      </div>

      <div className="message-content">
        {/* Attached Screen Capture / Multimodal Image */}
        {message.imageUrl && (
          <div className="message-attached-image-wrapper">
            <span className="message-image-container">
              <img
                src={message.imageUrl}
                alt="Captured Screen"
                className="message-image"
                loading="lazy"
                onClick={() => {
                  setSelectedImage({ src: message.imageUrl!, alt: "Captured Screen Preview" });
                }}
              />
              <span className="message-image-caption">
                <span className="caption-badge">📸</span>
                <span>Captured Screen Context</span>
              </span>
            </span>
          </div>
        )}

        {/* DeepSeek / Claude-style Thought Process Accordion */}
        {thinking && (
          <div className="think-process-accordion">
            <button
              type="button"
              className="think-process-header"
              onClick={() => setManualOpen(!isAccordionOpen)}
              aria-expanded={isAccordionOpen}
            >
              <div className="think-header-left">
                <Brain size={14} className={isComplete ? "text-emerald-400" : "text-cyan-400 animate-pulse"} />
                <span className={`think-status-dot ${isComplete ? "completed" : "pulsing"}`} />
                <span className="think-status-title">
                  {isComplete ? (
                    <>
                      {message.model?.includes("deepseek") || message.model?.includes("reasoner")
                        ? "DeepSeek-R1 Cognitive Reasoning"
                        : "Thought Process"}
                      {elapsedSec > 0 && <span className="think-meta">({elapsedSec}s)</span>}
                      {wordCount > 0 && <span className="think-meta">· {wordCount} words</span>}
                    </>
                  ) : (
                    <>
                      {message.model?.includes("deepseek") || message.model?.includes("reasoner")
                        ? "DeepSeek-R1 is thinking & deducting..."
                        : "JARVIS is thinking & analyzing..."}
                      {elapsedSec > 0 && <span className="think-meta">({elapsedSec}s)</span>}
                    </>
                  )}
                </span>
              </div>
              <div className="think-header-right">
                {isAccordionOpen ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
              </div>
            </button>

            {isAccordionOpen && (
              <div className="think-process-body">
                {thinking}
                {!isComplete && isStreaming && <span className="streaming-cursor">▍</span>}
              </div>
            )}
          </div>
        )}

        {/* Verified Web Sources Card */}
        {message.sources && message.sources.length > 0 && (
          <div className="web-sources-card">
            <div className="web-sources-header">
              <div className="web-sources-header-left">
                <Globe size={13} className="text-cyan-400" />
                <span className="web-sources-title">
                  Verified Web Sources ({message.sources.length})
                </span>
              </div>
            </div>
            <div className="web-sources-list">
              {message.sources.map((source, idx) => (
                <a
                  key={source.id || idx}
                  href={source.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="web-source-item"
                  title={`${source.title}\n\n${source.snippet}`}
                >
                  <span className="web-source-num">[{source.id || idx + 1}]</span>
                  <span className="web-source-title">{source.title || source.url}</span>
                </a>
              ))}
            </div>
          </div>
        )}

        {/* Response Body with full Markdown & Syntax Highlighting */}
        {response ? (
          <div className={`response-body markdown-content ${isStreaming && isAssistant ? "is-streaming" : ""}`}>
            <ReactMarkdown
              remarkPlugins={REMARK_PLUGINS}
              components={markdownComponents}
            >
              {response}
            </ReactMarkdown>
            {isStreaming && isAssistant && !message.interrupted && (
              <span className="streaming-cursor">▍</span>
            )}
          </div>
        ) : !thinking && (
          message.interrupted ? (
            <span className="interrupted-text">Response stopped.</span>
          ) : (
            <span className="thinking web-searching">
              {isStreaming && message.model?.includes("researcher") ? (
                <>
                  <Globe size={13} className="web-search-spin" style={{ color: "#38bdf8", marginRight: "6px" }} />
                  <span style={{ color: "#38bdf8", fontSize: "12px" }}>Searching the web…</span>
                </>
              ) : (
                <>
                  <span />
                  <span />
                  <span />
                </>
              )}
            </span>
          )
        )}
      </div>

      {/* Action Bar for Assistant Messages */}
      {isAssistant && response && !isStreaming && (
        <div className="message-action-bar">
          <button
            type="button"
            className="action-btn"
            onClick={handleCopyMessage}
            title={copiedResponse ? "Copied markdown!" : "Copy response"}
          >
            {copiedResponse ? <Check size={13} style={{ color: "#34d399" }} /> : <Copy size={13} />}
            <span>{copiedResponse ? "Copied" : "Copy"}</span>
          </button>
        </div>
      )}

      {message.interrupted && message.content && (
        <span className="interrupted">Response interrupted</span>
      )}

      {/* Lightbox Modal for Full-Size Visual Inspection */}
      {selectedImage && (
        <div
          className="image-lightbox-overlay"
          onClick={() => setSelectedImage(null)}
          role="dialog"
          aria-modal="true"
        >
          <div className="image-lightbox-content" onClick={(e) => e.stopPropagation()}>
            <button
              type="button"
              className="image-lightbox-close"
              onClick={() => setSelectedImage(null)}
              aria-label="Close image preview"
            >
              ✕
            </button>
            <img
              src={selectedImage.src}
              alt={selectedImage.alt}
              className="image-lightbox-img"
            />
            {selectedImage.alt && (
              <div className="image-lightbox-caption">{selectedImage.alt}</div>
            )}
          </div>
        </div>
      )}
    </article>
  );
});

export default ChatMessage;

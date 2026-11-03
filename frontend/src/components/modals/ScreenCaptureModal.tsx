import { useState, useEffect, useRef } from "react";
import { Monitor, Upload, Clipboard, X, Check, Loader2, Sparkles, AlertCircle, DownloadCloud } from "lucide-react";
import { captureDisplayMedia, fileToBase64 } from "@/utils/screenCapture";
import { captureHostScreen, getVisionStatus, pullVisionModel } from "@/api";
import type { VisionStatus } from "@/api";

interface ScreenCaptureModalProps {
  token: string;
  isOpen: boolean;
  onClose: () => void;
  onImageSelected: (dataUrl: string, defaultPrompt?: string) => void;
}

export default function ScreenCaptureModal({
  token,
  isOpen,
  onClose,
  onImageSelected,
}: ScreenCaptureModalProps) {
  const [visionStatus, setVisionStatus] = useState<VisionStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [pulling, setPulling] = useState(false);
  const [pullMessage, setPullMessage] = useState("");
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (isOpen) {
      setError(null);
      getVisionStatus(token)
        .then(setVisionStatus)
        .catch(() => {});
    }
  }, [isOpen, token]);

  if (!isOpen) return null;

  const handleDisplayCapture = async () => {
    try {
      setLoading(true);
      setError(null);
      const dataUrl = await captureDisplayMedia();
      onImageSelected(dataUrl, "Look at my screen and help me debug");
      onClose();
    } catch (e: any) {
      if (e?.name !== "NotAllowedError") {
        setError(e?.message || "Failed to capture screen display.");
      }
    } finally {
      setLoading(false);
    }
  };

  const handleHostCapture = async () => {
    try {
      setLoading(true);
      setError(null);
      const result = await captureHostScreen(token);
      if (result.imageBase64 && result.source !== "unavailable") {
        const mime = result.mimeType || "image/jpeg";
        const dataUrl = `data:${mime};base64,${result.imageBase64}`;
        onImageSelected(dataUrl, "Look at my screen and help me debug");
        onClose();
      } else {
        // Fallback to browser capture
        handleDisplayCapture();
      }
    } catch (e: any) {
      setError(e?.message || "Host desktop capture failed. Trying browser capture...");
      handleDisplayCapture();
    } finally {
      setLoading(false);
    }
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      setLoading(true);
      const dataUrl = await fileToBase64(file);
      onImageSelected(dataUrl, "Look at my screen and help me debug");
      onClose();
    } catch (err: any) {
      setError(err?.message || "Failed to load screenshot file.");
    } finally {
      setLoading(false);
    }
  };

  const handlePullModel = async () => {
    try {
      setPulling(true);
      setPullMessage("Downloading moondream:1.8b into local Ollama...");
      const res = await pullVisionModel("moondream:1.8b", token);
      setPullMessage(res.message || "Model download completed.");
      const refreshed = await getVisionStatus(token);
      setVisionStatus(refreshed);
    } catch (e: any) {
      setPullMessage("Download error: " + (e?.message || "Could not reach Ollama"));
    } finally {
      setPulling(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal-container screen-capture-modal"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="vision-modal-title"
      >
        <div className="modal-header">
          <div className="modal-title-group">
            <span className="modal-icon-badge">
              <Monitor size={18} className="text-cyan-400" />
            </span>
            <div>
              <h3 id="vision-modal-title">Screen Capture & Vision</h3>
              <p className="modal-subtitle">
                Capture your screen, active window, or paste error logs for JARVIS to analyze & debug
              </p>
            </div>
          </div>
          <button className="modal-close-btn" onClick={onClose} aria-label="Close modal">
            <X size={16} />
          </button>
        </div>

        {error && (
          <div className="vision-modal-error">
            <AlertCircle size={14} />
            <span>{error}</span>
          </div>
        )}

        <div className="screen-capture-grid">
          {/* Option 1: Browser Display / Window Capture */}
          <button
            type="button"
            className="screen-capture-card primary"
            onClick={handleDisplayCapture}
            disabled={loading}
          >
            <div className="capture-card-icon">
              <Monitor size={28} />
            </div>
            <div className="capture-card-content">
              <h4>Capture Display / Window</h4>
              <p>Select your entire screen, active IDE window (VS Code), or browser tab</p>
              <span className="card-badge recommend-badge">Recommended</span>
            </div>
          </button>

          {/* Option 2: Host Desktop Capture */}
          {visionStatus?.hostCaptureSupported && (
            <button
              type="button"
              className="screen-capture-card"
              onClick={handleHostCapture}
              disabled={loading}
            >
              <div className="capture-card-icon">
                <Sparkles size={24} />
              </div>
              <div className="capture-card-content">
                <h4>Instant Desktop Screenshot</h4>
                <p>One-click capture of primary monitor directly on host OS</p>
              </div>
            </button>
          )}

          {/* Option 3: Upload Image */}
          <button
            type="button"
            className="screen-capture-card"
            onClick={() => fileInputRef.current?.click()}
            disabled={loading}
          >
            <div className="capture-card-icon">
              <Upload size={24} />
            </div>
            <div className="capture-card-content">
              <h4>Upload Screenshot / Image</h4>
              <p>PNG, JPG, or WebP screenshot saved on your computer</p>
            </div>
            <input
              type="file"
              ref={fileInputRef}
              accept="image/*"
              style={{ display: "none" }}
              onChange={handleFileChange}
            />
          </button>
        </div>

        {/* Clipboard Paste Note */}
        <div className="clipboard-hint-box">
          <Clipboard size={15} className="text-cyan-400" />
          <span>
            <strong>Pro-tip:</strong> You can also press <kbd>Win</kbd> + <kbd>Shift</kbd> + <kbd>S</kbd> or <kbd>PrtScn</kbd> and press <kbd>Ctrl</kbd> + <kbd>V</kbd> anywhere in the chat to attach screenshots instantly!
          </span>
        </div>

        {/* Model Status & Vision Engine Details */}
        <div className="vision-engine-footer">
          <div className="vision-engine-info">
            <span className="engine-status-dot pulsing" />
            <div className="engine-details">
              <div className="engine-name">
                {visionStatus?.visionModelAvailable ? (
                  <>
                    <span className="text-emerald-400 font-semibold">Native Multimodal Neural Vision</span>
                    <span className="engine-model-tag">{visionStatus.activeModel}</span>
                  </>
                ) : (
                  <>
                    <span className="text-cyan-400 font-semibold">Screen Code & Error Extractor</span>
                    <span className="engine-model-tag">OCR + {visionStatus?.activeModel || "qwen3"}</span>
                  </>
                )}
              </div>
              <div className="engine-desc">
                {visionStatus?.visionModelAvailable
                  ? "End-to-end pixel vision active. Ready for UI inspection & code debugging."
                  : "Extracts stack traces, code errors, and terminal logs from screenshots directly into code reasoning."}
              </div>
            </div>
          </div>

          {!visionStatus?.visionModelAvailable && (
            <div className="vision-pull-wrapper">
              <button
                type="button"
                className="pull-vision-btn"
                onClick={handlePullModel}
                disabled={pulling}
                title="Download moondream:1.8b for direct pixel-level vision"
              >
                {pulling ? <Loader2 size={13} className="animate-spin" /> : <DownloadCloud size={13} />}
                <span>{pulling ? "Downloading..." : "Pull moondream (~829MB)"}</span>
              </button>
              {pullMessage && <div className="pull-status-note">{pullMessage}</div>}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

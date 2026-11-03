import { useState, useEffect } from "react";
import { X, Bug, Search, Code, AlertTriangle, Eye } from "lucide-react";

interface ScreenCaptureBarProps {
  imageDataUrl: string;
  onRemove: () => void;
  onSelectPrompt: (promptText: string) => void;
}

export default function ScreenCaptureBar({
  imageDataUrl,
  onRemove,
  onSelectPrompt,
}: ScreenCaptureBarProps) {
  const [dimensions, setDimensions] = useState<{ width: number; height: number } | null>(null);
  const [previewZoom, setPreviewZoom] = useState(false);

  useEffect(() => {
    if (!imageDataUrl) return;
    const img = new Image();
    img.onload = () => {
      setDimensions({ width: img.naturalWidth, height: img.naturalHeight });
    };
    img.src = imageDataUrl;
  }, [imageDataUrl]);

  const approxKb = Math.round((imageDataUrl.length * 0.75) / 1024);

  const presets = [
    { label: "Look at my screen and help me debug", icon: Bug },
    { label: "Explain what is on my screen", icon: Search },
    { label: "Extract code & error trace", icon: Code },
    { label: "What caused this bug and how to fix it?", icon: AlertTriangle },
  ];

  return (
    <div className="screen-capture-bar">
      <div className="screen-capture-preview-item">
        <div
          className="screen-capture-thumb-container"
          onClick={() => setPreviewZoom(true)}
          title="Click to preview screenshot"
        >
          <img src={imageDataUrl} alt="Attached screen capture" className="screen-capture-thumb" />
          <div className="thumb-hover-overlay">
            <Eye size={14} />
          </div>
        </div>

        <div className="screen-capture-meta">
          <div className="screen-capture-title">
            <span className="screen-badge">📸 SCREEN CAPTURE</span>
            <span className="screen-meta-specs">
              {dimensions ? `${dimensions.width}×${dimensions.height}` : "Attached"} · ~{approxKb} KB
            </span>
          </div>
          <div className="screen-capture-actions">
            <span className="quick-action-label">Quick debug:</span>
            {presets.map(({ label, icon: Icon }) => (
              <button
                key={label}
                type="button"
                className="screen-preset-pill"
                onClick={() => onSelectPrompt(label)}
              >
                <Icon size={11} />
                <span>{label}</span>
              </button>
            ))}
          </div>
        </div>

        <button
          type="button"
          className="screen-capture-remove-btn"
          onClick={onRemove}
          title="Remove screenshot"
          aria-label="Remove screenshot"
        >
          <X size={14} />
        </button>
      </div>

      {previewZoom && (
        <div className="lightbox-backdrop" onClick={() => setPreviewZoom(false)}>
          <div className="lightbox-content" onClick={(e) => e.stopPropagation()}>
            <img src={imageDataUrl} alt="Full screen preview" className="lightbox-image" />
            <button className="lightbox-close-btn" onClick={() => setPreviewZoom(false)}>
              <X size={18} />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

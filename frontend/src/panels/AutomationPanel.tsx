import React, { useEffect, useState } from "react";
import {
  Globe,
  Camera,
  ShieldAlert,
  RefreshCw,
  ExternalLink,
  Clock,
  CheckCircle2,
  AlertTriangle,
  Eye,
  FileText,
  Loader2,
  Play,
  Plus,
  Trash2,
  ListOrdered,
} from "lucide-react";
import {
  navigateAutomation,
  snapshotAutomation,
  getAutomationAudit,
  interactAutomation,
} from "@/api";
import type { ActionStepInput, InteractiveAutomationResult } from "@/api";

interface AutomationPanelProps {
  token: string;
}

interface SnapshotData {
  requestedUrl: string;
  finalUrl: string;
  title: string;
  screenshotBase64?: string;
  textContent?: string;
  durationMs: number;
  statusCode?: number;
  stepResults?: string[];
}

interface AuditEvent {
  timestamp: string;
  eventType: string;
  url?: string;
  status: string;
  message?: string;
  durationMs?: number;
}

export default function AutomationPanel({ token }: AutomationPanelProps) {
  const [url, setUrl] = useState("");
  const [fullPage, setFullPage] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<"snapshot" | "text">("snapshot");
  const [result, setResult] = useState<SnapshotData | null>(null);
  const [auditEvents, setAuditEvents] = useState<AuditEvent[]>([]);
  const [auditLoading, setAuditLoading] = useState(false);
  const [showInteractive, setShowInteractive] = useState(false);
  const [steps, setSteps] = useState<ActionStepInput[]>([]);

  const addStep = (type: ActionStepInput["type"]) => {
    setSteps([...steps, { type, selector: "", value: "" }]);
  };

  const updateStep = (index: number, updated: Partial<ActionStepInput>) => {
    const next = [...steps];
    next[index] = { ...next[index], ...updated };
    setSteps(next);
  };

  const removeStep = (index: number) => {
    setSteps(steps.filter((_, i) => i !== index));
  };

  const handleExecuteInteract = async () => {
    if (!url.trim()) return;
    if (steps.length === 0) {
      setError("Please add at least one action step to execute.");
      return;
    }
    setLoading(true);
    setError(null);
    setStatusMessage(null);

    try {
      const res = await interactAutomation(token, url.trim(), steps, 25000);
      setResult({
        requestedUrl: res.requestedUrl,
        finalUrl: res.finalUrl,
        title: res.title,
        screenshotBase64: res.screenshotBase64,
        textContent: res.textContent,
        durationMs: res.durationMs,
        stepResults: res.stepResults,
      });
      setActiveTab("snapshot");
      setStatusMessage(`Workflow completed (${res.stepResults.length} steps) in ${res.durationMs}ms`);
      void fetchAudit();
    } catch (err: any) {
      setError(err?.message || "Interaction failed");
      void fetchAudit();
    } finally {
      setLoading(false);
    }
  };

  const fetchAudit = async () => {
    setAuditLoading(true);
    try {
      const events = await getAutomationAudit(token, 25);
      setAuditEvents(events || []);
    } catch {
      // Ignore initial silent audit fetch error
    } finally {
      setAuditLoading(false);
    }
  };

  useEffect(() => {
    void fetchAudit();
  }, [token]);

  const handleNavigate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!url.trim()) return;
    setLoading(true);
    setError(null);
    setStatusMessage(null);
    setResult(null);

    try {
      const res = await navigateAutomation(token, url.trim(), 15000);
      setResult({
        requestedUrl: res.requestedUrl,
        finalUrl: res.finalUrl,
        title: res.title,
        textContent: res.content,
        durationMs: res.durationMs,
        statusCode: res.statusCode,
      });
      setActiveTab("text");
      setStatusMessage(`Navigated successfully (${res.statusCode}) in ${res.durationMs}ms`);
      void fetchAudit();
    } catch (err: any) {
      setError(err?.message || "Navigation failed");
      void fetchAudit();
    } finally {
      setLoading(false);
    }
  };

  const handleSnapshot = async () => {
    if (!url.trim()) return;
    setLoading(true);
    setError(null);
    setStatusMessage(null);

    try {
      const res = await snapshotAutomation(token, url.trim(), fullPage, 20000);
      setResult(res);
      setActiveTab("snapshot");
      setStatusMessage(`Captured screenshot & content in ${res.durationMs}ms`);
      void fetchAudit();
    } catch (err: any) {
      setError(err?.message || "Snapshot failed");
      void fetchAudit();
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex-1 overflow-y-auto p-6 max-w-5xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-zinc-800 pb-4">
        <div>
          <span className="eyebrow">BROWSER AUTOMATION</span>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-100 flex items-center gap-2">
            <Globe className="text-cyan-400 h-6 w-6" /> Playwright Web Engine
          </h1>
          <p className="text-sm text-zinc-400">
            Thread-isolated browser sessions with private IP &amp; SSRF defenses.
          </p>
        </div>
        <div className="flex items-center gap-2 text-xs bg-zinc-900 border border-zinc-800 rounded-lg px-3 py-1.5 text-zinc-300">
          <ShieldAlert className="h-4 w-4 text-emerald-400" />
          <span>Localhost &amp; RFC 1918 Blocked</span>
        </div>
      </div>

      {/* URL Input Form */}
      <form onSubmit={handleNavigate} className="space-y-3 bg-zinc-900/60 border border-zinc-800 rounded-xl p-4">
        <label className="text-xs font-semibold text-zinc-300 flex items-center gap-2">
          TARGET WEB ADDRESS (HTTPS / HTTP)
        </label>
        <div className="flex gap-2">
          <input
            type="url"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://news.ycombinator.com"
            disabled={loading}
            className="flex-1 bg-zinc-950 border border-zinc-700 rounded-lg px-3.5 py-2 text-sm text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-cyan-500 transition"
          />
          <button
            type="submit"
            disabled={loading || !url.trim()}
            className="bg-zinc-800 hover:bg-zinc-700 text-zinc-100 font-medium text-xs px-4 py-2 rounded-lg transition disabled:opacity-50 flex items-center gap-1.5"
          >
            {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <ExternalLink className="h-4 w-4" />}
            Navigate
          </button>
          <button
            type="button"
            onClick={handleSnapshot}
            disabled={loading || !url.trim()}
            className="bg-cyan-600 hover:bg-cyan-500 text-zinc-900 font-semibold text-xs px-4 py-2 rounded-lg transition disabled:opacity-50 flex items-center gap-1.5"
          >
            {loading ? <Loader2 className="h-4 w-4 animate-spin text-zinc-900" /> : <Camera className="h-4 w-4" />}
            Snapshot
          </button>
        </div>

        <div className="flex items-center justify-between pt-1">
          <label className="flex items-center gap-2 text-xs text-zinc-400 cursor-pointer select-none">
            <input
              type="checkbox"
              checked={fullPage}
              onChange={(e) => setFullPage(e.target.checked)}
              disabled={loading}
              className="rounded bg-zinc-950 border-zinc-700 text-cyan-500 focus:ring-0"
            />
            Capture Full Page Length
          </label>
          <button
            type="button"
            onClick={() => setShowInteractive(!showInteractive)}
            className="text-xs text-cyan-400 hover:text-cyan-300 flex items-center gap-1.5 transition font-medium"
          >
            <ListOrdered className="h-3.5 w-3.5" />
            {showInteractive ? "Hide Interactive Steps" : "Build Multi-Step Interactive Workflow"}
          </button>
        </div>

        {showInteractive && (
          <div className="space-y-3 pt-3 border-t border-zinc-800/80">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-zinc-300">INTERACTIVE ACTION SEQUENCE</span>
              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  onClick={() => addStep("CLICK")}
                  disabled={steps.length >= 10 || loading}
                  className="bg-zinc-800 hover:bg-zinc-700 text-zinc-200 text-[11px] px-2.5 py-1 rounded transition flex items-center gap-1"
                >
                  <Plus className="h-3 w-3" /> Click
                </button>
                <button
                  type="button"
                  onClick={() => addStep("FILL")}
                  disabled={steps.length >= 10 || loading}
                  className="bg-zinc-800 hover:bg-zinc-700 text-zinc-200 text-[11px] px-2.5 py-1 rounded transition flex items-center gap-1"
                >
                  <Plus className="h-3 w-3" /> Fill Input
                </button>
                <button
                  type="button"
                  onClick={() => addStep("PRESS")}
                  disabled={steps.length >= 10 || loading}
                  className="bg-zinc-800 hover:bg-zinc-700 text-zinc-200 text-[11px] px-2.5 py-1 rounded transition flex items-center gap-1"
                >
                  <Plus className="h-3 w-3" /> Press Key
                </button>
                <button
                  type="button"
                  onClick={() => addStep("WAIT_FOR")}
                  disabled={steps.length >= 10 || loading}
                  className="bg-zinc-800 hover:bg-zinc-700 text-zinc-200 text-[11px] px-2.5 py-1 rounded transition flex items-center gap-1"
                >
                  <Plus className="h-3 w-3" /> Wait For
                </button>
              </div>
            </div>

            {steps.length === 0 ? (
              <p className="text-xs text-zinc-500 py-2 italic text-center">
                No interactive steps added yet. Click an action above to build your sequence.
              </p>
            ) : (
              <div className="space-y-2">
                {steps.map((st, idx) => (
                  <div
                    key={idx}
                    className="flex items-center gap-2 p-2 bg-zinc-950 border border-zinc-800 rounded-lg text-xs"
                  >
                    <span className="text-[11px] font-mono text-zinc-500 w-5 text-center">{idx + 1}.</span>
                    <span className="font-semibold text-cyan-400 w-20 text-[11px]">{st.type}</span>

                    {st.type !== "PRESS" && (
                      <input
                        type="text"
                        placeholder="CSS or text selector (e.g. #search, button.submit)"
                        value={st.selector || ""}
                        onChange={(e) => updateStep(idx, { selector: e.target.value })}
                        disabled={loading}
                        className="flex-1 bg-zinc-900 border border-zinc-700 rounded px-2 py-1 text-xs text-zinc-100 placeholder-zinc-500"
                      />
                    )}

                    {(st.type === "FILL" || st.type === "PRESS") && (
                      <input
                        type="text"
                        placeholder={st.type === "FILL" ? "Text to type" : "Key name (e.g. Enter, Tab)"}
                        value={st.value || ""}
                        onChange={(e) => updateStep(idx, { value: e.target.value })}
                        disabled={loading}
                        className="flex-1 bg-zinc-900 border border-zinc-700 rounded px-2 py-1 text-xs text-zinc-100 placeholder-zinc-500"
                      />
                    )}

                    <button
                      type="button"
                      onClick={() => removeStep(idx)}
                      disabled={loading}
                      className="p-1 text-zinc-400 hover:text-red-400 transition"
                      title="Remove step"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </div>
                ))}

                <div className="flex justify-end pt-2">
                  <button
                    type="button"
                    onClick={handleExecuteInteract}
                    disabled={loading || steps.length === 0 || !url.trim()}
                    className="bg-emerald-600 hover:bg-emerald-500 text-zinc-950 font-semibold text-xs px-4 py-2 rounded-lg transition disabled:opacity-50 flex items-center gap-1.5"
                  >
                    {loading ? (
                      <Loader2 className="h-4 w-4 animate-spin text-zinc-950" />
                    ) : (
                      <Play className="h-4 w-4 fill-current" />
                    )}
                    Run Interactive Workflow
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </form>

      {/* Error or Status Feedback */}
      {error && (
        <div className="p-3 bg-red-500/10 border border-red-500/20 text-red-300 text-xs rounded-lg flex items-center gap-2">
          <AlertTriangle className="h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}
      {statusMessage && (
        <div className="p-3 bg-emerald-500/10 border border-emerald-500/20 text-emerald-300 text-xs rounded-lg flex items-center gap-2">
          <CheckCircle2 className="h-4 w-4 shrink-0" />
          <span>{statusMessage}</span>
        </div>
      )}

      {/* Execution Results View */}
      {result && (
        <div className="space-y-3 bg-zinc-900/60 border border-zinc-800 rounded-xl p-4">
          <div className="flex items-center justify-between border-b border-zinc-800 pb-3">
            <div>
              <h2 className="text-sm font-semibold text-zinc-200">{result.title || "Untitled Document"}</h2>
              <p className="text-xs text-zinc-400 truncate max-w-xl">{result.finalUrl || result.requestedUrl}</p>
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setActiveTab("snapshot")}
                disabled={!result.screenshotBase64}
                className={`text-xs px-3 py-1.5 rounded-lg flex items-center gap-1.5 transition ${
                  activeTab === "snapshot"
                    ? "bg-zinc-800 text-cyan-400 font-medium"
                    : "text-zinc-400 hover:text-zinc-200 disabled:opacity-40"
                }`}
              >
                <Eye className="h-3.5 w-3.5" /> Visual Capture
              </button>
              <button
                type="button"
                onClick={() => setActiveTab("text")}
                className={`text-xs px-3 py-1.5 rounded-lg flex items-center gap-1.5 transition ${
                  activeTab === "text" ? "bg-zinc-800 text-cyan-400 font-medium" : "text-zinc-400 hover:text-zinc-200"
                }`}
              >
                <FileText className="h-3.5 w-3.5" /> Extracted Text
              </button>
            </div>
          </div>

          {activeTab === "snapshot" && result.screenshotBase64 && (
            <div className="border border-zinc-800 rounded-lg overflow-hidden bg-zinc-950 max-h-[500px] overflow-y-auto">
              <img
                src={`data:image/png;base64,${result.screenshotBase64}`}
                alt="Page screenshot"
                className="w-full object-contain"
              />
            </div>
          )}

          {activeTab === "text" && (
            <div className="p-3 bg-zinc-950 border border-zinc-800 rounded-lg max-h-[400px] overflow-y-auto">
              <pre className="text-xs text-zinc-300 whitespace-pre-wrap font-mono">
                {result.textContent || "No text content extracted."}
              </pre>
            </div>
          )}
        </div>
      )}

      {/* Audit Events Section */}
      <div className="space-y-3 bg-zinc-900/60 border border-zinc-800 rounded-xl p-4">
        <div className="flex items-center justify-between border-b border-zinc-800 pb-3">
          <div className="flex items-center gap-2">
            <Clock className="h-4 w-4 text-zinc-400" />
            <h2 className="text-xs font-semibold text-zinc-300">RECENT AUTOMATION AUDIT LOG</h2>
          </div>
          <button
            onClick={fetchAudit}
            disabled={auditLoading}
            className="text-xs text-zinc-400 hover:text-zinc-200 flex items-center gap-1 transition"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${auditLoading ? "animate-spin" : ""}`} /> Refresh
          </button>
        </div>

        {auditEvents.length === 0 ? (
          <p className="text-xs text-zinc-500 py-3 text-center">No recent browser automation events recorded.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs text-zinc-300">
              <thead className="text-[11px] uppercase tracking-wider text-zinc-500 border-b border-zinc-800">
                <tr>
                  <th className="py-2 px-2">Timestamp</th>
                  <th className="py-2 px-2">Action</th>
                  <th className="py-2 px-2">Target URL</th>
                  <th className="py-2 px-2">Status</th>
                  <th className="py-2 px-2">Duration</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800/60 font-mono text-[11px]">
                {auditEvents.map((ev, i) => (
                  <tr key={i} className="hover:bg-zinc-800/30 transition">
                    <td className="py-2 px-2 text-zinc-400 whitespace-nowrap">
                      {new Date(ev.timestamp).toLocaleTimeString()}
                    </td>
                    <td className="py-2 px-2 font-semibold text-zinc-200">{ev.eventType}</td>
                    <td className="py-2 px-2 truncate max-w-xs" title={ev.url}>
                      {ev.url || "—"}
                    </td>
                    <td className="py-2 px-2">
                      <span
                        className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-semibold ${
                          ev.status === "SUCCESS"
                            ? "bg-emerald-500/10 text-emerald-400"
                            : ev.status === "BLOCKED"
                            ? "bg-amber-500/10 text-amber-400"
                            : "bg-red-500/10 text-red-400"
                        }`}
                      >
                        {ev.status}
                      </span>
                    </td>
                    <td className="py-2 px-2 text-zinc-400">{ev.durationMs ? `${ev.durationMs}ms` : "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

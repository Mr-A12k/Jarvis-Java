import React, { useEffect, useState } from "react";
import {
  CalendarClock,
  Play,
  Trash2,
  RefreshCw,
  Plus,
  CheckCircle2,
  AlertTriangle,
  Clock,
  Activity,
  HardDrive,
  FileCheck,
  Loader2,
} from "lucide-react";
import {
  listScheduledTasks,
  createScheduledTask,
  deleteScheduledTask,
  runScheduledTaskNow,
  getSchedulerHistory,
} from "@/api";
import type { ScheduledTaskRecord, SchedulerHistoryRecord } from "@/api";

interface SchedulerPanelProps {
  token: string;
}

export default function SchedulerPanel({ token }: SchedulerPanelProps) {
  const [tasks, setTasks] = useState<ScheduledTaskRecord[]>([]);
  const [history, setHistory] = useState<SchedulerHistoryRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  // New task form state
  const [taskType, setTaskType] = useState<string>("SYSTEM_METRICS_LOG");
  const [taskName, setTaskName] = useState<string>("");
  const [intervalSeconds, setIntervalSeconds] = useState<number>(3600);
  const [targetFolder, setTargetFolder] = useState<string>("");

  const loadData = async () => {
    setLoading(true);
    try {
      const [tList, hList] = await Promise.all([
        listScheduledTasks(token).catch(() => []),
        getSchedulerHistory(token, 25).catch(() => []),
      ]);
      setTasks(tList || []);
      setHistory(hList || []);
    } catch (err: any) {
      setError(err?.message || "Failed to load scheduler data");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadData();
    const timer = setInterval(() => void loadData(), 20000);
    return () => clearInterval(timer);
  }, [token]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setStatusMessage(null);
    setActionLoading("create");

    try {
      await createScheduledTask(
        token,
        taskType,
        intervalSeconds,
        taskName.trim() || undefined,
        taskType === "INDEX_FOLDER" ? targetFolder.trim() : undefined
      );
      setStatusMessage("Scheduled task created successfully");
      setTaskName("");
      setTargetFolder("");
      await loadData();
    } catch (err: any) {
      setError(err?.message || "Failed to create scheduled task");
    } finally {
      setActionLoading(null);
    }
  };

  const handleDelete = async (id: string) => {
    setError(null);
    setActionLoading(`delete-${id}`);
    try {
      await deleteScheduledTask(token, id);
      setStatusMessage("Task deleted");
      await loadData();
    } catch (err: any) {
      setError(err?.message || "Failed to delete task");
    } finally {
      setActionLoading(null);
    }
  };

  const handleRunNow = async (id: string) => {
    setError(null);
    setActionLoading(`run-${id}`);
    try {
      const res = await runScheduledTaskNow(token, id);
      setStatusMessage(`Task executed (${res.status}): ${res.message}`);
      await loadData();
    } catch (err: any) {
      setError(err?.message || "Execution failed");
    } finally {
      setActionLoading(null);
    }
  };

  const formatInterval = (sec: number) => {
    if (sec >= 86400) return `Every ${(sec / 86400).toFixed(0)} day(s)`;
    if (sec >= 3600) return `Every ${(sec / 3600).toFixed(0)} hour(s)`;
    if (sec >= 60) return `Every ${(sec / 60).toFixed(0)} min(s)`;
    return `Every ${sec}s`;
  };

  const getTypeIcon = (type: string) => {
    switch (type) {
      case "SYSTEM_METRICS_LOG":
        return <Activity className="h-4 w-4 text-emerald-400" />;
      case "CLEANUP_AUDIT":
        return <HardDrive className="h-4 w-4 text-amber-400" />;
      case "INDEX_FOLDER":
        return <FileCheck className="h-4 w-4 text-cyan-400" />;
      default:
        return <CalendarClock className="h-4 w-4 text-zinc-400" />;
    }
  };

  return (
    <div className="flex-1 overflow-y-auto p-6 max-w-5xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-zinc-800 pb-4">
        <div>
          <span className="eyebrow">AUTOMATION &amp; JOBS</span>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-100 flex items-center gap-2">
            <CalendarClock className="text-cyan-400 h-6 w-6" /> Task Scheduler
          </h1>
          <p className="text-sm text-zinc-400">
            Allowlisted background tasks, automated indexing, metrics recording, and audit rotation.
          </p>
        </div>
        <button
          onClick={loadData}
          disabled={loading}
          className="text-xs text-zinc-400 hover:text-zinc-200 flex items-center gap-1.5 transition bg-zinc-900 border border-zinc-800 px-3 py-1.5 rounded-lg"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`} /> Refresh
        </button>
      </div>

      {/* Notifications */}
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

      {/* Create New Scheduled Task Form */}
      <form onSubmit={handleCreate} className="space-y-4 bg-zinc-900/60 border border-zinc-800 rounded-xl p-4">
        <h2 className="text-xs font-semibold text-zinc-300 uppercase tracking-wide flex items-center gap-1.5">
          <Plus className="h-4 w-4 text-cyan-400" /> Schedule Allowlisted Task
        </h2>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <div>
            <label className="text-[11px] font-medium text-zinc-400 block mb-1">TASK TYPE</label>
            <select
              value={taskType}
              onChange={(e) => setTaskType(e.target.value)}
              className="w-full bg-zinc-950 border border-zinc-700 rounded-lg px-3 py-2 text-xs text-zinc-100 focus:outline-none focus:border-cyan-500"
            >
              <option value="SYSTEM_METRICS_LOG">System Hardware Snapshot</option>
              <option value="CLEANUP_AUDIT">Rotate / Prune Audit Logs</option>
              <option value="INDEX_FOLDER">Auto Re-Index Local Folder</option>
            </select>
          </div>

          <div>
            <label className="text-[11px] font-medium text-zinc-400 block mb-1">TASK LABEL (OPTIONAL)</label>
            <input
              type="text"
              value={taskName}
              onChange={(e) => setTaskName(e.target.value)}
              placeholder="e.g. Daily Documents Sync"
              className="w-full bg-zinc-950 border border-zinc-700 rounded-lg px-3 py-2 text-xs text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-cyan-500"
            />
          </div>

          <div>
            <label className="text-[11px] font-medium text-zinc-400 block mb-1">INTERVAL</label>
            <select
              value={intervalSeconds}
              onChange={(e) => setIntervalSeconds(Number(e.target.value))}
              className="w-full bg-zinc-950 border border-zinc-700 rounded-lg px-3 py-2 text-xs text-zinc-100 focus:outline-none focus:border-cyan-500"
            >
              <option value={60}>Every 1 minute (Testing)</option>
              <option value={300}>Every 5 minutes</option>
              <option value={1800}>Every 30 minutes</option>
              <option value={3600}>Every 1 hour</option>
              <option value={14400}>Every 4 hours</option>
              <option value={86400}>Every 24 hours (Daily)</option>
            </select>
          </div>
        </div>

        {taskType === "INDEX_FOLDER" && (
          <div>
            <label className="text-[11px] font-medium text-zinc-400 block mb-1">TARGET ABSOLUTE FOLDER</label>
            <input
              type="text"
              value={targetFolder}
              onChange={(e) => setTargetFolder(e.target.value)}
              placeholder="D:\Documents\Projects"
              required
              className="w-full bg-zinc-950 border border-zinc-700 rounded-lg px-3 py-2 text-xs text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-cyan-500"
            />
          </div>
        )}

        <div className="flex justify-end pt-1">
          <button
            type="submit"
            disabled={actionLoading === "create"}
            className="bg-cyan-600 hover:bg-cyan-500 text-zinc-900 font-semibold text-xs px-4 py-2 rounded-lg transition disabled:opacity-50 flex items-center gap-1.5"
          >
            {actionLoading === "create" ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
            Schedule Task
          </button>
        </div>
      </form>

      {/* Active Tasks List */}
      <div className="space-y-3 bg-zinc-900/60 border border-zinc-800 rounded-xl p-4">
        <h2 className="text-xs font-semibold text-zinc-300 uppercase tracking-wide flex items-center gap-1.5">
          <Clock className="h-4 w-4 text-cyan-400" /> Active Recurring Tasks ({tasks.length})
        </h2>

        {tasks.length === 0 ? (
          <p className="text-xs text-zinc-500 py-3 text-center">No scheduled tasks currently active.</p>
        ) : (
          <div className="space-y-2">
            {tasks.map((t) => (
              <div
                key={t.id}
                className="flex items-center justify-between p-3 bg-zinc-950 border border-zinc-800 rounded-lg text-xs"
              >
                <div className="flex items-center gap-3">
                  <div className="p-2 bg-zinc-900 border border-zinc-800 rounded-md">
                    {getTypeIcon(t.type)}
                  </div>
                  <div>
                    <h3 className="font-medium text-zinc-200">{t.name}</h3>
                    <p className="text-zinc-400 text-[11px]">
                      {t.type} • {formatInterval(t.intervalSeconds)}
                      {t.target ? ` • Target: ${t.target}` : ""}
                    </p>
                    <p className="text-zinc-500 text-[10px]">
                      Last executed: {t.lastRunAt ? new Date(t.lastRunAt).toLocaleTimeString() : "Never"}
                      {t.lastStatus ? ` (${t.lastStatus})` : ""}
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    onClick={() => handleRunNow(t.id)}
                    disabled={actionLoading === `run-${t.id}`}
                    title="Run now manually"
                    className="p-1.5 bg-zinc-800 hover:bg-zinc-700 text-zinc-200 rounded transition disabled:opacity-50"
                  >
                    {actionLoading === `run-${t.id}` ? (
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    ) : (
                      <Play className="h-3.5 w-3.5" />
                    )}
                  </button>
                  <button
                    onClick={() => handleDelete(t.id)}
                    disabled={actionLoading === `delete-${t.id}`}
                    title="Delete scheduled task"
                    className="p-1.5 bg-zinc-800 hover:bg-red-900/40 text-red-400 rounded transition disabled:opacity-50"
                  >
                    {actionLoading === `delete-${t.id}` ? (
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    ) : (
                      <Trash2 className="h-3.5 w-3.5" />
                    )}
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Execution History */}
      <div className="space-y-3 bg-zinc-900/60 border border-zinc-800 rounded-xl p-4">
        <h2 className="text-xs font-semibold text-zinc-300 uppercase tracking-wide">
          Scheduler Execution History
        </h2>

        {history.length === 0 ? (
          <p className="text-xs text-zinc-500 py-3 text-center">No scheduler execution history recorded yet.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs text-zinc-300">
              <thead className="text-[11px] uppercase tracking-wider text-zinc-500 border-b border-zinc-800">
                <tr>
                  <th className="py-2 px-2">Time</th>
                  <th className="py-2 px-2">Type</th>
                  <th className="py-2 px-2">Status</th>
                  <th className="py-2 px-2">Duration</th>
                  <th className="py-2 px-2">Result / Message</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800/60 font-mono text-[11px]">
                {history.map((h, idx) => (
                  <tr key={idx} className="hover:bg-zinc-800/30 transition">
                    <td className="py-2 px-2 text-zinc-400 whitespace-nowrap">
                      {new Date(h.timestamp).toLocaleTimeString()}
                    </td>
                    <td className="py-2 px-2 font-semibold text-zinc-200">{h.taskType}</td>
                    <td className="py-2 px-2">
                      <span
                        className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-semibold ${
                          h.status === "SUCCESS"
                            ? "bg-emerald-500/10 text-emerald-400"
                            : "bg-red-500/10 text-red-400"
                        }`}
                      >
                        {h.status}
                      </span>
                    </td>
                    <td className="py-2 px-2 text-zinc-400">{h.durationMs}ms</td>
                    <td className="py-2 px-2 truncate max-w-sm text-zinc-300" title={h.message}>
                      {h.message}
                    </td>
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

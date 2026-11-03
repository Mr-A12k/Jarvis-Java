import React, { useState } from 'react';
import { AlertTriangle, Check, X, ShieldAlert, Terminal } from 'lucide-react';

export interface McpChallenge {
  challengeId: string;
  toolName: string;
  parameters: Record<string, any>;
  description?: string;
  serverName?: string;
}

export interface McpApprovalModalProps {
  isOpen: boolean;
  challenge: McpChallenge | null;
  onApprove: (challengeId: string) => Promise<void> | void;
  onReject: (challengeId: string) => Promise<void> | void;
  onClose: () => void;
}

export default function McpApprovalModal({
  isOpen,
  challenge,
  onApprove,
  onReject,
  onClose,
}: McpApprovalModalProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen || !challenge) {
    return null;
  }

  const handleApprove = async () => {
    setLoading(true);
    setError(null);
    try {
      await onApprove(challenge.challengeId);
      onClose();
    } catch (err: any) {
      setError(err?.message || 'Failed to approve tool execution');
    } finally {
      setLoading(false);
    }
  };

  const handleReject = async () => {
    setLoading(true);
    setError(null);
    try {
      await onReject(challenge.challengeId);
      onClose();
    } catch (err: any) {
      setError(err?.message || 'Failed to reject tool execution');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="mcp-approval-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-fade-in"
      onClick={(e) => {
        if (e.target === e.currentTarget && !loading) {
          onClose();
        }
      }}
    >
      <div className="w-full max-w-lg rounded-xl border border-amber-500/30 bg-zinc-900 p-6 text-zinc-100 shadow-2xl transition-all">
        {/* Header */}
        <div className="flex items-start justify-between gap-3 border-b border-zinc-800 pb-4">
          <div className="flex items-center gap-3">
            <div className="rounded-lg bg-amber-500/10 p-2 text-amber-400">
              <ShieldAlert className="h-6 w-6" />
            </div>
            <div>
              <h3 id="mcp-approval-title" className="text-lg font-semibold tracking-wide text-zinc-100">
                Action Approval Required
              </h3>
              <p className="text-xs text-zinc-400">Human-in-the-Loop Safeguard Challenge</p>
            </div>
          </div>
          <button
            onClick={onClose}
            disabled={loading}
            aria-label="Close"
            className="rounded p-1 text-zinc-400 transition hover:bg-zinc-800 hover:text-zinc-200 disabled:opacity-50"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Warning Banner */}
        <div className="my-4 flex items-center gap-2.5 rounded-lg border border-amber-500/20 bg-amber-500/10 px-3 py-2.5 text-xs text-amber-300">
          <AlertTriangle className="h-4 w-4 shrink-0 text-amber-400" />
          <span>
            A consequential tool invocation was requested. This action may alter system state or access protected resources.
          </span>
        </div>

        {/* Details */}
        <div className="space-y-3">
          <div>
            <div className="text-xs font-semibold uppercase tracking-wider text-zinc-400">Tool Name</div>
            <div className="mt-1 flex items-center gap-2 font-mono text-sm font-bold text-amber-400">
              <Terminal className="h-4 w-4 text-zinc-400" />
              <span>{challenge.toolName}</span>
              {challenge.serverName && (
                <span className="rounded bg-zinc-800 px-1.5 py-0.5 text-xs font-normal text-zinc-400">
                  {challenge.serverName}
                </span>
              )}
            </div>
            {challenge.description && (
              <p className="mt-1 text-xs text-zinc-400">{challenge.description}</p>
            )}
          </div>

          <div>
            <div className="text-xs font-semibold uppercase tracking-wider text-zinc-400">Parameters</div>
            <pre className="mt-1 max-h-48 overflow-auto rounded-lg border border-zinc-800 bg-zinc-950 p-3 font-mono text-xs text-zinc-200">
              {JSON.stringify(challenge.parameters, null, 2)}
            </pre>
          </div>

          {error && (
            <div className="rounded-lg border border-red-500/30 bg-red-500/10 p-2.5 text-xs text-red-400">
              {error}
            </div>
          )}
        </div>

        {/* Action Buttons */}
        <div className="mt-6 flex items-center justify-end gap-3 border-t border-zinc-800 pt-4">
          <button
            onClick={handleReject}
            disabled={loading}
            className="flex items-center gap-1.5 rounded-lg border border-red-500/30 bg-red-500/10 px-4 py-2 text-sm font-medium text-red-400 transition hover:bg-red-500/20 hover:text-red-300 disabled:opacity-50"
          >
            <X className="h-4 w-4" />
            Reject
          </button>
          <button
            onClick={handleApprove}
            disabled={loading}
            className="flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-emerald-500 active:bg-emerald-700 disabled:opacity-50"
          >
            <Check className="h-4 w-4" />
            {loading ? 'Executing...' : 'Approve & Run'}
          </button>
        </div>
      </div>
    </div>
  );
}

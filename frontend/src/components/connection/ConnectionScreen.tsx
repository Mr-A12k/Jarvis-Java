import type React from "react";
import { ArrowUpRight, AudioLines, CircleHelp, KeyRound, LoaderCircle } from "lucide-react";

interface ConnectionScreenProps {
  candidate: string;
  onCandidateChange: (val: string) => void;
  onSubmit: (e: React.FormEvent) => void;
  connecting: boolean;
}

export default function ConnectionScreen({
  candidate,
  onCandidateChange,
  onSubmit,
  connecting,
}: ConnectionScreenProps) {
  return (
    <section className="connection-screen">
      <div className="orb">
        <AudioLines size={40} strokeWidth={1} />
      </div>
      <span className="eyebrow">YOUR LOCAL ASSISTANT</span>
      <h1>
        Welcome to
        <br />
        <span>your workspace.</span>
      </h1>
      <p>Connect to JARVIS running on this computer.</p>

      <form className="connect-form" onSubmit={onSubmit}>
        <label htmlFor="token">Backend connection token</label>
        <div className="token-input">
          <KeyRound size={17} />
          <input
            id="token"
            type="password"
            autoComplete="off"
            value={candidate}
            onChange={(e) => onCandidateChange(e.target.value)}
            placeholder="Paste your session token"
            required
            minLength={32}
          />
        </div>
        <button className="primary-button" disabled={connecting}>
          {connecting ? (
            <LoaderCircle className="spin" size={17} />
          ) : (
            <ArrowUpRight size={17} />
          )}{" "}
          {connecting ? "Connecting…" : "Connect workspace"}
        </button>
      </form>

      <details className="connection-help">
        <summary>
          <CircleHelp size={14} /> Where do I find my token?
        </summary>
        <p>
          In your backend PowerShell terminal, use{" "}
          <code>Set-Clipboard $env:JARVIS_API_TOKEN</code>, then paste it
          above. It stays in browser memory until you disconnect or reload.
        </p>
      </details>
    </section>
  );
}

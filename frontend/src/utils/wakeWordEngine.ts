import { playJarvisSound } from "./audioEffects";

export interface BargeInDetails {
  type: "abort" | "command" | "speech";
  transcript: string;
  commandText?: string;
}

export interface WakeWordConfig {
  enabled: boolean;
  lang: string;
  onWake: (trailingQuery: string) => void;
  onBargeIn: (details: BargeInDetails) => void;
  isSpeaking: boolean;
  getActiveSpeechText?: () => string;
  onStatusChange?: (status: "listening" | "dormant" | "error" | "speaking") => void;
  onInterimTranscript?: (transcript: string) => void;
}

export class WakeWordEngine {
  private recognition: any = null;
  private isRunning = false;
  private isPaused = false;
  private config: WakeWordConfig;
  private restartTimeout: any = null;
  private mediaStream: MediaStream | null = null;
  private lastBargeInTime = 0;

  // Wake-word regex patterns
  private static readonly WAKE_REGEX_EN = /\b(?:hey\s+|hello\s+|hi\s+|ok\s+)?jarvis\b/i;
  private static readonly WAKE_REGEX_TA = /(?:ஹே\s+|வணக்கம்\s+)?(?:ஜார்விஸ்|ஜார்விச|ஜார்வி)/i;
  private static readonly BARGE_IN_ABORT_REGEX =
    /\b(?:stop|cancel|quiet|silence|wait|hold\s+on|shut\s+up|nevermind|pause|stand\s+by|நிறுத்து|அமைதி|போதும்|நில்)\b/i;

  constructor(config: WakeWordConfig) {
    this.config = config;
  }

  public updateConfig(newConfig: Partial<WakeWordConfig>) {
    const wasEnabled = this.config.enabled;
    this.config = { ...this.config, ...newConfig };

    if (this.config.enabled && !wasEnabled) {
      this.start();
    } else if (!this.config.enabled && wasEnabled) {
      this.stop();
    }
  }

  public async start() {
    if (this.isRunning || this.isPaused) return;

    const SpeechRec =
      (typeof window !== "undefined" && ((window as any).SpeechRecognition || (window as any).webkitSpeechRecognition)) ||
      (typeof globalThis !== "undefined" && ((globalThis as any).SpeechRecognition || (globalThis as any).webkitSpeechRecognition));
    if (!SpeechRec) {
      this.config.onStatusChange?.("error");
      return;
    }

    // Engage hardware acoustic echo cancellation and noise suppression
    await this.initHardwareEchoCancellation();

    try {
      this.recognition = new SpeechRec();
      this.recognition.continuous = true;
      this.recognition.interimResults = true;
      this.recognition.lang = this.config.lang || "en-US";
      this.recognition.maxAlternatives = 1;

      this.recognition.onstart = () => {
        this.isRunning = true;
        this.config.onStatusChange?.(this.config.isSpeaking ? "speaking" : "listening");
      };

      this.recognition.onresult = (event: any) => {
        const lastResultIndex = event.results.length - 1;
        if (lastResultIndex < 0) return;

        const result = event.results[lastResultIndex];
        const transcript = (result[0]?.transcript || "").trim();
        const isFinal = result.isFinal;

        if (!transcript) return;

        if (this.config.onInterimTranscript) {
          this.config.onInterimTranscript(transcript);
        }

        // 1. Handle Barge-in (Interruption while JARVIS is speaking)
        if (this.config.isSpeaking) {
          const now = Date.now();
          // Debounce rapid repeated triggers within 400ms
          if (now - this.lastBargeInTime < 400) return;

          // Reject speaker self-echo: discard if transcript simply mirrors what JARVIS is actively vocalizing
          if (this.isAcousticSelfEcho(transcript)) {
            return;
          }

          // A. Explicit abort command ("stop", "quiet", "cancel", "shut up", "நிறுத்து", "அமைதி")
          if (WakeWordEngine.BARGE_IN_ABORT_REGEX.test(transcript)) {
            this.lastBargeInTime = now;
            playJarvisSound("barge_in");
            this.config.onBargeIn({
              type: "abort",
              transcript,
            });
            return;
          }

          // B. Wake-word interrupted with a new command ("Hey Jarvis, check CPU...")
          const matchEn = transcript.match(WakeWordEngine.WAKE_REGEX_EN);
          const matchTa = transcript.match(WakeWordEngine.WAKE_REGEX_TA);
          const match = matchEn || matchTa;

          if (match && match.index !== undefined) {
            this.lastBargeInTime = now;
            playJarvisSound("activate");
            const matchEndIndex = match.index + match[0].length;
            const trailing = transcript.slice(matchEndIndex).replace(/^[,.\s]+/, "").trim();
            this.config.onBargeIn({
              type: "command",
              transcript,
              commandText: trailing,
            });
            return;
          }

          // While speaking, strictly ignore ambient audio/echo unless it is an explicit abort or wake command
          return;
        }

        // 2. Handle Wake-Word Detection when in ambient idle state
        const matchEn = transcript.match(WakeWordEngine.WAKE_REGEX_EN);
        const matchTa = transcript.match(WakeWordEngine.WAKE_REGEX_TA);
        const match = matchEn || matchTa;

        if (match && match.index !== undefined) {
          playJarvisSound("activate");

          // Extract trailing command in same breath (e.g. "Hey Jarvis, what's my CPU temperature?")
          const matchEndIndex = match.index + match[0].length;
          const trailing = transcript.slice(matchEndIndex).replace(/^[,.\s]+/, "").trim();

          // Reset recognition session for clean turn-taking
          try {
            this.recognition.stop();
          } catch {}

          this.config.onWake(trailing);
        }
      };

      this.recognition.onerror = (e: any) => {
        // Ignored non-fatal network/no-speech errors (Chrome terminates sessions periodically)
        if (e.error !== "no-speech" && e.error !== "network" && e.error !== "aborted") {
          this.config.onStatusChange?.("error");
        }
      };

      this.recognition.onend = () => {
        this.isRunning = false;
        // Keep ambient listener alive if enabled and not temporarily paused
        if (this.config.enabled && !this.isPaused) {
          clearTimeout(this.restartTimeout);
          this.restartTimeout = setTimeout(() => {
            if (this.config.enabled && !this.isPaused && !this.isRunning) {
              try {
                this.recognition.start();
              } catch {}
            }
          }, 200);
        } else {
          this.config.onStatusChange?.("dormant");
        }
      };

      this.recognition.start();
    } catch {
      this.config.onStatusChange?.("error");
    }
  }

  public pause() {
    this.isPaused = true;
    clearTimeout(this.restartTimeout);
    if (this.recognition) {
      try {
        this.recognition.abort();
      } catch {}
      this.recognition = null;
    }
    this.isRunning = false;
  }

  public async resume() {
    this.isPaused = false;
    if (this.config.enabled && !this.isRunning) {
      await this.start();
    }
  }

  public stop() {
    this.config.enabled = false;
    this.isRunning = false;
    this.isPaused = false;
    clearTimeout(this.restartTimeout);
    if (this.recognition) {
      try {
        this.recognition.abort();
      } catch {}
      this.recognition = null;
    }
    if (this.mediaStream) {
      try {
        this.mediaStream.getTracks().forEach((t) => t.stop());
      } catch {}
      this.mediaStream = null;
    }
    this.config.onStatusChange?.("dormant");
  }

  /**
   * Initializes hardware acoustic echo cancellation and noise suppression via Web Audio API.
   * Prompts the browser audio pipeline (WASAPI on Windows) to subtract speaker output from microphone input.
   */
  private async initHardwareEchoCancellation() {
    if (typeof navigator !== "undefined" && navigator.mediaDevices?.getUserMedia) {
      try {
        if (!this.mediaStream) {
          this.mediaStream = await navigator.mediaDevices.getUserMedia({
            audio: {
              echoCancellation: true,
              noiseSuppression: true,
              autoGainControl: true,
            },
          });
        }
      } catch {
        // Non-fatal if microphone permission is handled by Web Speech API directly
      }
    }
  }

  /**
   * Evaluates if recognized text is merely an acoustic echo of JARVIS's own speaker output.
   * Compares the incoming transcript words with the active sentence and recent speech history.
   */
  private isAcousticSelfEcho(transcript: string): boolean {
    if (!this.config.getActiveSpeechText) return false;
    const activeText =
      typeof this.config.getActiveSpeechText === "function"
        ? this.config.getActiveSpeechText()
        : (this.config.getActiveSpeechText as unknown as string);
    if (!activeText || activeText.trim().length === 0) return false;

    const normalize = (t: string) =>
      t.toLowerCase().replace(/[^a-z0-9\u0B80-\u0BFF\s]/g, " ").trim();

    const activeNorm = normalize(activeText);
    const transcriptNorm = normalize(transcript);

    if (!transcriptNorm) return true;

    // Substring match
    if (activeNorm.includes(transcriptNorm)) {
      return true;
    }

    // Word overlap test: If >= 75% of the transcript words are found in the active spoken text
    const transcriptWords = transcriptNorm.split(/\s+/).filter((w) => w.length > 2);
    if (transcriptWords.length === 0) return false;

    let matchingWords = 0;
    for (const word of transcriptWords) {
      if (activeNorm.includes(word)) {
        matchingWords++;
      }
    }

    const ratio = matchingWords / transcriptWords.length;
    return ratio >= 0.75;
  }
}

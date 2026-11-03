/**
 * High-Tech Iron Man Audio Synthesizer
 * Uses Web Audio API to generate zero-latency Stark HUD sound effects in-browser
 * with zero external asset or network dependencies.
 */

let audioCtx: AudioContext | null = null;

function getAudioContext(): AudioContext | null {
  if (typeof window === "undefined") return null;
  if (!audioCtx) {
    const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
    if (AudioCtx) {
      audioCtx = new AudioCtx();
    }
  }
  if (audioCtx && audioCtx.state === "suspended") {
    audioCtx.resume().catch(() => {});
  }
  return audioCtx;
}

export type JarvisSoundType = "activate" | "confirm" | "standby" | "alert" | "listening" | "war_alert" | "lock_on" | "repulsor" | "barge_in" | "action";

export function playJarvisSound(type: JarvisSoundType = "activate") {
  const ctx = getAudioContext();
  if (!ctx) return;

  const now = ctx.currentTime;

  try {
    switch (type) {
      case "action": {
        // Quick high-tech action click/blip
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        osc.type = "sine";
        osc.frequency.setValueAtTime(1174.66, now);
        osc.frequency.exponentialRampToValueAtTime(880, now + 0.06);

        gain.gain.setValueAtTime(0.16, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.12);

        osc.connect(gain);
        gain.connect(ctx.destination);

        osc.start(now);
        osc.stop(now + 0.12);
        break;
      }
      case "barge_in": {
        // Futuristic instant cutoff chirp (high-to-low sweep with rapid decay)
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        osc.type = "sine";
        osc.frequency.setValueAtTime(1480, now);
        osc.frequency.exponentialRampToValueAtTime(440, now + 0.08);

        gain.gain.setValueAtTime(0.22, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.09);

        osc.connect(gain);
        gain.connect(ctx.destination);

        osc.start(now);
        osc.stop(now + 0.09);
        break;
      }
      case "activate": {
        // Futuristic Iron Man Arc reactor activation chord (880Hz -> 1760Hz sweep + 1320Hz overtone)
        const osc1 = ctx.createOscillator();
        const osc2 = ctx.createOscillator();
        const gain = ctx.createGain();

        osc1.type = "sine";
        osc2.type = "triangle";

        osc1.frequency.setValueAtTime(880, now);
        osc1.frequency.exponentialRampToValueAtTime(1760, now + 0.14);

        osc2.frequency.setValueAtTime(1320, now + 0.04);
        osc2.frequency.exponentialRampToValueAtTime(2640, now + 0.18);

        gain.gain.setValueAtTime(0.22, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.38);

        osc1.connect(gain);
        osc2.connect(gain);
        gain.connect(ctx.destination);

        osc1.start(now);
        osc2.start(now + 0.04);
        osc1.stop(now + 0.38);
        osc2.stop(now + 0.38);
        break;
      }

      case "listening": {
        // High-pitch ping indicating microphone is armed
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        osc.type = "sine";
        osc.frequency.setValueAtTime(1760, now);
        osc.frequency.exponentialRampToValueAtTime(2200, now + 0.08);

        gain.gain.setValueAtTime(0.15, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.22);

        osc.connect(gain);
        gain.connect(ctx.destination);

        osc.start(now);
        osc.stop(now + 0.22);
        break;
      }

      case "confirm": {
        // Dual-blip confirmation chime
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        osc.type = "sine";
        osc.frequency.setValueAtTime(1046.5, now); // C6
        osc.frequency.setValueAtTime(1318.5, now + 0.08); // E6

        gain.gain.setValueAtTime(0.18, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.28);

        osc.connect(gain);
        gain.connect(ctx.destination);

        osc.start(now);
        osc.stop(now + 0.28);
        break;
      }

      case "standby": {
        // Gentle descending standby tone
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        osc.type = "sine";
        osc.frequency.setValueAtTime(987.77, now); // B5
        osc.frequency.exponentialRampToValueAtTime(440, now + 0.25); // A4

        gain.gain.setValueAtTime(0.15, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.3);

        osc.connect(gain);
        gain.connect(ctx.destination);

        osc.start(now);
        osc.stop(now + 0.3);
        break;
      }

      case "alert": {
        // Fast dual alert pulse
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        osc.type = "sawtooth";
        osc.frequency.setValueAtTime(440, now);
        osc.frequency.setValueAtTime(880, now + 0.1);

        gain.gain.setValueAtTime(0.2, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.25);

        osc.connect(gain);
        gain.connect(ctx.destination);

        osc.start(now);
        osc.stop(now + 0.25);
        break;
      }

      case "war_alert": {
        // Stark Industries Red Alert / Combat Klaxon (Intense dual-tone siren)
        const osc1 = ctx.createOscillator();
        const osc2 = ctx.createOscillator();
        const gain = ctx.createGain();

        osc1.type = "sawtooth";
        osc2.type = "sine";

        // Frequency sweep from 600Hz to 1200Hz and back
        osc1.frequency.setValueAtTime(520, now);
        osc1.frequency.exponentialRampToValueAtTime(1180, now + 0.18);
        osc1.frequency.exponentialRampToValueAtTime(480, now + 0.36);

        osc2.frequency.setValueAtTime(260, now);
        osc2.frequency.exponentialRampToValueAtTime(590, now + 0.18);
        osc2.frequency.exponentialRampToValueAtTime(240, now + 0.36);

        gain.gain.setValueAtTime(0.28, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.42);

        osc1.connect(gain);
        osc2.connect(gain);
        gain.connect(ctx.destination);

        osc1.start(now);
        osc2.start(now);
        osc1.stop(now + 0.42);
        osc2.stop(now + 0.42);
        break;
      }

      case "lock_on": {
        // Targeting Lock-On Tone (Target designated)
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        osc.type = "sine";
        osc.frequency.setValueAtTime(1568, now); // G6
        osc.frequency.setValueAtTime(2093, now + 0.08); // C7
        osc.frequency.setValueAtTime(3136, now + 0.16); // G7

        gain.gain.setValueAtTime(0.22, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.28);

        osc.connect(gain);
        gain.connect(ctx.destination);

        osc.start(now);
        osc.stop(now + 0.28);
        break;
      }

      case "repulsor": {
        // High-energy Repulsor Charging Surge (140Hz up to 980Hz with sub-bass resonance)
        const osc = ctx.createOscillator();
        const sub = ctx.createOscillator();
        const gain = ctx.createGain();

        osc.type = "triangle";
        sub.type = "sine";

        osc.frequency.setValueAtTime(140, now);
        osc.frequency.exponentialRampToValueAtTime(980, now + 0.28);

        sub.frequency.setValueAtTime(80, now);
        sub.frequency.exponentialRampToValueAtTime(320, now + 0.28);

        gain.gain.setValueAtTime(0.3, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.35);

        osc.connect(gain);
        sub.connect(gain);
        gain.connect(ctx.destination);

        osc.start(now);
        sub.start(now);
        osc.stop(now + 0.35);
        sub.stop(now + 0.35);
        break;
      }
    }
  } catch {
    // Graceful fallback if browser audio is blocked
  }
}

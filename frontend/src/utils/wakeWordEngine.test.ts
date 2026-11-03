import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { WakeWordEngine, type BargeInDetails } from "./wakeWordEngine";

class MockSpeechRecognition {
  continuous = false;
  interimResults = false;
  lang = "en-US";
  maxAlternatives = 1;
  onstart: (() => void) | null = null;
  onresult: ((event: any) => void) | null = null;
  onerror: ((event: any) => void) | null = null;
  onend: (() => void) | null = null;

  start = vi.fn(() => {
    this.onstart?.();
  });

  stop = vi.fn(() => {
    this.onend?.();
  });

  abort = vi.fn(() => {
    this.onend?.();
  });

  simulateResult(transcript: string, isFinal = false) {
    this.onresult?.({
      results: [
        Object.assign([{ transcript }], { isFinal }),
      ],
    });
  }
}

describe("WakeWordEngine - Hands-Free & Barge-In", () => {
  let mockRec: MockSpeechRecognition;

  beforeEach(() => {
    mockRec = new MockSpeechRecognition();
    const MockRecConstructor = class {
      constructor() {
        return mockRec;
      }
    };
    vi.stubGlobal("SpeechRecognition", MockRecConstructor);
    vi.stubGlobal("webkitSpeechRecognition", MockRecConstructor);
    // Stub Web Audio API
    vi.stubGlobal("AudioContext", class {
      currentTime = 0;
      state = "running";
      createOscillator() {
        return {
          type: "sine",
          frequency: { setValueAtTime: vi.fn(), exponentialRampToValueAtTime: vi.fn() },
          connect: vi.fn(),
          start: vi.fn(),
          stop: vi.fn(),
        };
      }
      createGain() {
        return {
          gain: { setValueAtTime: vi.fn(), exponentialRampToValueAtTime: vi.fn() },
          connect: vi.fn(),
        };
      }
      destination = {};
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it("detects English wake word 'Hey Jarvis' and triggers onWake", async () => {
    const onWake = vi.fn();
    const onBargeIn = vi.fn();

    const engine = new WakeWordEngine({
      enabled: true,
      lang: "en-US",
      isSpeaking: false,
      onWake,
      onBargeIn,
    });

    await engine.start();
    expect(mockRec.start).toHaveBeenCalled();

    mockRec.simulateResult("hey jarvis", true);
    expect(onWake).toHaveBeenCalledWith("");
  });

  it("extracts trailing query spoken in the same breath as the wake word", async () => {
    const onWake = vi.fn();
    const onBargeIn = vi.fn();

    const engine = new WakeWordEngine({
      enabled: true,
      lang: "en-US",
      isSpeaking: false,
      onWake,
      onBargeIn,
    });

    await engine.start();
    mockRec.simulateResult("jarvis what is my cpu temperature", true);
    expect(onWake).toHaveBeenCalledWith("what is my cpu temperature");
  });

  it("detects Tamil wake word 'ஜார்விஸ்'", async () => {
    const onWake = vi.fn();
    const onBargeIn = vi.fn();

    const engine = new WakeWordEngine({
      enabled: true,
      lang: "ta-IN",
      isSpeaking: false,
      onWake,
      onBargeIn,
    });

    await engine.start();
    mockRec.simulateResult("ஹே ஜார்விஸ் கணினி நிலை என்ன", true);
    expect(onWake).toHaveBeenCalledWith("கணினி நிலை என்ன");
  });

  it("triggers instant Voice Barge-In on abort command while speaking", async () => {
    const onWake = vi.fn();
    const onBargeIn = vi.fn();

    const engine = new WakeWordEngine({
      enabled: true,
      lang: "en-US",
      isSpeaking: true,
      getActiveSpeechText: () => "I am currently calculating the trajectory for orbital re-entry.",
      onWake,
      onBargeIn,
    });

    await engine.start();
    mockRec.simulateResult("stop", true);

    expect(onBargeIn).toHaveBeenCalledTimes(1);
    const details = onBargeIn.mock.calls[0][0] as BargeInDetails;
    expect(details.type).toBe("abort");
    expect(onWake).not.toHaveBeenCalled();
  });

  it("triggers Voice Barge-In with new command when user interrupts with 'Hey Jarvis...'", async () => {
    const onWake = vi.fn();
    const onBargeIn = vi.fn();

    const engine = new WakeWordEngine({
      enabled: true,
      lang: "en-US",
      isSpeaking: true,
      getActiveSpeechText: () => "All propulsion systems are operating at ninety-five percent nominal capacity.",
      onWake,
      onBargeIn,
    });

    await engine.start();
    mockRec.simulateResult("hey jarvis check battery level", true);

    expect(onBargeIn).toHaveBeenCalledTimes(1);
    const details = onBargeIn.mock.calls[0][0] as BargeInDetails;
    expect(details.type).toBe("command");
    expect(details.commandText).toBe("check battery level");
  });

  it("rejects acoustic self-echo from speaker output while speaking", async () => {
    const onWake = vi.fn();
    const onBargeIn = vi.fn();

    const engine = new WakeWordEngine({
      enabled: true,
      lang: "en-US",
      isSpeaking: true,
      getActiveSpeechText: () => "All propulsion systems are operating at nominal capacity.",
      onWake,
      onBargeIn,
    });

    await engine.start();
    // Simulate microphone picking up words JARVIS just vocalized through the speakers
    mockRec.simulateResult("propulsion systems operating", false);

    // Should NOT trigger barge-in because it is detected as self-echo
    expect(onBargeIn).not.toHaveBeenCalled();
  });

  it("allows pause and resume without losing enabled configuration", async () => {
    const onWake = vi.fn();
    const onBargeIn = vi.fn();

    const engine = new WakeWordEngine({
      enabled: true,
      lang: "en-US",
      isSpeaking: false,
      onWake,
      onBargeIn,
    });

    await engine.start();
    engine.pause();
    expect(mockRec.abort).toHaveBeenCalled();

    await engine.resume();
    expect(mockRec.start).toHaveBeenCalledTimes(2);
  });
});

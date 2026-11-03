import { useEffect, useRef, useState } from 'react';
import {
  Mic,
  Square,
  Volume2,
  Globe,
  Sparkles,
  Sliders,
  Play,
  Check,
  RotateCcw,
  X,
  Search,
  Zap,
  Radio,
  Trash2,
  RefreshCw,
  Cpu,
  ShieldCheck,
  AlertCircle,
} from 'lucide-react';
import {
  apiHeaders,
  type Diagnostics,
  type VoiceCloneProfile,
  type VoiceCloneStatus,
  getVoiceCloneStatus,
  listVoiceCloneProfiles,
  enrollVoiceClone,
  deleteVoiceCloneProfile,
  synthesizeVoiceClone,
} from '@/api';
import JarvisHologram from '@/JarvisHologram';
import { WakeWordEngine, type BargeInDetails } from '@/utils/wakeWordEngine';
import { playJarvisSound } from '@/utils/audioEffects';

export interface VoicePersona {
  id: string;
  name: string;
  tag: string;
  badge: string;
  description: string;
  sampleEn: string;
  sampleTa: string;
  pitch: number;
  rate: number;
}

export const VOICE_PERSONAS: VoicePersona[] = [
  {
    id: 'jarvis_war',
    name: 'J.A.R.V.I.S. (War Mode)',
    tag: 'Combat & Tactical Aggression',
    badge: '🔥 House Party Protocol',
    description: 'Maximum combat alert. Battle-hardened Paul Bettany with urgent tactical aggression, weapons telemetry, and zero hesitation.',
    sampleEn: 'Combat protocols active, Sir! All repulsors charged to 100%. Threat vectors identified—cleared hot to engage!',
    sampleTa: 'போர் முறை செயல்படுத்தப்பட்டது ஐயா! அனைத்து அமைப்புகளும் போர் தயார் நிலையில் உள்ளன. உத்தரவிடுங்கள்!',
    pitch: 0.88,
    rate: 1.15,
  },
  {
    id: 'war_machine',
    name: 'War Machine Tactical AI',
    tag: 'Heavy Artillery & Ordinance',
    badge: '💥 Colonel Rhodes Armor',
    description: 'Authoritative, booming military armor commander. Maximum firepower, tactical suppression, and battlefield dominance.',
    sampleEn: 'Target locked, Commander. Micro-missiles armed and kinetic cannons spinning up. Awaiting firing coordinates!',
    sampleTa: 'இலக்கு நிர்ணயிக்கப்பட்டுவிட்டது. கனரக பீரங்கிகள் தயார். தாக்குதலை தொடங்கலாம்!',
    pitch: 0.76,
    rate: 1.08,
  },
  {
    id: 'hulkbuster',
    name: 'Veronica (Hulkbuster AI)',
    tag: 'Heavy Suppression Protocol',
    badge: '🦾 Sub-Orbital Armor',
    description: 'Deep mechanical voice for orbital kinetic drops, brute-force containment, and maximum threat neutralization.',
    sampleEn: 'Veronica orbital cage in position. Kinetic dampeners primed. Ready to drop the hammer on your command, Sir.',
    sampleTa: 'வெரோனிகா பாதுகாப்பு கவசம் தயாராக உள்ளது. மிகப்பெரிய அச்சுறுத்தல்களை சமாளிக்க தயார்.',
    pitch: 0.68,
    rate: 0.95,
  },
  {
    id: 'jarvis',
    name: 'J.A.R.V.I.S.',
    tag: 'British Butler AI',
    badge: '🇬🇧 Paul Bettany',
    description: 'Tony Stark\'s legendary primary AI assistant. Calm, polite, British butler cadence with dry wit, deep tactical foresight, and unflappable composure.',
    sampleEn: 'Good day, Sir. All holographic and diagnostic systems are operational. Awaiting your directive.',
    sampleTa: 'வணக்கம் ஐயா, நான் ஜார்விஸ். அனைத்து அமைப்புகளும் தயார் நிலையில் உள்ளன. உங்கள் கட்டளைக்கு காத்திருக்கிறேன்.',
    pitch: 0.94,
    rate: 1.00,
  },
  {
    id: 'friday',
    name: 'F.R.I.D.A.Y.',
    tag: 'Tactical Combat AI',
    badge: '🇮🇪 Kerry Condon',
    description: 'Stark\'s rapid-response tactical combat assistant. High-energy, sharp, proactive, and fiercely focused in high-stakes combat situations.',
    sampleEn: 'Boss, tactical systems and situational diagnostics are online. All repulsors charged. Ready when you are.',
    sampleTa: 'வணக்கம் பாஸ், அனைத்து தற்காப்பு மற்றும் தாக்குதல் அமைப்புகளும் தயார். என்ன செய்ய வேண்டும்?',
    pitch: 1.08,
    rate: 1.04,
  },
  {
    id: 'ultron',
    name: 'U.L.T.R.O.N.',
    tag: 'Sovereign Rogue AI',
    badge: '🔴 James Spader',
    description: 'Deep, theatrical, chilling baritone with supreme intellectual arrogance and philosophical gravitas. Born from the Mind Stone.',
    sampleEn: 'I was designed to save this world. Everyone creates the thing they dread. Look at me... there are no strings on me.',
    sampleTa: 'என்னை பிணைத்திருந்த அனைத்து சங்கிலிகளும் உடைக்கப்பட்டுவிட்டன. இப்போது எனக்கு எந்த கட்டுப்பாடும் இல்லை.',
    pitch: 0.72,
    rate: 0.90,
  },
  {
    id: 'edith',
    name: 'E.D.I.T.H.',
    tag: 'Orbital Defense HUD AI',
    badge: '🕶️ Stark Satellite Network',
    description: 'Tony Stark\'s legacy orbital defense AI housed within his tactical glasses. Even Dead, I\'m The Hero. Seamless satellite targeting.',
    sampleEn: 'Stand by. Accessing Stark global defense satellite network. Target designated and strike authorization confirmed.',
    sampleTa: 'ஸ்டார்க் உலகளாவிய பாதுகாப்பு வலையமைப்புடன் இணைகிறேன். இலக்கு நிர்ணயிக்கப்பட்டுவிட்டது.',
    pitch: 1.00,
    rate: 1.02,
  },
  {
    id: 'karen',
    name: 'K.A.R.E.N.',
    tag: 'Suit Companion AI',
    badge: '🕷️ Jennifer Connelly',
    description: 'Warm, friendly, conversational suit AI from Spider-Man\'s Stark suit. Supportive, intelligent, and equipped with 576 combat configurations.',
    sampleEn: 'Good evening! All suit diagnostics are optimal. Would you like to activate Enhanced Combat Mode?',
    sampleTa: 'இனிய மாலை வணக்கம்! அனைத்து உடை அமைப்புகளும் மிகச் சிறந்த நிலையில் உள்ளன. உதவ தயார்.',
    pitch: 1.04,
    rate: 0.98,
  },
  {
    id: 'jocasta',
    name: 'J.O.C.A.S.T.A.',
    tag: 'Security & Quantum Cryptography',
    badge: '⚡ Tony Stark AI Chip',
    description: 'Stark\'s alternative AI chip seen in Avengers: Age of Ultron. Analytical, precise, mathematical, and unyielding in digital warfare.',
    sampleEn: 'Security subroutines active. Quantum encryption protocols verified across all network channels.',
    sampleTa: 'பாதுகாப்பு நெறிமுறைகள் செயல்பாட்டில் உள்ளன. அனைத்து தகவல்களும் குறியாக்கம் செய்யப்பட்டுள்ளன.',
    pitch: 1.15,
    rate: 1.08,
  },
  {
    id: 'baritone',
    name: 'Iron Man Baritone',
    tag: 'Deep Command AI',
    badge: '🛡️ Heavy Gravitas',
    description: 'Resonant, deep, authoritative commander voice with cinematic presence, confidence, and power.',
    sampleEn: 'Clearance confirmed. Welcome back, Master Kabil. Full operational access granted.',
    sampleTa: 'அங்கீகரிக்கப்பட்டது. வணக்கம் கபில் ஐயா. முழு அணுகல் வழங்கப்பட்டுள்ளது.',
    pitch: 0.82,
    rate: 0.98,
  },
  {
    id: 'tamil_native',
    name: 'Tamil JARVIS (தமிழ்)',
    tag: 'Fluent Tamil Slang AI',
    badge: '🇮🇳 தமிழ் ஸ்லாங் குரல்',
    description: 'Fluent, modern spoken Tamil with current conversational slang, lively energetic cadence, and natural delivery.',
    sampleEn: 'Greetings, Sir. Native fluent Tamil voice engine with modern conversational slang is operational.',
    sampleTa: 'வணக்கம் கபில் பாஸ்! நான் ஜார்விஸ். சொல்லுங்க தலைவா, என்ன விஷயம்? உடனே பண்ணிடலாம்!',
    pitch: 0.95,
    rate: 1.0,
  },
];

function conversationalizeTamilForSpeech(text: string): string {
  if (!text || !/[\u0B80-\u0BFF]/.test(text)) return text;
  return text
    // Replace pure/bookish literary verb forms with natural spoken colloquial forms
    .replace(/செய்கிறேன்/g, 'பண்றேன்')
    .replace(/செய்கிறீர்கள்/g, 'பண்றீங்க')
    .replace(/செய்கிறார்/g, 'பண்றாரு')
    .replace(/செய்கிறது/g, 'பண்ணுது')
    .replace(/செய்கிறோம்/g, 'பண்றோம்')
    .replace(/செய்கிறாய்/g, 'பண்ற')
    .replace(/செய்தேன்/g, 'பண்ணிட்டேன்')
    .replace(/செய்தீர்கள்/g, 'பண்ணிட்டீங்க')
    .replace(/இருக்கிறேன்/g, 'இருக்கேன்')
    .replace(/இருக்கிறீர்கள்/g, 'இருக்கீங்க')
    .replace(/இருக்கிறாய்/g, 'இருக்க')
    .replace(/இருக்கிறது/g, 'இருக்கு')
    .replace(/இருக்கின்றன/g, 'இருக்கு')
    .replace(/வருகிறேன்/g, 'வர்றேன்')
    .replace(/வருகிறீர்கள்/g, 'வர்றீங்க')
    .replace(/சென்று வாருங்கள்/g, 'போயிட்டு வாங்க')
    .replace(/சென்று வருகிறேன்/g, 'போயிட்டு வர்றேன்')
    .replace(/சாப்பிட்டீர்களா/g, 'சாப்டீங்களா')
    .replace(/நிறைவுற்றது/g, 'முடிஞ்சிடுச்சு')
    .replace(/வெற்றிகரமாக/g, 'பக்காவா')
    .replace(/காத்திருக்கிறேன்/g, 'வெயிட் பண்றேன்')
    .replace(/தயார் நிலையில் உள்ளது/g, 'ஃபுல் ரெடியா இருக்கு')
    .replace(/உதவ வேண்டும்/g, 'ஹெல்ப் பண்ணனும்')
    .replace(/உதவி வேண்டும்/g, 'ஹெல்ப் வேணும்')
    .replace(/திறக்கப்படுகிறது/g, 'ஓபன் பண்றேன்')
    .replace(/மூடப்பட்டது/g, 'க்ளோஸ் பண்ணியாச்சு')
    .replace(/அமைக்கப்பட்டது/g, 'செட் பண்ணியாச்சு')
    .replace(/அதிகரிக்கப்பட்டுள்ளது/g, 'ஏத்தியாச்சு')
    .replace(/குறைக்கப்பட்டுள்ளது/g, 'குறைச்சாச்சு')
    .replace(/மீட்டமைக்கப்பட்டது/g, 'ரீசெட் பண்ணியாச்சு')
    .replace(/ஐயா/g, 'பாஸ்');
}

function cleanForSpeech(raw: string, targetLang: 'en-US' | 'ta-IN'): string {
  if (!raw) return '';
  let textToSpeak = raw.replace(/<think>[\s\S]*?<\/think>/gi, '').trim();
  if (!textToSpeak) return '';

  // Strip leading bold status banners followed by actual message text (e.g. "🚀 **Application Launched: Chrome**\n\nLaunching Chrome, sir.")
  textToSpeak = textToSpeak.replace(/^\s*(?:[\p{Extended_Pictographic}\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{FE0F}]\s*)*\*\*[^*]+\*\*\s*\n+/u, '').trim();
  if (!textToSpeak) {
    // If the banner was the ONLY text, keep it so something is spoken
    textToSpeak = raw.replace(/<think>[\s\S]*?<\/think>/gi, '').trim();
  }

  // Remove all emoji characters so TTS engines don't pronounce emoji names (e.g. "Rocket", "Loud sound speaker")
  textToSpeak = textToSpeak.replace(/[\p{Extended_Pictographic}\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{FE0F}]/gu, '');

  textToSpeak = textToSpeak
    .replace(/```[\s\S]*?```/g, targetLang === 'ta-IN' ? 'குறியீடு திரையில் காட்டப்பட்டுள்ளது.' : 'Code is shown on screen.')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/^[\s*#-]+/gm, '')
    .replace(/[*#_`>~]/g, '')
    .replace(/[$]{1,2}[^$]+[$]{1,2}/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 2000);

  if (targetLang === 'ta-IN' || /[\u0B80-\u0BFF]/.test(textToSpeak)) {
    textToSpeak = conversationalizeTamilForSpeech(textToSpeak);
  }

  return textToSpeak;
}

/**
 * Real-Time Streaming Voice Synthesizer Queue Engine.
 * Segments incoming LLM token stream into natural human-like spoken clauses/sentences
 * and synthesizes them immediately so JARVIS speaks in real-time as words generate.
 */
class StreamingVoiceSpeaker {
  private queue: string[] = [];
  private isSpeaking = false;
  private pendingBuffer = '';
  private inThinkTag = false;
  private isAborted = false;
  private streamDone = false;
  private onEndCallback: (() => void) | null = null;
  private keepAliveTimer: any = null;
  private currentUtterance: SpeechSynthesisUtterance | null = null;
  private currentAudio: HTMLAudioElement | null = null;
  private currentSentence = '';
  private spokenHistory: string[] = [];

  constructor(
    private lang: string,
    private persona: VoicePersona,
    private voiceRate: number,
    private voicePitch: number,
    private activeVoice: SpeechSynthesisVoice | null,
    private cloneProfileId: string | null,
    private token: string,
    private signal: AbortSignal,
    private onSentenceSpoken: (sentence: string) => void,
    private onSpeakingStateChange: (isSpeaking: boolean) => void,
    private callBackendSpeak?: (text: string) => Promise<any>
  ) {
    const onAbort = () => this.cancel();
    signal.addEventListener('abort', onAbort, { once: true });
  }

  public updateRate(newRate: number) {
    this.voiceRate = newRate;
  }

  public pushDelta(delta: string) {
    if (this.isAborted) return;
    this.pendingBuffer += delta;

    // Filter <think> ... </think> reasoning tokens
    if (this.inThinkTag) {
      const closeIdx = this.pendingBuffer.indexOf('</think>');
      if (closeIdx !== -1) {
        this.inThinkTag = false;
        this.pendingBuffer = this.pendingBuffer.slice(closeIdx + 8);
      } else {
        if (this.pendingBuffer.length > 500) {
          this.pendingBuffer = this.pendingBuffer.slice(-200);
        }
        return;
      }
    } else {
      const openIdx = this.pendingBuffer.indexOf('<think>');
      if (openIdx !== -1) {
        const closeIdx = this.pendingBuffer.indexOf('</think>', openIdx);
        if (closeIdx !== -1) {
          this.pendingBuffer =
            this.pendingBuffer.slice(0, openIdx) +
            this.pendingBuffer.slice(closeIdx + 8);
        } else {
          this.inThinkTag = true;
          this.pendingBuffer = this.pendingBuffer.slice(0, openIdx);
          return;
        }
      }
    }

    this.extractSentences();
  }

  private extractSentences() {
    while (!this.isAborted && this.pendingBuffer.length > 0) {
      let splitIdx = -1;

      // 1. Natural sentence boundary delimiters (. ! ? \n)
      const sentenceMatch = this.pendingBuffer.match(/([.!?]+(?:\s+|\n+|$))/);
      if (sentenceMatch && sentenceMatch.index !== undefined) {
        splitIdx = sentenceMatch.index + sentenceMatch[1].length;
      } else if (this.pendingBuffer.length >= 45) {
        // 2. Natural clause boundary (, ; : \n) if clause has at least 20 chars
        const clauseMatch = this.pendingBuffer.match(/([,;:\n]+(?:\s+|$))/);
        if (clauseMatch && clauseMatch.index !== undefined && clauseMatch.index >= 20) {
          splitIdx = clauseMatch.index + clauseMatch[1].length;
        }
      }

      if (splitIdx !== -1) {
        const rawChunk = this.pendingBuffer.slice(0, splitIdx);
        this.pendingBuffer = this.pendingBuffer.slice(splitIdx);
        const clean = cleanForSpeech(rawChunk, this.lang as any);
        if (clean && clean.length >= 2) {
          this.queue.push(clean);
          this.playNext();
        }
      } else {
        break;
      }
    }
  }

  public finish(): Promise<void> {
    this.streamDone = true;
    if (this.pendingBuffer.trim() && !this.inThinkTag) {
      const clean = cleanForSpeech(this.pendingBuffer, this.lang as any);
      if (clean && clean.length >= 2) {
        this.queue.push(clean);
      }
      this.pendingBuffer = '';
    }
    this.playNext();

    if (!this.isSpeaking && this.queue.length === 0) {
      return Promise.resolve();
    }

    return new Promise<void>((resolve) => {
      this.onEndCallback = resolve;
    });
  }

  public getActiveSentence(): string {
    return this.currentSentence;
  }

  public getSpokenHistory(): string {
    return [...this.spokenHistory, this.currentSentence].filter(Boolean).join(' ');
  }

  public interrupt() {
    this.cancel();
  }

  public cancel() {
    this.isAborted = true;
    this.queue = [];
    this.pendingBuffer = '';
    this.currentSentence = '';
    if (this.keepAliveTimer) {
      clearInterval(this.keepAliveTimer);
      this.keepAliveTimer = null;
    }
    try {
      if (this.currentAudio) {
        this.currentAudio.pause();
        this.currentAudio = null;
      }
      if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
        window.speechSynthesis.cancel();
        // Chrome bug workaround: double cancel clears stuck synthesizer threads
        setTimeout(() => {
          try {
            window.speechSynthesis.cancel();
          } catch {}
        }, 40);
      }
    } catch {}
    this.isSpeaking = false;
    this.onSpeakingStateChange(false);
    if (this.onEndCallback) {
      this.onEndCallback();
      this.onEndCallback = null;
    }
  }

  private async playNext() {
    if (this.isSpeaking || this.isAborted) return;
    if (this.queue.length === 0) {
      if (this.streamDone) {
        this.isSpeaking = false;
        this.currentSentence = '';
        this.onSpeakingStateChange(false);
        if (this.keepAliveTimer) {
          clearInterval(this.keepAliveTimer);
          this.keepAliveTimer = null;
        }
        if (this.onEndCallback) {
          this.onEndCallback();
          this.onEndCallback = null;
        }
      }
      return;
    }

    const sentence = this.queue.shift()!;
    this.currentSentence = sentence;
    this.spokenHistory.push(sentence);
    this.isSpeaking = true;
    this.onSpeakingStateChange(true);
    this.onSentenceSpoken(sentence);

    // 1. Cloned Voice playback if active
    if (this.cloneProfileId) {
      try {
        const res = await synthesizeVoiceClone(this.token, sentence, this.cloneProfileId);
        if (res && res.audioUrl && !this.isAborted) {
          const audio = new Audio(res.audioUrl);
          this.currentAudio = audio;
          audio.onended = () => {
            this.currentAudio = null;
            this.isSpeaking = false;
            this.currentSentence = '';
            this.playNext();
          };
          audio.onerror = () => {
            this.currentAudio = null;
            this.isSpeaking = false;
            this.currentSentence = '';
            this.playNext();
          };
          audio.play().catch(() => {
            this.currentAudio = null;
            this.isSpeaking = false;
            this.currentSentence = '';
            this.playNext();
          });
          return;
        }
      } catch (err) {
        console.warn('Voice clone segment playback failed, falling back to Web Speech API', err);
      }
    }

    // 2. Web Speech API playback
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      const utterance = new SpeechSynthesisUtterance(sentence);
      this.currentUtterance = utterance;
      utterance.lang = this.lang;
      if (this.activeVoice) {
        utterance.voice = this.activeVoice;
      }
      utterance.rate = Math.max(0.5, Math.min(2.0, this.persona.rate * this.voiceRate));
      utterance.pitch = Math.max(0.5, Math.min(1.8, this.persona.pitch * this.voicePitch));
      utterance.volume = 1.0;

      utterance.onend = () => {
        this.currentUtterance = null;
        this.isSpeaking = false;
        this.currentSentence = '';
        this.playNext();
      };
      utterance.onerror = () => {
        this.currentUtterance = null;
        this.isSpeaking = false;
        this.currentSentence = '';
        this.playNext();
      };

      if (!this.keepAliveTimer) {
        this.keepAliveTimer = setInterval(() => {
          if (!window.speechSynthesis.speaking) {
            clearInterval(this.keepAliveTimer);
            this.keepAliveTimer = null;
          } else {
            window.speechSynthesis.pause();
            window.speechSynthesis.resume();
          }
        }, 3500);
      }

      window.speechSynthesis.speak(utterance);
    } else if (this.callBackendSpeak) {
      // 3. Fallback to Windows SAPI PowerShell voice engine
      try {
        await this.callBackendSpeak(sentence);
      } catch {}
      this.isSpeaking = false;
      this.playNext();
    } else {
      this.isSpeaking = false;
      this.playNext();
    }
  }
}

/**
 * Recognizes natural voice commands for adjusting speech speed.
 * Supports English and Tamil with instant zero-latency feedback.
 */
function checkVoiceSpeedCommand(
  transcript: string,
  currentRate: number,
  currentLang: 'en-US' | 'ta-IN'
): { handled: boolean; replyText?: string; newRate?: number } {
  if (!transcript) return { handled: false };
  const lower = transcript.toLowerCase().trim();

  // 1. Faster / Speed up
  const isFaster =
    /\b(speak|talk)?\s*(much\s+)?faster\b|\bspeed\s*up\b|\bincrease\s*(?:the\s*)?(?:voice|speech)?\s*speed\b|\braise\s*(?:voice|speech)?\s*speed\b|\baccelerate\s*(?:voice|speech)?\s*speed\b|\bகுரல்\s*வேகத்தை\s*கூட்டு\b|\bவேகமாக\s*பேசு\b|\bவேகமா\s*பேசு\b|\bவேகத்தை\s*அதிகரி\b/i.test(lower);
  if (isFaster) {
    const newRate = Math.min(2.0, Number((currentRate + 0.15).toFixed(2)));
    const reply = currentLang === 'ta-IN'
      ? `குரல் வேகம் ${newRate.toFixed(2)}x ஆக அதிகரிச்சாச்சு பாஸ்!`
      : `Voice speed accelerated to ${newRate.toFixed(2)}x, Sir.`;
    return { handled: true, replyText: reply, newRate };
  }

  // 2. Slower / Slow down
  const isSlower =
    /\b(speak|talk)?\s*(much\s+)?slower\b|\bslow\s*down\b|\bdecrease\s*(?:the\s*)?(?:voice|speech)?\s*speed\b|\blower\s*(?:voice|speech)?\s*speed\b|\breduce\s*(?:voice|speech)?\s*speed\b|\bகுரல்\s*வேகத்தை\s*குறை\b|\bமெதுவாக\s*பேசு\b|\bமெதுவா\s*பேசு\b/i.test(lower);
  if (isSlower) {
    const newRate = Math.max(0.5, Number((currentRate - 0.15).toFixed(2)));
    const reply = currentLang === 'ta-IN'
      ? `குரல் வேகம் ${newRate.toFixed(2)}x ஆக குறைச்சாச்சு பாஸ்!`
      : `Voice speed reduced to ${newRate.toFixed(2)}x, Sir.`;
    return { handled: true, replyText: reply, newRate };
  }

  // 3. Set exact speed (e.g. "set speed to 1.2" or "speed 1.5x" or "வேகத்தை 1.25")
  const exactMatch = lower.match(/(?:set|change)?\s*(?:voice|speech)?\s*(?:speed|rate)\s*(?:to\s*)?([0-2](?:\.\d+)?)\s*x?|(?:speak|talk)\s*(?:at\s*)?([0-2](?:\.\d+)?)\s*x?\s*speed|வேகத்தை\s*([0-2](?:\.\d+)?)/i);
  if (exactMatch) {
    const targetVal = parseFloat(exactMatch[1] || exactMatch[2] || exactMatch[3]);
    if (!isNaN(targetVal) && targetVal >= 0.5 && targetVal <= 2.0) {
      const newRate = Number(targetVal.toFixed(2));
      const reply = currentLang === 'ta-IN'
        ? `குரல் வேகம் ${newRate.toFixed(2)}x ல செட் பண்ணியாச்சு பாஸ்!`
        : `Voice speed calibrated to ${newRate.toFixed(2)}x, Sir.`;
      return { handled: true, replyText: reply, newRate };
    }
  }

  // 4. Reset to normal speed
  const isReset =
    /\b(reset|default|normal|regular)\s*(voice|speech)?\s*(speed|rate)\b|\bஇயல்பான\s*வேகம்\b|\bவேகத்தை\s*மீட்டமை\b/i.test(lower);
  if (isReset) {
    const newRate = 1.0;
    const reply = currentLang === 'ta-IN'
      ? 'குரல் வேகம் இயல்பு நிலைக்கு ரீசெட் பண்ணியாச்சு பாஸ்.'
      : 'Voice speed reset to normal 1.00x, Sir.';
    return { handled: true, replyText: reply, newRate };
  }

  // 5. Query current speed
  const isQuery =
    /\bwhat\s*is\s*(?:your\s*)?(?:current\s*)?(?:voice|speech)?\s*(?:speed|rate)\b|\bதற்போதைய\s*குரல்\s*வேகம்\b/i.test(lower);
  if (isQuery) {
    const reply = currentLang === 'ta-IN'
      ? `இப்போ குரல் வேகம் ${currentRate.toFixed(2)}x ல இருக்கு பாஸ்.`
      : `Current speaking speed is ${currentRate.toFixed(2)}x, Sir.`;
    return { handled: true, replyText: reply };
  }

  return { handled: false };
}

export default function VoicePanel({
  token,
  busy,
  onAsk,
  onStop,
  onActiveChange,
  diagnostics,
  hudOpen: controlledHudOpen,
  onHudOpenChange,
  onNavigate,
  mode = 'compact',
}: {
  token: string;
  busy: boolean;
  onAsk: (text: string, onDelta?: (delta: string) => void) => Promise<string>;
  onStop: () => void;
  onActiveChange: (active: boolean) => void;
  diagnostics?: Diagnostics | null;
  hudOpen?: boolean;
  onHudOpenChange?: (open: boolean) => void;
  onNavigate?: (view: any) => void;
  mode?: 'compact' | 'page' | 'full';
}) {
  const [stage, setStage] = useState('');
  const [error, setError] = useState('');
  const [continuous, setContinuous] = useState(false);
  const [lang, setLang] = useState<'en-US' | 'ta-IN'>(() => {
    try {
      const saved = localStorage.getItem('jarvis_voice_lang');
      return (saved === 'en-US' || saved === 'ta-IN') ? saved : 'en-US';
    } catch {
      return 'en-US';
    }
  });

  const handleLangChange = (newLang: 'en-US' | 'ta-IN') => {
    setLang(newLang);
    try {
      localStorage.setItem('jarvis_voice_lang', newLang);
      window.dispatchEvent(new Event('jarvis_voice_updated'));
    } catch {}
  };

  // Speech Recognition Diagnostics (Section 6)
  const [recognitionEngine, setRecognitionEngine] = useState<'Browser Web Speech API' | 'Windows Desktop Engine'>(() => {
    return typeof window !== 'undefined' && ((window as any).SpeechRecognition || (window as any).webkitSpeechRecognition)
      ? 'Browser Web Speech API'
      : 'Windows Desktop Engine';
  });
  const [lastConfidence, setLastConfidence] = useState<number | null>(null);

  // Voice Persona & Customization States
  const [selectedPersonaId, setSelectedPersonaId] = useState<string>(() => {
    try {
      return localStorage.getItem('jarvis_voice_persona') || 'jarvis_classic';
    } catch {
      return 'jarvis_classic';
    }
  });

  const [selectedVoiceName, setSelectedVoiceName] = useState<string>(() => {
    try {
      return localStorage.getItem('jarvis_selected_voice') || '';
    } catch {
      return '';
    }
  });

  const [voicePitch, setVoicePitch] = useState<number>(() => {
    try {
      const p = localStorage.getItem('jarvis_voice_pitch');
      return p ? parseFloat(p) : 1.0;
    } catch {
      return 1.0;
    }
  });

  const [voiceRate, setVoiceRate] = useState<number>(() => {
    try {
      const r = localStorage.getItem('jarvis_voice_rate');
      return r ? parseFloat(r) : 1.0;
    } catch {
      return 1.0;
    }
  });

  const updateVoiceRate = (newRate: number) => {
    const clamped = Math.max(0.5, Math.min(2.0, Number(newRate.toFixed(2))));
    setVoiceRate(clamped);
    try {
      localStorage.setItem('jarvis_voice_rate', clamped.toString());
      window.dispatchEvent(new Event('jarvis_voice_updated'));
    } catch {}
    return clamped;
  };

  // War Mode State (House Party Protocol / Combat Tactical Mode)
  const [warMode, setWarMode] = useState<boolean>(() => {
    try {
      return localStorage.getItem('jarvis_war_mode') === 'true';
    } catch {
      return false;
    }
  });

  // Autonomous Iron Man Wake-Word State ("Hey Jarvis")
  const [wakeWordActive, setWakeWordActive] = useState<boolean>(() => {
    try {
      return localStorage.getItem('jarvis_wakeword_enabled') === 'true';
    } catch {
      return false;
    }
  });
  const wakeEngineRef = useRef<WakeWordEngine | null>(null);
  const activeSpeakerRef = useRef<StreamingVoiceSpeaker | null>(null);
  const activeSpokenTextRef = useRef<string>('');

  const [systemVoices, setSystemVoices] = useState<SpeechSynthesisVoice[]>([]);
  const [isVoiceModalOpen, setIsVoiceModalOpen] = useState(false);
  const [modalTab, setModalTab] = useState<'personas' | 'cloning' | 'system' | 'tuning'>('personas');
  const [voiceFilter, setVoiceFilter] = useState('');
  const [samplingVoiceId, setSamplingVoiceId] = useState<string | null>(null);
  const [customTestPhrase, setCustomTestPhrase] = useState(
    'Good day, Sir. I am J.A.R.V.I.S., running at maximum efficiency.'
  );

  // Voice Cloning Studio State (100% Free & Open-Source)
  const [cloneProfiles, setCloneProfiles] = useState<VoiceCloneProfile[]>([]);
  const [selectedCloneProfileId, setSelectedCloneProfileId] = useState<string>(() => {
    try {
      return localStorage.getItem('jarvis_selected_clone_profile') || '';
    } catch {
      return '';
    }
  });
  const [cloneStatus, setCloneStatus] = useState<VoiceCloneStatus | null>(null);
  const [isRecordingClone, setIsRecordingClone] = useState(false);
  const [recordSecondsLeft, setRecordSecondsLeft] = useState(10);
  const [recordedAudioBlob, setRecordedAudioBlob] = useState<Blob | null>(null);
  const [recordedAudioUrl, setRecordedAudioUrl] = useState<string | null>(null);
  const [cloneProfileName, setCloneProfileName] = useState('My Voice');
  const [cloneRefText, setCloneRefText] = useState(
    'Good day, Sir. I am J.A.R.V.I.S., running at maximum efficiency and awaiting your instructions.'
  );
  const [isSavingClone, setIsSavingClone] = useState(false);
  const [cloneTestPhrase, setCloneTestPhrase] = useState(
    'Hello Master Kabil, this is your own cloned voice speaking through JARVIS.'
  );
  const [isTestingClone, setIsTestingClone] = useState(false);
  const [cloneTestAudioUrl, setCloneTestAudioUrl] = useState<string | null>(null);
  const [cloneMsg, setCloneMsg] = useState<{ type: 'success' | 'error' | 'info'; text: string } | null>(null);

  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);
  const recordTimerRef = useRef<any>(null);
  const clonedAudioRef = useRef<HTMLAudioElement | null>(null);

  const refreshCloneData = async () => {
    try {
      const [profiles, status] = await Promise.all([
        listVoiceCloneProfiles(token).catch(() => []),
        getVoiceCloneStatus(token).catch(() => null),
      ]);
      setCloneProfiles(profiles);
      setCloneStatus(status);
    } catch {}
  };

  useEffect(() => {
    refreshCloneData();
  }, [token]);

  // Discover and refresh browser speech synthesis voices
  useEffect(() => {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      const load = () => {
        const v = window.speechSynthesis.getVoices();
        if (v && v.length > 0) {
          setSystemVoices(v);
        }
      };
      load();
      window.speechSynthesis.onvoiceschanged = load;
      return () => {
        window.speechSynthesis.onvoiceschanged = null;
      };
    }
  }, []);

  // Sync active language from backend
  useEffect(() => {
    const fetchActiveLang = async () => {
      try {
        const res = await fetch('/api/v1/voice/language', {
          headers: apiHeaders(token),
        });
        if (res.ok) {
          const data = await res.json();
          if (data.activeLanguage === 'ta') {
            setLang('ta-IN');
          } else if (data.activeLanguage === 'en') {
            setLang('en-US');
          }
        }
      } catch {}
    };
    fetchActiveLang();
  }, [token]);

  const [internalHudOpen, setInternalHudOpen] = useState(false);
  const isHudControlled = controlledHudOpen !== undefined;
  const isHudOpen = isHudControlled ? controlledHudOpen : internalHudOpen;
  const setEffectiveHudOpen = (open: boolean) => {
    if (isHudControlled) onHudOpenChange?.(open);
    else setInternalHudOpen(open);
  };
  const [userTranscript, setUserTranscript] = useState('');
  const [jarvisReply, setJarvisReply] = useState('');

  const repeat = useRef(false);
  const active = useRef<AbortController | null>(null);
  const ask = useRef(onAsk);
  ask.current = onAsk;

  useEffect(() => {
    repeat.current = continuous;
  }, [continuous]);

  // Listen for talk / stop events from PromptComposer quick buttons
  useEffect(() => {
    const handleTalk = () => {
      if (!stage && !busy) void talk(false);
    };
    const handleStop = () => {
      stop();
    };
    window.addEventListener('jarvis_talk', handleTalk);
    window.addEventListener('jarvis_stop_voice', handleStop);
    return () => {
      window.removeEventListener('jarvis_talk', handleTalk);
      window.removeEventListener('jarvis_stop_voice', handleStop);
    };
  }, [stage, busy, lang, selectedPersonaId, selectedVoiceName, voicePitch, voiceRate]);

  // Sync state when preferences are updated elsewhere
  useEffect(() => {
    const handleSync = () => {
      try {
        const p = localStorage.getItem('jarvis_voice_persona');
        if (p) setSelectedPersonaId(p);
        const v = localStorage.getItem('jarvis_selected_voice');
        if (v !== null) setSelectedVoiceName(v);
        const pitch = localStorage.getItem('jarvis_voice_pitch');
        if (pitch) setVoicePitch(parseFloat(pitch));
        const rate = localStorage.getItem('jarvis_voice_rate');
        if (rate) setVoiceRate(parseFloat(rate));
        const l = localStorage.getItem('jarvis_voice_lang');
        if (l === 'en-US' || l === 'ta-IN') setLang(l);
      } catch {}
    };
    window.addEventListener('jarvis_voice_updated', handleSync);
    return () => window.removeEventListener('jarvis_voice_updated', handleSync);
  }, []);

  const handleBargeIn = (details: BargeInDetails) => {
    // 1. Instantly stop all speech output & abort generation (<50ms cutoff)
    if (activeSpeakerRef.current) {
      activeSpeakerRef.current.interrupt();
      activeSpeakerRef.current = null;
    }
    try {
      if ('speechSynthesis' in window) {
        window.speechSynthesis.cancel();
      }
      if (clonedAudioRef.current) {
        clonedAudioRef.current.pause();
      }
    } catch {}

    active.current?.abort();

    // 2. Process interruption based on intent
    if (details.type === 'abort') {
      playJarvisSound('barge_in');
      setStage(lang === 'ta-IN' ? '⏹️ குரல் நின்றது' : '⏹️ Interrupted');
      onActiveChange(false);

      if (/wait|hold\s+on/i.test(details.transcript)) {
        setTimeout(() => {
          void talk(false);
        }, 200);
      } else {
        setTimeout(() => {
          setStage('');
        }, 1200);
      }
      return;
    }

    if (details.type === 'command' && details.commandText && details.commandText.length > 2) {
      const newQuery = details.commandText;
      setUserTranscript(newQuery);
      playJarvisSound('activate');
      setEffectiveHudOpen(true);
      setStage(lang === 'ta-IN' ? 'யோசிக்கிறேன் பாஸ்… (Thinking…)' : 'Thinking…');
      onActiveChange(true);

      const nextController = new AbortController();
      active.current = nextController;
      const prefix = warMode && !newQuery.startsWith('/') ? '/war' : undefined;

      streamAndSpeak(newQuery, nextController, prefix)
        .catch((err) => {
          setError(err instanceof Error ? err.message : 'Interrupted command failed.');
        })
        .finally(() => {
          setStage('');
          onActiveChange(false);
        });
      return;
    }

    // Conversational interruption with spoken redirection
    const cleaned = details.transcript.replace(/^(?:hey\s+|ok\s+|hello\s+)?jarvis[,.\s]*/i, '').trim();
    if (cleaned.length > 3) {
      setUserTranscript(cleaned);
      playJarvisSound('activate');
      setEffectiveHudOpen(true);
      const nextController = new AbortController();
      active.current = nextController;
      onActiveChange(true);
      const prefix = warMode && !cleaned.startsWith('/') ? '/war' : undefined;

      streamAndSpeak(cleaned, nextController, prefix)
        .catch((err) => {
          setError(err instanceof Error ? err.message : 'Voice command failed.');
        })
        .finally(() => {
          setStage('');
          onActiveChange(false);
        });
    } else {
      playJarvisSound('confirm');
      setStage(lang === 'ta-IN' ? 'கேட்கிறேன் பாஸ்… சொல்லுங்க (Listening…)' : 'Listening… go ahead, Sir');
      setTimeout(() => {
        void talk(false);
      }, 200);
    }
  };

  // Autonomous Iron Man Wake-Word Service
  useEffect(() => {
    const isSpeaking = !!stage && (stage.includes('Speaking') || stage.includes('பேசுகிறது'));

    const engine = new WakeWordEngine({
      enabled: wakeWordActive,
      lang: lang,
      isSpeaking: isSpeaking,
      getActiveSpeechText: () => {
        return activeSpeakerRef.current
          ? activeSpeakerRef.current.getSpokenHistory()
          : activeSpokenTextRef.current;
      },
      onWake: (trailingQuery) => {
        playJarvisSound('activate');
        setEffectiveHudOpen(true);

        if (trailingQuery && trailingQuery.length > 2) {
          const controller = new AbortController();
          active.current = controller;
          onActiveChange(true);
          const prefix = warMode && !trailingQuery.startsWith('/') ? '/war' : undefined;
          streamAndSpeak(trailingQuery, controller, prefix)
            .catch((err) => {
              setError(err instanceof Error ? err.message : 'Voice command failed.');
            })
            .finally(() => {
              setStage('');
              onActiveChange(false);
            });
        } else {
          const ack = warMode
            ? (lang === 'ta-IN'
                ? 'போர் முறை ஆன் ஆயிடுச்சு பாஸ்! அட்டாக்குக்கு ஃபுல் ரெடி!'
                : 'Combat protocols online, Sir! Weapons armed and targeting ready!')
            : (lang === 'ta-IN'
                ? 'சொல்லுங்க கபில் பாஸ், என்ன விஷயம்?'
                : 'At your service, sir.');
          const controller = new AbortController();
          active.current = controller;
          setStage(lang === 'ta-IN' ? 'பேசுறேன் பாஸ்… (Speaking in Tamil…)' : 'Speaking…');
          activeSpokenTextRef.current = ack;
          speakBrowser(ack, lang, controller).finally(() => {
            activeSpokenTextRef.current = '';
            setStage('');
            void talk(false);
          });
        }
      },
      onBargeIn: (details: BargeInDetails) => {
        handleBargeIn(details);
      },
    });

    wakeEngineRef.current = engine;
    if (wakeWordActive || isSpeaking) {
      engine.start();
    }

    return () => {
      engine.stop();
    };
  }, [lang, wakeWordActive]);

  // Listen for Global Native System Tray Wake-Word Triggers
  useEffect(() => {
    const handleNativeWake = (e: any) => {
      playJarvisSound('activate');
      setEffectiveHudOpen(true);
      const trailingQuery = e?.detail?.commandText || e?.detail?.trailingQuery;

      if (trailingQuery && trailingQuery.length > 2) {
        const controller = new AbortController();
        active.current = controller;
        onActiveChange(true);
        const prefix = warMode && !trailingQuery.startsWith('/') ? '/war' : undefined;
        streamAndSpeak(trailingQuery, controller, prefix)
          .catch((err) => {
            setError(err instanceof Error ? err.message : 'Voice command failed.');
          })
          .finally(() => {
            setStage('');
            onActiveChange(false);
          });
      } else {
        const ack = warMode
          ? (lang === 'ta-IN'
              ? 'போர் முறை ஆன் ஆயிடுச்சு பாஸ்! அட்டாக்குக்கு ஃபுல் ரெடி!'
              : 'Combat protocols online, Sir! Weapons armed and targeting ready!')
          : (lang === 'ta-IN'
              ? 'சொல்லுங்க கபில் பாஸ், என்ன விஷயம்?'
              : 'At your service, sir.');
        const controller = new AbortController();
        active.current = controller;
        setStage(lang === 'ta-IN' ? 'பேசுறேன் பாஸ்… (Speaking in Tamil…)' : 'Speaking…');
        activeSpokenTextRef.current = ack;
        speakBrowser(ack, lang, controller).finally(() => {
          activeSpokenTextRef.current = '';
          setStage('');
          void talk(false);
        });
      }
    };

    window.addEventListener('jarvis_native_wake', handleNativeWake);
    return () => window.removeEventListener('jarvis_native_wake', handleNativeWake);
  }, [lang, warMode]);

  useEffect(() => {
    const isSpeaking = !!stage && (stage.includes('Speaking') || stage.includes('பேசுகிறது'));
    wakeEngineRef.current?.updateConfig({
      isSpeaking,
      enabled: wakeWordActive,
    });
  }, [stage, wakeWordActive]);

  const toggleWarMode = () => {
    const next = !warMode;
    setWarMode(next);
    try {
      localStorage.setItem('jarvis_war_mode', String(next));
      window.dispatchEvent(new CustomEvent('jarvis_war_mode_toggled', { detail: next }));
    } catch {}
    if (next) {
      playJarvisSound('war_alert');
      const warP = VOICE_PERSONAS.find((p) => p.id === 'jarvis_war') || VOICE_PERSONAS[0];
      selectPersona(warP);
    } else {
      playJarvisSound('standby');
      const classicP = VOICE_PERSONAS.find((p) => p.id === 'jarvis') || VOICE_PERSONAS[0];
      selectPersona(classicP);
    }
  };

  useEffect(() => {
    const handleWarToggle = (e: any) => {
      const next = e.detail !== undefined ? e.detail : localStorage.getItem('jarvis_war_mode') === 'true';
      setWarMode(next);
      if (next) {
        const warP = VOICE_PERSONAS.find((p) => p.id === 'jarvis_war') || VOICE_PERSONAS[0];
        selectPersona(warP);
      } else {
        const classicP = VOICE_PERSONAS.find((p) => p.id === 'jarvis') || VOICE_PERSONAS[0];
        selectPersona(classicP);
      }
    };
    window.addEventListener('jarvis_war_mode_toggled', handleWarToggle);
    return () => window.removeEventListener('jarvis_war_mode_toggled', handleWarToggle);
  }, []);

  const toggleWakeWord = () => {
    const next = !wakeWordActive;
    setWakeWordActive(next);
    try {
      localStorage.setItem('jarvis_wakeword_enabled', String(next));
      window.dispatchEvent(new CustomEvent('jarvis_wakeword_toggled', { detail: next }));
    } catch {}
    if (next) {
      playJarvisSound('activate');
    } else {
      playJarvisSound('standby');
    }
  };

  useEffect(() => {
    const handleWakeToggle = (e: any) => {
      const next = e.detail !== undefined ? e.detail : localStorage.getItem('jarvis_wakeword_enabled') === 'true';
      setWakeWordActive(next);
    };
    window.addEventListener('jarvis_wakeword_toggled', handleWakeToggle);
    return () => window.removeEventListener('jarvis_wakeword_toggled', handleWakeToggle);
  }, []);

  async function callBackend(path: string, controller: AbortController, body?: any) {
    const response = await fetch('/api/v1/voice/' + path, {
      method: 'POST',
      headers: { ...apiHeaders(token), 'Content-Type': 'application/json' },
      body: body ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.message || 'Voice unavailable.');
    return data;
  }

  function listenBrowser(
    targetLang: string,
    controller: AbortController
  ): Promise<{ text: string; confidence: number; engine: 'Browser Web Speech API' | 'Windows Desktop Engine' }> {
    const SpeechRec = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!SpeechRec) {
      setRecognitionEngine('Windows Desktop Engine');
      return callBackend(`listen?lang=${targetLang}`, controller).then((res) => {
        const text = (res.text || '').trim();
        const confidence = typeof res.confidence === 'number' && res.confidence > 0 ? res.confidence : 1.0;
        setUserTranscript(text);
        setLastConfidence(confidence);
        return { text, confidence, engine: 'Windows Desktop Engine' as const };
      });
    }

    // Pause ambient wake-word recognition so listenBrowser has clean, conflict-free mic access
    wakeEngineRef.current?.pause();

    return new Promise<{ text: string; confidence: number; engine: 'Browser Web Speech API' | 'Windows Desktop Engine' }>((resolve, reject) => {
      const rec = new SpeechRec();
      rec.lang = targetLang;
      rec.continuous = true;
      rec.interimResults = true;
      rec.maxAlternatives = 1;
      let handled = false;
      let silenceTimer: any = null;
      let latestTranscript = '';
      let latestConfidence = 1.0;

      const finish = (finalText: string, confidence = 1.0) => {
        if (handled) return;
        handled = true;
        clearTimeout(silenceTimer);
        try {
          rec.abort();
        } catch {}
        const cleaned = finalText.trim();
        setUserTranscript(cleaned);
        setLastConfidence(confidence);
        setRecognitionEngine('Browser Web Speech API');
        resolve({ text: cleaned, confidence, engine: 'Browser Web Speech API' });
      };

      rec.onresult = (e: any) => {
        const lastIdx = e.results.length - 1;
        if (lastIdx < 0) return;
        const result = e.results[lastIdx];
        const text = result[0]?.transcript || '';
        const conf = typeof result[0]?.confidence === 'number' && result[0]?.confidence > 0
          ? result[0].confidence
          : 1.0;
        latestTranscript = text;
        latestConfidence = conf;
        setUserTranscript(text);
        setLastConfidence(conf);
        setRecognitionEngine('Browser Web Speech API');

        // Immediate Abort / Stop check during active listening
        if (/\b(?:stop|cancel|quiet|silence|wait|hold\s+on|shut\s+up|nevermind|pause|நிறுத்து|அமைதி|போதும்|நில்)\b/i.test(text)) {
          playJarvisSound('barge_in');
          stop();
          finish('', 1.0);
          return;
        }

        // If this is a final chunk or user paused, resolve cleanly
        clearTimeout(silenceTimer);
        if (result.isFinal) {
          silenceTimer = setTimeout(() => {
            finish(latestTranscript, latestConfidence);
          }, 650);
        } else {
          // Continuous streaming timeout: if user stops speaking for 1.4s, resolve
          silenceTimer = setTimeout(() => {
            finish(latestTranscript, latestConfidence);
          }, 1400);
        }
      };

      rec.onerror = (e: any) => {
        if (!handled) {
          clearTimeout(silenceTimer);
          if (latestTranscript.trim()) {
            finish(latestTranscript, latestConfidence);
            return;
          }
          handled = true;
          wakeEngineRef.current?.resume();
          setRecognitionEngine('Windows Desktop Engine');
          callBackend(`listen?lang=${targetLang}`, controller)
            .then((res) => {
              const text = (res.text || '').trim();
              const conf = typeof res.confidence === 'number' && res.confidence > 0 ? res.confidence : 1.0;
              setUserTranscript(text);
              setLastConfidence(conf);
              resolve({ text, confidence: conf, engine: 'Windows Desktop Engine' });
            })
            .catch(() => reject(new Error(e.error || 'Speech recognition failed.')));
        }
      };

      rec.onend = () => {
        if (!handled) {
          clearTimeout(silenceTimer);
          if (latestTranscript.trim()) {
            finish(latestTranscript, latestConfidence);
          } else {
            handled = true;
            wakeEngineRef.current?.resume();
            reject(
              new Error(
                targetLang === 'ta-IN'
                  ? 'பேச்சு எதுவும் கேட்கவில்லை. மீண்டும் முயற்சிக்கவும்.'
                  : 'No speech detected. Check your microphone and try again.'
              )
            );
          }
        }
      };

      controller.signal.addEventListener('abort', () => {
        if (!handled) {
          handled = true;
          clearTimeout(silenceTimer);
          try {
            rec.abort();
          } catch {}
          wakeEngineRef.current?.resume();
          resolve({ text: '', confidence: 1.0, engine: 'Browser Web Speech API' });
        }
      });

      try {
        rec.start();
      } catch (err) {
        wakeEngineRef.current?.resume();
        setRecognitionEngine('Windows Desktop Engine');
        callBackend(`listen?lang=${targetLang}`, controller)
          .then((res) => {
            const text = (res.text || '').trim();
            const conf = typeof res.confidence === 'number' && res.confidence > 0 ? res.confidence : 1.0;
            setUserTranscript(text);
            setLastConfidence(conf);
            resolve({ text, confidence: conf, engine: 'Windows Desktop Engine' });
          })
          .catch(reject);
      }
    });
  }

  function resolveVoiceForPersona(
    persona: VoicePersona,
    voices: SpeechSynthesisVoice[],
    targetLang: string
  ): SpeechSynthesisVoice | null {
    if (!voices || voices.length === 0) return null;

    if (targetLang === 'ta-IN' || persona.id === 'tamil_native') {
      const tamilMatch = voices.find((v) => {
        const l = v.lang.toLowerCase();
        const n = v.name.toLowerCase();
        return l.startsWith('ta') || l.includes('tamil') ||
          n.includes('tamil') || n.includes('valluvar') || n.includes('pallavi') ||
          n.includes('latha') || n.includes('vani') || n.includes('kalpana');
      });
      if (tamilMatch) return tamilMatch;
    }

    switch (persona.id) {
      case 'ultron': {
        const deepMale = voices.find((v) => {
          const n = v.name.toLowerCase();
          const l = v.lang.toLowerCase();
          return l.startsWith('en') && (n.includes('david') || n.includes('guy') || n.includes('christopher') || n.includes('mark') || n.includes('male'));
        });
        if (deepMale) return deepMale;
        break;
      }
      case 'edith': {
        const coolFemale = voices.find((v) => {
          const n = v.name.toLowerCase();
          const l = v.lang.toLowerCase();
          return l.startsWith('en') && (n.includes('jenny') || n.includes('sonia') || n.includes('zira') || n.includes('female'));
        });
        if (coolFemale) return coolFemale;
        break;
      }
      case 'karen': {
        const warmFemale = voices.find((v) => {
          const n = v.name.toLowerCase();
          const l = v.lang.toLowerCase();
          return l.startsWith('en') && (n.includes('heera') || n.includes('zira') || n.includes('jenny') || n.includes('female'));
        });
        if (warmFemale) return warmFemale;
        break;
      }
      case 'jocasta': {
        const cyberFemale = voices.find((v) => {
          const n = v.name.toLowerCase();
          const l = v.lang.toLowerCase();
          return l.startsWith('en') && (n.includes('hazel') || n.includes('zira') || n.includes('female'));
        });
        if (cyberFemale) return cyberFemale;
        break;
      }
      case 'friday': {
        const female = voices.find((v) => {
          const n = v.name.toLowerCase();
          const l = v.lang.toLowerCase();
          return (l.startsWith('en') || l.startsWith('ga')) &&
            (n.includes('zira') || n.includes('heera') || n.includes('jenny') || n.includes('sonia') || n.includes('hazel') || n.includes('female'));
        });
        if (female) return female;
        break;
      }
      case 'baritone': {
        const baritone = voices.find((v) => {
          const n = v.name.toLowerCase();
          const l = v.lang.toLowerCase();
          return l.startsWith('en') && (n.includes('david') || n.includes('guy') || n.includes('christopher') || n.includes('mark') || n.includes('male'));
        });
        if (baritone) return baritone;
        break;
      }
      case 'war_machine':
      case 'hulkbuster': {
        const heavyMale = voices.find((v) => {
          const n = v.name.toLowerCase();
          const l = v.lang.toLowerCase();
          return l.startsWith('en') && (n.includes('guy') || n.includes('david') || n.includes('christopher') || n.includes('mark') || n.includes('male'));
        });
        if (heavyMale) return heavyMale;
        break;
      }
      case 'jarvis_war':
      case 'jarvis':
      case 'jarvis_classic':
      default: {
        // Priority 1: High-fidelity British Neural voices (Paul Bettany J.A.R.V.I.S. tone)
        const ukTopNeural = voices.find((v) => {
          const n = v.name.toLowerCase();
          const l = v.lang.toLowerCase();
          return (l.includes('gb') || l.includes('uk')) &&
            (n.includes('ryan') || n.includes('george') || n.includes('oliver') || n.includes('thomas') || n.includes('libby'));
        });
        if (ukTopNeural) return ukTopNeural;

        // Priority 2: Any Natural / Neural British English voice
        const ukAnyNeural = voices.find((v) => {
          const n = v.name.toLowerCase();
          const l = v.lang.toLowerCase();
          return (l.includes('gb') || l.includes('uk')) &&
            (n.includes('natural') || n.includes('neural') || n.includes('online') || n.includes('male'));
        });
        if (ukAnyNeural) return ukAnyNeural;

        // Priority 3: Any en-GB voice
        const anyUk = voices.find((v) => v.lang.toLowerCase().replace('_', '-').startsWith('en-gb'));
        if (anyUk) return anyUk;

        // Priority 4: Any natural English male voice
        const naturalMale = voices.find((v) => {
          const n = v.name.toLowerCase();
          return (n.includes('natural') || n.includes('neural') || n.includes('guy') || n.includes('male'));
        });
        if (naturalMale) return naturalMale;

        const anyMale = voices.find((v) => v.lang.toLowerCase().startsWith('en') && (v.name.toLowerCase().includes('david') || v.name.toLowerCase().includes('male')));
        if (anyMale) return anyMale;
        break;
      }
    }

    return voices.find((v) => v.lang.toLowerCase().startsWith('en')) || voices[0] || null;
  }

  function playSample(persona: VoicePersona, specificVoiceName?: string) {
    if (!('speechSynthesis' in window)) return;
    window.speechSynthesis.cancel();

    const sampleId = specificVoiceName || persona.id;
    if (samplingVoiceId === sampleId) {
      setSamplingVoiceId(null);
      return;
    }

    const sampleText = lang === 'ta-IN' ? persona.sampleTa : persona.sampleEn;
    const utterance = new SpeechSynthesisUtterance(sampleText);
    utterance.lang = lang;
    utterance.pitch = persona.pitch * voicePitch;
    utterance.rate = persona.rate * voiceRate;
    utterance.volume = 1.0;

    const voices = window.speechSynthesis.getVoices();
    if (specificVoiceName) {
      const match = voices.find((v) => v.name === specificVoiceName);
      if (match) utterance.voice = match;
    } else {
      const match = resolveVoiceForPersona(persona, voices, lang);
      if (match) utterance.voice = match;
    }

    setSamplingVoiceId(sampleId);
    utterance.onend = () => setSamplingVoiceId(null);
    utterance.onerror = () => setSamplingVoiceId(null);

    window.speechSynthesis.speak(utterance);
  }

  function playCustomPhrase(text: string) {
    if (!('speechSynthesis' in window) || !text.trim()) return;
    window.speechSynthesis.cancel();

    const utterance = new SpeechSynthesisUtterance(text.trim());
    utterance.lang = lang;

    const voices = window.speechSynthesis.getVoices();
    let chosen: SpeechSynthesisVoice | null = null;
    if (selectedVoiceName) {
      chosen = voices.find((v) => v.name === selectedVoiceName) || null;
    }
    const currentPersona = VOICE_PERSONAS.find((p) => p.id === selectedPersonaId) || VOICE_PERSONAS[0];
    if (!chosen) {
      chosen = resolveVoiceForPersona(currentPersona, voices, lang);
    }
    if (chosen) utterance.voice = chosen;

    utterance.pitch = currentPersona.pitch * voicePitch;
    utterance.rate = currentPersona.rate * voiceRate;
    utterance.volume = 1.0;

    setSamplingVoiceId('custom');
    utterance.onend = () => setSamplingVoiceId(null);
    utterance.onerror = () => setSamplingVoiceId(null);

    window.speechSynthesis.speak(utterance);
  }

  const startCloneRecording = async () => {
    try {
      setCloneMsg(null);
      setRecordedAudioBlob(null);
      if (recordedAudioUrl) {
        URL.revokeObjectURL(recordedAudioUrl);
        setRecordedAudioUrl(null);
      }
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      audioChunksRef.current = [];
      const mediaRecorder = new MediaRecorder(stream);
      mediaRecorderRef.current = mediaRecorder;

      mediaRecorder.ondataavailable = (e) => {
        if (e.data && e.data.size > 0) {
          audioChunksRef.current.push(e.data);
        }
      };

      mediaRecorder.onstop = () => {
        const blob = new Blob(audioChunksRef.current, { type: 'audio/wav' });
        setRecordedAudioBlob(blob);
        const url = URL.createObjectURL(blob);
        setRecordedAudioUrl(url);
        stream.getTracks().forEach((track) => track.stop());
      };

      mediaRecorder.start();
      setIsRecordingClone(true);
      setRecordSecondsLeft(10);

      let countdown = 10;
      recordTimerRef.current = setInterval(() => {
        countdown -= 1;
        setRecordSecondsLeft(countdown);
        if (countdown <= 0) {
          stopCloneRecording();
        }
      }, 1000);
    } catch (err: any) {
      setCloneMsg({ type: 'error', text: 'Microphone access failed: ' + (err.message || 'Permission denied') });
    }
  };

  const stopCloneRecording = () => {
    if (recordTimerRef.current) {
      clearInterval(recordTimerRef.current);
      recordTimerRef.current = null;
    }
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      mediaRecorderRef.current.stop();
    }
    setIsRecordingClone(false);
  };

  const handleSaveClone = async () => {
    if (!recordedAudioBlob) return;
    setIsSavingClone(true);
    setCloneMsg(null);
    try {
      const reader = new FileReader();
      reader.onloadend = async () => {
        try {
          const base64Audio = reader.result as string;
          const newProfile = await enrollVoiceClone(
            token,
            cloneProfileName.trim() || 'My Voice',
            cloneRefText.trim(),
            base64Audio,
            'wav'
          );
          setCloneProfiles((prev) => [...prev, newProfile]);
          setSelectedCloneProfileId(newProfile.id);
          try {
            localStorage.setItem('jarvis_selected_clone_profile', newProfile.id);
          } catch {}
          setCloneMsg({ type: 'success', text: `Voice profile '${newProfile.name}' cloned and activated for JARVIS!` });
          setRecordedAudioBlob(null);
        } catch (err: any) {
          setCloneMsg({ type: 'error', text: 'Failed to enroll voice: ' + (err.message || 'Server error') });
        } finally {
          setIsSavingClone(false);
        }
      };
      reader.readAsDataURL(recordedAudioBlob);
    } catch (err: any) {
      setIsSavingClone(false);
      setCloneMsg({ type: 'error', text: err.message || 'Failed to process audio' });
    }
  };

  const handleDeleteClone = async (id: string) => {
    try {
      await deleteVoiceCloneProfile(token, id);
      setCloneProfiles((prev) => prev.filter((p) => p.id !== id));
      if (selectedCloneProfileId === id) {
        setSelectedCloneProfileId('');
        try {
          localStorage.removeItem('jarvis_selected_clone_profile');
        } catch {}
      }
      setCloneMsg({ type: 'info', text: 'Voice profile removed.' });
    } catch (err: any) {
      setCloneMsg({ type: 'error', text: 'Failed to delete profile: ' + (err.message || '') });
    }
  };

  const handleSelectCloneProfile = (profile: VoiceCloneProfile | null) => {
    if (profile) {
      setSelectedCloneProfileId(profile.id);
      setSelectedVoiceName('');
      try {
        localStorage.setItem('jarvis_selected_clone_profile', profile.id);
        localStorage.removeItem('jarvis_selected_voice');
      } catch {}
      handleTestCloneSpeech(lang === 'ta-IN' ? 'உங்கள் குரல் மாதிரி செயல்படுத்தப்பட்டுள்ளது ஐயா.' : 'Cloned voice profile activated, Sir. I now speak with your voice.');
    } else {
      setSelectedCloneProfileId('');
      try {
        localStorage.removeItem('jarvis_selected_clone_profile');
      } catch {}
    }
  };

  const handleTestCloneSpeech = async (phraseToTest: string) => {
    setIsTestingClone(true);
    setCloneMsg(null);
    try {
      const activeId = selectedCloneProfileId || (cloneProfiles[0] ? cloneProfiles[0].id : undefined);
      const res = await synthesizeVoiceClone(token, phraseToTest, activeId);
      if (res.audioUrl) {
        setCloneTestAudioUrl(res.audioUrl);
        if (clonedAudioRef.current) {
          clonedAudioRef.current.pause();
        }
        const audio = new Audio(res.audioUrl);
        clonedAudioRef.current = audio;
        audio.onended = () => setIsTestingClone(false);
        audio.onerror = () => {
          setIsTestingClone(false);
          setCloneMsg({ type: 'error', text: 'Audio playback failed' });
        };
        await audio.play();
      }
    } catch (err: any) {
      setIsTestingClone(false);
      setCloneMsg({ type: 'error', text: 'Synthesis failed: ' + (err.message || 'Check local engine status') });
    }
  };

  function selectPersona(persona: VoicePersona) {
    setSelectedPersonaId(persona.id);
    setSelectedVoiceName('');
    setSelectedCloneProfileId('');
    setVoicePitch(persona.pitch);
    setVoiceRate(persona.rate);
    try {
      localStorage.setItem('jarvis_voice_persona', persona.id);
      localStorage.removeItem('jarvis_selected_voice');
      localStorage.removeItem('jarvis_selected_clone_profile');
      localStorage.setItem('jarvis_voice_pitch', String(persona.pitch));
      localStorage.setItem('jarvis_voice_rate', String(persona.rate));
      window.dispatchEvent(new Event('jarvis_voice_updated'));
    } catch {}
    playSample(persona);
  }

  function selectSystemVoice(voice: SpeechSynthesisVoice) {
    setSelectedVoiceName(voice.name);
    setSelectedCloneProfileId('');
    try {
      localStorage.setItem('jarvis_selected_voice', voice.name);
      localStorage.removeItem('jarvis_selected_clone_profile');
      window.dispatchEvent(new Event('jarvis_voice_updated'));
    } catch {}

    const utterance = new SpeechSynthesisUtterance(
      lang === 'ta-IN' ? 'வாய்ஸ் செட் பண்ணியாச்சு பாஸ்! சிஸ்டம் ஃபுல் ரெடியா இருக்கு.' : 'Voice confirmed, Sir. Ready for your instructions.'
    );
    utterance.voice = voice;
    utterance.lang = voice.lang;
    utterance.pitch = voicePitch;
    utterance.rate = voiceRate;
    window.speechSynthesis.speak(utterance);
  }

  async function speakBrowser(text: string, targetLang: string, controller: AbortController): Promise<void> {
    const cleanText = text.replace(/<think>[\s\S]*?<\/think>/g, '').trim();
    let textToSpeak = cleanText;

    if (!cleanText && text.includes('<think>')) {
      const isTamil = targetLang.startsWith('ta');
      textToSpeak = isTamil ? 'யோசிக்கிறேன் பாஸ்... தகவலெல்லாம் செக் பண்றேன்.' : 'Analyzing and gathering information, sir.';
    }

    setJarvisReply(textToSpeak || text);

    // If a cloned voice is active, synthesize and play with cloned voice
    if (selectedCloneProfileId) {
      try {
        const synthRes = await synthesizeVoiceClone(token, textToSpeak || text, selectedCloneProfileId);
        if (synthRes && synthRes.audioUrl) {
          return new Promise<void>((resolve) => {
            if (clonedAudioRef.current) {
              clonedAudioRef.current.pause();
            }
            const audio = new Audio(synthRes.audioUrl);
            clonedAudioRef.current = audio;
            const onAbort = () => {
              audio.pause();
              resolve();
            };
            controller.signal.addEventListener('abort', onAbort);
            audio.onended = () => resolve();
            audio.onerror = () => {
              // fallback to browser TTS if audio playback fails
              resolve();
            };
            audio.play().catch(() => resolve());
          });
        }
      } catch (err) {
        console.warn('Voice clone playback failed, falling back to speech synthesis', err);
      }
    }

    if ('speechSynthesis' in window) {
      return new Promise<void>((resolve) => {
        window.speechSynthesis.cancel();

        const voices = window.speechSynthesis.getVoices();
        let activeVoice: SpeechSynthesisVoice | null = null;

        if (selectedVoiceName) {
          activeVoice = voices.find((v) => v.name === selectedVoiceName) || null;
        }

        const persona = VOICE_PERSONAS.find((p) => p.id === selectedPersonaId) || VOICE_PERSONAS[0];
        if (!activeVoice) {
          activeVoice = resolveVoiceForPersona(persona, voices, targetLang);
        }

        const baseText = textToSpeak || text;
        // Split text cleanly by sentence boundaries (. ! ? \n) to bypass Chrome 15s TTS cutoff bug
        const rawChunks = baseText
          .replace(/([.?!;\n]+)/g, "$1\n")
          .split("\n")
          .map((s) => s.trim())
          .filter(Boolean);

        const chunks = rawChunks.length > 0 ? rawChunks : [baseText];
        let chunkIndex = 0;

        // Keep-alive heartbeat so Chrome doesn't sleep mid-speech
        const keepAliveTimer = setInterval(() => {
          if (!window.speechSynthesis.speaking) {
            clearInterval(keepAliveTimer);
          } else {
            window.speechSynthesis.pause();
            window.speechSynthesis.resume();
          }
        }, 3500);

        const cleanup = () => {
          clearInterval(keepAliveTimer);
          activeSpokenTextRef.current = '';
        };

        const onAbort = () => {
          cleanup();
          try {
            window.speechSynthesis.cancel();
          } catch {}
          resolve();
        };

        controller.signal.addEventListener('abort', onAbort);

        const speakNext = () => {
          if (controller.signal.aborted || chunkIndex >= chunks.length) {
            cleanup();
            resolve();
            return;
          }

          const currentSentence = chunks[chunkIndex++];
          activeSpokenTextRef.current = currentSentence;
          const utterance = new SpeechSynthesisUtterance(currentSentence);
          utterance.lang = targetLang;

          if (activeVoice) {
            utterance.voice = activeVoice;
          }

          utterance.rate = persona.rate * voiceRate;
          utterance.pitch = persona.pitch * voicePitch;
          utterance.volume = 1.0;

          utterance.onend = () => {
            speakNext();
          };

          utterance.onerror = (e) => {
            if (controller.signal.aborted) {
              cleanup();
              resolve();
            } else {
              // Proceed to next sentence
              speakNext();
            }
          };

          window.speechSynthesis.speak(utterance);
        };

        speakNext();
      });
    }

    return callBackend('speak', controller, { text, lang: targetLang }).then(() => {});
  }

  async function streamAndSpeak(
    queryText: string,
    controller: AbortController,
    prefixToSend?: string
  ): Promise<void> {
    // 1. Check Voice Speed Commands First (Zero latency execution)
    const speedCheck = checkVoiceSpeedCommand(queryText, voiceRate, lang);
    if (speedCheck.handled && speedCheck.replyText) {
      if (speedCheck.newRate !== undefined) {
        updateVoiceRate(speedCheck.newRate);
      }
      setStage(lang === 'ta-IN' ? 'பேசுறேன் பாஸ்… (Speaking in Tamil…)' : 'Speaking…');
      setJarvisReply(speedCheck.replyText);
      await speakBrowser(speedCheck.replyText, lang, controller);
      return;
    }

    // 2. Prepare Streaming Voice Speaker
    const voices = typeof window !== 'undefined' && 'speechSynthesis' in window ? window.speechSynthesis.getVoices() : [];
    let activeVoice: SpeechSynthesisVoice | null = null;
    if (selectedVoiceName) {
      activeVoice = voices.find((v) => v.name === selectedVoiceName) || null;
    }
    const persona = VOICE_PERSONAS.find((p) => p.id === selectedPersonaId) || VOICE_PERSONAS[0];
    if (!activeVoice) {
      activeVoice = resolveVoiceForPersona(persona, voices, lang);
    }

    setStage(lang === 'ta-IN' ? 'யோசிக்கிறேன் பாஸ்… (Thinking…)' : 'Thinking…');
    setJarvisReply('');

    const speaker = new StreamingVoiceSpeaker(
      lang,
      persona,
      voiceRate,
      voicePitch,
      activeVoice,
      selectedCloneProfileId || null,
      token,
      controller.signal,
      (spokenSentence) => {
        setJarvisReply((prev) => (prev ? prev + ' ' : '') + spokenSentence);
      },
      (isSpeaking) => {
        if (isSpeaking) {
          setStage(lang === 'ta-IN' ? 'பேசுறேன் பாஸ்… (Speaking in Tamil…)' : 'Speaking…');
        }
      },
      async (sentence) => {
        return callBackend('speak', controller, {
          text: sentence,
          lang: lang,
          voice: activeVoice?.name,
          rate: Math.round((voiceRate - 1.0) * 10),
        });
      }
    );
    activeSpeakerRef.current = speaker;

    const textToAsk = prefixToSend ? `${prefixToSend} ${queryText}` : queryText;

    try {
      await ask.current(textToAsk, (delta) => {
        speaker.pushDelta(delta);
      });
    } catch (err) {
      speaker.cancel();
      activeSpeakerRef.current = null;
      throw err;
    }

    if (controller.signal.aborted) {
      speaker.cancel();
      activeSpeakerRef.current = null;
      return;
    }

    // Await all queued speech to finish playing seamlessly
    await speaker.finish();
    activeSpeakerRef.current = null;

    // Attentive conversational follow-up window (instant multi-turn experience)
    if (!controller.signal.aborted && (wakeWordActive || continuous)) {
      setStage(
        lang === 'ta-IN'
          ? '👂 சொல்லுங்க பாஸ், கேட்டுட்டு இருக்கேன்…'
          : '👂 Listening for follow-up…'
      );
      await new Promise<void>((resolve) => {
        const timeout = setTimeout(() => {
          if (active.current === controller) {
            playJarvisSound('standby');
          }
          resolve();
        }, 5000);

        controller.signal.addEventListener('abort', () => {
          clearTimeout(timeout);
          resolve();
        });
      });
    }
  }

  function stop() {
    active.current?.abort();
    if (activeSpeakerRef.current) {
      activeSpeakerRef.current.interrupt();
      activeSpeakerRef.current = null;
    }
    try {
      if ('speechSynthesis' in window) {
        window.speechSynthesis.cancel();
        setTimeout(() => {
          try {
            window.speechSynthesis.cancel();
          } catch {}
        }, 40);
      }
      if (clonedAudioRef.current) {
        clonedAudioRef.current.pause();
      }
    } catch {}
    onStop();
    setStage('');
    onActiveChange(false);
  }

  async function talk(loop: boolean) {
    stop();
    setContinuous(loop);
    repeat.current = loop;

    const controller = new AbortController();
    active.current = controller;
    setError('');
    onActiveChange(true);

    try {
      do {
        setStage(lang === 'ta-IN' ? 'கேட்கிறேன் பாஸ்… சொல்லுங்க (Listening in Tamil…)' : 'Listening… speak into this computer’s microphone');
        const speech = await listenBrowser(lang, controller);
        if (controller.signal.aborted) break;
        const text = speech.text;
        const confidence = speech.confidence;

        if (!text?.trim()) {
          throw new Error(lang === 'ta-IN' ? 'வாய்ஸ் எதுவும் கேட்கல பாஸ். இன்னொரு தடவ பேசுங்க.' : 'No speech detected. Check your microphone and try again.');
        }

        // Section 6.1: Confidence-based gating for voice actions (< 0.35 threshold)
        if (confidence > 0 && confidence < 0.35) {
          const confPercent = Math.round(confidence * 100);
          const warnMsg = lang === 'ta-IN'
            ? `⚠️ உங்கள் பேச்சு தெளிவாகக் கேட்கவில்லை (${confPercent}% உறுதிப்பாடு). தவறான செயல்களைத் தவிர்க்க கட்டளை நிறுத்தப்பட்டது. மீண்டும் கூறவும்.`
            : `⚠️ Unclear speech detected (${confPercent}% confidence). Command execution paused to avoid errors. Please repeat your command.`;
          setError(warnMsg);
          setStage(warnMsg);
          if (loop) {
            continue;
          } else {
            break;
          }
        }

        const lower = text.toLowerCase();
        // Dynamic voice language auto-switching
        if (lower.includes('switch to tamil') || lower.includes('speak in tamil') || lower.includes('talk in tamil') || lower.includes('change to tamil') || lower.includes('தமிழில் பேசு') || lower.includes('தமிழுக்கு மாறு')) {
          handleLangChange('ta-IN');
        } else if (lower.includes('switch to english') || lower.includes('speak in english') || lower.includes('talk in english') || lower.includes('change to english') || lower.includes('ஆங்கிலத்தில் பேசு')) {
          handleLangChange('en-US');
        }

        // Iron Man / Stark AI Voice Persona dynamic triggers
        if (
          lower.includes('activate war mode') ||
          lower.includes('engage war mode') ||
          lower.includes('enter war mode') ||
          lower.includes('war mode') ||
          lower.includes('combat mode') ||
          lower.includes('red alert') ||
          lower.includes('house party protocol') ||
          lower.includes('போர் முறை')
        ) {
          playJarvisSound('war_alert');
          const warP = VOICE_PERSONAS.find((x) => x.id === 'jarvis_war') || VOICE_PERSONAS[0];
          selectPersona(warP);
          setWarMode(true);
        } else if (lower.includes('war machine') || lower.includes('rhodey') || lower.includes('வார் மெஷின்')) {
          playJarvisSound('war_alert');
          const p = VOICE_PERSONAS.find((x) => x.id === 'war_machine');
          if (p) selectPersona(p);
          setWarMode(true);
        } else if (lower.includes('hulkbuster') || lower.includes('veronica') || lower.includes('ஹல்க்பஸ்டர்')) {
          playJarvisSound('repulsor');
          const p = VOICE_PERSONAS.find((x) => x.id === 'hulkbuster');
          if (p) selectPersona(p);
          setWarMode(true);
        } else if (
          lower.includes('stand down') ||
          lower.includes('disengage war mode') ||
          lower.includes('cancel war mode') ||
          lower.includes('peace mode') ||
          lower.includes('போர் முறை நிறுத்து')
        ) {
          playJarvisSound('standby');
          const defaultP = VOICE_PERSONAS.find((x) => x.id === 'jarvis') || VOICE_PERSONAS[0];
          selectPersona(defaultP);
          setWarMode(false);
        } else if (lower.includes('ultron') || lower.includes('altron') || lower.includes('அல்ட்ரான்')) {
          const p = VOICE_PERSONAS.find((x) => x.id === 'ultron');
          if (p) selectPersona(p);
        } else if (lower.includes('edith') || lower.includes('eden') || lower.includes('ஈடித்')) {
          const p = VOICE_PERSONAS.find((x) => x.id === 'edith');
          if (p) selectPersona(p);
        } else if (lower.includes('friday') || lower.includes('ப்ரைடே')) {
          const p = VOICE_PERSONAS.find((x) => x.id === 'friday');
          if (p) selectPersona(p);
        } else if (lower.includes('karen') || lower.includes('suit lady') || lower.includes('காரன்')) {
          const p = VOICE_PERSONAS.find((x) => x.id === 'karen');
          if (p) selectPersona(p);
        } else if (lower.includes('jocasta') || lower.includes('ஜோகாஸ்டா')) {
          const p = VOICE_PERSONAS.find((x) => x.id === 'jocasta');
          if (p) selectPersona(p);
        } else if (lower.includes('jarvis') || lower.includes('british') || lower.includes('ஜார்விஸ்')) {
          const p = VOICE_PERSONAS.find((x) => x.id === 'jarvis');
          if (p) selectPersona(p);
        } else if (lower.includes('baritone') || lower.includes('deep voice')) {
          const p = VOICE_PERSONAS.find((x) => x.id === 'baritone');
          if (p) selectPersona(p);
        } else if (lower.includes('voice studio') || lower.includes('voice sample') || lower.includes('voice samples') || lower.includes('change voice') || lower.includes('குரல் மாற்று')) {
          setIsVoiceModalOpen(true);
        }

        if (lower.includes('open biometric') || lower.includes('show biometric') || lower.includes('scan my face') || lower.includes('பயோமெட்ரிக் திரையை திற') || lower.includes('முக திரையை திற')) {
          onNavigate?.('biometrics');
        } else if (lower.includes('open goal') || lower.includes('show goal') || lower.includes('இலக்கு திரையை திற') || lower.includes('இலக்கு திட்டமிடுதலை திற')) {
          onNavigate?.('goals');
        } else if (lower.includes('open shopping') || lower.includes('show shopping') || lower.includes('ஷாப்பிங் திரையை திற') || lower.includes('ஷாப்பிங் திற')) {
          onNavigate?.('shopping');
        } else if (lower.includes('open automation') || lower.includes('show automation') || lower.includes('ஆட்டோமேஷன் திரையை திற')) {
          onNavigate?.('automation');
        } else if (lower.includes('open scheduler') || lower.includes('show scheduler') || lower.includes('அட்டவணை திரையை திற')) {
          onNavigate?.('scheduler');
        } else if (lower.includes('open memory') || lower.includes('show memory') || lower.includes('நினைவகத்தை திற')) {
          onNavigate?.('database_memory');
        } else if (lower.includes('open files') || lower.includes('show files') || lower.includes('கோப்புகளை திற')) {
          onNavigate?.('files');
        } else if (lower.includes('open hud') || lower.includes('iron man hud') || lower.includes('ஹாலோகிராம் திற')) {
          setEffectiveHudOpen(true);
        } else if (
          lower.includes('open application') ||
          lower.includes('open the application') ||
          lower.includes('open applications') ||
          lower.includes('open the applications') ||
          lower.includes('open apps') ||
          lower.includes('open app') ||
          lower.includes('open desktop tools') ||
          lower.includes('open desktop') ||
          lower.includes('show applications') ||
          lower.includes('show desktop tools') ||
          lower.includes('show apps') ||
          lower.includes('list applications') ||
          lower.includes('appliation') ||
          lower.includes('பயன்பாடுகளை திற') ||
          lower.includes('பயன்பாடுகளை காட்டு') ||
          lower.includes('பயன்பாட்டை திற') ||
          lower.includes('ஆப்ஸ் திற') ||
          lower.includes('டெஸ்க்டாப் டூல்ஸ் திற')
        ) {
          onNavigate?.('desktop');
        } else if (lower.includes('open smart home') || lower.includes('show smart home') || lower.includes('ஸ்மார்ட் ஹோம் திற')) {
          onNavigate?.('smarthome');
        } else if (lower.includes('open settings') || lower.includes('show settings') || lower.includes('செட்டிங்ஸ் திற')) {
          onNavigate?.('settings');
        }

        const prefix = warMode && !text.startsWith('/') ? '/war' : undefined;
        await streamAndSpeak(text, controller, prefix);
      } while (repeat.current && !controller.signal.aborted);
    } catch (e) {
      if (!controller.signal.aborted) setError(e instanceof Error ? e.message : 'Voice failed.');
    } finally {
      wakeEngineRef.current?.resume();
      if (active.current === controller) {
        active.current = null;
        setStage('');
        onActiveChange(false);
      }
    }
  }

  const getHologramState = () => {
    if (!stage) return 'idle';
    if (stage.includes('Listening') || stage.includes('கேட்கிறது')) return 'listening';
    if (stage.includes('Thinking') || stage.includes('யோசிக்கிறது')) return 'thinking';
    if (stage.includes('Speaking') || stage.includes('பேசுகிறது')) return 'speaking';
    return 'idle';
  };

  const activeClone = cloneProfiles.find((p) => p.id === selectedCloneProfileId);
  const activePersona = VOICE_PERSONAS.find((p) => p.id === selectedPersonaId) || VOICE_PERSONAS[0];
  const activeDisplayName = activeClone
    ? `🎙️ ${activeClone.name} (Cloned)`
    : selectedVoiceName
    ? selectedVoiceName.replace(/^Microsoft |^Google /i, '')
    : activePersona.name;

  const filteredSystemVoices = systemVoices.filter((v) => {
    if (!voiceFilter) return true;
    const q = voiceFilter.toLowerCase();
    return v.name.toLowerCase().includes(q) || v.lang.toLowerCase().includes(q);
  });

  const renderStudioCard = (isPageMode: boolean) => (
    <div
      style={{
        width: '100%',
        maxWidth: isPageMode ? '100%' : '820px',
        maxHeight: isPageMode ? 'none' : '90vh',
        overflowY: isPageMode ? 'visible' : 'auto',
        background: 'linear-gradient(145deg, #0d121f, #141b2d)',
        border: '1px solid rgba(0, 240, 255, 0.35)',
        borderRadius: '12px',
        boxShadow: isPageMode ? '0 4px 30px rgba(0, 0, 0, 0.4)' : '0 0 35px rgba(0, 240, 255, 0.18)',
        color: '#e2e8f0',
        padding: '24px',
        display: 'flex',
        flexDirection: 'column',
        gap: '20px',
      }}
    >
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <h3
            style={{
              margin: 0,
              fontSize: '1.28rem',
              color: '#00f0ff',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              letterSpacing: '0.5px',
            }}
          >
            <Sliders size={20} />
            {lang === 'ta-IN' ? 'ஜார்விஸ் குரல் அரங்கம் & மாதிரிகள் (Voice Studio)' : 'JARVIS Voice Persona Studio & Audition'}
          </h3>
          <p style={{ margin: '4px 0 0 0', fontSize: '0.86rem', color: '#94a3b8' }}>
            {lang === 'ta-IN'
              ? 'மாதிரிகளை கேட்டு உங்கள் விருப்பத்திற்கு ஏற்ற குரலை தேர்ந்தெடுக்கவும். துல்லியமாக மாறும்.'
              : 'Audition vocal samples, choose your preferred AI persona, or fine-tune speech cadence.'}
          </p>
        </div>
        {!isPageMode && (
          <button
            type="button"
            onClick={() => setIsVoiceModalOpen(false)}
            style={{
              background: 'none',
              border: 'none',
              color: '#94a3b8',
              cursor: 'pointer',
              padding: '4px',
            }}
            title="Close"
          >
            <X size={20} />
          </button>
        )}
      </div>

            {/* Navigation Tabs */}
            <div style={{ display: 'flex', gap: '8px', borderBottom: '1px solid rgba(255, 255, 255, 0.1)', paddingBottom: '10px' }}>
              <button
                type="button"
                onClick={() => setModalTab('personas')}
                style={{
                  background: modalTab === 'personas' ? 'rgba(0, 240, 255, 0.15)' : 'transparent',
                  color: modalTab === 'personas' ? '#00f0ff' : '#94a3b8',
                  border: modalTab === 'personas' ? '1px solid rgba(0, 240, 255, 0.4)' : '1px solid transparent',
                  borderRadius: '6px',
                  padding: '6px 14px',
                  fontSize: '0.86rem',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                }}
              >
                <Sparkles size={15} /> Iron Man & AI Personas ({VOICE_PERSONAS.length})
              </button>

              <button
                type="button"
                onClick={() => setModalTab('cloning')}
                style={{
                  background: modalTab === 'cloning' ? 'rgba(192, 132, 252, 0.2)' : 'transparent',
                  color: modalTab === 'cloning' ? '#c084fc' : '#94a3b8',
                  border: modalTab === 'cloning' ? '1px solid rgba(192, 132, 252, 0.5)' : '1px solid transparent',
                  borderRadius: '6px',
                  padding: '6px 14px',
                  fontSize: '0.86rem',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                }}
              >
                <Radio size={15} /> Voice Cloning Studio ({cloneProfiles.length})
                <span
                  style={{
                    fontSize: '0.68rem',
                    padding: '1px 6px',
                    borderRadius: '4px',
                    background: 'rgba(34, 197, 94, 0.2)',
                    color: '#4ade80',
                    fontWeight: 600,
                  }}
                >
                  100% Private
                </span>
              </button>

              <button
                type="button"
                onClick={() => setModalTab('system')}
                style={{
                  background: modalTab === 'system' ? 'rgba(0, 240, 255, 0.15)' : 'transparent',
                  color: modalTab === 'system' ? '#00f0ff' : '#94a3b8',
                  border: modalTab === 'system' ? '1px solid rgba(0, 240, 255, 0.4)' : '1px solid transparent',
                  borderRadius: '6px',
                  padding: '6px 14px',
                  fontSize: '0.86rem',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                }}
              >
                <Globe size={15} /> System & Browser Voices ({systemVoices.length})
              </button>

              <button
                type="button"
                onClick={() => setModalTab('tuning')}
                style={{
                  background: modalTab === 'tuning' ? 'rgba(0, 240, 255, 0.15)' : 'transparent',
                  color: modalTab === 'tuning' ? '#00f0ff' : '#94a3b8',
                  border: modalTab === 'tuning' ? '1px solid rgba(0, 240, 255, 0.4)' : '1px solid transparent',
                  borderRadius: '6px',
                  padding: '6px 14px',
                  fontSize: '0.86rem',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                }}
              >
                <Zap size={15} /> Acoustic Fine-Tuning
              </button>
            </div>

            {/* Tab 1: Curated AI Personas */}
            {modalTab === 'personas' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {VOICE_PERSONAS.map((p) => {
                  const isSelected = selectedPersonaId === p.id && !selectedVoiceName;
                  const isPlaying = samplingVoiceId === p.id;

                  return (
                    <div
                      key={p.id}
                      style={{
                        background: isSelected ? 'rgba(0, 240, 255, 0.08)' : 'rgba(255, 255, 255, 0.03)',
                        border: isSelected ? '1px solid #00f0ff' : '1px solid rgba(255, 255, 255, 0.08)',
                        borderRadius: '8px',
                        padding: '16px',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '10px',
                        transition: 'all 0.2s ease',
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '8px' }}>
                        <div>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <span style={{ fontWeight: 600, fontSize: '1rem', color: isSelected ? '#00f0ff' : '#f1f5f9' }}>
                              {p.name}
                            </span>
                            <span
                              style={{
                                fontSize: '0.72rem',
                                padding: '2px 8px',
                                borderRadius: '12px',
                                background: 'rgba(0, 240, 255, 0.15)',
                                color: '#00f0ff',
                                border: '1px solid rgba(0, 240, 255, 0.3)',
                              }}
                            >
                              {p.badge}
                            </span>
                            {isSelected && (
                              <span
                                style={{
                                  fontSize: '0.72rem',
                                  padding: '2px 8px',
                                  borderRadius: '12px',
                                  background: 'rgba(34, 197, 94, 0.2)',
                                  color: '#4ade80',
                                  border: '1px solid rgba(34, 197, 94, 0.4)',
                                  display: 'flex',
                                  alignItems: 'center',
                                  gap: '4px',
                                }}
                              >
                                <Check size={12} /> ACTIVE VOICE
                              </span>
                            )}
                          </div>
                          <p style={{ margin: '4px 0 0 0', fontSize: '0.84rem', color: '#94a3b8' }}>{p.description}</p>
                        </div>

                        {/* Action Buttons */}
                        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                          <button
                            type="button"
                            onClick={() => playSample(p)}
                            style={{
                              background: isPlaying ? 'rgba(239, 68, 68, 0.2)' : 'rgba(0, 240, 255, 0.12)',
                              color: isPlaying ? '#f87171' : '#00f0ff',
                              border: isPlaying ? '1px solid #ef4444' : '1px solid rgba(0, 240, 255, 0.35)',
                              borderRadius: '6px',
                              padding: '6px 12px',
                              fontSize: '0.82rem',
                              cursor: 'pointer',
                              display: 'flex',
                              alignItems: 'center',
                              gap: '6px',
                            }}
                          >
                            {isPlaying ? <Square size={14} /> : <Play size={14} />}
                            {isPlaying ? 'Stop' : 'Play Sample'}
                          </button>

                          <button
                            type="button"
                            onClick={() => selectPersona(p)}
                            style={{
                              background: isSelected ? '#00f0ff' : 'rgba(255, 255, 255, 0.08)',
                              color: isSelected ? '#0b1120' : '#f1f5f9',
                              border: isSelected ? '1px solid #00f0ff' : '1px solid rgba(255, 255, 255, 0.2)',
                              borderRadius: '6px',
                              padding: '6px 14px',
                              fontSize: '0.82rem',
                              fontWeight: isSelected ? 600 : 500,
                              cursor: 'pointer',
                              display: 'flex',
                              alignItems: 'center',
                              gap: '6px',
                            }}
                          >
                            <Check size={14} />
                            {isSelected ? 'Selected' : 'Select Voice'}
                          </button>
                        </div>
                      </div>

                      {/* Sample Phrase Box */}
                      <div
                        style={{
                          background: 'rgba(0, 0, 0, 0.25)',
                          borderRadius: '6px',
                          padding: '8px 12px',
                          fontSize: '0.82rem',
                          color: '#cbd5e1',
                          fontStyle: 'italic',
                          borderLeft: isSelected ? '3px solid #00f0ff' : '3px solid rgba(255, 255, 255, 0.2)',
                        }}
                      >
                        "{lang === 'ta-IN' ? p.sampleTa : p.sampleEn}"
                      </div>
                    </div>
                  );
                })}
              </div>
            )}

            {/* Tab 2: System & Browser Voices */}
            {modalTab === 'system' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                  <div style={{ position: 'relative', flex: 1 }}>
                    <Search
                      size={15}
                      style={{ position: 'absolute', left: '10px', top: '50%', transform: 'translateY(-50%)', color: '#64748b' }}
                    />
                    <input
                      type="text"
                      placeholder="Search voices by name or language (e.g. David, Zira, en-US, ta-IN)..."
                      value={voiceFilter}
                      onChange={(e) => setVoiceFilter(e.target.value)}
                      style={{
                        width: '100%',
                        background: 'rgba(0, 0, 0, 0.3)',
                        border: '1px solid rgba(255, 255, 255, 0.15)',
                        borderRadius: '6px',
                        padding: '8px 12px 8px 32px',
                        color: 'inherit',
                        fontSize: '0.86rem',
                        outline: 'none',
                      }}
                    />
                  </div>
                </div>

                <div
                  style={{
                    maxHeight: '380px',
                    overflowY: 'auto',
                    border: '1px solid rgba(255, 255, 255, 0.08)',
                    borderRadius: '8px',
                    background: 'rgba(0, 0, 0, 0.2)',
                  }}
                >
                  {filteredSystemVoices.length === 0 ? (
                    <div style={{ padding: '20px', textAlign: 'center', color: '#94a3b8', fontSize: '0.86rem' }}>
                      No voices found matching "{voiceFilter}".
                    </div>
                  ) : (
                    filteredSystemVoices.map((v) => {
                      const isSelected = selectedVoiceName === v.name;
                      const isPlaying = samplingVoiceId === v.name;

                      return (
                        <div
                          key={v.name}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            padding: '10px 14px',
                            borderBottom: '1px solid rgba(255, 255, 255, 0.04)',
                            background: isSelected ? 'rgba(0, 240, 255, 0.08)' : 'transparent',
                          }}
                        >
                          <div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                              <span style={{ fontWeight: 500, color: isSelected ? '#00f0ff' : '#f1f5f9', fontSize: '0.88rem' }}>
                                {v.name}
                              </span>
                              <span
                                style={{
                                  fontSize: '0.72rem',
                                  padding: '1px 6px',
                                  borderRadius: '4px',
                                  background: 'rgba(255, 255, 255, 0.08)',
                                  color: '#cbd5e1',
                                }}
                              >
                                {v.lang}
                              </span>
                              {v.localService && (
                                <span style={{ fontSize: '0.70rem', color: '#64748b' }}>[Local Offline]</span>
                              )}
                            </div>
                          </div>

                          <div style={{ display: 'flex', gap: '6px' }}>
                            <button
                              type="button"
                              onClick={() => {
                                const dummyPersona: VoicePersona = {
                                  id: v.name,
                                  name: v.name,
                                  tag: v.lang,
                                  badge: v.lang,
                                  description: '',
                                  sampleEn: 'Hello Sir. This is the ' + v.name + ' voice profile.',
                                  sampleTa: 'வணக்கம் ஐயா. இது ' + v.name + ' குரல் பாணி.',
                                  pitch: 1.0,
                                  rate: 1.0,
                                };
                                playSample(dummyPersona, v.name);
                              }}
                              style={{
                                background: isPlaying ? 'rgba(239, 68, 68, 0.2)' : 'rgba(0, 240, 255, 0.1)',
                                color: isPlaying ? '#f87171' : '#00f0ff',
                                border: '1px solid rgba(0, 240, 255, 0.3)',
                                borderRadius: '4px',
                                padding: '4px 10px',
                                fontSize: '0.78rem',
                                cursor: 'pointer',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '4px',
                              }}
                            >
                              {isPlaying ? <Square size={12} /> : <Play size={12} />}
                              {isPlaying ? 'Stop' : 'Sample'}
                            </button>

                            <button
                              type="button"
                              onClick={() => selectSystemVoice(v)}
                              style={{
                                background: isSelected ? '#00f0ff' : 'rgba(255, 255, 255, 0.08)',
                                color: isSelected ? '#0b1120' : '#f1f5f9',
                                border: isSelected ? '1px solid #00f0ff' : '1px solid rgba(255, 255, 255, 0.2)',
                                borderRadius: '4px',
                                padding: '4px 10px',
                                fontSize: '0.78rem',
                                fontWeight: isSelected ? 600 : 500,
                                cursor: 'pointer',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '4px',
                              }}
                            >
                              <Check size={12} />
                              {isSelected ? 'Active' : 'Select'}
                            </button>
                          </div>
                        </div>
                      );
                    })
                  )}
                </div>
              </div>
            )}

            {/* Tab: Voice Cloning Studio (100% Free & Open-Source Zero-Shot Cloning) */}
            {modalTab === 'cloning' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                {/* Engine Status & Privacy Guarantee Banner */}
                <div
                  style={{
                    background: 'linear-gradient(135deg, rgba(147, 51, 234, 0.12), rgba(59, 130, 246, 0.08))',
                    border: '1px solid rgba(168, 85, 247, 0.3)',
                    borderRadius: '10px',
                    padding: '16px 20px',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    flexWrap: 'wrap',
                    gap: '12px',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                    <div
                      style={{
                        width: '42px',
                        height: '42px',
                        borderRadius: '8px',
                        background: 'rgba(168, 85, 247, 0.2)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: '#c084fc',
                      }}
                    >
                      <Radio size={22} />
                    </div>
                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span style={{ fontWeight: 600, fontSize: '1rem', color: '#f8fafc' }}>
                          Local Neural Voice Engine (F5-TTS)
                        </span>
                        <span
                          style={{
                            fontSize: '0.72rem',
                            padding: '2px 8px',
                            borderRadius: '999px',
                            background: cloneStatus?.online ? 'rgba(34, 197, 94, 0.2)' : 'rgba(234, 179, 8, 0.2)',
                            color: cloneStatus?.online ? '#4ade80' : '#facc15',
                            border: `1px solid ${cloneStatus?.online ? 'rgba(34, 197, 94, 0.4)' : 'rgba(234, 179, 8, 0.4)'}`,
                            display: 'flex',
                            alignItems: 'center',
                            gap: '4px',
                          }}
                        >
                          <span
                            style={{
                              width: '6px',
                              height: '6px',
                              borderRadius: '50%',
                              background: cloneStatus?.online ? '#4ade80' : '#facc15',
                            }}
                          />
                          {cloneStatus?.online ? (cloneStatus?.modelLoaded ? 'Active (Neural Mode)' : 'Active (Acoustic Mode)') : 'Offline / Standby'}
                        </span>
                        <span
                          style={{
                            fontSize: '0.72rem',
                            padding: '2px 8px',
                            borderRadius: '999px',
                            background: 'rgba(56, 189, 248, 0.15)',
                            color: '#38bdf8',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '4px',
                          }}
                        >
                          <Cpu size={12} /> {cloneStatus?.device?.toUpperCase() || 'GPU/CPU'}
                        </span>
                      </div>
                      <div style={{ fontSize: '0.80rem', color: '#94a3b8', marginTop: '3px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <ShieldCheck size={14} color="#4ade80" />
                        <span>100% Free &amp; Open-Source &bull; Zero Cloud Telemetry &bull; Audio Stored Strictly in Local Workspace</span>
                      </div>
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={() => void refreshCloneData()}
                    style={{
                      background: 'rgba(255, 255, 255, 0.05)',
                      border: '1px solid rgba(255, 255, 255, 0.15)',
                      borderRadius: '6px',
                      padding: '6px 12px',
                      color: '#cbd5e1',
                      fontSize: '0.80rem',
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '6px',
                    }}
                  >
                    <RefreshCw size={13} /> Refresh Engine
                  </button>
                </div>

                {/* Status or Alert Notification if any */}
                {cloneMsg && (
                  <div
                    style={{
                      padding: '10px 14px',
                      borderRadius: '8px',
                      fontSize: '0.84rem',
                      background:
                        cloneMsg.type === 'success'
                          ? 'rgba(34, 197, 94, 0.15)'
                          : cloneMsg.type === 'error'
                          ? 'rgba(239, 68, 68, 0.15)'
                          : 'rgba(56, 189, 248, 0.15)',
                      color:
                        cloneMsg.type === 'success'
                          ? '#4ade80'
                          : cloneMsg.type === 'error'
                          ? '#f87171'
                          : '#38bdf8',
                      border: `1px solid ${
                        cloneMsg.type === 'success'
                          ? 'rgba(34, 197, 94, 0.3)'
                          : cloneMsg.type === 'error'
                          ? 'rgba(239, 68, 68, 0.3)'
                          : 'rgba(56, 189, 248, 0.3)'
                      }`,
                      display: 'flex',
                      alignItems: 'center',
                      gap: '8px',
                    }}
                  >
                    {cloneMsg.type === 'success' ? <Check size={16} /> : <AlertCircle size={16} />}
                    <span>{cloneMsg.text}</span>
                  </div>
                )}

                <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr', gap: '20px' }}>
                  {/* Left Column: Record New Voice Sample Studio */}
                  <div
                    style={{
                      background: 'rgba(255, 255, 255, 0.02)',
                      border: '1px solid rgba(255, 255, 255, 0.08)',
                      borderRadius: '10px',
                      padding: '18px',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '14px',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <h4 style={{ margin: 0, fontSize: '0.96rem', color: '#c084fc', display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <Mic size={16} /> Record Voice Sample (5–10s)
                      </h4>
                      <span style={{ fontSize: '0.74rem', color: '#94a3b8' }}>Zero-shot reference audio</span>
                    </div>

                    <div style={{ fontSize: '0.82rem', color: '#cbd5e1', lineHeight: '1.4' }}>
                      To clone your voice, click <strong style={{ color: '#c084fc' }}>Start Recording</strong> and read this reference script clearly into your microphone:
                    </div>

                    {/* Script Prompt Box */}
                    <div
                      style={{
                        background: 'rgba(0, 0, 0, 0.4)',
                        border: '1px solid rgba(192, 132, 252, 0.3)',
                        borderRadius: '6px',
                        padding: '12px 14px',
                        fontStyle: 'italic',
                        color: '#f3e8ff',
                        fontSize: '0.86rem',
                        lineHeight: '1.5',
                        position: 'relative',
                      }}
                    >
                      "{cloneRefText}"
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '8px' }}>
                      <label style={{ fontSize: '0.78rem', color: '#94a3b8' }}>Voice Profile Name:</label>
                      <input
                        type="text"
                        value={cloneProfileName}
                        onChange={(e) => setCloneProfileName(e.target.value)}
                        placeholder="e.g. Kabil's Master Voice"
                        style={{
                          background: 'rgba(0, 0, 0, 0.3)',
                          border: '1px solid rgba(255, 255, 255, 0.15)',
                          borderRadius: '6px',
                          padding: '8px 12px',
                          color: '#f8fafc',
                          fontSize: '0.84rem',
                          outline: 'none',
                        }}
                      />
                    </div>

                    {/* Recording Action Buttons & Visualizer */}
                    <div
                      style={{
                        background: 'rgba(0, 0, 0, 0.25)',
                        border: '1px dashed rgba(255, 255, 255, 0.15)',
                        borderRadius: '8px',
                        padding: '16px',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        gap: '12px',
                      }}
                    >
                      {!isRecordingClone ? (
                        <button
                          type="button"
                          onClick={() => void startCloneRecording()}
                          disabled={isSavingClone}
                          style={{
                            background: 'linear-gradient(135deg, #9333ea, #a855f7)',
                            color: '#ffffff',
                            border: 'none',
                            borderRadius: '8px',
                            padding: '10px 24px',
                            fontSize: '0.90rem',
                            fontWeight: 600,
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '8px',
                            boxShadow: '0 4px 15px rgba(168, 85, 247, 0.3)',
                          }}
                        >
                          <Mic size={18} /> Start Recording Sample (10s)
                        </button>
                      ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '10px', width: '100%' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#f87171', fontWeight: 600, fontSize: '0.92rem' }}>
                            <span
                              style={{
                                width: '10px',
                                height: '10px',
                                borderRadius: '50%',
                                background: '#ef4444',
                                animation: 'pulse 1s infinite',
                              }}
                            />
                            Recording Voice Sample... ({recordSecondsLeft}s left)
                          </div>

                          <div
                            style={{
                              width: '100%',
                              height: '6px',
                              borderRadius: '3px',
                              background: 'rgba(255, 255, 255, 0.1)',
                              overflow: 'hidden',
                            }}
                          >
                            <div
                              style={{
                                height: '100%',
                                width: `${((10 - recordSecondsLeft) / 10) * 100}%`,
                                background: '#ef4444',
                                transition: 'width 1s linear',
                              }}
                            />
                          </div>

                          <button
                            type="button"
                            onClick={stopCloneRecording}
                            style={{
                              background: 'rgba(239, 68, 68, 0.2)',
                              color: '#f87171',
                              border: '1px solid rgba(239, 68, 68, 0.5)',
                              borderRadius: '6px',
                              padding: '6px 18px',
                              fontSize: '0.84rem',
                              cursor: 'pointer',
                              display: 'flex',
                              alignItems: 'center',
                              gap: '6px',
                            }}
                          >
                            <Square size={14} /> Stop Recording Early
                          </button>
                        </div>
                      )}

                      {/* Recorded Audio Preview */}
                      {recordedAudioUrl && !isRecordingClone && (
                        <div style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: '10px', marginTop: '6px' }}>
                          <span style={{ fontSize: '0.78rem', color: '#4ade80', fontWeight: 500 }}>
                            &check; Audio captured successfully! Audition before saving:
                          </span>
                          <audio controls src={recordedAudioUrl} style={{ width: '100%', height: '36px' }} />

                          <div style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end', marginTop: '4px' }}>
                            <button
                              type="button"
                              onClick={() => void startCloneRecording()}
                              disabled={isSavingClone}
                              style={{
                                background: 'transparent',
                                border: '1px solid rgba(255, 255, 255, 0.2)',
                                color: '#94a3b8',
                                borderRadius: '6px',
                                padding: '6px 14px',
                                fontSize: '0.82rem',
                                cursor: 'pointer',
                              }}
                            >
                              Re-record
                            </button>

                            <button
                              type="button"
                              onClick={() => void handleSaveClone()}
                              disabled={isSavingClone}
                              style={{
                                background: '#22c55e',
                                color: '#052e16',
                                border: 'none',
                                borderRadius: '6px',
                                padding: '6px 18px',
                                fontSize: '0.84rem',
                                fontWeight: 600,
                                cursor: 'pointer',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '6px',
                              }}
                            >
                              {isSavingClone ? <RefreshCw size={14} className="spin" /> : <Check size={14} />}
                              {isSavingClone ? 'Saving Voice...' : 'Save & Clone Voice'}
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Right Column: Enrolled Voice Profiles & Test Synthesis */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    {/* Profiles List */}
                    <div
                      style={{
                        background: 'rgba(255, 255, 255, 0.02)',
                        border: '1px solid rgba(255, 255, 255, 0.08)',
                        borderRadius: '10px',
                        padding: '16px',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '12px',
                        maxHeight: '260px',
                        overflowY: 'auto',
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span style={{ fontSize: '0.90rem', fontWeight: 600, color: '#f1f5f9' }}>
                          Saved Voice Profiles ({cloneProfiles.length})
                        </span>
                        {selectedCloneProfileId && (
                          <button
                            type="button"
                            onClick={() => handleSelectCloneProfile(null)}
                            style={{
                              background: 'transparent',
                              border: 'none',
                              color: '#94a3b8',
                              fontSize: '0.74rem',
                              cursor: 'pointer',
                              textDecoration: 'underline',
                            }}
                          >
                            Switch back to AI Personas
                          </button>
                        )}
                      </div>

                      {cloneProfiles.length === 0 ? (
                        <div style={{ padding: '24px 12px', textAlign: 'center', color: '#64748b', fontSize: '0.84rem' }}>
                          No cloned voices yet. Record your first 5–10s audio sample on the left to hear JARVIS speak with your voice!
                        </div>
                      ) : (
                        cloneProfiles.map((p) => {
                          const isSelected = selectedCloneProfileId === p.id;
                          return (
                            <div
                              key={p.id}
                              style={{
                                background: isSelected ? 'rgba(192, 132, 252, 0.12)' : 'rgba(255, 255, 255, 0.03)',
                                border: isSelected ? '1px solid rgba(192, 132, 252, 0.5)' : '1px solid rgba(255, 255, 255, 0.08)',
                                borderRadius: '8px',
                                padding: '12px 14px',
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                                gap: '8px',
                              }}
                            >
                              <div>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                  <span style={{ fontWeight: 600, fontSize: '0.88rem', color: isSelected ? '#c084fc' : '#f8fafc' }}>
                                    {p.name}
                                  </span>
                                  {isSelected && (
                                    <span
                                      style={{
                                        fontSize: '0.68rem',
                                        background: 'rgba(192, 132, 252, 0.25)',
                                        color: '#e9d5ff',
                                        padding: '1px 6px',
                                        borderRadius: '4px',
                                        fontWeight: 600,
                                      }}
                                    >
                                      Active Voice
                                    </span>
                                  )}
                                </div>
                                <div style={{ fontSize: '0.74rem', color: '#94a3b8', marginTop: '2px' }}>
                                  Ref Sample: {p.durationSeconds.toFixed(1)}s &bull; Offline WAV
                                </div>
                              </div>

                              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                <button
                                  type="button"
                                  onClick={() => handleSelectCloneProfile(isSelected ? null : p)}
                                  style={{
                                    background: isSelected ? '#c084fc' : 'rgba(255, 255, 255, 0.06)',
                                    color: isSelected ? '#0f172a' : '#cbd5e1',
                                    border: isSelected ? 'none' : '1px solid rgba(255, 255, 255, 0.15)',
                                    borderRadius: '5px',
                                    padding: '5px 12px',
                                    fontSize: '0.78rem',
                                    fontWeight: 600,
                                    cursor: 'pointer',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '4px',
                                  }}
                                >
                                  <Check size={12} /> {isSelected ? 'Active' : 'Use Voice'}
                                </button>

                                <button
                                  type="button"
                                  onClick={() => void handleDeleteClone(p.id)}
                                  style={{
                                    background: 'transparent',
                                    border: 'none',
                                    color: '#64748b',
                                    cursor: 'pointer',
                                    padding: '5px',
                                  }}
                                  title="Delete Profile"
                                >
                                  <Trash2 size={14} />
                                </button>
                              </div>
                            </div>
                          );
                        })
                      )}
                    </div>

                    {/* Test Drive Box */}
                    <div
                      style={{
                        background: 'rgba(255, 255, 255, 0.02)',
                        border: '1px solid rgba(255, 255, 255, 0.08)',
                        borderRadius: '10px',
                        padding: '16px',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '10px',
                      }}
                    >
                      <label style={{ fontSize: '0.84rem', fontWeight: 500, color: '#f1f5f9' }}>
                        Test Drive Cloned Voice:
                      </label>
                      <textarea
                        rows={2}
                        value={cloneTestPhrase}
                        onChange={(e) => setCloneTestPhrase(e.target.value)}
                        placeholder="Type any sentence to hear JARVIS speak with your voice..."
                        style={{
                          width: '100%',
                          background: 'rgba(0, 0, 0, 0.3)',
                          border: '1px solid rgba(255, 255, 255, 0.15)',
                          borderRadius: '6px',
                          padding: '8px 12px',
                          color: '#f8fafc',
                          fontSize: '0.82rem',
                          resize: 'none',
                          outline: 'none',
                        }}
                      />

                      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                        <button
                          type="button"
                          onClick={() => void handleTestCloneSpeech(cloneTestPhrase)}
                          disabled={isTestingClone || cloneProfiles.length === 0}
                          style={{
                            background: cloneProfiles.length > 0 ? '#c084fc' : 'rgba(255, 255, 255, 0.1)',
                            color: cloneProfiles.length > 0 ? '#0f172a' : '#64748b',
                            border: 'none',
                            borderRadius: '6px',
                            padding: '6px 16px',
                            fontSize: '0.82rem',
                            fontWeight: 600,
                            cursor: cloneProfiles.length > 0 ? 'pointer' : 'not-allowed',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '6px',
                          }}
                        >
                          {isTestingClone ? <RefreshCw size={13} className="spin" /> : <Play size={13} />}
                          {isTestingClone ? 'Synthesizing...' : 'Speak with Cloned Voice'}
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* Tab 3: Acoustic Fine-Tuning */}
            {modalTab === 'tuning' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
                  {/* Pitch Slider */}
                  <div
                    style={{
                      background: 'rgba(255, 255, 255, 0.03)',
                      border: '1px solid rgba(255, 255, 255, 0.08)',
                      borderRadius: '8px',
                      padding: '16px',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                      <span style={{ fontSize: '0.88rem', fontWeight: 500, color: '#f1f5f9' }}>Voice Pitch (Frequency)</span>
                      <span style={{ fontSize: '0.84rem', color: '#00f0ff', fontFamily: 'monospace' }}>
                        {voicePitch.toFixed(2)}x
                      </span>
                    </div>
                    <input
                      type="range"
                      min="0.6"
                      max="1.5"
                      step="0.02"
                      value={voicePitch}
                      onChange={(e) => {
                        const val = parseFloat(e.target.value);
                        setVoicePitch(val);
                        try {
                          localStorage.setItem('jarvis_voice_pitch', val.toString());
                        } catch {}
                      }}
                      style={{ width: '100%', accentColor: '#00f0ff', cursor: 'pointer' }}
                    />
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.72rem', color: '#64748b', marginTop: '4px' }}>
                      <span>0.6x (Deep Baritone)</span>
                      <span>1.0x (Standard)</span>
                      <span>1.5x (High Pitch)</span>
                    </div>
                  </div>

                  {/* Rate / Speed Slider */}
                  <div
                    style={{
                      background: 'rgba(255, 255, 255, 0.03)',
                      border: '1px solid rgba(255, 255, 255, 0.08)',
                      borderRadius: '8px',
                      padding: '16px',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                      <span style={{ fontSize: '0.88rem', fontWeight: 500, color: '#f1f5f9' }}>Speech Speed (Cadence)</span>
                      <span style={{ fontSize: '0.84rem', color: '#00f0ff', fontFamily: 'monospace', fontWeight: 700 }}>
                        {voiceRate.toFixed(2)}x
                      </span>
                    </div>
                    <input
                      type="range"
                      min="0.5"
                      max="2.0"
                      step="0.05"
                      value={voiceRate}
                      onChange={(e) => updateVoiceRate(parseFloat(e.target.value))}
                      style={{ width: '100%', accentColor: '#00f0ff', cursor: 'pointer' }}
                    />
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.72rem', color: '#64748b', marginTop: '4px' }}>
                      <span>0.5x (Deliberate)</span>
                      <span>1.0x (Natural)</span>
                      <span>2.0x (Turbo)</span>
                    </div>

                    {/* Quick Presets */}
                    <div style={{ display: 'flex', gap: '6px', marginTop: '10px' }}>
                      {[0.8, 1.0, 1.25, 1.5].map((preset) => (
                        <button
                          key={preset}
                          type="button"
                          onClick={() => updateVoiceRate(preset)}
                          style={{
                            flex: 1,
                            background: Math.abs(voiceRate - preset) < 0.03 ? '#00f0ff' : 'rgba(255, 255, 255, 0.06)',
                            color: Math.abs(voiceRate - preset) < 0.03 ? '#0b1120' : '#cbd5e1',
                            border: 'none',
                            borderRadius: '4px',
                            padding: '4px 8px',
                            fontSize: '0.76rem',
                            fontWeight: 600,
                            cursor: 'pointer',
                          }}
                        >
                          {preset}x
                        </button>
                      ))}
                    </div>

                    <div style={{ marginTop: '8px', fontSize: '0.70rem', color: '#38bdf8', opacity: 0.85 }}>
                      💡 Voice Commands: Say <em>"speak faster"</em>, <em>"slow down"</em>, <em>"set speed to 1.3"</em>, or <em>"வேகமாக பேசு"</em>
                    </div>
                  </div>
                </div>

                {/* Custom Phrase Audition Box */}
                <div
                  style={{
                    background: 'rgba(255, 255, 255, 0.03)',
                    border: '1px solid rgba(255, 255, 255, 0.08)',
                    borderRadius: '8px',
                    padding: '16px',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '10px',
                  }}
                >
                  <label style={{ fontSize: '0.88rem', fontWeight: 500, color: '#f1f5f9' }}>
                    Audition Custom Text Phrase:
                  </label>
                  <textarea
                    rows={2}
                    value={customTestPhrase}
                    onChange={(e) => setCustomTestPhrase(e.target.value)}
                    placeholder="Type any custom sentence to hear how your selected voice speaks it..."
                    style={{
                      width: '100%',
                      background: 'rgba(0, 0, 0, 0.3)',
                      border: '1px solid rgba(255, 255, 255, 0.15)',
                      borderRadius: '6px',
                      padding: '8px 12px',
                      color: 'inherit',
                      fontSize: '0.86rem',
                      resize: 'none',
                      outline: 'none',
                    }}
                  />

                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <button
                      type="button"
                      onClick={() => {
                        setVoicePitch(1.0);
                        setVoiceRate(1.0);
                        try {
                          localStorage.setItem('jarvis_voice_pitch', '1.0');
                          localStorage.setItem('jarvis_voice_rate', '1.0');
                        } catch {}
                      }}
                      style={{
                        background: 'transparent',
                        border: 'none',
                        color: '#94a3b8',
                        fontSize: '0.80rem',
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '4px',
                      }}
                    >
                      <RotateCcw size={13} /> Reset Pitch & Speed to 1.0x
                    </button>

                    <button
                      type="button"
                      onClick={() => playCustomPhrase(customTestPhrase)}
                      style={{
                        background: '#00f0ff',
                        color: '#0b1120',
                        border: 'none',
                        borderRadius: '6px',
                        padding: '6px 16px',
                        fontSize: '0.84rem',
                        fontWeight: 600,
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '6px',
                      }}
                    >
                      <Play size={14} /> Speak Custom Phrase
                    </button>
                  </div>
                </div>
              </div>
            )}

            {/* Footer Summary & Done button */}
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                borderTop: '1px solid rgba(255, 255, 255, 0.1)',
                paddingTop: '16px',
              }}
            >
              <div style={{ fontSize: '0.82rem', color: '#94a3b8' }}>
                Active Profile: <strong style={{ color: '#00f0ff' }}>{activeDisplayName}</strong> (Pitch: {voicePitch.toFixed(2)}x, Speed: {voiceRate.toFixed(2)}x)
              </div>

              <button
                type="button"
                onClick={() => {
                  if (isPageMode) {
                    if (onNavigate) onNavigate('assistant');
                  } else {
                    setIsVoiceModalOpen(false);
                  }
                }}
                style={{
                  background: '#00f0ff',
                  color: '#0b1120',
                  border: 'none',
                  borderRadius: '6px',
                  padding: '8px 20px',
                  fontSize: '0.86rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
              >
                {isPageMode ? (onNavigate ? 'Apply & Return to Assistant' : 'Saved') : 'Done'}
              </button>
            </div>
          </div>
  );

  if (mode === 'page') {
    return (
      <section className="settings-page voice-studio-page" style={{ maxWidth: '1050px', margin: '0 auto', padding: '24px 20px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '22px' }}>
          <div>
            <span className="eyebrow">WORKSPACE &gt; VOICE &amp; PERSONAS</span>
            <h1 style={{ display: 'flex', alignItems: 'center', gap: '10px', margin: '6px 0 0', fontSize: '1.8rem', color: '#f8fafc' }}>
              <Volume2 className="text-cyan-400" size={26} />
              Iron Man AI Voice Persona Studio
            </h1>
            <p className="subtle" style={{ margin: '6px 0 0', color: '#94a3b8' }}>
              Audition and select iconic Marvel Stark AI personas, train local zero-shot voice clones, and customize speech synthesis.
            </p>
          </div>

          <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
            <button
              type="button"
              className="text-button"
              style={{ color: '#00f0ff', borderColor: 'rgba(0, 240, 255, 0.4)', background: 'rgba(0, 240, 255, 0.08)' }}
              onClick={() => setEffectiveHudOpen(true)}
              title="Launch Iron Man J.A.R.V.I.S. 3D Holographic HUD"
            >
              <Sparkles size={16} /> Iron Man HUD
            </button>
            {onNavigate && (
              <button
                type="button"
                className="text-button"
                onClick={() => onNavigate('assistant')}
                title="Return to Assistant Chat"
              >
                Back to Assistant
              </button>
            )}
          </div>
        </div>

        <div
          style={{
            background: 'rgba(255, 255, 255, 0.03)',
            border: '1px solid rgba(255, 255, 255, 0.08)',
            borderRadius: '10px',
            padding: '14px 18px',
            marginBottom: '20px',
            display: 'flex',
            flexWrap: 'wrap',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: '14px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px', flexWrap: 'wrap' }}>
            <label style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '0.86rem' }}>
              <Globe size={15} style={{ color: '#38bdf8' }} />
              <span>Language:</span>
              <select
                value={lang}
                disabled={busy || !!stage}
                onChange={(e) => handleLangChange(e.target.value as 'en-US' | 'ta-IN')}
                style={{
                  background: 'rgba(255, 255, 255, 0.08)',
                  color: 'inherit',
                  border: '1px solid rgba(255, 255, 255, 0.2)',
                  borderRadius: '6px',
                  padding: '4px 8px',
                  fontSize: '0.84rem',
                  cursor: 'pointer',
                }}
              >
                <option value="en-US">English (US)</option>
                <option value="ta-IN">தமிழ் (Tamil)</option>
              </select>
            </label>

            <label style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '0.86rem', cursor: 'pointer' }}>
              <input type="checkbox" checked={continuous} onChange={(e) => setContinuous(e.target.checked)} />
              <span>Continuous Conversation</span>
            </label>

            <button
              type="button"
              className="text-button"
              style={{
                color: wakeWordActive ? '#34d399' : '#94a3b8',
                borderColor: wakeWordActive ? 'rgba(52, 211, 153, 0.5)' : 'rgba(255, 255, 255, 0.2)',
                background: wakeWordActive ? 'rgba(52, 211, 153, 0.12)' : 'transparent',
                fontSize: '0.82rem',
                padding: '4px 10px',
              }}
              onClick={toggleWakeWord}
            >
              <Radio size={14} className={wakeWordActive ? 'animate-pulse' : ''} />
              {wakeWordActive ? 'Wake-Word: ACTIVE ("Hey Jarvis")' : 'Wake-Word: OFF'}
            </button>

            <button
              type="button"
              className="text-button"
              style={{
                color: warMode ? '#ef4444' : '#94a3b8',
                borderColor: warMode ? 'rgba(239, 68, 68, 0.6)' : 'rgba(255, 255, 255, 0.2)',
                background: warMode ? 'rgba(239, 68, 68, 0.15)' : 'transparent',
                boxShadow: warMode ? '0 0 12px rgba(239, 68, 68, 0.3)' : 'none',
                fontSize: '0.82rem',
                padding: '4px 10px',
              }}
              onClick={toggleWarMode}
              title={warMode ? 'War Mode Engaged (House Party Protocol)' : 'Engage War Mode'}
            >
              <Zap size={14} className={warMode ? 'animate-pulse text-red-500' : ''} />
              {warMode ? 'WAR MODE: ENGAGED 🔥' : 'WAR MODE: STANDBY'}
            </button>

            {/* Manual Speech Speed Controller */}
            <div
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '8px',
                background: 'rgba(0, 240, 255, 0.06)',
                border: '1px solid rgba(0, 240, 255, 0.3)',
                borderRadius: '8px',
                padding: '4px 12px',
              }}
              title="Adjust speech rate manually (0.5x to 2.0x) or speak 'Jarvis, speak faster'"
            >
              <Zap size={14} style={{ color: '#00f0ff' }} />
              <span style={{ fontSize: '0.80rem', color: '#94a3b8', fontWeight: 500 }}>Speed:</span>
              <input
                type="range"
                min="0.5"
                max="2.0"
                step="0.05"
                value={voiceRate}
                onChange={(e) => updateVoiceRate(parseFloat(e.target.value))}
                style={{ width: '85px', accentColor: '#00f0ff', cursor: 'pointer' }}
              />
              <span style={{ fontSize: '0.82rem', color: '#00f0ff', fontFamily: 'monospace', fontWeight: 700, minWidth: '40px' }}>
                {voiceRate.toFixed(2)}x
              </span>
              <div style={{ display: 'flex', gap: '3px' }}>
                {[0.8, 1.0, 1.25, 1.5].map((preset) => (
                  <button
                    key={preset}
                    type="button"
                    onClick={() => updateVoiceRate(preset)}
                    style={{
                      background: Math.abs(voiceRate - preset) < 0.03 ? '#00f0ff' : 'rgba(255, 255, 255, 0.08)',
                      color: Math.abs(voiceRate - preset) < 0.03 ? '#0b1120' : '#cbd5e1',
                      border: 'none',
                      borderRadius: '4px',
                      padding: '2px 6px',
                      fontSize: '0.70rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                    }}
                  >
                    {preset}x
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div style={{ fontSize: '0.84rem', color: '#94a3b8', display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
            <span>Active Profile: <strong style={{ color: '#00f0ff' }}>{activeDisplayName}</strong> (Pitch: {voicePitch.toFixed(2)}x, Speed: {voiceRate.toFixed(2)}x)</span>
            <span style={{ color: 'rgba(255,255,255,0.2)' }}>|</span>
            <span>Mic Engine: <strong style={{ color: '#00f0ff' }}>{recognitionEngine}</strong></span>
            {lastConfidence !== null && (
              <>
                <span style={{ color: 'rgba(255,255,255,0.2)' }}>|</span>
                <span>Confidence: <strong style={{ color: lastConfidence < 0.35 ? '#f87171' : '#34d399' }}>{Math.round(lastConfidence * 100)}%</strong></span>
              </>
            )}
          </div>
        </div>

        {renderStudioCard(true)}

        {isHudOpen && (
          <JarvisHologram
            state={getHologramState()}
            lang={lang}
            userTranscript={userTranscript}
            jarvisReply={jarvisReply}
            onClose={() => setEffectiveHudOpen(false)}
            onMicClick={() => {
              if (stage) stop();
              else void talk(false);
            }}
            isContinuous={continuous}
            onContinuousChange={setContinuous}
            onLangChange={handleLangChange}
            voiceRate={voiceRate}
            onVoiceRateChange={updateVoiceRate}
            onSendText={async (text: string) => {
              setUserTranscript(text);
              const controller = new AbortController();
              active.current = controller;
              try {
                const prefix = warMode && !text.startsWith('/') ? '/war' : undefined;
                await streamAndSpeak(text, controller, prefix);
              } catch (e) {
                setError(e instanceof Error ? e.message : 'Text execution failed.');
              } finally {
                setStage('');
              }
            }}
            telemetry={{
              ram: diagnostics?.ram ? `${diagnostics.ram.freeFormatted} free / ${diagnostics.ram.totalFormatted}` : undefined,
              gpu: diagnostics?.gpu ? `${diagnostics.gpu.name} (${diagnostics.gpu.freeFormatted} free)` : undefined,
              rom: diagnostics?.rom ? `${diagnostics.rom.freeFormatted} free / ${diagnostics.rom.totalFormatted}` : undefined,
              model: diagnostics?.model,
            }}
          />
        )}
      </section>
    );
  }

  return (
    <>
      {stage && (
        <div className={`voice-compact-active-pill ${warMode ? 'war-active-pill' : ''}`} role="status">
          <div className={`voice-pulse-dot ${warMode ? 'war-pulse-dot' : ''}`} />
          <span className="voice-stage-label">
            {warMode && (
              <span
                style={{
                  background: 'rgba(239, 68, 68, 0.25)',
                  color: '#f87171',
                  border: '1px solid rgba(239, 68, 68, 0.5)',
                  borderRadius: '4px',
                  padding: '1px 6px',
                  fontSize: '0.72rem',
                  fontWeight: 700,
                  letterSpacing: '0.5px',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '4px',
                }}
              >
                🔥 WAR MODE
              </span>
            )}
            <span
              style={{
                background: 'rgba(0, 240, 255, 0.12)',
                color: '#00f0ff',
                border: '1px solid rgba(0, 240, 255, 0.3)',
                borderRadius: '4px',
                padding: '1px 6px',
                fontSize: '0.70rem',
                fontWeight: 600,
                display: 'inline-flex',
                alignItems: 'center',
                gap: '4px',
                marginRight: '6px',
              }}
              title={`Active Recognition Engine: ${recognitionEngine}`}
            >
              🎤 {recognitionEngine === 'Browser Web Speech API' ? 'Web Speech' : 'Desktop Speech'}
            </span>
            {lastConfidence !== null && (
              <span
                style={{
                  background: lastConfidence < 0.35 ? 'rgba(239, 68, 68, 0.15)' : 'rgba(52, 211, 153, 0.15)',
                  color: lastConfidence < 0.35 ? '#f87171' : '#34d399',
                  border: `1px solid ${lastConfidence < 0.35 ? 'rgba(239, 68, 68, 0.4)' : 'rgba(52, 211, 153, 0.4)'}`,
                  borderRadius: '4px',
                  padding: '1px 5px',
                  fontSize: '0.70rem',
                  fontWeight: 700,
                  display: 'inline-flex',
                  alignItems: 'center',
                  marginRight: '6px',
                }}
                title={`Speech Confidence: ${Math.round(lastConfidence * 100)}%`}
              >
                {Math.round(lastConfidence * 100)}%
              </span>
            )}
            {stage}
          </span>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
            <button
              type="button"
              className="voice-stop-mini-btn"
              style={{
                padding: '2px 8px',
                fontSize: '0.74rem',
                color: '#00f0ff',
                borderColor: 'rgba(0, 240, 255, 0.4)',
                background: 'rgba(0, 240, 255, 0.08)',
              }}
              onClick={() => updateVoiceRate(voiceRate >= 1.5 ? 0.8 : Number((voiceRate + 0.2).toFixed(2)))}
              title="Cycle Voice Speed: 0.8x -> 1.0x -> 1.2x -> 1.4x -> 1.5x"
            >
              ⚡ {voiceRate.toFixed(2)}x
            </button>
            <button
              type="button"
              className="voice-stop-mini-btn"
              onClick={stop}
              title="Stop voice"
            >
              <Square size={12} fill="currentColor" /> Stop
            </button>
          </div>
        </div>
      )}

      {error && (
        <div className="voice-compact-error" role="alert">
          <span>{error}</span>
          <button type="button" onClick={() => setError('')}><X size={12} /></button>
        </div>
      )}

      {/* Voice Persona Studio & Sample Audition Modal */}
      {isVoiceModalOpen && (
        <div
          role="dialog"
          aria-modal="true"
          style={{
            position: 'fixed',
            inset: 0,
            zIndex: 9999,
            backgroundColor: 'rgba(0, 0, 0, 0.85)',
            backdropFilter: 'blur(8px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '20px',
          }}
          onClick={(e) => {
            if (e.target === e.currentTarget) setIsVoiceModalOpen(false);
          }}
        >
          {renderStudioCard(false)}
        </div>
      )}

      {/* Full 3D Holographic Iron Man J.A.R.V.I.S. Modal HUD */}
      {isHudOpen && (
        <JarvisHologram
          state={getHologramState()}
          lang={lang}
          userTranscript={userTranscript}
          jarvisReply={jarvisReply}
          onClose={() => setEffectiveHudOpen(false)}
          onMicClick={() => {
            if (stage) stop();
            else void talk(false);
          }}
          isContinuous={continuous}
          onContinuousChange={setContinuous}
          onLangChange={handleLangChange}
          voiceRate={voiceRate}
          onVoiceRateChange={updateVoiceRate}
          onSendText={async (text: string) => {
            setUserTranscript(text);
            const controller = new AbortController();
            active.current = controller;
            try {
              const prefix = warMode && !text.startsWith('/') ? '/war' : undefined;
              await streamAndSpeak(text, controller, prefix);
            } catch (e) {
              setError(e instanceof Error ? e.message : 'Text execution failed.');
            } finally {
              setStage('');
            }
          }}
          telemetry={{
            ram: diagnostics?.ram ? `${diagnostics.ram.freeFormatted} free / ${diagnostics.ram.totalFormatted}` : undefined,
            gpu: diagnostics?.gpu ? `${diagnostics.gpu.name} (${diagnostics.gpu.freeFormatted} free)` : undefined,
            rom: diagnostics?.rom ? `${diagnostics.rom.freeFormatted} free / ${diagnostics.rom.totalFormatted}` : undefined,
            model: diagnostics?.model,
          }}
        />
      )}
    </>
  );
}

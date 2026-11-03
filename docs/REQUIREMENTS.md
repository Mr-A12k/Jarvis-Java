# JARVIS AI System Requirements Specification

## 1. Executive Summary

JARVIS AI is a sovereign, local-first artificial intelligence assistant engineered for deep-thinking reasoning, real-time autonomous research, multi-factor biometric authentication, hardware telemetry, and fluid multi-lingual interaction. 

Unlike traditional static assistants, JARVIS is strictly forbidden from relying on hardcoded, prebuilt answers. When faced with missing context or dynamic queries, the system engages an internal reasoning engine with real-time web search, continuous learning, and transparent thought-process streaming (`<think>` blocks). 

JARVIS supports omnipresent voice and text control across all subsystems, allowing users to orchestrate biometrics, hardware telemetry, web search, browser automation, and goal-directed agent workflows directly from voice or text prompts without navigating away from the workspace.

---

## 2. Dynamic Multi-Lingual Architecture & Strict Language Mirroring

### 2.1 Strict Language Mirroring
- **English Queries**: Any question or command received in English must be processed and answered **strictly in English**, including both the final response and the internal `<think>` reasoning trace.
- **Tamil Queries**: Any question or command received in Tamil (or Tamil phonetic transliteration) must be processed and answered **strictly in Tamil**, honoring native Tamil script, grammar, and vocabulary.
- **Prompt Isolation**: System memory notes, conversation transcripts, and background prompts must be strictly isolated to prevent cross-language pollution (e.g., historical Tamil research notes must never cause an English query to be answered in Tamil).
- **Zero-Bleed Detection**: Queries containing foreign language names (e.g., *"Why are you speaking in Tamil?"* or *"Teach me Tamil"*) must remain recognized as English commands and not trigger false-positive Tamil rendering.

### 2.2 Dynamic Multi-Lingual Auto-Switching
JARVIS continuously monitors natural language directives to dynamically alter its active conversational language across both voice synthesis (TTS) and LLM prompt directives.
- **Supported Languages**: English (`en`), Tamil (`ta`), Hindi (`hi`), French (`fr`), Spanish (`es`), German (`de`), Japanese (`ja`).
- **Dynamic Trigger Patterns**:
  - English: *"switch to English"*, *"speak in English"*, *"change to English"*, *"talk in English"*
  - Tamil: *"switch to Tamil"*, *"speak in Tamil"*, *"தமிழில் பேசு"*, *"தமிழ் மொழிக்கு மாறு"*, *"தமிழில் பதிலளி"*
  - Hindi: *"switch to Hindi"*, *"speak in Hindi"*, *"हिंदी में बोलो"*
  - French: *"switch to French"*, *"speak in French"*, *"parlez en français"*
  - Spanish: *"switch to Spanish"*, *"speak in Spanish"*, *"habla en español"*
  - German: *"switch to German"*, *"speak in German"*, *"sprich auf deutsch"*
  - Japanese: *"switch to Japanese"*, *"speak in Japanese"*, *"日本語で話して"*
- **Instantaneous Confirmation**: When a switch command is received, JARVIS immediately confirms the transition in the **target language** with an embedded `<think>` reasoning block explaining the context shift.

### 2.3 Tamil Language High Priority
- **Native Script Quality**: High-priority support for Tamil typography, morphological parsing, and idiomatic speech synthesis.
- **Tamil Phonetic Dictionary**: Built-in phonetic mapper for key operational concepts (e.g., "நிலை", "உறுதிப்படுத்தப்பட்டது", "முழுமை").
- **Voice Synthesis**: Direct integration with Tamil text-to-speech engines (`ta-IN`) for natural spoken output when operating in Tamil mode.

---

## 3. Zero Prebuilt Answers & Model Deep-Thinking Architecture

### 3.1 Prohibition of Prebuilt Answers
- **Dynamic Reasoning**: JARVIS must never use hardcoded or pre-baked answers for factual or conversational queries.
- **Internal Thinking Blocks (`<think>`)**: JARVIS exposes its step-by-step reasoning process using `<think>...</think>` tags modeled after DeepSeek thinking behavior.
- **Audible & Textual Process Updates**: While formulating an answer or executing multi-step retrieval, JARVIS communicates what it is actively doing in either voice or text thought mode.

### 3.2 Autonomous Web Retrieval & Online Learning
- **Zero-Knowledge Recovery**: If the local model does not possess sufficient knowledge or facts are out of date, JARVIS autonomously invokes web search providers (DuckDuckGo, Wikipedia, Google Scraper).
- **Context Synthesis & Learning**: JARVIS reads, synthesizes, and incorporates the retrieved search snippets into its live working memory before delivering the verified final answer.
- **Evidence Citations**: All retrieved facts must cite timestamped source URLs and verified snippets.

---

## 4. Omnipresent Multimodal Voice & Text Control

### 4.1 Global Command Dispatcher
Users can operate all platform features seamlessly through conversational voice commands and text commands without being required to open individual UI pages:
- **Biometric Authentication**:
  - *"Verify my face and identity"* -> Triggers multi-factor biometric camera verification.
  - *"Register my face"* -> Enrolls new facial profile and extracts 128-d feature embeddings.
  - *"Who am I?"* / *"Check my authorization"* -> Returns active user identity profile.
- **Hardware Telemetry**:
  - *"What is the hardware status?"* / *"System diagnostics"* -> Queries live CPU, RAM, GPU, and disk metrics.
- **Autonomous Research**:
  - *"Search the web for [topic]"* -> Performs live multi-source web crawl and summarizes results.
- **Browser Automation**:
  - *"Open browser and navigate to [url]"* -> Launches sandboxed Playwright session.
- **Goal & Plan Execution**:
  - *"Create a goal to [objective]"* -> Decomposes request into autonomous multi-step subtasks.
- **Language Switching**:
  - *"Switch to English"*, *"Speak in Tamil"* -> Instantly reconfigures global language preference.

### 4.2 Voice Input & Output Pipeline
- **Push-to-Talk & Continuous Listening**: Low-latency speech recognition with silence timeout and continuous conversation modes.
- **Speech Synthesis (TTS)**: Offline Windows System.Speech integration with fallback to Web Speech API, matching the active user language.

### 4.3 Iron Man & Marvel AI Universe Voice Personas
- **Curated Stark AI Personas**:
  1. *J.A.R.V.I.S.*: British butler & strategic AI (Paul Bettany cadence, pitch 0.94, rate 1.00).
  2. *F.R.I.D.A.Y.*: Tactical combat assistant (Kerry Condon, pitch 1.08, rate 1.04).
  3. *U.L.T.R.O.N.*: Chilling theatrical sovereign AI (James Spader, pitch 0.72, rate 0.90).
  4. *E.D.I.T.H.*: Orbital defense & drone satellite HUD AI (Dawn Michelle King, pitch 1.00, rate 1.02).
  5. *K.A.R.E.N.*: Suit companion AI (Jennifer Connelly / Suit Lady, pitch 1.04, rate 0.98).
  6. *J.O.C.A.S.T.A.*: Alternative Stark encryption & security AI (pitch 1.15, rate 1.08).
  7. *Iron Man Baritone*: Deep authoritative male commander (pitch 0.82, rate 0.98).
  8. *Tamil JARVIS*: Native South Indian Tamil inflection honoring pure pronunciation.
- **Audio Sample Audition**: Users can audition each voice persona or installed Windows voice via live interactive audio playback.
- **Acoustic Fine-Tuning**: Real-time control of pitch (0.6x - 1.5x) and speech rate (0.6x - 1.5x) with custom phrase testing.
- **Vocal Switching Commands**: Natural language voice commands to change voice persona on demand (*"Change voice to Ultron"*, *"Change voice to Friday"*, *"Change voice to Edith"*, *"Change voice to Jarvis"*, *"Change voice to Karen"*).

### 4.4 Ergonomic Workspace Layout & Quick Access Hierarchy
- **Distraction-Free Chat Area**: Bulky configuration panels, dropdown menus, and helper paragraphs are removed from the chat input viewport.
- **Mandatory Quick-Access Tools**: Embedded directly within the prompt composer bottom bar beside the send button:
  - `[🌐 Web search]`: Instant toggle pill for DuckDuckGo live retrieval.
  - `[🎤 Voice]`: Tap-to-talk mic button that animates with waveform pulses when recording and toggles to stop.
- **Dynamic Live Activity Indicator**: A compact floating pill (`[● Listening... · Stop ⏹]`) appears only during active speech/synthesis and automatically clears when idle.
- **Dedicated Navigation & Settings Locations**: Full Voice & Persona Studio controls (auditioning, voice cloning, acoustic sliders) are housed in:
  1. **Left Sidebar**: Dedicated *Voice & Personas* workspace tab.
  2. **Settings Page**: *Voice & AI Personas* configuration section.
  3. **Right Inspector**: *Active Voice Persona* card with single-click studio access.

---

## 5. Security, Biometrics & Hardware Diagnostics

### 5.1 Biometric Security
- **Face Recognition**: 128-dimensional Euclidean distance matching with liveness verification (blink detection, head pose variation).
- **Voice Acoustic Analysis**: Pitch, fundamental frequency (F0), and spectral centroid matching for voice authentication.
- **Local Profile Store**: Biometric vectors are encrypted and stored locally in `data/biometrics/profiles.json`; biometric data never leaves the host machine.

### 5.2 Real-Time Hardware Telemetry
- **CPU & RAM**: Live thread utilization, heap memory headroom, and swap paging.
- **GPU Diagnostics**: NVIDIA NVML / DirectX query for VRAM allocation, temperature, and compute load.
- **Disk & Network**: I/O throughput and network reachability.

---

## 6. Goal-Driven Autonomous Execution

### 6.1 Multi-Step Decomposition
- **Goal Formulation**: Converts high-level user directives into Directed Acyclic Graphs (DAGs) of executable subtasks.
- **Execution Loop**: Autonomous iteration through subtasks with pre-conditions, actions, and post-execution validation checkpoints.
- **Safety Gates**: Critical actions (file deletion, network egress, credential storage) require human-in-the-loop cryptographic authorization tokens.

---

## 7. Verification & Acceptance Criteria

| Requirement Area | Success Criteria | Verification Method |
|---|---|---|
| **Strict English Mirroring** | English questions answered 100% in English with English `<think>` block | Automated curl / live browser chat |
| **Strict Tamil Mirroring** | Tamil questions answered in Tamil script with Tamil `<think>` block | Automated curl / live browser chat |
| **Dynamic Language Switch** | Immediate language switch upon vocal/text command with confirmation | Live speech/text trigger test |
| **Zero Prebuilt Answers** | Answers generated dynamically with visible reasoning trace | Inspection of LLM output tokens |
| **Autonomous Search** | Out-of-knowledge queries trigger web research before answering | DuckDuckGo web search integration test |
| **Voice Command Control** | Voice commands execute biometrics, hardware status, and goals | Full voice dispatcher integration suite |
| **Biometric Verification** | Face match confirmed within Euclidean distance threshold (< 0.6) | Camera verification test suite |

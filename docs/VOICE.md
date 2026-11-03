# Offline Voice & Multi-Lingual Speech Architecture

## 1. Overview & Voice Operation
Press **Talk to JARVIS** or use the push-to-talk microphone button. The recognized speech appears in the chat transcript; JARVIS reasons through the prompt with DeepSeek-style thinking mode (`<think>` blocks), formulates the answer, and synthesizes speech aloud.
- **Continuous Conversation**: Enable **Continue conversation** to automatically listen again after each spoken answer.
- **Stop Voice**: Cancels active listening, LLM generation, or speech synthesis immediately.
- **Zero Cloud Recording**: Audio processing operates locally without recording or persisting raw audio buffers.

## 2. Dynamic Multi-Lingual Voice Support & Auto-Switching
JARVIS features dynamic multi-lingual speech recognition and text-to-speech (TTS) synthesis with strict language mirroring:
- **Default Recognition**: Initialized to English (`en-US`) or user preference.
- **Automatic Language Switching**: Voice commands dynamically reconfigure both speech recognition and TTS on the fly:
  - *"Switch to English"* / *"Speak in English"* -> Reconfigures recognizer and voice synthesizer to `en-US`.
  - *"Switch to Tamil"* / *"Speak in Tamil"* / *"தமிழில் பேசு"* -> Reconfigures recognizer and voice synthesizer to `ta-IN`.
  - *"Switch to Hindi"* / *"Speak in Hindi"* -> Reconfigures to `hi-IN`.
  - *"Switch to French"* / *"Speak in French"* -> Reconfigures to `fr-FR`.
  - *"Switch to Spanish"* / *"Speak in Spanish"* -> Reconfigures to `es-ES`.
  - *"Switch to German"* / *"Speak in German"* -> Reconfigures to `de-DE`.
  - *"Switch to Japanese"* / *"Speak in Japanese"* -> Reconfigures to `ja-JP`.
- **Target Language Confirmation**: JARVIS acknowledges language transitions in the target language with an audible confirmation and explicit reasoning trace.

## 3. Omnipresent Multimodal Voice Commands
All key system features can be triggered directly through voice or text commands without navigating away from the chat screen:
- **Biometrics**: *"Verify my face and identity"*, *"Register my face"*, *"Who am I?"*.
- **Hardware Telemetry**: *"What is the hardware status?"*, *"Check system diagnostics"*.
- **Web Search**: *"Search the web for [query]"*, *"Look up latest news on [topic]"*.
- **Browser Automation**: *"Open browser and go to [url]"*.
- **Goal Planning**: *"Create a goal to [task]"*.
- **Language Switch**: *"Switch to English"*, *"Speak in Tamil"*.

## 4. Iron Man & Marvel AI Universe Voice Personas
JARVIS features a dedicated **Voice Persona Studio** with full integration of iconic Marvel AI characters, live sample auditions, and fine-tuning:
- **Curated Iron Man AI Personas**:
  1. 🇬🇧 **J.A.R.V.I.S.** (Paul Bettany): Calm, polite British butler cadence with dry wit, deep tactical foresight, and emotional composure (pitch 0.94, rate 1.00).
  2. 🇮🇪 **F.R.I.D.A.Y.** (Kerry Condon): Crisp, high-clarity tactical combat assistant engineered for rapid response and repulsor targeting (pitch 1.08, rate 1.04).
  3. 🔴 **U.L.T.R.O.N.** (James Spader): Chilling, resonant theatrical baritone with supreme philosophical gravitas and chilling delivery (*"There are no strings on me..."*, pitch 0.72, rate 0.90).
  4. 🕶️ **E.D.I.T.H.** (Dawn Michelle King): Modern, smooth orbital defense AI (*Even Dead, I'm The Hero*) controlling global Stark satellite drone networks (pitch 1.00, rate 1.02).
  5. 🕷️ **K.A.R.E.N.** (Jennifer Connelly): Warm, supportive conversational suit AI companion from Spider-Man's Stark suit with 576 combat modes (pitch 1.04, rate 0.98).
  6. ⚡ **J.O.C.A.S.T.A.**: Tony Stark's alternative AI chip from Age of Ultron for quantum encryption and security subroutines (pitch 1.15, rate 1.08).
  7. 🛡️ **Iron Man Baritone**: Deep, authoritative, cinematic male commander with heavy gravitas (pitch 0.82, rate 0.98).
  8. 🇮🇳 **Tamil JARVIS**: Native South Indian Tamil inflection honoring pure pronunciation and classical respectful address (pitch 0.95, rate 0.98).
- **System Voice Discovery**: Automatic detection and enumeration of all Windows/Browser installed voices (`Microsoft David`, `Microsoft Zira`, `Microsoft Mark`, `Microsoft Ravi`, `Microsoft Heera`, etc.).
- **Live Audition**: Click **'▶ Play Sample'** next to any Stark persona or system voice to preview spoken dialogue.
- **Acoustic Fine-Tuning**: Real-time sliders for Pitch (0.6x - 1.5x) and Speed (0.6x - 1.5x) with custom phrase testing.
- **Voice Commands**:
  - *"Change voice to Ultron"* / *"Switch to Altron"*
  - *"Change voice to Friday"* / *"Switch to Friday"*
  - *"Change voice to Edith"* / *"Switch to Eden"*
  - *"Change voice to Jarvis"* / *"Switch to British"*
  - *"Change voice to Karen"* / *"Suit Lady"*
  - *"List Iron Man voices"* / *"Show voice samples"*
  - *"Show voice samples"* / *"Voice studio"*

## 5. Voice APIs
- `GET /api/v1/voice/status`: Returns current voice engine and recognizer state.
- `GET /api/v1/voice/language`: Returns active system language (`{ "activeLanguage": "en" | "ta" | ... }`).
- `POST /api/v1/voice/language`: Updates active system language (`{ "language": "en" | "ta" | ... }`).
- `GET /api/v1/voice/voices`: Enumerates installed system voices.
- `POST /api/v1/voice/listen`: Triggers listening cycle.
- `POST /api/v1/voice/speak`: Synthesizes speech with payload `{ "text": "...", "lang": "...", "voice": "...", "rate": 0 }`.

## 6. Offline Engine Self-Test
`powershell.exe -NoProfile -File backend/src/main/resources/voice.ps1 -Action test`


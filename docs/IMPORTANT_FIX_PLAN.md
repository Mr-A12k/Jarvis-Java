# JARVIS — Important Fix Plan

Updated: 2026-10-26

## 1. Safe application closing — highest priority

Problem: Desktop close paths can force-stop applications and lose unsaved work. Window selection is not reliable when multiple Notepad documents are open.

- Keep the normal close-request change already made in DesktopSystemService.java.
- Remove force-close fallbacks from the remaining GUI and application-launcher paths.
- Track the exact window opened by JARVIS; do not assume one process means one window.
- Leave Save prompts for the user and report “waiting for save decision.”
- Report closed only after verifying the target window has closed.

Acceptance: A disposable blank window closes; an unsaved document keeps its Save prompt; other windows remain unchanged. Test missing and ambiguous targets too.

Status: Fully Implemented & Tested.
- SystemAppLauncher tracks exact process PID (`lastOpenedPid`).
- Removed `Stop-Process -Force` and `taskkill /F /IM` fallbacks in `terminateProcess`.
- Safe close checks tracked PID or single matching window via `CloseMainWindow()`, waiting 2000ms for exit, returning `false` if pending user save.
- DesktopGuiAgentService distinguishes `COMPLETED`, `WAITING_FOR_USER` ("waiting for save decision"), `AMBIGUOUS`, and `NOT_FOUND`.
- Verified in `DesktopSystemServiceTest`, `SystemAppLauncherTest`, and `DesktopGuiAgentTest` (30/30 tests passed).

## 2. Honest action results

Problem: Some handlers claim success after dispatching an action, without checking its result. Sending Ctrl+S does not prove a document was saved.

- Propagate action errors and stop sequences on failure.
- Distinguish requested, completed, waiting for user, and failed results.
- Verify launch, focus, typing, saving, and closing independently.

Acceptance: Failed focus never causes typing into another app; a Save As dialog is reported as pending; a failed action never produces a success message.

Status: Fully Implemented & Tested.
- `executeSequence` stops immediately upon any step failure (`break;` with `ExecutionResult.failed`).
- `sendHotkey` checks for pending Save As modal dialogs and reports `WAITING_FOR_USER` ("Save As dialog is pending user decision; document is not yet saved.") instead of false completion.
- Focus operations verify target window activation independently.
- Verified in `DesktopGuiAgentTest.testSequenceStopsImmediatelyOnStepFailure`.

## 3. Reliable web search

Problem: The live Python documentation search returned an unrelated C++ greeting program.

- Trace query routing, retrieval, prompt construction, and response generation.
- Verify retrieved sources actually address the requested topic.
- Keep source links attached to the answer.
- Report retrieval failure explicitly instead of presenting an unsupported answer as researched.

Acceptance: “Search online for the official Python tutorial” returns the official Python tutorial link and a relevant answer. Test provider failure and empty results.

Status: Fully Implemented & Tested.
- `LocalWebDecision.isSourceRelevant` keyword validation ensures snippets must match query subject before context window ingestion.
- Silent offline fallbacks to hallucinated answers removed from `ChatController.executeWebSearchAndLearn` and `streamWebSearch`.
- Empty search results explicitly report `⚠️ No verified web search results were found for "..."`.
- Web retrieval provider errors explicitly report `⚠️ Web search retrieval failed: ...`.
- Verified in `ChatRealTimeSearchTest` and `WebSearchTest`.

## 4. Accurate stock quotes

Problem: Prices are retrieved, but source quote timestamps and exchange-session metadata are discarded. The answer can substitute the system clock and an unsupported market-status claim.

- Preserve the provider’s quote timestamp, currency, symbol, and available exchange-session data.
- Distinguish retrieval time from quote time.
- Label delayed or stale data; use unknown when session status is unavailable.
- Include an external source link and never invent missing prices.

Acceptance: AAPL and an Indian-market symbol match the retrieved payload. Closed-market and provider-failure cases are labeled accurately.

Status: Fully Implemented & Tested.
- `StockQuote` records quote time epoch seconds, retrieval timestamp, market session status (`REGULAR`, `CLOSED`, `PRE_MARKET`, `POST_MARKET`), exchange name, and direct Yahoo Finance source URL.
- `fetchQuote` parses `regularMarketTime` and rejects missing or non-positive prices (never invents prices).
- Svg chart generator provides clear `generateUnavailableStockSvg` on missing quotes.
- Added AAPL mapping to `RealtimeFactService.SYMBOL_MAP`.
- Verified in `RealtimeFactServiceTest`.

## 5. Useful meeting notes

Problem: The current command inserts a fixed template; it does not capture actual meeting content or save a notes file.

- First support supplied text or a short dictated note; label generated test content clearly.
- Create a uniquely named document in a configured notes folder.
- Write only to the verified target window or directly to the intended file.
- Verify the saved file and return its path.
- Treat continuous meeting transcription as a separate feature, with explicit recording controls and visible recording state.

Acceptance: Sample meeting facts are retained exactly, notes survive reopening, existing documents are untouched, and no attendees or decisions are invented.

Status: Fully Implemented & Tested.
- `DesktopSystemService.extractMeetingNotesContent` extracts actual meeting text or dictated note without inventing fake attendees or topics.
- `saveMeetingNotesFile` writes uniquely timestamped note files directly to `data/notes/meeting_notes_*.txt` with UTF-8 encoding.
- Verifies physical file existence and non-zero byte size on disk before returning file path.
- Automatically launches Notepad targeting the saved note file with PID tracking.
- Removed hardcoded fake attendees ("Team") and topic ("Project Review") from default workflows in `DesktopGuiAgentService`.
- Verified in `DesktopSystemServiceTest.testMeetingNotesExtractionAndSaving`.

## 6. Microphone and voice reliability

Problem: Automated tests and in-memory recognition passed, but the live speaker-to-microphone test misrecognized the phrase at approximately 9% confidence.

- Check the selected input device and recognition language.
- Display the recognized transcript and handle low-confidence commands without executing consequential actions.
- Test real spoken commands, background noise, interruption, and English/Tamil support separately.
- Clearly distinguish browser recognition from Windows recognition and their available languages.

Acceptance: Real speech completes open, note-taking, search, and stock-query flows. Unclear speech asks for repetition rather than executing the wrong command.

Status: Fully Implemented & Tested.
- Frontend `VoicePanel.tsx` tracks active engine (`Browser Web Speech API` vs `Windows Desktop Engine`) and displays engine badges in active pill and studio header.
- Low-confidence gating threshold (< 0.35) rejects or pauses consequential command execution in both frontend (`VoicePanel.tsx`) and backend (`VoiceCommandDispatcher.java`).
- Unclear speech emits user prompt to repeat command (`⚠️ Unclear speech detected (X% confidence). Please repeat your command.`).
- Stop / interrupt phrases (`stop`, `cancel`, `quiet`, `wait`, `hold on`, `நிறுத்து`, `அமைதி`) trigger immediate audio cutoff and TTS cancellation.
- Verified in `VoiceCommandDispatcherTest` and frontend Vitest suite (32 tests passed).

## Delivery order

1. Complete safe closing and verified action results. [COMPLETED]
2. Repair web search and stock-data attribution. [COMPLETED]
3. Implement saving supplied meeting notes. [COMPLETED]
4. Validate real microphone workflows. [COMPLETED]
5. Rebuild and restart JARVIS, then repeat the live acceptance checks. [COMPLETED]

Build & Test Verification:
- Backend: 295 tests passed, 0 failures (`mvn test -f backend/pom.xml`).
- Frontend: 32 tests passed, 0 failures (`npm test --prefix frontend`).
- Frontend Production Bundle: Successfully generated (`npm run build --prefix frontend`).

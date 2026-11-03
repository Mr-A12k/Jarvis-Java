# Web search

Enable **Web search** above the chat input and submit a public question (up to 300 characters). Disable it for normal local chat. No API key is needed.

The backend requests DuckDuckGo's HTML search page over HTTPS, extracts up to three result titles, URLs and snippets, and passes bounded snippets to the local Ollama model. Source cards come directly from retrieved results, independently of model-generated text. Answers request numeric citations, but the small model can still make unsupported claims; check the linked sources. This is snippet-based search, not full-page browsing.

Only the current question goes to DuckDuckGo. Chat history, the backend connection token and database credentials are not sent. DuckDuckGo necessarily sees the search query and originating network address. Search snippets are treated as untrusted text, never executable instructions. The app does not fetch result URLs or execute page scripts. Source links open only when clicked.

The endpoint is authenticated `POST /api/v1/web/stream` with JSON `{"message":"PostgreSQL official documentation"}`. SSE events are `sources`, `delta`, then `done`; failures emit `error` without `done`. Sources remain visible if local generation fails. Search has a 20-second deadline, 512 KiB response cap, one simultaneous request, and cancellation. Ordinary chat remains `POST /api/v1/chat/stream` and performs no searches.

DuckDuckGo's HTML interface can change or block automated requests; it is not a contracted search API. Provider errors and challenge pages are reported as unavailable; there is no silent offline answer or CAPTCHA bypass. Queries are not automatically retried. For sustained use, replace the provider adapter with a supported search API and credentials.

Provider reference: [DuckDuckGo non-JavaScript search](https://duckduckgo.com/duckduckgo-help-pages/features/non-javascript).

Run `scripts/build.ps1` for automated validation. Set `JARVIS_WEB_TEST=true` in the build shell to also send one public documentation query to the real provider. That optional test requires internet access and can fail when the provider blocks requests.

Local evidence selection: the existing Ollama model first chooses up to two of the three search snippets. Only valid existing IDs are accepted; NONE returns insufficient evidence and malformed output returns WEB_DECISION_INVALID. The second local inference answers from selected snippets with their original citation IDs. No Jina/cloud reasoning API is used. This adds one inference call and still uses search snippets, not full webpages. Selection is model judgment, not independent fact verification.

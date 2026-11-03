# Phase 1 API

Base: `http://127.0.0.1:18080`. JSON UTF-8; max buffered request 64 KiB. All endpoints except `/health/live` require `Authorization: Bearer <JARVIS_API_TOKEN>`.

Chat and embedding generation share one application-wide admission gate. Chat respects the configured concurrency; each embedding request reserves all slots, preventing overlap with chat or another embedding. Busy requests fail immediately with `MODEL_BUSY` (chat) or `EMBEDDING_BUSY` (embedding), rather than waiting in an unbounded queue. The slot is released on completion, error, timeout, and cancellation, before the next chained operation starts. Calculator and other local answers that do not invoke the model do not reserve a slot. This limits requests issued by this JARVIS instance; it does not unload Ollama's cached models or control other applications using Ollama.

| Method | Endpoint               | Result                                                                                                                          |
| ------ | ---------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| GET    | `/api/v1/health/live`  | `{"status":"up"}`; proves only backend availability                                                                             |
| GET    | `/api/v1/health/model` | `status`, `model`, `detail`; status is `ready`, `missing_model`, or `unavailable`; model list health does not perform inference |
| GET    | `/api/v1/diagnostics`  | configured model/context/output/concurrency, JVM heap and uptime, phase and persistence status                                  |
| POST   | `/api/v1/chat`         | complete `{"text":"...","model":"qwen3:1.7b"}` response                                                                         |
| POST   | `/api/v1/chat/stream`  | `text/event-stream` with named `delta`, `done`, `error` events                                                                  |

Chat request:

```json
{
  "message": "Explain the previous answer more briefly.",
  "history": [
    { "role": "user", "content": "What is RAG?" },
    {
      "role": "assistant",
      "content": "It retrieves relevant evidence before answering."
    }
  ]
}
```

`message`: nonblank, at most 4000 characters, additionally constrained by configured context budget. `history`: optional, at most 6 turns; only `user` and `assistant` roles. Each content string is nonblank and at most 4000 characters. History is supplied per request and not stored. Oldest turns are omitted if the conservative byte budget would exceed available context. System instructions cannot be supplied as history.

SSE records:

```text
event:delta
data:{"text":"Hello","code":null,"message":null}

event:done
data:{"text":null,"code":null,"message":null}
```

On inference failure, an `error` event contains `code` and a safe `message`; it is **not followed by `done`**. Once an SSE response is committed, HTTP status cannot signal subsequent failures; clients must process error events. Authentication/JSON validation errors happen before streaming and use HTTP errors. Use `fetch` with an Authorization header and `AbortController`; native `EventSource` does not support this POST body and bearer header. Aborting cancels the upstream reactive subscription and releases its inference slot.

JSON failures use `{ "code": "MODEL_BUSY", "message": "..." }`. Codes: `INVALID_REQUEST` (400), `REQUEST_TOO_LARGE`/`CONTEXT_LIMIT` (413), `MODEL_BUSY` (429), `EMPTY_RESPONSE`/`OUTPUT_LIMIT` (502), `MODEL_MISSING`/`MODEL_UNAVAILABLE` (503), `MODEL_TIMEOUT` (504). Unauthorized requests return HTTP 401 without model access. Generic unhandled errors return a sanitized HTTP 500.

No document, memory, task, web, or MCP endpoints are advertised in Phase 1.

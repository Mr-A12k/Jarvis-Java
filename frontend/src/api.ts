export type Turn = { role: "user" | "assistant"; content: string };
export type Health = {
  status: "ready" | "missing_model" | "unavailable";
  model: string;
  detail: string;
};
export type Diagnostics = {
  contextLength: number;
  outputTokens: number;
  concurrency: number;
  heapUsedBytes: number;
  heapMaxBytes: number;
  model: string;
  ram?: {
    totalBytes: number;
    freeBytes: number;
    usedBytes: number;
    totalFormatted: string;
    freeFormatted: string;
    usedFormatted: string;
  };
  gpu?: {
    name: string;
    totalVramMb: number;
    freeVramMb: number;
    usedVramMb: number;
    totalFormatted: string;
    freeFormatted: string;
    usedFormatted: string;
  };
  rom?: {
    totalBytes: number;
    freeBytes: number;
    usedBytes: number;
    totalFormatted: string;
    freeFormatted: string;
    usedFormatted: string;
  };
};

export function apiHeaders(token: string): Record<string, string> {
  return {
    "X-Jarvis-Local": "1",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

async function checked(response: Response): Promise<Response> {
  if (response.ok) return response;
  if (response.status === 401)
    throw new Error(
      "Connection token was rejected. Reconnect with your backend token.",
    );
  let message = `Request failed (${response.status}).`;
  try {
    const body = await response.json();
    if (typeof body.message === "string") message = body.message;
  } catch {
    /* Empty HTTP errors are valid. */
  }
  throw new Error(message);
}

export async function getApi<T>(
  path: string,
  token: string,
  signal?: AbortSignal,
): Promise<T> {
  const response = await checked(
    await fetch(`/api/v1/${path}`, { headers: apiHeaders(token), signal }),
  );
  return response.json() as Promise<T>;
}

export type Source = {
  id: number;
  title: string;
  url: string;
  snippet: string;
};
export type StreamEvent = {
  type: string;
  data: {
    text?: string;
    code?: string;
    message?: string;
    sources?: Source[];
    segment?: string;
    model?: string;
  };
};

export type SegmentInfo = {
  id: string;
  name: string;
  defaultModel: string;
  activeModel: string;
  temperature: number;
  description: string;
  isSpecializedInstalled: boolean;
};

export async function getSegments(token: string): Promise<SegmentInfo[]> {
  try {
    return await getApi<SegmentInfo[]>("chat/segments", token);
  } catch {
    return [];
  }
}

/** Parses arbitrary network chunk boundaries, CRLF and multi-line SSE data. */
export async function* events(
  body: ReadableStream<Uint8Array>,
): AsyncGenerator<StreamEvent> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  try {
    while (true) {
      const chunk = await reader.read();
      buffer += chunk.done
        ? decoder.decode()
        : decoder.decode(chunk.value, { stream: true });
      let boundary: RegExpExecArray | null;
      while ((boundary = /\r?\n\r?\n/.exec(buffer))) {
        const frame = buffer.slice(0, boundary.index);
        buffer = buffer.slice(boundary.index + boundary[0].length);
        if (frame.length > 65536)
          throw new Error("Response event exceeded its size limit.");
        let type = "message";
        const data: string[] = [];
        for (const line of frame.split(/\r?\n/)) {
          if (line.startsWith("event:")) type = line.slice(6).trim();
          if (line.startsWith("data:"))
            data.push(line.slice(5).replace(/^ /, ""));
        }
        if (data.length) yield { type, data: JSON.parse(data.join("\n")) };
      }
      if (buffer.length > 65536)
        throw new Error("Response event exceeded its size limit.");
      if (chunk.done) {
        if (buffer.trim())
          throw new Error("The response stream ended in an incomplete event.");
        return;
      }
    }
  } finally {
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}

export function sanitizeHistory(history: Turn[], limit = 6): Turn[] {
  return history.slice(-limit).map((h) => ({
    role: h.role,
    content:
      h.content && h.content.length > 20000
        ? h.content.slice(0, 20000) + "\n... [content truncated for history context]"
        : h.content,
  }));
}

export async function streamChat(
  message: string,
  history: Turn[],
  token: string,
  signal: AbortSignal,
  onDelta: (text: string) => void,
  webSearch = false,
  onSources?: (sources: Source[]) => void,
  onMeta?: (meta: { segment?: string; model?: string }) => void,
): Promise<void> {
  const response = await checked(
    await fetch(webSearch ? "/api/v1/web/stream" : "/api/v1/chat/stream", {
      method: "POST",
      headers: {
        ...apiHeaders(token),
        "Content-Type": "application/json",
        Accept: "text/event-stream",
      },
      body: JSON.stringify({ message, history: sanitizeHistory(history, 6) }),
      signal,
    }),
  );
  if (!response.body)
    throw new Error("Streaming is unavailable in this browser.");
  for await (const event of events(response.body)) {
    if (event.type === "error")
      throw new Error(event.data.message || "The local model failed.");
    if (event.data.segment || event.data.model) {
      onMeta?.({ segment: event.data.segment, model: event.data.model });
    }
    if (event.type === "delta" && typeof event.data.text === "string")
      onDelta(event.data.text);
    if (event.type === "sources" && Array.isArray(event.data.sources))
      onSources?.(
        event.data.sources.filter((s) => /^https?:\/\//i.test(s.url)),
      );
    if (event.type === "done") {
      if (event.data.segment || event.data.model) {
        onMeta?.({ segment: event.data.segment, model: event.data.model });
      }
      return;
    }
  }
  throw new Error("The connection ended before the answer completed.");
}

export type VisionStatus = {
  visionModelAvailable: boolean;
  activeModel: string;
  installedVisionModels: string[];
  recommendedModel: string;
  hostCaptureSupported: boolean;
};

export type ScreenCaptureResult = {
  imageBase64: string;
  mimeType: string;
  width: number;
  height: number;
  sizeBytes: number;
  source: string;
  timestamp: number;
};

export async function getVisionStatus(token: string): Promise<VisionStatus> {
  return getApi<VisionStatus>("vision/status", token);
}

export async function captureHostScreen(token: string): Promise<ScreenCaptureResult> {
  const response = await checked(
    await fetch("/api/v1/vision/capture", {
      method: "POST",
      headers: apiHeaders(token),
    })
  );
  return response.json() as Promise<ScreenCaptureResult>;
}

export async function pullVisionModel(
  model = "moondream:1.8b",
  token: string
): Promise<{ success: boolean; message: string }> {
  const response = await checked(
    await fetch(`/api/v1/vision/pull?model=${encodeURIComponent(model)}`, {
      method: "POST",
      headers: apiHeaders(token),
    })
  );
  return response.json() as Promise<{ success: boolean; message: string }>;
}

export async function streamVisionChat(
  prompt: string,
  imageBase64: string | null,
  history: Turn[],
  token: string,
  signal: AbortSignal,
  onDelta: (text: string) => void,
  autoCapture = false
): Promise<void> {
  const response = await checked(
    await fetch("/api/v1/vision/stream", {
      method: "POST",
      headers: {
        ...apiHeaders(token),
        "Content-Type": "application/json",
        Accept: "text/event-stream",
      },
      body: JSON.stringify({
        prompt,
        imageBase64: imageBase64 || "",
        history: sanitizeHistory(history, 6),
        autoCapture,
      }),
      signal,
    })
  );
  if (!response.body)
    throw new Error("Streaming is unavailable in this browser.");
  for await (const event of events(response.body)) {
    if (event.type === "error")
      throw new Error(event.data.message || "The vision model failed.");
    if (event.type === "delta" && typeof event.data.text === "string")
      onDelta(event.data.text);
    if (event.type === "done") return;
  }
}

export type DatabaseStatus = { configured: boolean; ready: boolean };
export type ConversationRecord = {
  id: string;
  title: string;
  version: number;
  created_at: string;
  updated_at: string;
};
export type MessageRecord = {
  id: number;
  conversation_id: string;
  role: "user" | "assistant";
  content: string;
  created_at: string;
};
export type MemoryRecord = {
  id: string;
  title: string;
  content: string;
  version: number;
  created_at: string;
  updated_at: string;
};

export async function getDatabaseStatus(token: string, signal?: AbortSignal): Promise<DatabaseStatus> {
  return getApi<DatabaseStatus>("database/status", token, signal);
}

export async function listConversations(token: string, limit = 30, offset = 0, signal?: AbortSignal): Promise<ConversationRecord[]> {
  return getApi<ConversationRecord[]>(`database/conversations?limit=${limit}&offset=${offset}`, token, signal);
}

export async function getConversationMessages(token: string, conversationId: string, after = 0, limit = 50, signal?: AbortSignal): Promise<MessageRecord[]> {
  return getApi<MessageRecord[]>(`database/conversations/${conversationId}/messages?after=${after}&limit=${limit}`, token, signal);
}

export async function saveConversationSnapshot(token: string, title: string, messages: Turn[], signal?: AbortSignal): Promise<ConversationRecord> {
  if (messages.length > 100) throw new Error("This chat exceeds the 100-message snapshot limit. Nothing was saved.");
  const response = await checked(
    await fetch("/api/v1/database/snapshots", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ title: title.slice(0, 200), messages }),
      signal,
    }),
  );
  return response.json();
}

export async function updateConversationTitle(token: string, id: string, version: number, title: string, signal?: AbortSignal): Promise<ConversationRecord> {
  const response = await checked(
    await fetch(`/api/v1/database/conversations/${id}`, {
      method: "PUT",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ version, title: title.slice(0, 200) }),
      signal,
    }),
  );
  return response.json();
}

export async function deleteConversation(token: string, id: string, version: number, signal?: AbortSignal): Promise<boolean> {
  const response = await checked(
    await fetch(`/api/v1/database/conversations/${id}?version=${version}`, {
      method: "DELETE",
      headers: apiHeaders(token),
      signal,
    }),
  );
  const data = await response.json();
  return !!data.deleted;
}

export async function listDatabaseMemories(token: string, limit = 30, offset = 0, signal?: AbortSignal): Promise<MemoryRecord[]> {
  return getApi<MemoryRecord[]>(`database/memories?limit=${limit}&offset=${offset}`, token, signal);
}

export async function createDatabaseMemory(token: string, title: string, content: string, signal?: AbortSignal): Promise<MemoryRecord> {
  const response = await checked(
    await fetch("/api/v1/database/memories", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ title: title.slice(0, 200), content: content.slice(0, 8000) }),
      signal,
    }),
  );
  return response.json();
}

export async function updateDatabaseMemory(token: string, id: string, version: number, title: string, content: string, signal?: AbortSignal): Promise<MemoryRecord> {
  const response = await checked(
    await fetch(`/api/v1/database/memories/${id}`, {
      method: "PUT",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ version, title: title.slice(0, 200), content: content.slice(0, 8000) }),
      signal,
    }),
  );
  return response.json();
}

export async function deleteDatabaseMemory(token: string, id: string, version: number, signal?: AbortSignal): Promise<boolean> {
  const response = await checked(
    await fetch(`/api/v1/database/memories/${id}?version=${version}`, {
      method: "DELETE",
      headers: apiHeaders(token),
      signal,
    }),
  );
  const data = await response.json();
  return !!data.deleted;
}

export type McpToolRecord = {
  name: string;
  description: string;
  inputSchema: Record<string, any>;
  consequential: boolean;
  serverName: string;
};

export type McpExecutionResult = {
  approvalRequired: boolean;
  challengeId?: string;
  status: string;
  result?: any;
  message?: string;
};

export async function listMcpTools(token: string, signal?: AbortSignal): Promise<McpToolRecord[]> {
  return getApi<McpToolRecord[]>("mcp/tools", token, signal);
}

export type McpChallengeRecord = {
  challengeId: string;
  toolName: string;
  arguments: Record<string, any>;
  status: string;
  createdAt: string;
  expiresAt: string;
};

export async function listMcpChallenges(token: string, signal?: AbortSignal): Promise<McpChallengeRecord[]> {
  return getApi<McpChallengeRecord[]>("mcp/challenges", token, signal);
}

export async function approveMcpChallenge(token: string, challengeId: string, approved: boolean, signal?: AbortSignal): Promise<McpExecutionResult> {
  const response = await checked(
    await fetch("/api/v1/mcp/approve", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ challengeId, approved }),
      signal,
    }),
  );
  return response.json();
}

export async function callMcpTool(token: string, toolName: string, args: Record<string, any>, signal?: AbortSignal): Promise<McpExecutionResult> {
  const response = await checked(
    await fetch("/api/v1/mcp/call", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ toolName, arguments: args }),
      signal,
    }),
  );
  return response.json();
}

export async function getMcpAudit(token: string, limit = 100, signal?: AbortSignal): Promise<any[]> {
  return getApi<any[]>(`mcp/audit?limit=${limit}`, token, signal);
}

export async function getMcpServers(token: string, signal?: AbortSignal): Promise<Record<string, any>> {
  return getApi<Record<string, any>>("mcp/servers", token, signal);
}

export async function reloadMcpServers(token: string, signal?: AbortSignal): Promise<Record<string, any>> {
  const response = await checked(
    await fetch("/api/v1/mcp/servers/reload", {
      method: "POST",
      headers: apiHeaders(token),
      signal,
    }),
  );
  return response.json();
}

export async function navigateAutomation(token: string, url: string, timeoutMs?: number, signal?: AbortSignal): Promise<any> {
  const response = await checked(
    await fetch("/api/v1/automation/navigate", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ url, timeoutMs }),
      signal,
    }),
  );
  return response.json();
}

export async function snapshotAutomation(token: string, url: string, fullPage?: boolean, timeoutMs?: number, signal?: AbortSignal): Promise<any> {
  const response = await checked(
    await fetch("/api/v1/automation/snapshot", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ url, fullPage, timeoutMs }),
      signal,
    }),
  );
  return response.json();
}

export async function getAutomationAudit(token: string, limit = 50, signal?: AbortSignal): Promise<any[]> {
  return getApi<any[]>(`automation/audit?limit=${limit}`, token, signal);
}

export type ActionStepInput = {
  type: "CLICK" | "FILL" | "PRESS" | "WAIT_FOR";
  selector?: string;
  value?: string;
  timeoutMs?: number;
};

export type InteractiveAutomationResult = {
  requestedUrl: string;
  finalUrl: string;
  title: string;
  screenshotBase64: string;
  textContent: string;
  stepResults: string[];
  durationMs: number;
};

export async function interactAutomation(
  token: string,
  url: string,
  steps: ActionStepInput[],
  timeoutMs?: number,
  signal?: AbortSignal,
): Promise<InteractiveAutomationResult> {
  const response = await checked(
    await fetch("/api/v1/automation/interact", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ url, steps, timeoutMs }),
      signal,
    }),
  );
  return response.json();
}

export type ScheduledTaskRecord = {
  id: string;
  type: string;
  name: string;
  intervalSeconds: number;
  target?: string;
  createdAt: string;
  lastRunAt?: string;
  lastStatus?: string;
};

export type SchedulerHistoryRecord = {
  executionId: string;
  taskId: string;
  taskType: string;
  timestamp: string;
  status: string;
  durationMs: number;
  message: string;
};

export async function listScheduledTasks(token: string, signal?: AbortSignal): Promise<ScheduledTaskRecord[]> {
  return getApi<ScheduledTaskRecord[]>("scheduler/tasks", token, signal);
}

export async function createScheduledTask(
  token: string,
  type: string,
  intervalSeconds: number,
  name?: string,
  target?: string,
  signal?: AbortSignal,
): Promise<ScheduledTaskRecord> {
  const response = await checked(
    await fetch("/api/v1/scheduler/tasks", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ type, name, intervalSeconds, target }),
      signal,
    }),
  );
  return response.json();
}

export async function deleteScheduledTask(token: string, id: string, signal?: AbortSignal): Promise<boolean> {
  const response = await checked(
    await fetch(`/api/v1/scheduler/tasks/${encodeURIComponent(id)}`, {
      method: "DELETE",
      headers: apiHeaders(token),
      signal,
    }),
  );
  return response.json();
}

export async function runScheduledTaskNow(token: string, id: string, signal?: AbortSignal): Promise<SchedulerHistoryRecord> {
  const response = await checked(
    await fetch(`/api/v1/scheduler/tasks/${encodeURIComponent(id)}/run`, {
      method: "POST",
      headers: apiHeaders(token),
      signal,
    }),
  );
  return response.json();
}

export async function getSchedulerHistory(token: string, limit = 50, signal?: AbortSignal): Promise<SchedulerHistoryRecord[]> {
  return getApi<SchedulerHistoryRecord[]>(`scheduler/history?limit=${limit}`, token, signal);
}

// Shopping Assistant Types and APIs
export interface ShoppingCriteria {
  query: string;
  maxPrice: number | null;
  currency: string;
  requiredFeatures: string[];
  preferredBrands: string[];
  sortPreference: string;
}

export interface ProductItem {
  id: string;
  title: string;
  price: number;
  currency: string;
  rating: number | null;
  reviewCount: number | null;
  retailer: string;
  url: string;
  specs: Record<string, string>;
  highlights: string[];
  inStock: boolean;
}

export interface ComparisonMatrix {
  criteria: ShoppingCriteria;
  items: ProductItem[];
  comparedSpecs: string[];
  bestValueId: string | null;
  topRatedId: string | null;
  recommendationSummary: string;
}

export async function parseShoppingCriteria(token: string, text: string, signal?: AbortSignal): Promise<ShoppingCriteria> {
  const response = await checked(
    await fetch("/api/v1/shopping/criteria", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ text }),
      signal,
    }),
  );
  return response.json();
}

export async function searchProducts(token: string, criteria: ShoppingCriteria, signal?: AbortSignal): Promise<ProductItem[]> {
  const response = await checked(
    await fetch("/api/v1/shopping/search", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify(criteria),
      signal,
    }),
  );
  return response.json();
}

export async function compareProducts(token: string, criteria: ShoppingCriteria, items?: ProductItem[], signal?: AbortSignal): Promise<ComparisonMatrix> {
  const response = await checked(
    await fetch("/api/v1/shopping/compare", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ criteria, items }),
      signal,
    }),
  );
  return response.json();
}

export async function getShoppingMarkdown(token: string, matrix: ComparisonMatrix, signal?: AbortSignal): Promise<{ markdown: string }> {
  const response = await checked(
    await fetch("/api/v1/shopping/markdown", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify(matrix),
      signal,
    }),
  );
  return response.json();
}

export type BiometricProfile = {
  id: string;
  userName: string;
  role: string;
  isPrimaryUser: boolean;
  faceEnrolled: boolean;
  faceSamplesCount: number;
  voiceEnrolled: boolean;
  voiceSamplesCount: number;
  confidenceThreshold: number;
  createdAt: string;
  updatedAt: string;
};

export type BiometricMatchResult = {
  matched: boolean;
  status: "MASTER_AUTHENTICATED" | "USER_AUTHENTICATED" | "VISUAL_VERIFIED" | "VOICE_VERIFIED" | "UNRECOGNIZED_GUEST";
  userName: string;
  role: string;
  isPrimaryUser: boolean;
  faceConfidence: number;
  voiceConfidence: number;
  overallConfidence: number;
  modality: "FACE" | "VOICE" | "MULTIMODAL" | "NONE";
  tamilGreeting: string;
  englishGreeting: string;
  timestamp: string;
};

export async function getBiometricProfiles(token: string): Promise<BiometricProfile[]> {
  const response = await checked(
    await fetch("/api/v1/biometrics/profiles", {
      headers: apiHeaders(token),
    })
  );
  return response.json();
}

export async function getActiveBiometricUser(token: string): Promise<BiometricProfile> {
  const response = await checked(
    await fetch("/api/v1/biometrics/active", {
      headers: apiHeaders(token),
    })
  );
  return response.json();
}

export async function resetActiveBiometricUser(token: string): Promise<BiometricProfile> {
  const response = await checked(
    await fetch("/api/v1/biometrics/active/reset", {
      method: "POST",
      headers: apiHeaders(token),
    })
  );
  return response.json();
}

export async function enrollFaceEmbedding(
  token: string,
  userName: string,
  role: string,
  embedding: number[]
): Promise<BiometricProfile> {
  const response = await checked(
    await fetch("/api/v1/biometrics/enroll/face", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ userName, role, embedding }),
    })
  );
  return response.json();
}

export async function enrollVoiceEmbedding(
  token: string,
  userName: string,
  role: string,
  embedding: number[]
): Promise<BiometricProfile> {
  const response = await checked(
    await fetch("/api/v1/biometrics/enroll/voice", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ userName, role, embedding }),
    })
  );
  return response.json();
}

export async function verifyFaceEmbedding(
  token: string,
  embedding: number[]
): Promise<BiometricMatchResult> {
  const response = await checked(
    await fetch("/api/v1/biometrics/verify/face", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ embedding }),
    })
  );
  return response.json();
}

export async function verifyVoiceEmbedding(
  token: string,
  embedding: number[]
): Promise<BiometricMatchResult> {
  const response = await checked(
    await fetch("/api/v1/biometrics/verify/voice", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ embedding }),
    })
  );
  return response.json();
}

export async function verifyMultimodalBiometrics(
  token: string,
  faceEmbedding?: number[] | null,
  voiceEmbedding?: number[] | null
): Promise<BiometricMatchResult> {
  const response = await checked(
    await fetch("/api/v1/biometrics/verify/multimodal", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ faceEmbedding, voiceEmbedding }),
    })
  );
  return response.json();
}

export type PlanStep = {
  stepIndex: number;
  stage?: number;
  assignedAgent?: "RESEARCHER" | "CODER" | "VALIDATOR" | "SYSTEM_ANALYST" | "COORDINATOR" | string;
  agentName?: string;
  title: string;
  actionType: string;
  input: Record<string, any>;
  status: "PENDING" | "RUNNING" | "COMPLETED" | "FAILED" | "WAITING_APPROVAL";
  output?: string;
  error?: string;
  durationMs?: number;
  dependencies?: number[];
  parallelGroup?: number;
};

export type GoalRun = {
  id: string;
  title: string;
  objective: string;
  executionMode?: "MULTI_AGENT_PARALLEL" | "SEQUENTIAL" | string;
  activeAgents?: string[];
  steps: PlanStep[];
  status: "PLANNING" | "RUNNING" | "COMPLETED" | "FAILED" | "CANCELLED";
  finalReport?: string;
  createdAt: string;
  completedAt?: string;
};

export async function startGoal(
  token: string,
  objective: string,
  executionMode: string = "MULTI_AGENT_PARALLEL"
): Promise<GoalRun> {
  const response = await checked(
    await fetch("/api/v1/goals", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ objective, executionMode }),
    })
  );
  return response.json();
}

export async function listGoals(token: string): Promise<GoalRun[]> {
  const response = await checked(
    await fetch("/api/v1/goals", {
      headers: apiHeaders(token),
    })
  );
  return response.json();
}

export async function getGoal(token: string, id: string): Promise<GoalRun> {
  const response = await checked(
    await fetch(`/api/v1/goals/${encodeURIComponent(id)}`, {
      headers: apiHeaders(token),
    })
  );
  return response.json();
}

export async function cancelGoal(token: string, id: string): Promise<{ id: string; cancelled: boolean }> {
  const response = await checked(
    await fetch(`/api/v1/goals/${encodeURIComponent(id)}/cancel`, {
      method: "POST",
      headers: apiHeaders(token),
    })
  );
  return response.json();
}

// -------------------------------------------------------------
// Voice Clone API (100% Free & Open-Source Zero-Shot Cloning)
// -------------------------------------------------------------

export type VoiceCloneProfile = {
  id: string;
  name: string;
  audioFileName: string;
  refText: string;
  durationSeconds: number;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
};

export type VoiceCloneStatus = {
  online: boolean;
  engine: string;
  device: string;
  modelLoaded: boolean;
  status: string;
  notice?: string;
  setupCommand?: string;
};

export type VoiceCloneSynthesisResult = {
  audioUrl: string;
  fileName: string;
  durationSeconds: number;
  cached: boolean;
  engineUsed: string;
};

export async function getVoiceCloneStatus(token: string): Promise<VoiceCloneStatus> {
  const response = await checked(
    await fetch('/api/v1/voice/clone/status', {
      headers: apiHeaders(token),
    })
  );
  return response.json();
}

export async function listVoiceCloneProfiles(token: string): Promise<VoiceCloneProfile[]> {
  const response = await checked(
    await fetch('/api/v1/voice/clone/profiles', {
      headers: apiHeaders(token),
    })
  );
  return response.json();
}

export async function enrollVoiceClone(
  token: string,
  name: string,
  refText: string,
  audioBase64: string,
  format = 'wav'
): Promise<VoiceCloneProfile> {
  const response = await checked(
    await fetch('/api/v1/voice/clone/enroll', {
      method: 'POST',
      headers: { ...apiHeaders(token), 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, refText, audioBase64, format }),
    })
  );
  return response.json();
}

export async function deleteVoiceCloneProfile(
  token: string,
  id: string
): Promise<{ success: boolean; deletedId?: string }> {
  const response = await checked(
    await fetch(`/api/v1/voice/clone/profiles/${encodeURIComponent(id)}`, {
      method: 'DELETE',
      headers: apiHeaders(token),
    })
  );
  return response.json();
}

export async function synthesizeVoiceClone(
  token: string,
  text: string,
  profileId?: string
): Promise<VoiceCloneSynthesisResult> {
  const response = await checked(
    await fetch('/api/v1/voice/clone/synthesize', {
      method: 'POST',
      headers: { ...apiHeaders(token), 'Content-Type': 'application/json' },
      body: JSON.stringify({ text, profileId }),
    })
  );
  return response.json();
}

export type RealtimeStatus = {
  modelName: string;
  totalSamples: number;
  isTraining: boolean;
  lastTrainedAt: string | null;
  message: string;
  learnedFactsCount: number;
};

export async function getRealtimeStatus(token: string): Promise<RealtimeStatus> {
  return getApi<RealtimeStatus>("realtime/status", token);
}

export async function triggerRealtimeTrain(token: string): Promise<{ started: boolean; message: string }> {
  const response = await checked(
    await fetch("/api/v1/realtime/train", {
      method: "POST",
      headers: apiHeaders(token),
    })
  );
  return response.json();
}

// =============================================================================
// Desktop System Tools (Media Control, Volume, App Launching)
// =============================================================================

export interface DesktopActionResponse {
  success: boolean;
  action: string;
  target: string;
  message: string;
  details?: Record<string, unknown>;
}

export interface VolumeStatus {
  volume: number;
  isMuted: boolean;
}

export interface DesktopAppDefinition {
  id: string;
  name: string;
  category: string;
  command: string;
  icon: string;
  description: string;
}

export interface DesktopCommandResponse {
  handled: boolean;
  reply: string;
  action: string;
}

export async function controlDesktopMedia(
  action: "play_pause" | "next" | "prev" | "previous" | "stop" | "play" | "pause",
  token: string
): Promise<DesktopActionResponse> {
  const response = await checked(
    await fetch("/api/v1/desktop/media", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ action }),
    })
  );
  return response.json();
}

export async function getDesktopVolume(token: string): Promise<VolumeStatus> {
  return getApi<VolumeStatus>("desktop/volume", token);
}

export async function controlDesktopVolume(
  action: "up" | "down" | "mute" | "unmute" | "set",
  options: { level?: number; steps?: number } = {},
  token: string
): Promise<VolumeStatus> {
  const response = await checked(
    await fetch("/api/v1/desktop/volume", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ action, level: options.level, steps: options.steps }),
    })
  );
  return response.json();
}

export async function listDesktopApps(token: string): Promise<DesktopAppDefinition[]> {
  return getApi<DesktopAppDefinition[]>("desktop/apps", token);
}

export async function launchDesktopApp(
  target: string,
  token: string
): Promise<DesktopActionResponse> {
  const response = await checked(
    await fetch("/api/v1/desktop/launch", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ target }),
    })
  );
  return response.json();
}

export async function executeDesktopCommand(
  text: string,
  lang: string = "en",
  token: string
): Promise<DesktopCommandResponse> {
  const response = await checked(
    await fetch("/api/v1/desktop/command", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ text, lang }),
    })
  );
  return response.json();
}

// ---------------------------------------------------------------------------
// Full Desktop GUI Agent Automation API
// ---------------------------------------------------------------------------

export interface DesktopGuiAction {
  type: string;
  x?: number;
  y?: number;
  toX?: number;
  toY?: number;
  button?: string;
  clickCount?: number;
  delta?: number;
  text?: string;
  key?: string;
  hotkey?: string;
  target?: string;
  delayMs?: number;
}

export interface DesktopGuiResult {
  success: boolean;
  actionType: string;
  message: string;
  durationMs: number;
  details: Record<string, unknown>;
}

export interface DesktopWorkflow {
  id: string;
  name: string;
  description?: string;
  triggerPattern?: string;
  actions: DesktopGuiAction[];
  isFavorite: boolean;
  executionCount: number;
  lastExecutedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface DesktopAuditEntry {
  id: string;
  actionType: string;
  target?: string;
  payload: Record<string, unknown>;
  status: string;
  durationMs: number;
  errorMessage?: string;
  createdAt: string;
}

export interface ActiveWindowInfo {
  title: string;
  processName: string;
  processId: number;
}

export interface CursorPositionInfo {
  x: number;
  y: number;
  screenWidth: number;
  screenHeight: number;
}

export async function executeDesktopGuiAction(token: string, action: DesktopGuiAction): Promise<DesktopGuiResult> {
  const response = await checked(
    await fetch("/api/v1/desktop/agent/action", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify(action),
    })
  );
  return response.json();
}

export async function executeDesktopGuiSequence(
  token: string,
  workflowId: string | null,
  workflowName: string,
  actions: DesktopGuiAction[]
): Promise<DesktopGuiResult> {
  const response = await checked(
    await fetch("/api/v1/desktop/agent/sequence", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ workflowId, workflowName, actions }),
    })
  );
  return response.json();
}

export async function getDesktopCursorInfo(token: string): Promise<CursorPositionInfo> {
  return getApi<CursorPositionInfo>("desktop/agent/cursor", token);
}

export async function getDesktopActiveWindows(token: string): Promise<ActiveWindowInfo[]> {
  return getApi<ActiveWindowInfo[]>("desktop/agent/windows", token);
}

export async function focusDesktopWindow(token: string, target: string): Promise<DesktopGuiResult> {
  const response = await checked(
    await fetch("/api/v1/desktop/agent/focus", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ target }),
    })
  );
  return response.json();
}

export async function closeDesktopWindow(token: string, target: string): Promise<DesktopGuiResult> {
  const response = await checked(
    await fetch("/api/v1/desktop/agent/close", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ target }),
    })
  );
  return response.json();
}

export async function showDesktop(token: string): Promise<DesktopGuiResult> {
  const response = await checked(
    await fetch("/api/v1/desktop/agent/show-desktop", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({}),
    })
  );
  return response.json();
}

export async function getDesktopWorkflows(token: string): Promise<DesktopWorkflow[]> {
  return getApi<DesktopWorkflow[]>("desktop/agent/workflows", token);
}

export async function saveDesktopWorkflow(
  token: string,
  name: string,
  description: string,
  triggerPattern: string,
  actions: DesktopGuiAction[],
  isFavorite: boolean
): Promise<DesktopWorkflow> {
  const response = await checked(
    await fetch("/api/v1/desktop/agent/workflows", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ name, description, triggerPattern, actions, isFavorite }),
    })
  );
  return response.json();
}

export async function deleteDesktopWorkflow(token: string, id: string): Promise<{ success: boolean }> {
  const response = await checked(
    await fetch(`/api/v1/desktop/agent/workflows/${id}`, {
      method: "DELETE",
      headers: apiHeaders(token),
    })
  );
  return response.json();
}

export async function getDesktopAuditLogs(token: string, limit = 25): Promise<DesktopAuditEntry[]> {
  return getApi<DesktopAuditEntry[]>(`desktop/agent/audit?limit=${limit}`, token);
}

// ---------------------------------------------------------------------------
// Smart Home & IoT Automation API
// ---------------------------------------------------------------------------

export type SmartDeviceType = "LIGHT" | "SWITCH" | "CLIMATE" | "SENSOR" | "SCENE" | "OTHER";
export type SmartProtocol = "HOME_ASSISTANT" | "MQTT" | "SIMULATED";

export interface SmartDevice {
  id: string;
  entityId: string;
  name: string;
  type: SmartDeviceType;
  room: string;
  state: string; // "on", "off", "cool", etc.
  protocol: SmartProtocol;
  attributes: Record<string, any>;
  active: boolean;
  lastUpdated: string;
}

export interface SmartSceneAction {
  deviceId: string;
  action: string;
  value: any;
}

export interface SmartScene {
  id: string;
  name: string;
  icon: string;
  description: string;
  actions: SmartSceneAction[];
  active: boolean;
}

export interface SmartHomeConfig {
  haEnabled: boolean;
  haBaseUrl: string;
  haToken: string;
  mqttEnabled: boolean;
  mqttBrokerUrl: string;
  mqttClientId: string;
  mqttUsername: string;
  mqttPassword?: string;
  mqttBaseTopic: string;
  simulationMode: boolean;
}

export interface SmartHomeStatus {
  homeAssistantConnected: boolean;
  homeAssistantBaseUrl: string;
  mqttConnected: boolean;
  mqttBrokerUrl: string;
  simulationMode: boolean;
  totalDevices: number;
  activeDevices: number;
  totalScenes: number;
}

export interface SmartHomeEvent {
  id: string;
  timestamp: string;
  source: string;
  action: string;
  target: string;
  message: string;
  success: boolean;
  details?: Record<string, any>;
}

export async function getSmartHomeStatus(token: string, signal?: AbortSignal): Promise<SmartHomeStatus> {
  return getApi<SmartHomeStatus>("smarthome/status", token, signal);
}

export async function listSmartDevices(token: string, signal?: AbortSignal): Promise<SmartDevice[]> {
  return getApi<SmartDevice[]>("smarthome/devices", token, signal);
}

export async function executeSmartDeviceAction(
  entityId: string,
  action: string,
  params: Record<string, any> = {},
  token: string
): Promise<SmartDevice> {
  const response = await checked(
    await fetch(`/api/v1/smarthome/devices/${encodeURIComponent(entityId)}/action`, {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ action, params }),
    })
  );
  return response.json();
}

export async function listSmartScenes(token: string, signal?: AbortSignal): Promise<SmartScene[]> {
  return getApi<SmartScene[]>("smarthome/scenes", token, signal);
}

export async function activateSmartScene(sceneId: string, token: string): Promise<any> {
  const response = await checked(
    await fetch(`/api/v1/smarthome/scenes/${encodeURIComponent(sceneId)}/activate`, {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
    })
  );
  return response.json();
}

export async function getSmartHomeConfig(token: string, signal?: AbortSignal): Promise<SmartHomeConfig> {
  return getApi<SmartHomeConfig>("smarthome/config", token, signal);
}

export async function updateSmartHomeConfig(config: SmartHomeConfig, token: string): Promise<SmartHomeConfig> {
  const response = await checked(
    await fetch("/api/v1/smarthome/config", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify(config),
    })
  );
  return response.json();
}

export async function syncSmartHomeEntities(token: string): Promise<{ syncedCount: number; status: string }> {
  const response = await checked(
    await fetch("/api/v1/smarthome/sync", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
    })
  );
  return response.json();
}

export async function getSmartHomeEvents(token: string, signal?: AbortSignal): Promise<SmartHomeEvent[]> {
  return getApi<SmartHomeEvent[]>("smarthome/events", token, signal);
}

export async function sendSmartHomeCommand(
  command: string,
  isTamil: boolean = false,
  token: string
): Promise<{ matched: boolean; response?: string; message?: string }> {
  const response = await checked(
    await fetch("/api/v1/smarthome/command", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ command, isTamil }),
    })
  );
  return response.json();
}

// ============================================================================
// Coding Agent & Developer Studio API
// ============================================================================

export interface CodingStep {
  index: number;
  type: string;
  action: string;
  thought?: string;
  thoughtSummary?: string;
  output?: string;
  error?: string;
  status: string;
  durationMs: number;
  timestamp: string;
}

export interface CodingDiff {
  stepIndex: number;
  filePath: string;
  changeType: string;
  unifiedDiff: string;
  originalContent?: string;
  newContent?: string;
  timestamp: string;
}

export interface CodingSession {
  id: string;
  title: string;
  objective: string;
  workspaceRoot: string;
  model: string;
  searchProvider: string;
  status: string;
  totalTurns: number;
  filesModified: string[];
  checkpoint: Record<string, any>;
  finalSummary?: string;
  createdAt: string;
  updatedAt: string;
  completedAt?: string;
  steps: CodingStep[];
  diffs: CodingDiff[];
}

export interface WorkspaceFileNode {
  name: string;
  path: string;
  isDirectory: boolean;
  sizeBytes: number;
  children?: WorkspaceFileNode[];
}

export interface FileReadData {
  path: string;
  totalLines: number;
  startLine: number;
  endLine: number;
  content: string;
}

export async function startCodingSession(
  objective: string,
  model: string = "qwen2.5-coder:1.5b",
  searchProvider: string = "AUTO",
  token: string = ""
): Promise<CodingSession> {
  const response = await checked(
    await fetch("/api/v1/coding/session", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ objective, model, searchProvider }),
    })
  );
  return response.json();
}

export async function listCodingSessions(token: string = ""): Promise<CodingSession[]> {
  return getApi<CodingSession[]>("coding/sessions", token);
}

export async function getCodingSession(id: string, token: string = ""): Promise<CodingSession> {
  return getApi<CodingSession>(`coding/session/${encodeURIComponent(id)}`, token);
}

export async function cancelCodingSession(
  id: string,
  token: string = ""
): Promise<{ sessionId: string; cancelled: boolean }> {
  const response = await checked(
    await fetch(`/api/v1/coding/session/${encodeURIComponent(id)}/cancel`, {
      method: "POST",
      headers: apiHeaders(token),
    })
  );
  return response.json();
}

export async function resumeCodingSession(
  id: string,
  token: string = ""
): Promise<{ sessionId: string; resumed: boolean }> {
  const response = await checked(
    await fetch(`/api/v1/coding/session/${encodeURIComponent(id)}/resume`, {
      method: "POST",
      headers: apiHeaders(token),
    })
  );
  return response.json();
}

export async function rollbackCodingSession(
  id: string,
  token: string = ""
): Promise<{ sessionId: string; rolledBack: boolean }> {
  const response = await checked(
    await fetch(`/api/v1/coding/session/${encodeURIComponent(id)}/rollback`, {
      method: "POST",
      headers: apiHeaders(token),
    })
  );
  return response.json();
}

export async function getWorkspaceTree(
  path: string = "",
  depth: number = 3,
  token: string = ""
): Promise<WorkspaceFileNode> {
  return getApi<WorkspaceFileNode>(
    `coding/workspace/tree?path=${encodeURIComponent(path)}&depth=${depth}`,
    token
  );
}

export async function readWorkspaceFile(
  path: string,
  startLine?: number,
  endLine?: number,
  token: string = ""
): Promise<FileReadData> {
  let url = `coding/workspace/file?path=${encodeURIComponent(path)}`;
  if (startLine !== undefined) url += `&startLine=${startLine}`;
  if (endLine !== undefined) url += `&endLine=${endLine}`;
  return getApi<FileReadData>(url, token);
}

export interface CommandResult {
  command: string;
  exitCode: number;
  stdout: string;
  stderr: string;
  durationMs: number;
  timedOut: boolean;
}

export async function executeWorkspaceCommand(
  command: string,
  subDir?: string,
  timeoutSeconds?: number,
  token: string = ""
): Promise<CommandResult> {
  const response = await checked(
    await fetch("/api/v1/coding/workspace/execute", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ command, subDir, timeoutSeconds }),
    })
  );
  return response.json();
}

// ============================================================================
// Dynamic Search Engine Provider API
// ============================================================================

export interface SearchProviderInfo {
  id: string;
  displayName: string;
  description: string;
  active: boolean;
}

export async function getSearchProviders(token: string = ""): Promise<SearchProviderInfo[]> {
  return getApi<SearchProviderInfo[]>("web/providers", token);
}

export async function setSearchProvider(
  provider: string,
  token: string = ""
): Promise<SearchProviderInfo> {
  const response = await checked(
    await fetch("/api/v1/web/provider", {
      method: "POST",
      headers: { ...apiHeaders(token), "Content-Type": "application/json" },
      body: JSON.stringify({ provider }),
    })
  );
  return response.json();
}

// ============================================================================
// Obsidian-Style Neural Knowledge Graph API
// ============================================================================

export interface GraphNode {
  id: string;
  label: string;
  type: "CHAT" | "MEMORY" | "DOCUMENT" | "CODE_SESSION" | "CODE_FILE" | "LEARNED_FACT";
  size: number;
  metadata: Record<string, any>;
}

export interface GraphEdge {
  source: string;
  target: string;
  relationship: string;
  weight: number;
}

export interface GraphTopology {
  nodes: GraphNode[];
  edges: GraphEdge[];
  categoryCounts: Record<string, number>;
}

export async function getNeuralGraphTopology(
  filter?: string,
  maxNodes: number = 150,
  token: string = ""
): Promise<GraphTopology> {
  let url = `graph/topology?maxNodes=${maxNodes}`;
  if (filter && filter.trim()) url += `&filter=${encodeURIComponent(filter.trim())}`;
  return getApi<GraphTopology>(url, token);
}










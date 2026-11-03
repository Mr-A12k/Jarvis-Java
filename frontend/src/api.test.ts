import { describe, it, expect, vi, afterEach } from "vitest";
import {
  events,
  streamChat,
  getDatabaseStatus,
  listConversations,
  saveConversationSnapshot,
  deleteConversation,
  createDatabaseMemory,
  listMcpChallenges,
  approveMcpChallenge,
  getMcpServers,
  reloadMcpServers,
  listScheduledTasks,
  createScheduledTask,
  deleteScheduledTask,
} from "./api";

function body(parts: Uint8Array[]) {
  return new ReadableStream<Uint8Array>({
    start(controller) {
      parts.forEach((part) => controller.enqueue(part));
      controller.close();
    },
  });
}
const encode = (text: string) => new TextEncoder().encode(text);
afterEach(() => vi.unstubAllGlobals());

describe("SSE transport", () => {
  it("web search sends only the question and exposes safe source links", async () => {
    const mocked = vi
      .fn()
      .mockResolvedValue(
        new Response(
          body([
            encode(
              'event:sources\ndata:{"sources":[{"id":1,"title":"Docs","url":"https://example.com","snippet":"Evidence"},{"id":2,"title":"Bad","url":"javascript:alert(1)","snippet":""}]}\n\nevent:done\ndata:{}\n\n',
            ),
          ]),
        ),
      );
    vi.stubGlobal("fetch", mocked);
    const sources = vi.fn();
    await streamChat(
      "Public question",
      [{ role: "user", content: "Private history" }],
      "secret",
      new AbortController().signal,
      () => {},
      true,
      sources,
    );
    expect(mocked.mock.calls[0][0]).toBe("/api/v1/web/stream");
    expect(JSON.parse(mocked.mock.calls[0][1].body)).toEqual({
      message: "Public question",
      history: [{ role: "user", content: "Private history" }],
    });
    expect(sources.mock.calls[0][0]).toHaveLength(1);
  });
  it("handles every byte boundary including Unicode and CRLF", async () => {
    const bytes = encode(
      'event:delta\r\ndata:{"text":"Hello தமிழ்"}\r\n\r\nevent:done\ndata:{}\n\n',
    );
    const result = [];
    for await (const event of events(
      body([...bytes].map((b) => new Uint8Array([b]))),
    ))
      result.push(event);
    expect(result).toEqual([
      { type: "delta", data: { text: "Hello தமிழ்" } },
      { type: "done", data: {} },
    ]);
  });
  it("rejects truncated frames", async () => {
    const consume = async () => {
      for await (const _ of events(body([encode("event:delta\ndata:{")]))) {
        /* consume */
      }
    };
    await expect(consume()).rejects.toThrow("incomplete");
  });
  it("propagates server errors instead of reporting success", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            body([encode('event:error\ndata:{"message":"Model missing"}\n\n')]),
          ),
        ),
    );
    await expect(
      streamChat("Hi", [], "token", new AbortController().signal, () => {}),
    ).rejects.toThrow("Model missing");
  });
  it("rejects missing done events", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            body([encode('event:delta\ndata:{"text":"partial"}\n\n')]),
          ),
        ),
    );
    await expect(
      streamChat("Hi", [], "token", new AbortController().signal, () => {}),
    ).rejects.toThrow("before the answer completed");
  });
  it("sends bearer authorization and bounded history", async () => {
    const mocked = vi
      .fn()
      .mockResolvedValue(
        new Response(body([encode("event:done\ndata:{}\n\n")])),
      );
    vi.stubGlobal("fetch", mocked);
    await streamChat(
      "Hi",
      Array.from({ length: 9 }, () => ({
        role: "user" as const,
        content: "x",
      })),
      "secret",
      new AbortController().signal,
      () => {},
    );
    const init = mocked.mock.calls[0][1];
    expect(init.headers.Authorization).toBe("Bearer secret");
    expect(JSON.parse(init.body).history).toHaveLength(6);
  });
  it("handles unauthorized HTTP responses", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response(null, { status: 401 })),
    );
    await expect(
      streamChat("Hi", [], "wrong", new AbortController().signal, () => {}),
    ).rejects.toThrow("token was rejected");
  });
});

describe("Database API helpers", () => {
  it("getDatabaseStatus fetches status with local headers", async () => {
    const mocked = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ configured: true, ready: true }), {
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", mocked);
    const status = await getDatabaseStatus("test-token");
    expect(mocked).toHaveBeenCalledWith(
      "/api/v1/database/status",
      expect.objectContaining({
        headers: expect.objectContaining({
          "X-Jarvis-Local": "1",
          Authorization: "Bearer test-token",
        }),
      }),
    );
    expect(status).toEqual({ configured: true, ready: true });
  });

  it("listConversations requests paginated records", async () => {
    const mocked = vi.fn().mockResolvedValue(
      new Response(JSON.stringify([{ id: "conv-1", title: "Test", version: 1 }]), {
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", mocked);
    const result = await listConversations("tok", 20, 10);
    expect(mocked).toHaveBeenCalledWith(
      "/api/v1/database/conversations?limit=20&offset=10",
      expect.anything(),
    );
    expect(result).toHaveLength(1);
  });

  it("saveConversationSnapshot sends title and messages payload", async () => {
    const mocked = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ id: "new-conv", title: "Saved", version: 1 }), {
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", mocked);
    const saved = await saveConversationSnapshot("tok", "My Chat", [
      { role: "user", content: "Hello" },
    ]);
    expect(mocked).toHaveBeenCalledWith(
      "/api/v1/database/snapshots",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          title: "My Chat",
          messages: [{ role: "user", content: "Hello" }],
        }),
      }),
    );
    expect(saved.id).toBe("new-conv");
  });

  it("deleteConversation executes DELETE with version query parameter", async () => {
    const mocked = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ deleted: true }), {
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", mocked);
    const deleted = await deleteConversation("tok", "conv-123", 2);
    expect(mocked).toHaveBeenCalledWith(
      "/api/v1/database/conversations/conv-123?version=2",
      expect.objectContaining({ method: "DELETE" }),
    );
    expect(deleted).toBe(true);
  });

  it("rejects oversized snapshots without silently discarding messages", async () => {
    const mocked = vi.fn();
    vi.stubGlobal("fetch", mocked);
    await expect(saveConversationSnapshot("tok", "Long chat", Array.from({ length: 101 }, () => ({ role: "user" as const, content: "Hello" }))))
      .rejects.toThrow("Nothing was saved");
    expect(mocked).not.toHaveBeenCalled();
  });

  it("createDatabaseMemory posts memory title and content", async () => {
    const mocked = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ id: "mem-1", title: "Pref", content: "Dark mode", version: 1 }), {
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", mocked);
    const created = await createDatabaseMemory("tok", "Pref", "Dark mode");
    expect(mocked).toHaveBeenCalledWith(
      "/api/v1/database/memories",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ title: "Pref", content: "Dark mode" }),
      }),
    );
    expect(created.title).toBe("Pref");
  });

  it("listMcpChallenges fetches active challenges", async () => {
    const mocked = vi.fn().mockResolvedValue(
      new Response(JSON.stringify([{ challengeId: "c-1", toolName: "delete_resource", arguments: {}, status: "PENDING" }]), {
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", mocked);
    const list = await listMcpChallenges("tok");
    expect(mocked).toHaveBeenCalledWith(
      "/api/v1/mcp/challenges",
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: "Bearer tok" }),
      }),
    );
    expect(list).toHaveLength(1);
    expect(list[0].challengeId).toBe("c-1");
  });

  it("approveMcpChallenge sends challenge decision", async () => {
    const mocked = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ approvalRequired: false, challengeId: "c-1", status: "APPROVED" }), {
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", mocked);
    const res = await approveMcpChallenge("tok", "c-1", true);
    expect(mocked).toHaveBeenCalledWith(
      "/api/v1/mcp/approve",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ challengeId: "c-1", approved: true }),
      }),
    );
    expect(res.status).toBe("APPROVED");
  });

  it("listScheduledTasks requests tasks from backend", async () => {
    const mocked = vi.fn().mockResolvedValue(
      new Response(JSON.stringify([{ id: "t-1", type: "CLEANUP_AUDIT", name: "Audit", intervalSeconds: 3600 }]), {
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", mocked);
    const tasks = await listScheduledTasks("tok");
    expect(mocked).toHaveBeenCalledWith(
      "/api/v1/scheduler/tasks",
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: "Bearer tok" }),
      }),
    );
    expect(tasks).toHaveLength(1);
    expect(tasks[0].id).toBe("t-1");
  });

  it("createScheduledTask posts new task payload", async () => {
    const mocked = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ id: "t-2", type: "SYSTEM_METRICS_LOG", intervalSeconds: 60 }), {
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", mocked);
    const task = await createScheduledTask("tok", "SYSTEM_METRICS_LOG", 60, "Metrics");
    expect(mocked).toHaveBeenCalledWith(
      "/api/v1/scheduler/tasks",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ type: "SYSTEM_METRICS_LOG", name: "Metrics", intervalSeconds: 60 }),
      }),
    );
    expect(task.id).toBe("t-2");
  });

  it("deleteScheduledTask calls delete endpoint", async () => {
    const mocked = vi.fn().mockResolvedValue(
      new Response("true", { headers: { "Content-Type": "application/json" } }),
    );
    vi.stubGlobal("fetch", mocked);
    const result = await deleteScheduledTask("tok", "t-1");
    expect(mocked).toHaveBeenCalledWith(
      "/api/v1/scheduler/tasks/t-1",
      expect.objectContaining({ method: "DELETE" }),
    );
    expect(result).toBe(true);
  });

  it("getMcpServers fetches server connection statuses", async () => {
    const mocked = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ "test-server": { connected: true, toolCount: 2 } }), {
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", mocked);
    const servers = await getMcpServers("tok");
    expect(mocked).toHaveBeenCalledWith(
      "/api/v1/mcp/servers",
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: "Bearer tok" }),
      }),
    );
    expect(servers["test-server"].connected).toBe(true);
  });

  it("reloadMcpServers triggers server reload", async () => {
    const mocked = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ "test-server": { connected: true } }), {
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", mocked);
    const servers = await reloadMcpServers("tok");
    expect(mocked).toHaveBeenCalledWith(
      "/api/v1/mcp/servers/reload",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ Authorization: "Bearer tok" }),
      }),
    );
    expect(servers["test-server"]).toBeDefined();
  });

  it("shopping API searches products and generates comparison matrix", async () => {
    const fakeProduct = {
      id: "p1",
      title: "Keychron K2",
      price: 79.99,
      currency: "USD",
      rating: 4.7,
      reviewCount: 4800,
      retailer: "Amazon",
      url: "https://amazon.com/dp/test",
      specs: { Connectivity: "Wireless" },
      highlights: ["RGB"],
      inStock: true,
    };

    const mocked = vi.fn().mockResolvedValue(
      new Response(JSON.stringify([fakeProduct]), {
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", mocked);

    const { searchProducts } = await import("./api");
    const items = await searchProducts("tok", {
      query: "keyboard",
      maxPrice: 100,
      currency: "USD",
      requiredFeatures: [],
      preferredBrands: [],
      sortPreference: "best_value",
    });

    expect(items).toHaveLength(1);
    expect(items[0].title).toBe("Keychron K2");
    expect(mocked).toHaveBeenCalledWith(
      "/api/v1/shopping/search",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ Authorization: "Bearer tok" }),
      }),
    );
  });

  it("desktop system tools controls media, volume, and launches apps", async () => {
    const mocked = vi.fn().mockImplementation((url: string) => {
      if (url.includes("/desktop/media")) {
        return Promise.resolve(new Response(JSON.stringify({ success: true, action: "play_pause", target: "media", message: "Toggled" })));
      }
      if (url.includes("/desktop/volume")) {
        return Promise.resolve(new Response(JSON.stringify({ volume: 75, isMuted: false })));
      }
      if (url.includes("/desktop/launch")) {
        return Promise.resolve(new Response(JSON.stringify({ success: true, action: "launch", target: "Spotify", message: "Opening Spotify" })));
      }
      return Promise.reject(new Error("Unknown url"));
    });
    vi.stubGlobal("fetch", mocked);

    const { controlDesktopMedia, controlDesktopVolume, launchDesktopApp } = await import("./api");
    const mediaRes = await controlDesktopMedia("play_pause", "secret-tok");
    expect(mediaRes.success).toBe(true);

    const volRes = await controlDesktopVolume("set", { level: 75 }, "secret-tok");
    expect(volRes.volume).toBe(75);

    const launchRes = await launchDesktopApp("spotify", "secret-tok");
    expect(launchRes.success).toBe(true);
    expect(launchRes.target).toBe("Spotify");
  });

  it("coding agent API manages sessions, cancellations, rollbacks, and neural graph", async () => {
    const mocked = vi.fn().mockImplementation((url: string, opts: any) => {
      if (url.includes("/coding/session") && opts?.method === "POST" && !url.includes("/cancel") && !url.includes("/resume") && !url.includes("/rollback")) {
        return Promise.resolve(new Response(JSON.stringify({
          id: "test-sess-1",
          task: "Fix login bug",
          workspaceRoot: ".",
          status: "RUNNING",
          currentStep: 1,
          maxSteps: 30,
          createdAt: 1730000000,
          updatedAt: 1730000000,
          diffs: [],
        })));
      }
      if (url.includes("/coding/session/test-sess-1/cancel")) {
        return Promise.resolve(new Response(JSON.stringify({ sessionId: "test-sess-1", cancelled: true })));
      }
      if (url.includes("/coding/session/test-sess-1/resume")) {
        return Promise.resolve(new Response(JSON.stringify({ sessionId: "test-sess-1", resumed: true })));
      }
      if (url.includes("/coding/session/test-sess-1/rollback")) {
        return Promise.resolve(new Response(JSON.stringify({ sessionId: "test-sess-1", rolledBack: true })));
      }
      if (url.includes("/graph/topology")) {
        return Promise.resolve(new Response(JSON.stringify({
          nodes: [{ id: "n1", label: "Auth", type: "CODE_FILE", size: 5, metadata: {} }],
          edges: [{ source: "n1", target: "n2", relationship: "REVEALS", weight: 0.8 }],
          categoryCounts: { CODE_FILE: 1 },
        })));
      }
      if (url.includes("/web/providers")) {
        return Promise.resolve(new Response(JSON.stringify([
          { id: "CHROMIUM_GOOGLE", displayName: "Chromium (Google)", description: "Playwright Headless", active: true },
          { id: "DUCKDUCKGO_HTTP", displayName: "DuckDuckGo HTTP", description: "Standard HTTP", active: false }
        ])));
      }
      if (url.includes("/web/provider") && opts?.method === "POST") {
        return Promise.resolve(new Response(JSON.stringify(
          { id: "DUCKDUCKGO_HTTP", displayName: "DuckDuckGo HTTP", description: "Standard HTTP", active: true }
        )));
      }
      if (url.includes("/coding/workspace/execute") && opts?.method === "POST") {
        return Promise.resolve(new Response(JSON.stringify({
          command: "git status",
          exitCode: 0,
          stdout: "On branch master\nnothing to commit",
          stderr: "",
          durationMs: 45,
          timedOut: false,
        })));
      }
      return Promise.reject(new Error("Unknown url: " + url));
    });
    vi.stubGlobal("fetch", mocked);

    const {
      startCodingSession,
      cancelCodingSession,
      resumeCodingSession,
      rollbackCodingSession,
      executeWorkspaceCommand,
      getNeuralGraphTopology,
      getSearchProviders,
      setSearchProvider,
    } = await import("./api");

    const session = await startCodingSession("Fix login bug", ".", "secret-tok");
    expect(session.id).toBe("test-sess-1");
    expect(session.status).toBe("RUNNING");

    const cancelRes = await cancelCodingSession("test-sess-1", "secret-tok");
    expect(cancelRes.cancelled).toBe(true);

    const resumeRes = await resumeCodingSession("test-sess-1", "secret-tok");
    expect(resumeRes.resumed).toBe(true);

    const rollRes = await rollbackCodingSession("test-sess-1", "secret-tok");
    expect(rollRes.rolledBack).toBe(true);

    const graph = await getNeuralGraphTopology(undefined, 100, "secret-tok");
    expect(graph.nodes).toHaveLength(1);
    expect(graph.edges).toHaveLength(1);

    const providers = await getSearchProviders("secret-tok");
    expect(providers).toHaveLength(2);
    expect(providers[0].id).toBe("CHROMIUM_GOOGLE");

    const setRes = await setSearchProvider("DUCKDUCKGO_HTTP", "secret-tok");
    expect(setRes.id).toBe("DUCKDUCKGO_HTTP");

    const execRes = await executeWorkspaceCommand("git status", undefined, 60, "secret-tok");
    expect(execRes.exitCode).toBe(0);
    expect(execRes.stdout).toContain("On branch master");
  });
});

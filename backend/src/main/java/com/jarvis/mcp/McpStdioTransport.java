package com.jarvis.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.ApiException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * Standard I/O transport for MCP servers communicating via JSON-RPC 2.0 lines.
 */
public class McpStdioTransport implements McpTransport {

    private static final Logger log = LoggerFactory.getLogger(McpStdioTransport.class);

    private final String serverName;
    private final String command;
    private final List<String> args;
    private final Map<String, String> env;
    private final ObjectMapper json;
    private final Duration timeout;

    private final boolean customStreams;
    private final InputStream customIn;
    private final OutputStream customOut;

    private final AtomicLong idCounter = new AtomicLong(1);
    private final ConcurrentHashMap<Long, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();

    private volatile boolean running = false;
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private Thread readerThread;

    public McpStdioTransport(
            String serverName,
            String command,
            List<String> args,
            Map<String, String> env,
            ObjectMapper json,
            Duration timeout) {
        this.serverName = Objects.requireNonNull(serverName, "serverName must not be null");
        this.command = command;
        this.args = args != null ? List.copyOf(args) : List.of();
        this.env = env != null ? Map.copyOf(env) : Map.of();
        this.json = json != null ? json : new ObjectMapper();
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(10);
        this.customStreams = false;
        this.customIn = null;
        this.customOut = null;
    }

    /**
     * Testing constructor allowing custom streams without spawning an OS process.
     */
    public McpStdioTransport(
            String serverName,
            InputStream in,
            OutputStream out,
            ObjectMapper json,
            Duration timeout) {
        this.serverName = Objects.requireNonNull(serverName, "serverName must not be null");
        this.command = "mock";
        this.args = List.of();
        this.env = Map.of();
        this.json = json != null ? json : new ObjectMapper();
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(10);
        this.customStreams = true;
        this.customIn = Objects.requireNonNull(in, "in must not be null");
        this.customOut = Objects.requireNonNull(out, "out must not be null");
    }

    @Override
    public String getServerName() {
        return serverName;
    }

    @Override
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        if (customStreams) {
            this.writer = new BufferedWriter(new OutputStreamWriter(customOut, StandardCharsets.UTF_8));
            this.reader = new BufferedReader(new InputStreamReader(customIn, StandardCharsets.UTF_8));
        } else {
            if (command == null || command.isBlank()) {
                throw new IllegalArgumentException("Command cannot be blank for stdio transport: " + serverName);
            }

            List<String> fullCommand = new ArrayList<>();
            fullCommand.add(command);
            fullCommand.addAll(args);

            ProcessBuilder pb = new ProcessBuilder(fullCommand);
            if (!env.isEmpty()) {
                pb.environment().putAll(env);
            }
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);

            log.info("Launching MCP server '{}' with command: {}", serverName, fullCommand);
            this.process = pb.start();
            this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            this.reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        }

        this.running = true;
        this.readerThread = new Thread(this::readLoop, "mcp-reader-" + serverName);
        this.readerThread.setDaemon(true);
        this.readerThread.start();
    }

    private void readLoop() {
        try {
            String line;
            while (running && !Thread.currentThread().isInterrupted() && (line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                handleIncomingLine(trimmed);
            }
        } catch (Exception e) {
            log.debug("Reader loop ended for MCP server '{}': {}", serverName, e.getMessage());
        } finally {
            running = false;
            ApiException lostEx = new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_DISCONNECTED",
                    "MCP server '" + serverName + "' connection closed");
            pendingRequests.forEach((id, future) -> future.completeExceptionally(lostEx));
            pendingRequests.clear();
        }
    }

    private void handleIncomingLine(String line) {
        try {
            JsonNode node = json.readTree(line);
            if (node.has("id") && !node.get("id").isNull()) {
                long id = node.get("id").asLong();
                CompletableFuture<JsonNode> future = pendingRequests.remove(id);
                if (future != null) {
                    if (node.has("error") && !node.get("error").isNull()) {
                        String errMsg = node.get("error").path("message").asText("MCP error");
                        future.completeExceptionally(new ApiException(HttpStatus.BAD_GATEWAY, "MCP_SERVER_ERROR", errMsg));
                    } else if (node.has("result")) {
                        future.complete(node.get("result"));
                    } else {
                        future.complete(node);
                    }
                }
            } else {
                log.debug("Received MCP notification from '{}': {}", serverName, line);
            }
        } catch (Exception e) {
            log.warn("Failed to parse incoming line from MCP server '{}': {}", serverName, line, e);
        }
    }

    @Override
    public CompletableFuture<JsonNode> sendRequest(String method, Object params) {
        if (!running) {
            return CompletableFuture.failedFuture(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_NOT_CONNECTED",
                    "MCP server '" + serverName + "' is not connected"));
        }

        long id = idCounter.getAndIncrement();
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("jsonrpc", "2.0");
        req.put("id", id);
        req.put("method", method);
        if (params != null) {
            req.put("params", params);
        }

        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        try {
            String jsonLine = json.writeValueAsString(req);
            synchronized (writer) {
                writer.write(jsonLine);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            pendingRequests.remove(id);
            return CompletableFuture.failedFuture(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_IO_ERROR",
                    "Failed to send request to '" + serverName + "': " + e.getMessage()));
        }

        return future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    pendingRequests.remove(id);
                    if (ex instanceof TimeoutException) {
                        throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "MCP_TIMEOUT",
                                "Request '" + method + "' to MCP server '" + serverName + "' timed out after " + timeout.toMillis() + "ms");
                    }
                    if (ex instanceof CompletionException ce && ce.getCause() instanceof ApiException ae) {
                        throw ae;
                    }
                    if (ex instanceof ApiException ae) {
                        throw ae;
                    }
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "MCP_ERROR", ex.getMessage());
                });
    }

    @Override
    public void sendNotification(String method, Object params) {
        if (!running) {
            return;
        }

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("jsonrpc", "2.0");
        req.put("method", method);
        if (params != null) {
            req.put("params", params);
        }

        try {
            String jsonLine = json.writeValueAsString(req);
            synchronized (writer) {
                writer.write(jsonLine);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            log.warn("Failed to send notification to MCP server '{}': {}", serverName, e.getMessage());
        }
    }

    @Override
    public boolean isConnected() {
        if (!running) {
            return false;
        }
        if (process != null) {
            return process.isAlive();
        }
        return true;
    }

    @Override
    public synchronized void close() {
        running = false;
        if (readerThread != null && readerThread.isAlive()) {
            readerThread.interrupt();
        }
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException ignored) {}
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException ignored) {}

        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(1, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }

        ApiException closedEx = new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_DISCONNECTED",
                "MCP server '" + serverName + "' transport closed");
        pendingRequests.forEach((id, future) -> future.completeExceptionally(closedEx));
        pendingRequests.clear();
    }
}

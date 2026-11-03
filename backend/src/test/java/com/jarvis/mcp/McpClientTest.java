package com.jarvis.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.ApiException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpClientTest {

    private ObjectMapper json;
    private PipedOutputStream clientOut; // Client writes to this
    private PipedInputStream serverIn;   // Server reads from this
    private PipedOutputStream serverOut; // Server writes to this
    private PipedInputStream clientIn;   // Client reads from this

    private BufferedReader serverReader;
    private BufferedWriter serverWriter;

    @BeforeEach
    void setUp() throws IOException {
        json = new ObjectMapper();

        clientOut = new PipedOutputStream();
        serverIn = new PipedInputStream(clientOut);

        serverOut = new PipedOutputStream();
        clientIn = new PipedInputStream(serverOut);

        serverReader = new BufferedReader(new InputStreamReader(serverIn, StandardCharsets.UTF_8));
        serverWriter = new BufferedWriter(new OutputStreamWriter(serverOut, StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() throws IOException {
        try { serverWriter.close(); } catch (Exception ignored) {}
        try { clientOut.close(); } catch (Exception ignored) {}
        try { serverReader.close(); } catch (Exception ignored) {}
        try { clientIn.close(); } catch (Exception ignored) {}
    }

    private void respond(String jsonResponse) throws IOException {
        serverWriter.write(jsonResponse);
        serverWriter.newLine();
        serverWriter.flush();
    }

    @Test
    void stdioTransportInitializesAndDiscoversTools() throws Exception {
        McpStdioTransport transport = new McpStdioTransport(
                "mock-server",
                clientIn,
                clientOut,
                json,
                Duration.ofSeconds(3)
        );
        transport.start();

        McpClientManager manager = new McpClientManager(json);
        manager.registerTransport("mock-server", transport);

        // Run client initialization in background
        CompletableFuture<Void> initFuture = CompletableFuture.runAsync(() ->
                manager.initializeAndDiscover("mock-server")
        );

        // 1. Server receives "initialize" request
        String initReq = serverReader.readLine();
        JsonNode initNode = json.readTree(initReq);
        assertThat(initNode.path("method").asText()).isEqualTo("initialize");
        long initId = initNode.path("id").asLong();

        // Server sends initialize response
        respond("{\"jsonrpc\":\"2.0\",\"id\":" + initId + ",\"result\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"mock-srv\",\"version\":\"1.0\"}}}");

        // 2. Server receives "notifications/initialized" notification
        String notif = serverReader.readLine();
        JsonNode notifNode = json.readTree(notif);
        assertThat(notifNode.path("method").asText()).isEqualTo("notifications/initialized");

        // 3. Server receives "tools/list" request
        String listReq = serverReader.readLine();
        JsonNode listNode = json.readTree(listReq);
        assertThat(listNode.path("method").asText()).isEqualTo("tools/list");
        long listId = listNode.path("id").asLong();

        // Server sends tools/list response
        String toolsJson = "{\"jsonrpc\":\"2.0\",\"id\":" + listId + ",\"result\":{\"tools\":["
                + "{\"name\":\"safe_echo\",\"description\":\"Echo text safely\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"msg\":{\"type\":\"string\"}},\"required\":[\"msg\"]}},"
                + "{\"name\":\"delete_records\",\"description\":\"Delete table records permanently\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}}"
                + "]}}";
        respond(toolsJson);

        initFuture.get(5, TimeUnit.SECONDS);

        assertThat(manager.isConnected("mock-server")).isTrue();
        List<McpApprovalService.ToolInfo> tools = manager.getTools("mock-server");
        assertThat(tools).hasSize(2);

        McpApprovalService.ToolInfo safeTool = tools.stream().filter(t -> t.name().equals("safe_echo")).findFirst().orElseThrow();
        assertThat(safeTool.consequential()).isFalse();
        assertThat(safeTool.serverName()).isEqualTo("mock-server");

        McpApprovalService.ToolInfo deleteTool = tools.stream().filter(t -> t.name().equals("delete_records")).findFirst().orElseThrow();
        assertThat(deleteTool.consequential()).isTrue();

        transport.close();
    }

    @Test
    void callSafeToolReturnsTextResult() throws Exception {
        McpStdioTransport transport = new McpStdioTransport("test-srv", clientIn, clientOut, json, Duration.ofSeconds(3));
        transport.start();

        McpClientManager manager = new McpClientManager(json);
        manager.registerTransport("test-srv", transport);

        CompletableFuture<Object> callFuture = CompletableFuture.supplyAsync(() ->
                manager.callTool("test-srv", "safe_echo", Map.of("msg", "hello world"))
        );

        String callReq = serverReader.readLine();
        JsonNode reqNode = json.readTree(callReq);
        assertThat(reqNode.path("method").asText()).isEqualTo("tools/call");
        assertThat(reqNode.path("params").path("name").asText()).isEqualTo("safe_echo");
        assertThat(reqNode.path("params").path("arguments").path("msg").asText()).isEqualTo("hello world");
        long callId = reqNode.path("id").asLong();

        respond("{\"jsonrpc\":\"2.0\",\"id\":" + callId + ",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"echo: hello world\"}],\"isError\":false}}");

        Object result = callFuture.get(5, TimeUnit.SECONDS);
        assertThat(result).isEqualTo("echo: hello world");

        transport.close();
    }

    @Test
    void callToolWithServerErrorThrowsApiException() throws Exception {
        McpStdioTransport transport = new McpStdioTransport("test-srv", clientIn, clientOut, json, Duration.ofSeconds(3));
        transport.start();

        McpClientManager manager = new McpClientManager(json);
        manager.registerTransport("test-srv", transport);

        CompletableFuture<Object> callFuture = CompletableFuture.supplyAsync(() ->
                manager.callTool("test-srv", "fail_tool", Map.of())
        );

        String callReq = serverReader.readLine();
        long callId = json.readTree(callReq).path("id").asLong();

        respond("{\"jsonrpc\":\"2.0\",\"id\":" + callId + ",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"Database connection failed\"}],\"isError\":true}}");

        assertThatThrownBy(() -> callFuture.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiEx = (ApiException) e.getCause();
                    assertThat(apiEx.code()).isEqualTo("MCP_TOOL_ERROR");
                    assertThat(apiEx.getMessage()).contains("Database connection failed");
                });

        transport.close();
    }

    @Test
    void callToolTimeoutThrowsGatewayTimeout() throws Exception {
        McpStdioTransport transport = new McpStdioTransport("timeout-srv", clientIn, clientOut, json, Duration.ofMillis(100));
        transport.start();

        McpClientManager manager = new McpClientManager(json);
        manager.registerTransport("timeout-srv", transport);

        assertThatThrownBy(() -> manager.callTool("timeout-srv", "slow_tool", Map.of()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiEx = (ApiException) e;
                    assertThat(apiEx.status()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(apiEx.code()).isEqualTo("MCP_TIMEOUT");
                });

        transport.close();
    }

    @Test
    void transportDisconnectionFailsPendingRequests() throws Exception {
        McpStdioTransport transport = new McpStdioTransport("disc-srv", clientIn, clientOut, json, Duration.ofSeconds(5));
        transport.start();

        CompletableFuture<JsonNode> future = transport.sendRequest("test/ping", Map.of());

        // Close server side stream abruptly
        serverOut.close();

        assertThatThrownBy(() -> future.get(3, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiEx = (ApiException) e.getCause();
                    assertThat(apiEx.code()).isEqualTo("MCP_DISCONNECTED");
                });

        transport.close();
    }

    @Test
    void configDiscoveryParsesMcpServersJson() throws IOException {
        Path tempJson = Files.createTempFile("mcp-test-servers", ".json");
        try {
            String configContent = """
            {
              "mcpServers": {
                "weather-service": {
                  "command": "python",
                  "args": ["weather.py", "--port", "9000"],
                  "env": { "API_KEY": "secret-123" },
                  "disabled": false,
                  "timeoutMs": 5000
                },
                "disabled-srv": {
                  "command": "node",
                  "args": ["disabled.js"],
                  "disabled": true
                }
              }
            }
            """;
            Files.writeString(tempJson, configContent);

            McpClientManager manager = new McpClientManager(json, null, tempJson.toString());
            Map<String, McpServerConfig> discovered = manager.discoverServerConfigs();

            assertThat(discovered).containsKey("weather-service");
            McpServerConfig weather = discovered.get("weather-service");
            assertThat(weather.command()).isEqualTo("python");
            assertThat(weather.args()).containsExactly("weather.py", "--port", "9000");
            assertThat(weather.env()).containsEntry("API_KEY", "secret-123");
            assertThat(weather.timeoutMs()).isEqualTo(5000L);
            assertThat(weather.disabled()).isFalse();

            assertThat(discovered).containsKey("disabled-srv");
            assertThat(discovered.get("disabled-srv").disabled()).isTrue();
        } finally {
            Files.deleteIfExists(tempJson);
        }
    }

    @Test
    void approvalServiceExecutesSafeAndConsequentialToolsViaTransport() throws Exception {
        Path tempAudit = Files.createTempFile("mcp-audit-manager-test", ".jsonl");
        try {
            McpStdioTransport transport = new McpStdioTransport("active-srv", clientIn, clientOut, json, Duration.ofSeconds(3));
            transport.start();

            McpClientManager manager = new McpClientManager(json);
            manager.registerTransport("active-srv", transport);

            McpApprovalService service = new McpApprovalService(json, tempAudit.toString(), manager);

            // Register safe tool
            service.registerTool(new McpApprovalService.ToolInfo(
                    "get_notes",
                    "Retrieve user notes",
                    Map.of("type", "object"),
                    false,
                    "active-srv"
            ));

            // Register consequential tool
            service.registerTool(new McpApprovalService.ToolInfo(
                    "delete_notes",
                    "Delete user notes",
                    Map.of("type", "object"),
                    true,
                    "active-srv"
            ));

            // 1. Safe tool execution -> directly calls transport
            CompletableFuture<McpApprovalService.ExecutionResult> safeFuture = CompletableFuture.supplyAsync(() ->
                    service.requestToolExecution("get_notes", Map.of())
            );

            String safeReq = serverReader.readLine();
            long safeId = json.readTree(safeReq).path("id").asLong();
            respond("{\"jsonrpc\":\"2.0\",\"id\":" + safeId + ",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"Meeting at 10am\"}]}}");

            McpApprovalService.ExecutionResult safeResult = safeFuture.get(5, TimeUnit.SECONDS);
            assertThat(safeResult.approvalRequired()).isFalse();
            assertThat(safeResult.result()).isEqualTo("Meeting at 10am");

            // 2. Consequential tool execution -> generates approval challenge
            McpApprovalService.ExecutionResult challengeResult = service.requestToolExecution("delete_notes", Map.of());
            assertThat(challengeResult.approvalRequired()).isTrue();
            assertThat(challengeResult.challengeId()).isNotNull();

            // 3. User approves challenge -> calls transport
            CompletableFuture<McpApprovalService.ExecutionResult> approveFuture = CompletableFuture.supplyAsync(() ->
                    service.processApproval(challengeResult.challengeId(), true)
            );

            String delReq = serverReader.readLine();
            long delId = json.readTree(delReq).path("id").asLong();
            respond("{\"jsonrpc\":\"2.0\",\"id\":" + delId + ",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"Notes deleted successfully\"}]}}");

            McpApprovalService.ExecutionResult approvedExec = approveFuture.get(5, TimeUnit.SECONDS);
            assertThat(approvedExec.status()).isEqualTo("APPROVED");
            assertThat(approvedExec.result()).isEqualTo("Notes deleted successfully");

            transport.close();
        } finally {
            Files.deleteIfExists(tempAudit);
        }
    }
}

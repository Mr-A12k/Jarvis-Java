package com.jarvis.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.ApiException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Manages MCP client transports (stdio processes), tool discovery, and execution dispatching.
 */
@Service
public class McpClientManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpClientManager.class);

    private static final Pattern CONSEQUENTIAL_PATTERN = Pattern.compile(
            ".*(delete|remove|exec|shell|terminal|drop|kill|destroy|write|update|modify|install|send|publish).*",
            Pattern.CASE_INSENSITIVE
    );

    private final ObjectMapper json;
    private final McpConfiguration mcpConfiguration;
    private final Path configuredPath;

    private final Map<String, McpTransport> activeTransports = new ConcurrentHashMap<>();
    private final Map<String, List<McpApprovalService.ToolInfo>> serverTools = new ConcurrentHashMap<>();
    private Consumer<McpApprovalService.ToolInfo> toolListener;

    public McpClientManager(ObjectMapper json) {
        this(json, null, "data/mcp-servers.json");
    }

    @Autowired
    public McpClientManager(
            ObjectMapper json,
            @Autowired(required = false) McpConfiguration mcpConfiguration,
            @Value("${jarvis.mcp.servers-path:data/mcp-servers.json}") String serversPath) {
        this.json = json != null ? json : new ObjectMapper();
        this.mcpConfiguration = mcpConfiguration;
        this.configuredPath = Path.of(serversPath).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        loadAndConnectServers();
    }

    public synchronized void setToolListener(Consumer<McpApprovalService.ToolInfo> listener) {
        this.toolListener = listener;
        if (listener != null) {
            for (var tools : serverTools.values()) {
                for (var tool : tools) {
                    listener.accept(tool);
                }
            }
        }
    }

    public synchronized void loadAndConnectServers() {
        Map<String, McpServerConfig> configs = discoverServerConfigs();
        for (var entry : configs.entrySet()) {
            String serverName = entry.getKey();
            McpServerConfig serverConfig = entry.getValue();
            if (serverConfig.disabled()) {
                log.info("MCP server '{}' is disabled; skipping startup", serverName);
                continue;
            }
            try {
                connectStdioServer(serverConfig);
            } catch (Exception e) {
                log.warn("Failed to connect MCP server '{}': {}", serverName, e.getMessage());
            }
        }
    }

    public Map<String, McpServerConfig> discoverServerConfigs() {
        Map<String, McpServerConfig> result = new LinkedHashMap<>();

        // 1. Check primary file path (e.g. data/mcp-servers.json)
        Path filePath = configuredPath;
        if (!Files.exists(filePath)) {
            // Check secondary path (e.g. config/mcp-servers.json)
            Path secondary = Path.of("config/mcp-servers.json").toAbsolutePath().normalize();
            if (Files.exists(secondary)) {
                filePath = secondary;
            }
        }

        if (Files.exists(filePath)) {
            try {
                String content = Files.readString(filePath);
                JsonNode root = json.readTree(content);
                JsonNode serversNode = null;
                if (root.has("mcpServers") && root.get("mcpServers").isObject()) {
                    serversNode = root.get("mcpServers");
                } else if (root.has("servers") && root.get("servers").isObject()) {
                    serversNode = root.get("servers");
                } else if (root.isObject()) {
                    serversNode = root;
                }

                if (serversNode != null) {
                    var fields = serversNode.fields();
                    while (fields.hasNext()) {
                        var field = fields.next();
                        String name = field.getKey();
                        JsonNode srvNode = field.getValue();
                        String command = srvNode.path("command").asText(null);
                        if (command == null || command.isBlank()) {
                            continue;
                        }

                        List<String> args = new ArrayList<>();
                        if (srvNode.has("args") && srvNode.get("args").isArray()) {
                            for (JsonNode a : srvNode.get("args")) {
                                args.add(a.asText());
                            }
                        }

                        Map<String, String> env = new LinkedHashMap<>();
                        if (srvNode.has("env") && srvNode.get("env").isObject()) {
                            var envFields = srvNode.get("env").fields();
                            while (envFields.hasNext()) {
                                var ef = envFields.next();
                                env.put(ef.getKey(), ef.getValue().asText());
                            }
                        }

                        String type = srvNode.path("type").asText("stdio");
                        boolean disabled = srvNode.path("disabled").asBoolean(false);
                        long timeoutMs = srvNode.path("timeoutMs").asLong(10000L);

                        result.put(name, new McpServerConfig(name, command, args, env, type, disabled, timeoutMs));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to read MCP server configurations from {}: {}", filePath, e.getMessage());
            }
        }

        // 2. Merge with McpConfiguration if present
        if (mcpConfiguration != null && mcpConfiguration.getServers() != null) {
            for (var entry : mcpConfiguration.getServers().entrySet()) {
                String name = entry.getKey();
                if (!result.containsKey(name)) {
                    var sc = entry.getValue();
                    result.put(name, new McpServerConfig(
                            name,
                            sc.getCommand(),
                            sc.getArgs(),
                            sc.getEnv(),
                            sc.getType(),
                            false,
                            10000L
                    ));
                }
            }
        }

        return result;
    }

    public synchronized void connectStdioServer(McpServerConfig config) throws IOException {
        disconnectServer(config.name());

        McpStdioTransport transport = new McpStdioTransport(
                config.name(),
                config.command(),
                config.args(),
                config.env(),
                json,
                Duration.ofMillis(config.timeoutMs())
        );

        transport.start();
        activeTransports.put(config.name(), transport);
        initializeAndDiscover(config.name());
    }

    public synchronized void registerTransport(String serverName, McpTransport transport) {
        disconnectServer(serverName);
        activeTransports.put(serverName, transport);
    }

    public void initializeAndDiscover(String serverName) {
        McpTransport transport = activeTransports.get(serverName);
        if (transport == null || !transport.isConnected()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_NOT_CONNECTED",
                    "MCP server '" + serverName + "' is not connected");
        }

        try {
            // 1. Initialize
            Map<String, Object> initParams = Map.of(
                    "protocolVersion", "2024-11-05",
                    "capabilities", Map.of(),
                    "clientInfo", Map.of("name", "jarvis-mcp", "version", "1.0.0")
            );
            transport.sendRequest("initialize", initParams).get(10, TimeUnit.SECONDS);

            // 2. Initialized notification
            transport.sendNotification("notifications/initialized", Map.of());

            // 3. Discover tools
            JsonNode listResult = transport.sendRequest("tools/list", Map.of()).get(10, TimeUnit.SECONDS);
            List<McpApprovalService.ToolInfo> discovered = parseDiscoveredTools(serverName, listResult);
            serverTools.put(serverName, discovered);

            if (toolListener != null) {
                for (var tool : discovered) {
                    toolListener.accept(tool);
                }
            }
            log.info("Discovered {} tools from MCP server '{}'", discovered.size(), serverName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MCP_ERROR", "Initialization interrupted for " + serverName);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ApiException ae) {
                throw ae;
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MCP_ERROR",
                    "Failed to initialize server '" + serverName + "': " + cause.getMessage());
        } catch (TimeoutException e) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "MCP_TIMEOUT",
                    "Handshake timed out for server '" + serverName + "'");
        }
    }

    private List<McpApprovalService.ToolInfo> parseDiscoveredTools(String serverName, JsonNode listResult) {
        List<McpApprovalService.ToolInfo> tools = new ArrayList<>();
        if (listResult == null || !listResult.has("tools") || !listResult.get("tools").isArray()) {
            return tools;
        }

        for (JsonNode toolNode : listResult.get("tools")) {
            String name = toolNode.path("name").asText(null);
            if (name == null || name.isBlank()) {
                continue;
            }
            String description = toolNode.path("description").asText("");
            Map<String, Object> schema = Map.of();
            if (toolNode.has("inputSchema") && toolNode.get("inputSchema").isObject()) {
                schema = json.convertValue(toolNode.get("inputSchema"), new TypeReference<Map<String, Object>>() {});
            }

            boolean consequential = isConsequential(name, description);
            tools.add(new McpApprovalService.ToolInfo(name, description, schema, consequential, serverName));
        }

        return tools;
    }

    private boolean isConsequential(String name, String description) {
        return CONSEQUENTIAL_PATTERN.matcher(name).matches()
                || (description != null && CONSEQUENTIAL_PATTERN.matcher(description).matches());
    }

    public Object callTool(String serverName, String toolName, Map<String, Object> arguments) {
        McpTransport transport = activeTransports.get(serverName);
        if (transport == null || !transport.isConnected()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_NOT_CONNECTED",
                    "No MCP transport connected for server '" + serverName + "'");
        }

        Map<String, Object> params = Map.of(
                "name", toolName,
                "arguments", arguments != null ? arguments : Map.of()
        );

        try {
            JsonNode resultNode = transport.sendRequest("tools/call", params).get(15, TimeUnit.SECONDS);
            return extractToolResult(resultNode);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MCP_ERROR", "Execution interrupted for tool " + toolName);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ApiException ae) {
                throw ae;
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MCP_TOOL_ERROR",
                    "Tool execution failed: " + cause.getMessage());
        } catch (TimeoutException e) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "MCP_TIMEOUT",
                    "Execution timed out for tool '" + toolName + "'");
        }
    }

    private Object extractToolResult(JsonNode resultNode) {
        if (resultNode == null || resultNode.isNull()) {
            return null;
        }

        boolean isError = resultNode.path("isError").asBoolean(false);

        if (resultNode.has("content") && resultNode.get("content").isArray()) {
            List<String> items = new ArrayList<>();
            for (JsonNode item : resultNode.get("content")) {
                if (item.has("text")) {
                    items.add(item.get("text").asText());
                } else {
                    items.add(item.toString());
                }
            }
            String combined = String.join("\n", items);
            if (isError) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "MCP_TOOL_ERROR", combined);
            }
            return combined;
        }

        if (isError) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MCP_TOOL_ERROR", resultNode.toString());
        }

        if (resultNode.isTextual()) {
            return resultNode.asText();
        }

        return json.convertValue(resultNode, Object.class);
    }

    public boolean isConnected(String serverName) {
        if (serverName == null) return false;
        McpTransport transport = activeTransports.get(serverName);
        return transport != null && transport.isConnected();
    }

    public boolean hasActiveConnections() {
        return activeTransports.values().stream().anyMatch(McpTransport::isConnected);
    }

    public List<McpApprovalService.ToolInfo> getTools(String serverName) {
        return serverTools.getOrDefault(serverName, List.of());
    }

    public List<McpApprovalService.ToolInfo> getAllDiscoveredTools() {
        List<McpApprovalService.ToolInfo> all = new ArrayList<>();
        serverTools.values().forEach(all::addAll);
        return all;
    }

    public synchronized void disconnectServer(String serverName) {
        McpTransport transport = activeTransports.remove(serverName);
        if (transport != null) {
            try {
                transport.close();
            } catch (Exception e) {
                log.debug("Error closing transport for '{}': {}", serverName, e.getMessage());
            }
        }
        serverTools.remove(serverName);
    }

    public synchronized void reloadServers() {
        disconnectAll();
        loadAndConnectServers();
    }

    public synchronized void disconnectAll() {
        for (String serverName : new ArrayList<>(activeTransports.keySet())) {
            disconnectServer(serverName);
        }
    }

    public Map<String, Object> getServerStatuses() {
        Map<String, Object> statuses = new LinkedHashMap<>();
        for (Map.Entry<String, McpTransport> entry : activeTransports.entrySet()) {
            String name = entry.getKey();
            McpTransport transport = entry.getValue();
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("connected", transport.isConnected());
            List<McpApprovalService.ToolInfo> tools = serverTools.getOrDefault(name, List.of());
            s.put("toolsCount", tools.size());
            s.put("tools", tools.stream().map(McpApprovalService.ToolInfo::name).toList());
            statuses.put(name, s);
        }
        return statuses;
    }

    @PreDestroy
    @Override
    public void close() {
        disconnectAll();
    }
}

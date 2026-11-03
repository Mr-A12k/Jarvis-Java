package com.jarvis.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.ApiException;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Handles tool discovery, JSON schema checking, and human-in-the-loop approval
 * challenges for consequential actions. Stores audit events to data/mcp-audit.jsonl.
 */
@Service
public class McpApprovalService {

    private static final Logger log = LoggerFactory.getLogger(McpApprovalService.class);
    private static final Pattern CONSEQUENTIAL_PATTERN = Pattern.compile(
            ".*(delete|remove|exec|shell|terminal|drop|kill|destroy|write|update|modify|install|send|publish).*",
            Pattern.CASE_INSENSITIVE
    );

    public record ToolInfo(
            String name,
            String description,
            Map<String, Object> inputSchema,
            boolean consequential,
            String serverName
    ) {}

    public record ApprovalChallenge(
            String challengeId,
            String toolName,
            Map<String, Object> arguments,
            String status, // PENDING, APPROVED, REJECTED, EXPIRED
            Instant createdAt,
            Instant expiresAt
    ) {}

    public record ExecutionResult(
            boolean approvalRequired,
            String challengeId,
            String status,
            Object result,
            String message
    ) {}

    private final ObjectMapper json;
    private final Path auditFilePath;
    private final McpClientManager clientManager;
    private final Map<String, ToolInfo> registeredTools = new ConcurrentHashMap<>();
    private final Map<String, ApprovalChallenge> pendingChallenges = new ConcurrentHashMap<>();
    private volatile java.util.function.BiFunction<String, Map<String, Object>, Object> nativeToolExecutor;

    public void setNativeToolExecutor(java.util.function.BiFunction<String, Map<String, Object>, Object> executor) {
        this.nativeToolExecutor = executor;
    }

    public McpApprovalService(ObjectMapper json, String auditPath) {
        this(json, auditPath, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public McpApprovalService(
            ObjectMapper json,
            @Value("${jarvis.mcp.audit-path:data/mcp-audit.jsonl}") String auditPath,
            @org.springframework.beans.factory.annotation.Autowired(required = false) McpClientManager clientManager) {
        this.json = json;
        this.auditFilePath = Path.of(auditPath).toAbsolutePath().normalize();
        this.clientManager = clientManager;
        initDefaultTools();
        if (clientManager != null) {
            clientManager.setToolListener(this::registerTool);
            for (ToolInfo tool : clientManager.getAllDiscoveredTools()) {
                registerTool(tool);
            }
        }
    }

    private void initDefaultTools() {
        // Register standard built-in MCP tools
        registerTool(new ToolInfo(
                "read_resource",
                "Read contents of a safe local text resource",
                Map.of(
                        "type", "object",
                        "required", List.of("uri"),
                        "properties", Map.of("uri", Map.of("type", "string", "description", "URI of the resource"))
                ),
                false,
                "local-safe"
        ));

        registerTool(new ToolInfo(
                "execute_system_command",
                "Execute an arbitrary terminal or system shell command",
                Map.of(
                        "type", "object",
                        "required", List.of("command"),
                        "properties", Map.of(
                                "command", Map.of("type", "string", "description", "The shell command to run"),
                                "args", Map.of("type", "array", "description", "Command line arguments")
                        )
                ),
                true,
                "system-core"
        ));

        registerTool(new ToolInfo(
                "delete_resource",
                "Delete a specified resource or file permanently",
                Map.of(
                        "type", "object",
                        "required", List.of("targetId"),
                        "properties", Map.of("targetId", Map.of("type", "string", "description", "Resource ID or path to delete"))
                ),
                true,
                "system-core"
        ));
    }

    public synchronized void registerTool(ToolInfo tool) {
        registeredTools.put(tool.name(), tool);
        logAudit("TOOL_REGISTERED", tool.name(), Map.of("server", tool.serverName()), null, "SUCCESS", "Tool registered");
    }

    public List<ToolInfo> discoverTools() {
        logAudit("TOOL_DISCOVERY", null, null, null, "SUCCESS", "Discovered " + registeredTools.size() + " tools");
        return new ArrayList<>(registeredTools.values());
    }

    public boolean hasActiveTools() {
        return clientManager != null && clientManager.hasActiveConnections();
    }

    public List<ToolInfo> discoverActiveTools() {
        if (clientManager == null || !clientManager.hasActiveConnections()) {
            return Collections.emptyList();
        }
        return registeredTools.values().stream()
                .filter(tool -> clientManager.isConnected(tool.serverName()))
                .toList();
    }

    public Optional<ToolInfo> getTool(String name) {
        return Optional.ofNullable(registeredTools.get(name));
    }

    public boolean isConsequential(ToolInfo tool) {
        if (tool == null) return true;
        if (tool.consequential()) return true;
        return CONSEQUENTIAL_PATTERN.matcher(tool.name()).matches()
                || (tool.description() != null && CONSEQUENTIAL_PATTERN.matcher(tool.description()).matches());
    }

    /**
     * Validates arguments against the tool's JSON schema.
     */
    public void validateSchema(ToolInfo tool, Map<String, Object> arguments) {
        if (tool.inputSchema() == null || tool.inputSchema().isEmpty()) {
            return;
        }

        Map<String, Object> schema = tool.inputSchema();
        Map<String, Object> args = arguments != null ? arguments : Map.of();

        // 1. Validate required properties
        Object requiredObj = schema.get("required");
        if (requiredObj instanceof List<?> requiredList) {
            for (Object req : requiredList) {
                String reqField = String.valueOf(req);
                if (!args.containsKey(reqField) || args.get(reqField) == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "MCP_SCHEMA_VALIDATION",
                            "Missing required argument: " + reqField);
                }
            }
        }

        // 2. Validate property types if specified
        Object propertiesObj = schema.get("properties");
        if (propertiesObj instanceof Map<?, ?> propertiesMap) {
            for (Map.Entry<String, Object> entry : args.entrySet()) {
                String paramName = entry.getKey();
                Object paramVal = entry.getValue();

                Object propDefObj = propertiesMap.get(paramName);
                if (propDefObj instanceof Map<?, ?> propDef) {
                    Object expectedType = propDef.get("type");
                    if (expectedType instanceof String typeStr && paramVal != null) {
                        boolean valid = switch (typeStr.toLowerCase(Locale.ROOT)) {
                            case "string" -> paramVal instanceof CharSequence;
                            case "number" -> paramVal instanceof Number;
                            case "integer" -> paramVal instanceof Integer || paramVal instanceof Long || (paramVal instanceof Number n && n.doubleValue() == Math.floor(n.doubleValue()));
                            case "boolean" -> paramVal instanceof Boolean;
                            case "object" -> paramVal instanceof Map;
                            case "array" -> paramVal instanceof List;
                            default -> true;
                        };
                        if (!valid) {
                            throw new ApiException(HttpStatus.BAD_REQUEST, "MCP_SCHEMA_VALIDATION",
                                    "Argument '" + paramName + "' must be of type '" + typeStr + "'");
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles tool call request: checks schema, identifies consequential actions,
     * generates approval challenge if needed, or executes safe tools.
     */
    public ExecutionResult requestToolExecution(String toolName, Map<String, Object> arguments) {
        ToolInfo tool = registeredTools.get(toolName);
        if (tool == null) {
            logAudit("INVOKE_REQUESTED", toolName, arguments, null, "FAILED", "Tool not found");
            throw new ApiException(HttpStatus.NOT_FOUND, "MCP_TOOL_NOT_FOUND", "Tool not found: " + toolName);
        }

        validateSchema(tool, arguments);

        if (isConsequential(tool)) {
            String challengeId = UUID.randomUUID().toString();
            Instant now = Instant.now();
            ApprovalChallenge challenge = new ApprovalChallenge(
                    challengeId,
                    toolName,
                    arguments != null ? arguments : Map.of(),
                    "PENDING",
                    now,
                    now.plusSeconds(300) // 5 minutes TTL
            );
            pendingChallenges.put(challengeId, challenge);

            logAudit("APPROVAL_REQUIRED", toolName, arguments, challengeId, "PENDING", "Human approval challenge generated");
            return new ExecutionResult(true, challengeId, "PENDING", null, "Consequential action requires human approval");
        }

        // Safe tool execution
        logAudit("INVOKE_REQUESTED", toolName, arguments, null, "EXECUTING", "Direct execution permitted");
        Object result = executeInternal(tool, arguments);
        logAudit("TOOL_EXECUTED", toolName, arguments, null, "SUCCESS", "Executed successfully");
        return new ExecutionResult(false, null, "SUCCESS", result, "Tool executed successfully");
    }

    /**
     * Processes approval decision for a pending challenge.
     */
    public synchronized ExecutionResult processApproval(String challengeId, boolean approved) {
        ApprovalChallenge challenge = pendingChallenges.get(challengeId);
        if (challenge == null) {
            logAudit("APPROVAL_FAILED", null, null, challengeId, "ERROR", "Challenge not found");
            throw new ApiException(HttpStatus.NOT_FOUND, "MCP_CHALLENGE_NOT_FOUND", "Challenge not found: " + challengeId);
        }

        if (Instant.now().isAfter(challenge.expiresAt())) {
            pendingChallenges.remove(challengeId);
            logAudit("APPROVAL_EXPIRED", challenge.toolName(), challenge.arguments(), challengeId, "EXPIRED", "Challenge expired");
            throw new ApiException(HttpStatus.BAD_REQUEST, "MCP_CHALLENGE_EXPIRED", "Approval challenge has expired");
        }

        if (!approved) {
            pendingChallenges.remove(challengeId);
            logAudit("APPROVAL_REJECTED", challenge.toolName(), challenge.arguments(), challengeId, "REJECTED", "User rejected challenge");
            return new ExecutionResult(false, challengeId, "REJECTED", null, "Action rejected by user");
        }

        // User approved
        pendingChallenges.remove(challengeId);
        logAudit("APPROVAL_GRANTED", challenge.toolName(), challenge.arguments(), challengeId, "APPROVED", "User approved challenge");

        ToolInfo tool = registeredTools.get(challenge.toolName());
        Object result = executeInternal(tool, challenge.arguments());
        logAudit("TOOL_EXECUTED", challenge.toolName(), challenge.arguments(), challengeId, "SUCCESS", "Executed after approval");
        return new ExecutionResult(false, challengeId, "APPROVED", result, "Tool approved and executed");
    }

    public Optional<ApprovalChallenge> getChallenge(String challengeId) {
        return Optional.ofNullable(pendingChallenges.get(challengeId));
    }

    public List<ApprovalChallenge> getPendingChallenges() {
        Instant now = Instant.now();
        pendingChallenges.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
        return new ArrayList<>(pendingChallenges.values());
    }

    private Object executeInternal(ToolInfo tool, Map<String, Object> arguments) {
        if (tool == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MCP_TOOL_NOT_FOUND", "Tool not found");
        }
        if ("system-native".equals(tool.serverName()) && nativeToolExecutor != null) {
            try {
                Object result = nativeToolExecutor.apply(tool.name(), arguments);
                logAudit("TOOL_EXECUTED", tool.name(), arguments, null, "SUCCESS", "Executed native system tool");
                return result;
            } catch (ApiException e) {
                logAudit("TOOL_EXECUTION_FAILED", tool.name(), arguments, null, "ERROR", e.getMessage());
                throw e;
            } catch (Exception e) {
                logAudit("TOOL_EXECUTION_FAILED", tool.name(), arguments, null, "ERROR", e.getMessage());
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "NATIVE_TOOL_ERROR", "Native tool error: " + e.getMessage());
            }
        }
        if (clientManager != null && clientManager.isConnected(tool.serverName())) {
            try {
                Object result = clientManager.callTool(tool.serverName(), tool.name(), arguments);
                logAudit("TOOL_EXECUTED", tool.name(), arguments, null, "SUCCESS", "Executed on MCP server " + tool.serverName());
                return result;
            } catch (ApiException e) {
                logAudit("TOOL_EXECUTION_FAILED", tool.name(), arguments, null, "ERROR", e.getMessage());
                throw e;
            } catch (Exception e) {
                logAudit("TOOL_EXECUTION_FAILED", tool.name(), arguments, null, "ERROR", e.getMessage());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "MCP_EXECUTION_ERROR",
                        "Failed to execute tool on " + tool.serverName() + ": " + e.getMessage());
            }
        }
        logAudit("TOOL_UNAVAILABLE", tool.name(), null, null, "ERROR", "No MCP transport configured");
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_NOT_CONNECTED", "No MCP transport is connected. This tool has not executed.");
    }

    public synchronized void logAudit(String eventType, String toolName, Map<String, Object> arguments,
                                      String challengeId, String status, String message) {
        try {
            if (auditFilePath.getParent() != null && !Files.exists(auditFilePath.getParent())) {
                Files.createDirectories(auditFilePath.getParent());
            }

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("timestamp", Instant.now().toString());
            event.put("eventType", eventType);
            if (toolName != null) event.put("toolName", toolName);
            if (arguments != null) event.put("arguments", arguments);
            if (challengeId != null) event.put("challengeId", challengeId);
            event.put("status", status);
            event.put("message", message);

            String line = json.writeValueAsString(event) + "\n";
            Files.writeString(auditFilePath, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to write to MCP audit file: {}", e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> getAuditEvents(int limit) {
        if (!Files.exists(auditFilePath)) {
            return Collections.emptyList();
        }
        try {
            List<String> lines = Files.readAllLines(auditFilePath, StandardCharsets.UTF_8);
            int start = Math.max(0, lines.size() - limit);
            List<Map<String, Object>> events = new ArrayList<>();
            for (int i = lines.size() - 1; i >= start; i--) {
                String line = lines.get(i).trim();
                if (!line.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = json.readValue(line, Map.class);
                    events.add(map);
                }
            }
            return events;
        } catch (IOException e) {
            log.error("Failed to read MCP audit log: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}


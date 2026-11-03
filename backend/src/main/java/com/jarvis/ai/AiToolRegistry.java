package com.jarvis.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.ApiException;
import com.jarvis.database.DatabaseStore;
import com.jarvis.mcp.McpApprovalService;
import com.jarvis.web.ChromiumSearchService;
import com.jarvis.web.SearchProvider;
import com.jarvis.web.WebSearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * First-Class Unified AI Tool Registry with Mandatory User Permission Gating.
 * Exposes core tools (web search, docs, file editing, terminal, neural graph, database memories)
 * directly to the AI model in concise formats optimized for minimal-resource small models.
 * Enforces interactive human-in-the-loop approvals before executing consequential actions.
 */
@Component
public class AiToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(AiToolRegistry.class);

    public enum PermissionPolicy {
        ASK_ALL,             // Prompt user for every tool call
        ASK_CONSEQUENTIAL,   // Allow safe reads automatically, ask permission for writes/commands
        ALLOW_SESSION        // Auto-approve within active validated session
    }

    public record ToolDefinition(
            String name,
            String description,
            Map<String, String> parameterDoc,
            boolean consequential,
            BiFunction<Map<String, Object>, String, Object> handler
    ) {}

    public record ToolExecutionResult(
            boolean requiresApproval,
            String challengeId,
            String toolName,
            Map<String, Object> arguments,
            Object output,
            String message
    ) {}

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    private final McpApprovalService approvalService;
    private final ObjectMapper json;
    private volatile PermissionPolicy permissionPolicy = PermissionPolicy.ASK_CONSEQUENTIAL;

    // Optional lazy hooks for services wired after startup
    private volatile WebSearch webSearch;
    private volatile ChromiumSearchService chromiumSearchService;
    private volatile DatabaseStore databaseStore;

    @Autowired
    public AiToolRegistry(
            @Autowired(required = false) McpApprovalService approvalService,
            ObjectMapper json,
            @Autowired(required = false) WebSearch webSearch,
            @Autowired(required = false) ChromiumSearchService chromiumSearchService,
            @Autowired(required = false) DatabaseStore databaseStore) {
        this.approvalService = approvalService;
        this.json = json != null ? json : new ObjectMapper();
        this.webSearch = webSearch;
        this.chromiumSearchService = chromiumSearchService;
        this.databaseStore = databaseStore;

        if (this.approvalService != null) {
            this.approvalService.setNativeToolExecutor((name, args) -> {
                ToolDefinition def = tools.get(name);
                if (def != null) {
                    return def.handler().apply(args, null);
                }
                throw new ApiException(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", "Native tool not found: " + name);
            });
        }

        registerBuiltInTools();
    }

    public PermissionPolicy getPermissionPolicy() {
        return permissionPolicy;
    }

    public void setPermissionPolicy(PermissionPolicy policy) {
        if (policy != null) {
            this.permissionPolicy = policy;
            log.info("AiToolRegistry: Permission policy set to [{}]", policy);
        }
    }

    public void registerTool(ToolDefinition tool) {
        tools.put(tool.name(), tool);
        if (approvalService != null) {
            Map<String, Object> props = new LinkedHashMap<>();
            tool.parameterDoc().forEach((k, v) -> props.put(k, Map.of("type", "string", "description", v)));
            approvalService.registerTool(new McpApprovalService.ToolInfo(
                    tool.name(),
                    tool.description(),
                    Map.of(
                            "type", "object",
                            "properties", props
                    ),
                    tool.consequential(),
                    "system-native"
            ));
        }
        log.debug("AiToolRegistry: Registered tool [{}] (consequential: {})", tool.name(), tool.consequential());
    }

    public Optional<ToolDefinition> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<ToolDefinition> listTools() {
        return new ArrayList<>(tools.values());
    }

    private void registerBuiltInTools() {
        // 1. Live Web Search Tool
        registerTool(new ToolDefinition(
                "search_web",
                "Searches the live internet using the Chromium browser engine or DuckDuckGo HTTP.",
                Map.of("query", "The search terms or question to research", "provider", "Optional search engine (AUTO, CHROMIUM_GOOGLE, CHROMIUM_DUCKDUCKGO, CHROMIUM_BING, DUCKDUCKGO_HTTP)"),
                false,
                (args, sessionId) -> {
                    String query = (String) args.getOrDefault("query", "");
                    String provStr = (String) args.get("provider");
                    SearchProvider prov = provStr != null ? SearchProvider.fromString(provStr) : null;
                    if (webSearch != null) {
                        List<WebSearch.Source> sources = webSearch.search(query, prov).blockOptional(java.time.Duration.ofSeconds(15)).orElse(List.of());
                        if (sources.isEmpty()) return "No verified web search results found for: " + query;
                        StringBuilder sb = new StringBuilder("Web Search Results for '").append(query).append("':\n");
                        for (var s : sources) {
                            sb.append("- [").append(s.title()).append("](").append(s.url()).append("): ").append(s.snippet()).append("\n");
                        }
                        return sb.toString().strip();
                    }
                    return "Web search engine is not configured.";
                }
        ));

        // 2. Deep Technical Documentation Reader
        registerTool(new ToolDefinition(
                "read_docs",
                "Navigates to a specific URL and scrapes clean Markdown text and code blocks, stripping ads and headers.",
                Map.of("url", "Full HTTP/HTTPS URL of the documentation or webpage to read"),
                false,
                (args, sessionId) -> {
                    String url = (String) args.getOrDefault("url", "");
                    if (chromiumSearchService != null) {
                        var res = chromiumSearchService.fetchCleanMarkdown(url, 12000);
                        return "### Content from " + res.title() + " (" + url + "):\n\n" + res.markdownContent();
                    } else if (webSearch != null) {
                        String text = webSearch.fetchPageContent(url).blockOptional(java.time.Duration.ofSeconds(8)).orElse("");
                        return "### Content from " + url + ":\n\n" + text;
                    }
                    return "Web document scraper is not configured.";
                }
        ));

        // 3. Database Memory Query
        registerTool(new ToolDefinition(
                "read_database_memory",
                "Queries user-specific persistent memories and stored facts from the relational database.",
                Map.of("query", "Keywords or subject to find in memory"),
                false,
                (args, sessionId) -> {
                    String q = (String) args.getOrDefault("query", "");
                    if (databaseStore != null && databaseStore.configured()) {
                        var memories = databaseStore.list(DatabaseStore.Kind.memories, 10, 0);
                        StringBuilder sb = new StringBuilder();
                        for (var m : memories) {
                            String title = String.valueOf(m.get("title"));
                            String content = String.valueOf(m.get("content"));
                            if (q.isBlank() || title.toLowerCase().contains(q.toLowerCase()) || content.toLowerCase().contains(q.toLowerCase())) {
                                sb.append("- **").append(title).append("**: ").append(content).append("\n");
                            }
                        }
                        return sb.isEmpty() ? "No matching database memories found for: " + q : sb.toString().strip();
                    }
                    return "No persistent database memories configured.";
                }
        ));
    }

    /**
     * Checks if a tool call requires user permission.
     */
    public boolean requiresPermission(String toolName) {
        if (permissionPolicy == PermissionPolicy.ASK_ALL) {
            return true;
        }
        if (permissionPolicy == PermissionPolicy.ALLOW_SESSION) {
            return false;
        }
        ToolDefinition tool = tools.get(toolName);
        return tool != null && tool.consequential();
    }

    /**
     * Executes a tool with human-in-the-loop permission gating.
     */
    public ToolExecutionResult executeTool(String toolName, Map<String, Object> arguments, String sessionId) {
        ToolDefinition tool = tools.get(toolName);
        if (tool == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", "Unknown tool: " + toolName);
        }

        // Check if user approval is required
        if (requiresPermission(toolName)) {
            String challengeId = UUID.randomUUID().toString();
            if (approvalService != null) {
                var execution = approvalService.requestToolExecution(toolName, arguments);
                return new ToolExecutionResult(
                        true,
                        execution.challengeId(),
                        toolName,
                        arguments,
                        null,
                        "Permission required: Tool '" + toolName + "' requires user confirmation before execution."
                );
            }
            return new ToolExecutionResult(
                    true,
                    challengeId,
                    toolName,
                    arguments,
                    null,
                    "Permission required: Tool '" + toolName + "' requires user confirmation."
            );
        }

        // Direct execution permitted
        try {
            Object result = tool.handler().apply(arguments, sessionId);
            return new ToolExecutionResult(false, null, toolName, arguments, result, "Executed successfully");
        } catch (Exception e) {
            log.warn("AiToolRegistry: Execution failed for tool '{}': {}", toolName, e.getMessage());
            return new ToolExecutionResult(false, null, toolName, arguments, "Execution error: " + e.getMessage(), "Failed: " + e.getMessage());
        }
    }

    /**
     * Formats tools into a clean, concise prompt string for local models.
     */
    public String formatToolPrompt() {
        StringBuilder sb = new StringBuilder("\n### AVAILABLE NATIVE TOOLS:\n");
        sb.append("You can invoke tools when needed to gather facts, inspect files, or execute tests.\n\n");
        for (ToolDefinition t : tools.values()) {
            sb.append("- `").append(t.name()).append("`: ").append(t.description()).append("\n");
            if (!t.parameterDoc().isEmpty()) {
                sb.append("  Parameters: ");
                t.parameterDoc().forEach((k, v) -> sb.append(k).append(" (").append(v).append("), "));
                sb.setLength(sb.length() - 2);
                sb.append("\n");
            }
        }
        sb.append("\n**TOOL CALL SYNTAX:**\n");
        sb.append("To call a tool, output exactly:\n");
        sb.append("[TOOL_CALL: tool_name {\"param\": \"value\"}]\n\n");
        sb.append("NOTE: Consequential actions (editing files, executing commands) will automatically ask the user for permission.\n");
        return sb.toString();
    }
}

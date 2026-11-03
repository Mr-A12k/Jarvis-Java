package com.jarvis.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiToolRegistryTest {

    private AiToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AiToolRegistry(null, new ObjectMapper(), null, null, null);
    }

    @Test
    void testListBuiltInTools() {
        var tools = registry.listTools();
        assertThat(tools).isNotEmpty();
        assertThat(tools.stream().anyMatch(t -> t.name().equals("search_web"))).isTrue();
        assertThat(tools.stream().anyMatch(t -> t.name().equals("read_docs"))).isTrue();
    }

    @Test
    void testPermissionPolicyGating() {
        // Register custom safe tool and consequential tool
        registry.registerTool(new AiToolRegistry.ToolDefinition(
                "echo_safe", "Safe echo", Map.of("text", "Input text"), false,
                (args, sId) -> "Echo: " + args.get("text")
        ));

        registry.registerTool(new AiToolRegistry.ToolDefinition(
                "modify_action", "Consequential edit", Map.of("target", "Target"), true,
                (args, sId) -> "Modified"
        ));

        // Default ASK_CONSEQUENTIAL
        assertThat(registry.requiresPermission("echo_safe")).isFalse();
        assertThat(registry.requiresPermission("modify_action")).isTrue();

        // Safe tool executes directly
        var safeRes = registry.executeTool("echo_safe", Map.of("text", "hello"), "test-session");
        assertThat(safeRes.requiresApproval()).isFalse();
        assertThat(safeRes.output()).isEqualTo("Echo: hello");

        // Consequential tool prompts for permission
        var reqRes = registry.executeTool("modify_action", Map.of("target", "file.txt"), "test-session");
        assertThat(reqRes.requiresApproval()).isTrue();
        assertThat(reqRes.challengeId()).isNotNull();

        // Switch policy to ASK_ALL
        registry.setPermissionPolicy(AiToolRegistry.PermissionPolicy.ASK_ALL);
        assertThat(registry.requiresPermission("echo_safe")).isTrue();

        // Switch policy to ALLOW_SESSION
        registry.setPermissionPolicy(AiToolRegistry.PermissionPolicy.ALLOW_SESSION);
        assertThat(registry.requiresPermission("modify_action")).isFalse();
    }

    @Test
    void testFormatToolPrompt() {
        String prompt = registry.formatToolPrompt();
        assertThat(prompt).contains("AVAILABLE NATIVE TOOLS");
        assertThat(prompt).contains("search_web");
        assertThat(prompt).contains("[TOOL_CALL: tool_name");
    }

    @Test
    void testMcpApprovalServiceIntegration() {
        com.jarvis.mcp.McpApprovalService mcp = new com.jarvis.mcp.McpApprovalService(new ObjectMapper(), "target/test-audit.jsonl");
        AiToolRegistry reg = new AiToolRegistry(mcp, new ObjectMapper(), null, null, null);

        // Register a consequential tool
        reg.registerTool(new AiToolRegistry.ToolDefinition(
                "custom_dangerous_tool",
                "Dangerous action that writes data",
                Map.of("payload", "Data payload"),
                true,
                (args, sId) -> "Success: " + args.get("payload")
        ));

        // Request execution via McpApprovalService
        var result = mcp.requestToolExecution("custom_dangerous_tool", Map.of("payload", "test-val"));
        assertThat(result.approvalRequired()).isTrue();
        assertThat(result.challengeId()).isNotNull();

        // User approves the challenge
        var approveResult = mcp.processApproval(result.challengeId(), true);
        assertThat(approveResult.approvalRequired()).isFalse();
        assertThat(approveResult.status()).isEqualTo("APPROVED");
        assertThat(approveResult.result()).isEqualTo("Success: test-val");
    }
}

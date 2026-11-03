package com.jarvis.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpApprovalTest {

    private Path tempAuditFile;
    private ObjectMapper json;
    private McpApprovalService service;

    @BeforeEach
    void setUp() throws IOException {
        tempAuditFile = Files.createTempFile("mcp-audit-test", ".jsonl");
        json = new ObjectMapper();
        service = new McpApprovalService(json, tempAuditFile.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(tempAuditFile);
    }

    @Test
    void toolDiscoveryReturnsBuiltInTools() {
        List<McpApprovalService.ToolInfo> tools = service.discoverTools();
        assertThat(tools).isNotEmpty();
        assertThat(tools).anyMatch(t -> t.name().equals("read_resource") && !t.consequential());
        assertThat(tools).anyMatch(t -> t.name().equals("execute_system_command") && t.consequential());
        assertThat(tools).anyMatch(t -> t.name().equals("delete_resource") && t.consequential());
    }

    @Test
    void schemaValidationPassesForValidArguments() {
        McpApprovalService.ToolInfo tool = service.getTool("read_resource").orElseThrow();
        service.validateSchema(tool, Map.of("uri", "file:///test.txt"));
    }

    @Test
    void schemaValidationRejectsMissingRequiredArgument() {
        McpApprovalService.ToolInfo tool = service.getTool("read_resource").orElseThrow();
        assertThatThrownBy(() -> service.validateSchema(tool, Map.of()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiEx = (ApiException) e;
                    assertThat(apiEx.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.code()).isEqualTo("MCP_SCHEMA_VALIDATION");
                    assertThat(apiEx.getMessage()).contains("Missing required argument: uri");
                });
    }

    @Test
    void schemaValidationRejectsInvalidType() {
        McpApprovalService.ToolInfo tool = service.getTool("read_resource").orElseThrow();
        assertThatThrownBy(() -> service.validateSchema(tool, Map.of("uri", 12345)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiEx = (ApiException) e;
                    assertThat(apiEx.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.code()).isEqualTo("MCP_SCHEMA_VALIDATION");
                    assertThat(apiEx.getMessage()).contains("Argument 'uri' must be of type 'string'");
                });
    }

    @Test
    void disconnectedToolNeverReportsSuccess() {
        assertThatThrownBy(() -> service.requestToolExecution("read_resource", Map.of("uri", "memo.txt")))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("MCP_NOT_CONNECTED"));
    }

    @Test
    void consequentialToolGeneratesApprovalChallenge() {
        McpApprovalService.ExecutionResult result = service.requestToolExecution(
                "execute_system_command",
                Map.of("command", "ls -la")
        );

        assertThat(result.approvalRequired()).isTrue();
        assertThat(result.challengeId()).isNotNull();
        assertThat(result.status()).isEqualTo("PENDING");

        // Verify challenge is stored
        var challengeOpt = service.getChallenge(result.challengeId());
        assertThat(challengeOpt).isPresent();
        assertThat(challengeOpt.get().toolName()).isEqualTo("execute_system_command");
    }

    @Test
    void approvalExecutionFlow() {
        // 1. Generate challenge
        McpApprovalService.ExecutionResult challenge = service.requestToolExecution(
                "delete_resource",
                Map.of("targetId", "item-123")
        );
        assertThat(challenge.approvalRequired()).isTrue();

        // 2. Approve
        assertThatThrownBy(() -> service.processApproval(challenge.challengeId(), true))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("MCP_NOT_CONNECTED"));

        // 3. Challenge should now be removed/consumed
        assertThat(service.getChallenge(challenge.challengeId())).isEmpty();
    }

    @Test
    void getPendingChallengesReturnsActiveChallenges() {
        assertThat(service.getPendingChallenges()).isEmpty();
        McpApprovalService.ExecutionResult challenge = service.requestToolExecution(
                "delete_resource",
                Map.of("targetId", "item-999")
        );
        assertThat(challenge.approvalRequired()).isTrue();
        List<McpApprovalService.ApprovalChallenge> list = service.getPendingChallenges();
        assertThat(list).hasSize(1);
        assertThat(list.getFirst().challengeId()).isEqualTo(challenge.challengeId());
        assertThat(list.getFirst().toolName()).isEqualTo("delete_resource");
    }

    @Test
    void rejectionFlow() {
        McpApprovalService.ExecutionResult challenge = service.requestToolExecution(
                "delete_resource",
                Map.of("targetId", "item-123")
        );

        McpApprovalService.ExecutionResult rejectionResult = service.processApproval(challenge.challengeId(), false);
        assertThat(rejectionResult.status()).isEqualTo("REJECTED");
        assertThat(rejectionResult.result()).isNull();
        assertThat(service.getChallenge(challenge.challengeId())).isEmpty();
    }

    @Test
    void invalidChallengeThrowsNotFound() {
        assertThatThrownBy(() -> service.processApproval("non-existent-id", true))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiEx = (ApiException) e;
                    assertThat(apiEx.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.code()).isEqualTo("MCP_CHALLENGE_NOT_FOUND");
                });
    }

    @Test
    void auditLogPersistsEvents() throws IOException {
        assertThatThrownBy(() -> service.requestToolExecution("read_resource", Map.of("uri", "data.txt"))).isInstanceOf(ApiException.class);

        List<String> lines = Files.readAllLines(tempAuditFile);
        assertThat(lines).isNotEmpty();

        List<Map<String, Object>> auditEvents = service.getAuditEvents(10);
        assertThat(auditEvents).isNotEmpty();
        assertThat(auditEvents).anyMatch(e -> "read_resource".equals(e.get("toolName")));
    }
}


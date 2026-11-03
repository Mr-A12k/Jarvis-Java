package com.jarvis.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.AiProvider;
import com.jarvis.chat.ChatRequest;
import com.jarvis.chat.ChatService;
import com.jarvis.chat.PromptFactory;
import com.jarvis.config.JarvisProperties;
import com.jarvis.system.SystemAppLauncher;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class McpChatToolLoopTest {

    private Path tempAudit;
    private ObjectMapper json;
    private McpApprovalService approvalService;

    @BeforeEach
    void setUp() throws IOException {
        tempAudit = Files.createTempFile("mcp-chat-audit", ".jsonl");
        json = new ObjectMapper();
        approvalService = new McpApprovalService(json, tempAudit.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(tempAudit);
    }

    static JarvisProperties settings() {
        return new JarvisProperties("test-token-012345678901234567890123456", URI.create("http://127.0.0.1:11434"),
                "qwen3:1.7b", 2048, 384, 1, Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(1), "30s");
    }

    static class MultiTurnFakeProvider implements AiProvider {
        private final Queue<Flux<String>> responses = new ArrayDeque<>();
        public Prompt lastPrompt;

        public void enqueue(String... chunks) {
            responses.add(Flux.fromArray(chunks));
        }

        @Override
        public Flux<String> stream(Prompt prompt) {
            this.lastPrompt = prompt;
            Flux<String> next = responses.poll();
            return next != null ? next : Flux.just("Default answer");
        }

        @Override
        public Mono<ModelHealth> health() {
            return Mono.just(new ModelHealth("ready", "fake", "ok"));
        }
    }

    @Test
    void promptFactoryFormatsAvailableTools() {
        PromptFactory factory = new PromptFactory(settings());
        List<McpApprovalService.ToolInfo> tools = List.of(
                new McpApprovalService.ToolInfo(
                        "get_weather",
                        "Look up weather report",
                        Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string")), "required", List.of("city")),
                        false,
                        "weather-srv"
                )
        );

        Prompt prompt = factory.build(new ChatRequest("What is the weather?", null), "", tools);
        String systemText = prompt.getInstructions().getFirst().getText();
        assertThat(systemText).contains("Available MCP tools:");
        assertThat(systemText).contains("get_weather: Look up weather report");
        assertThat(systemText).contains("Parameters:");
        assertThat(systemText).contains("[TOOL_CALL: tool_name {\"arg\": \"val\"}]");
    }

    @Test
    void directToolCallInvokesSafeTool() {
        // Register a fake transport
        McpClientManager mockManager = new McpClientManager(json) {
            @Override
            public boolean isConnected(String serverName) {
                return true;
            }

            @Override
            public Object callTool(String serverName, String toolName, Map<String, Object> arguments) {
                return "Current temperature in Chennai is 31°C";
            }
        };

        McpApprovalService service = new McpApprovalService(json, tempAudit.toString(), mockManager);
        service.registerTool(new McpApprovalService.ToolInfo(
                "weather_now",
                "Check weather",
                Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string"))),
                false,
                "weather-srv"
        ));

        ChatService chatService = new ChatService(
                new MultiTurnFakeProvider(),
                new PromptFactory(settings()),
                settings(),
                new SystemAppLauncher(false),
                null,
                service,
                json
        );

        StepVerifier.create(chatService.stream(new ChatRequest("/tool weather_now {\"city\": \"Chennai\"}", null)))
                .expectNextMatches(reply -> reply.contains("Tool 'weather_now' executed:") && reply.contains("Chennai is 31°C"))
                .verifyComplete();
    }

    @Test
    void directToolCallRequiresApprovalForConsequentialAction() {
        McpClientManager mockManager = new McpClientManager(json) {
            @Override
            public boolean isConnected(String serverName) {
                return true;
            }

            @Override
            public Object callTool(String serverName, String toolName, Map<String, Object> arguments) {
                return "Table dropped";
            }
        };

        McpApprovalService service = new McpApprovalService(json, tempAudit.toString(), mockManager);
        service.registerTool(new McpApprovalService.ToolInfo(
                "drop_table",
                "Drop a database table",
                Map.of("type", "object", "properties", Map.of("table", Map.of("type", "string"))),
                true,
                "db-srv"
        ));

        ChatService chatService = new ChatService(
                new MultiTurnFakeProvider(),
                new PromptFactory(settings()),
                settings(),
                new SystemAppLauncher(false),
                null,
                service,
                json
        );

        StepVerifier.create(chatService.stream(new ChatRequest("/tool drop_table {\"table\": \"users\"}", null)))
                .expectNextMatches(reply -> reply.contains("Action requires human approval") && reply.contains("challengeId:"))
                .verifyComplete();

        // Ensure challenge was recorded in approval service
        assertThat(service.getPendingChallenges()).hasSize(1);
        assertThat(service.getPendingChallenges().getFirst().toolName()).isEqualTo("drop_table");
    }

    @Test
    void chatToolLoopExecutesToolAndSynthesizesAnswer() {
        McpClientManager mockManager = new McpClientManager(json) {
            @Override
            public boolean isConnected(String serverName) {
                return true;
            }

            @Override
            public Object callTool(String serverName, String toolName, Map<String, Object> arguments) {
                return "Battery: 88%, Status: Charging";
            }
        };

        McpApprovalService service = new McpApprovalService(json, tempAudit.toString(), mockManager);
        service.registerTool(new McpApprovalService.ToolInfo(
                "get_battery",
                "Get system battery stats",
                Map.of("type", "object"),
                false,
                "hw-srv"
        ));

        MultiTurnFakeProvider provider = new MultiTurnFakeProvider();
        // Turn 1: Model decides to call the tool
        provider.enqueue("[TOOL_CALL: get_battery {}]");
        // Turn 2: Model uses tool observation to respond
        provider.enqueue("Your battery is currently at 88% and charging.");

        ChatService chatService = new ChatService(
                provider,
                new PromptFactory(settings()),
                settings(),
                new SystemAppLauncher(false),
                null,
                service,
                json
        );

        StepVerifier.create(chatService.complete(new ChatRequest("How is my battery?", null)))
                .expectNext("Your battery is currently at 88% and charging.")
                .verifyComplete();

        // Verify the follow-up prompt contained the tool observation
        assertThat(provider.lastPrompt.getInstructions().stream()
                .anyMatch(msg -> msg.getText().contains("Tool 'get_battery' observation: Battery: 88%"))).isTrue();
    }

    @Test
    void chatToolLoopHaltsForConsequentialActionRequiringApproval() {
        McpClientManager mockManager = new McpClientManager(json) {
            @Override
            public boolean isConnected(String serverName) {
                return true;
            }

            @Override
            public Object callTool(String serverName, String toolName, Map<String, Object> arguments) {
                return "Resource deleted";
            }
        };

        McpApprovalService service = new McpApprovalService(json, tempAudit.toString(), mockManager);
        service.registerTool(new McpApprovalService.ToolInfo(
                "delete_file",
                "Delete file permanently",
                Map.of("type", "object", "properties", Map.of("path", Map.of("type", "string"))),
                true,
                "fs-srv"
        ));

        MultiTurnFakeProvider provider = new MultiTurnFakeProvider();
        provider.enqueue("[TOOL_CALL: delete_file {\"path\": \"temp.txt\"}]");

        ChatService chatService = new ChatService(
                provider,
                new PromptFactory(settings()),
                settings(),
                new SystemAppLauncher(false),
                null,
                service,
                json
        );

        StepVerifier.create(chatService.complete(new ChatRequest("Delete temp.txt", null)))
                .expectNextMatches(reply -> reply.contains("Action requires human approval") && reply.contains("challengeId:"))
                .verifyComplete();

        assertThat(service.getPendingChallenges()).hasSize(1);
    }
}

package com.jarvis.mcp;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/mcp")
public class McpController {

    private final McpApprovalService approvalService;
    private final McpClientManager clientManager;

    public McpController(McpApprovalService approvalService) {
        this(approvalService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public McpController(McpApprovalService approvalService,
                         @org.springframework.beans.factory.annotation.Autowired(required = false) McpClientManager clientManager) {
        this.approvalService = approvalService;
        this.clientManager = clientManager;
    }

    public record ApprovalRequest(
            @NotBlank String challengeId,
            boolean approved
    ) {}

    public record CallToolRequest(
            @NotBlank String toolName,
            @NotNull Map<String, Object> arguments
    ) {}

    private static <T> Mono<T> io(Callable<T> work) {
        return Mono.fromCallable(work).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/tools")
    public Mono<List<McpApprovalService.ToolInfo>> getTools() {
        return io(approvalService::discoverTools);
    }

    @GetMapping("/challenges")
    public Mono<List<McpApprovalService.ApprovalChallenge>> getChallenges() {
        return io(approvalService::getPendingChallenges);
    }

    @PostMapping("/call")
    public Mono<McpApprovalService.ExecutionResult> callTool(@Valid @RequestBody CallToolRequest request) {
        return io(() -> approvalService.requestToolExecution(request.toolName(), request.arguments()));
    }

    @PostMapping("/approve")
    public Mono<McpApprovalService.ExecutionResult> approve(@Valid @RequestBody ApprovalRequest request) {
        return io(() -> approvalService.processApproval(request.challengeId(), request.approved()));
    }

    @GetMapping("/audit")
    public Mono<List<Map<String, Object>>> getAudit(@RequestParam(defaultValue = "100") int limit) {
        return io(() -> approvalService.getAuditEvents(limit));
    }

    @GetMapping("/servers")
    public Mono<Map<String, Object>> getServers() {
        return io(() -> clientManager != null ? clientManager.getServerStatuses() : Map.of());
    }

    @PostMapping("/servers/reload")
    public Mono<Map<String, Object>> reloadServers() {
        return io(() -> {
            if (clientManager != null) {
                clientManager.reloadServers();
                return clientManager.getServerStatuses();
            }
            return Map.of();
        });
    }
}

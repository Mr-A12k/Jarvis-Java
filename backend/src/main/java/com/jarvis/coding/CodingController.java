package com.jarvis.coding;

import com.jarvis.common.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * REST and SSE Controller for the Autonomous Coding Agent & Developer Studio.
 */
@RestController
@RequestMapping("/api/v1/coding")
public class CodingController {

    private final CodingAgentService agentService;
    private final CodingWorkspaceService workspaceService;
    private final CodingExecutionService executionService;

    @Autowired
    public CodingController(
            CodingAgentService agentService,
            CodingWorkspaceService workspaceService,
            CodingExecutionService executionService) {
        this.agentService = agentService;
        this.workspaceService = workspaceService;
        this.executionService = executionService;
    }

    public record StartSessionRequest(String objective, String model, String searchProvider) {}
    public record EditFileRequest(String path, String target, String replacement) {}
    public record WriteFileRequest(String path, String content, boolean overwrite) {}
    public record CommandRequest(String command, String subDir, Integer timeoutSeconds) {}

    @PostMapping("/session")
    public CodingDatabaseStore.SessionRecord startSession(@RequestBody StartSessionRequest request) {
        if (request == null || request.objective() == null || request.objective().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Objective cannot be empty");
        }
        return agentService.startSession(request.objective(), request.model(), request.searchProvider());
    }

    @GetMapping("/sessions")
    public List<CodingDatabaseStore.SessionRecord> listSessions() {
        return agentService.listSessions();
    }

    @GetMapping("/session/{id}")
    public CodingDatabaseStore.SessionRecord getSession(@PathVariable String id) {
        return agentService.getSession(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Session not found: " + id));
    }

    @GetMapping(value = "/session/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<CodingAgentService.AgentStreamEvent>> streamSession(@PathVariable String id) {
        return agentService.streamSession(id)
                .map(event -> ServerSentEvent.<CodingAgentService.AgentStreamEvent>builder()
                        .event(event.eventType())
                        .data(event)
                        .build());
    }

    @PostMapping("/session/{id}/cancel")
    public Map<String, Object> cancelSession(@PathVariable String id) {
        boolean ok = agentService.cancelSession(id);
        return Map.of("sessionId", id, "cancelled", ok);
    }

    @PostMapping("/session/{id}/resume")
    public Map<String, Object> resumeSession(@PathVariable String id) {
        boolean ok = agentService.resumeSession(id);
        return Map.of("sessionId", id, "resumed", ok);
    }

    @PostMapping("/session/{id}/rollback")
    public Map<String, Object> rollbackSession(@PathVariable String id) {
        boolean ok = agentService.rollbackSession(id);
        return Map.of("sessionId", id, "rolledBack", ok);
    }

    @GetMapping("/workspace/tree")
    public CodingWorkspaceService.FileNode getWorkspaceTree(
            @RequestParam(defaultValue = "") String path,
            @RequestParam(defaultValue = "3") int depth) {
        return workspaceService.getWorkspaceTree(path, depth);
    }

    @GetMapping("/workspace/file")
    public CodingWorkspaceService.FileReadResult readFile(
            @RequestParam String path,
            @RequestParam(required = false) Integer startLine,
            @RequestParam(required = false) Integer endLine) {
        return workspaceService.readFile(path, startLine, endLine);
    }

    @PostMapping("/workspace/edit")
    public CodingWorkspaceService.FileWriteResult editFile(@RequestBody EditFileRequest req) {
        return workspaceService.editFile(req.path(), req.target(), req.replacement());
    }

    @PostMapping("/workspace/file")
    public CodingWorkspaceService.FileWriteResult writeFile(@RequestBody WriteFileRequest req) {
        return workspaceService.writeFile(req.path(), req.content(), req.overwrite());
    }

    @PostMapping("/workspace/execute")
    public CodingExecutionService.CommandResult executeCommand(@RequestBody CommandRequest req) {
        int timeout = req.timeoutSeconds() != null ? req.timeoutSeconds() : 60;
        return executionService.execute(req.command(), req.subDir(), timeout, null);
    }
}

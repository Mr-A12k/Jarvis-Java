package com.jarvis.system;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/desktop")
public class DesktopController {

    private final DesktopSystemService desktopService;
    private final DesktopGuiAgentService agentService;

    public DesktopController(DesktopSystemService desktopService) {
        this(desktopService, null);
    }

    @Autowired
    public DesktopController(DesktopSystemService desktopService,
                             @Autowired(required = false) DesktopGuiAgentService agentService) {
        this.desktopService = desktopService;
        this.agentService = agentService;
    }

    public record MediaRequest(String action) {}
    public record VolumeRequest(String action, Integer level, Integer steps) {}
    public record LaunchRequest(@NotBlank String target) {}
    public record CommandRequest(@NotBlank String text, String lang) {}
    public record CommandResponse(boolean handled, String reply, String action) {}

    public record TargetRequest(String target) {}
    public record SequenceRequest(String workflowId, String workflowName, List<DesktopGuiAgentService.DesktopGuiAction> actions) {}
    public record WorkflowSaveRequest(String name, String description, String triggerPattern,
                                      List<DesktopGuiAgentService.DesktopGuiAction> actions, Boolean isFavorite) {}

    // =========================================================================
    // Media & Volume & Launch
    // =========================================================================

    @PostMapping("/media")
    public Mono<DesktopSystemService.DesktopActionResponse> controlMedia(@RequestBody MediaRequest request) {
        return Mono.fromCallable(() -> {
            String act = request != null && request.action() != null
                    ? request.action().toLowerCase().trim()
                    : "play_pause";
            return switch (act) {
                case "next" -> desktopService.nextTrack();
                case "prev", "previous" -> desktopService.previousTrack();
                case "stop" -> desktopService.stopMedia();
                case "play_pause", "play", "pause", "toggle" -> desktopService.playPause();
                default -> desktopService.playPause();
            };
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/volume")
    public Mono<DesktopSystemService.VolumeStatus> getVolume() {
        return Mono.fromCallable(desktopService::getVolumeStatus)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/volume")
    public Mono<DesktopSystemService.VolumeStatus> controlVolume(@RequestBody VolumeRequest request) {
        return Mono.fromCallable(() -> {
            String act = request != null && request.action() != null
                    ? request.action().toLowerCase().trim()
                    : "up";
            return switch (act) {
                case "up" -> desktopService.volumeUp(request.steps() != null ? request.steps() : 2);
                case "down" -> desktopService.volumeDown(request.steps() != null ? request.steps() : 2);
                case "mute" -> desktopService.toggleMute();
                case "unmute" -> desktopService.setMute(false);
                case "set" -> desktopService.setVolume(request.level() != null ? request.level() : 50);
                default -> desktopService.getVolumeStatus();
            };
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/apps")
    public Mono<List<DesktopSystemService.AppDefinition>> getApps() {
        return Mono.fromCallable(desktopService::getRegisteredApps)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/launch")
    public Mono<DesktopSystemService.DesktopActionResponse> launchApp(@Valid @RequestBody LaunchRequest request) {
        return Mono.fromCallable(() -> desktopService.launchApp(request.target()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/command")
    public Mono<CommandResponse> executeCommand(@Valid @RequestBody CommandRequest request) {
        return Mono.fromCallable(() -> {
            boolean isTamil = request.lang() != null && request.lang().toLowerCase().startsWith("ta");
            var result = desktopService.handleVoiceCommand(request.text(), isTamil);
            if (result.isPresent()) {
                return new CommandResponse(true, result.get(), "desktop_action");
            }
            return new CommandResponse(false, "", "none");
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // =========================================================================
    // Full Desktop GUI Agent Automation APIs
    // =========================================================================

    @PostMapping("/agent/action")
    public Mono<DesktopGuiAgentService.DesktopGuiResult> executeGuiAction(@RequestBody DesktopGuiAgentService.DesktopGuiAction action) {
        return Mono.fromCallable(() -> agentService != null
                ? agentService.executeAction(action)
                : new DesktopGuiAgentService.DesktopGuiResult(false, "ERROR", "Desktop GUI Agent Service not available", 0, Map.of())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/agent/sequence")
    public Mono<DesktopGuiAgentService.DesktopGuiResult> executeGuiSequence(@RequestBody SequenceRequest request) {
        return Mono.fromCallable(() -> agentService != null
                ? agentService.executeSequence(
                        request.workflowId(),
                        request.workflowName() != null ? request.workflowName() : "Custom Sequence",
                        request.actions() != null ? request.actions() : List.of()
                )
                : new DesktopGuiAgentService.DesktopGuiResult(false, "ERROR", "Desktop GUI Agent Service not available", 0, Map.of())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/agent/cursor")
    public Mono<DesktopGuiAgentService.CursorPositionInfo> getCursorInfo() {
        return Mono.fromCallable(() -> agentService != null
                ? agentService.getCursorAndScreen()
                : new DesktopGuiAgentService.CursorPositionInfo(0, 0, 1920, 1080)
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/agent/windows")
    public Mono<List<DesktopGuiAgentService.ActiveWindowInfo>> getActiveWindows() {
        return Mono.fromCallable(() -> agentService != null
                ? agentService.listActiveWindows()
                : List.<DesktopGuiAgentService.ActiveWindowInfo>of()
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/agent/focus")
    public Mono<DesktopGuiAgentService.DesktopGuiResult> focusWindow(@RequestBody TargetRequest request) {
        return Mono.fromCallable(() -> agentService != null
                ? agentService.focusWindow(request != null ? request.target() : "")
                : new DesktopGuiAgentService.DesktopGuiResult(false, "FOCUS_WINDOW", "Agent not available", 0, Map.of())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/agent/close")
    public Mono<DesktopGuiAgentService.DesktopGuiResult> closeWindow(@RequestBody TargetRequest request) {
        return Mono.fromCallable(() -> agentService != null
                ? agentService.closeWindow(request != null ? request.target() : "")
                : new DesktopGuiAgentService.DesktopGuiResult(false, "CLOSE_WINDOW", "Agent not available", 0, Map.of())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/agent/show-desktop")
    public Mono<DesktopGuiAgentService.DesktopGuiResult> showDesktop() {
        return Mono.fromCallable(() -> agentService != null
                ? agentService.showDesktop()
                : new DesktopGuiAgentService.DesktopGuiResult(false, "SHOW_DESKTOP", "Agent not available", 0, Map.of())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/agent/workflows")
    public Mono<List<DesktopGuiAgentService.DesktopWorkflow>> getWorkflows() {
        return Mono.fromCallable(() -> agentService != null
                ? agentService.listWorkflows()
                : List.<DesktopGuiAgentService.DesktopWorkflow>of()
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/agent/workflows")
    public Mono<DesktopGuiAgentService.DesktopWorkflow> saveWorkflow(@RequestBody WorkflowSaveRequest request) {
        return Mono.fromCallable(() -> agentService != null
                ? agentService.saveWorkflow(
                        request.name() != null ? request.name() : "Workflow " + System.currentTimeMillis(),
                        request.description(),
                        request.triggerPattern(),
                        request.actions() != null ? request.actions() : List.of(),
                        request.isFavorite() != null ? request.isFavorite() : false
                )
                : null
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/agent/workflows/{id}")
    public Mono<Map<String, Object>> deleteWorkflow(@PathVariable("id") UUID id) {
        return Mono.fromCallable(() -> {
            boolean ok = agentService != null && agentService.deleteWorkflow(id);
            return Map.<String, Object>of("success", ok, "id", id.toString());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/agent/audit")
    public Mono<List<DesktopGuiAgentService.DesktopAuditEntry>> getAuditLogs(@RequestParam(value = "limit", defaultValue = "25") int limit) {
        return Mono.fromCallable(() -> agentService != null
                ? agentService.getRecentAudit(limit)
                : List.<DesktopGuiAgentService.DesktopAuditEntry>of()
        ).subscribeOn(Schedulers.boundedElastic());
    }
}

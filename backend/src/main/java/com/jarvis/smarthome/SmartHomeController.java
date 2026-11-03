package com.jarvis.smarthome;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API Controller for Smart Home & IoT Automation.
 * Exposes device controls, scene triggering, integration configs, and voice command execution.
 */
@RestController
@RequestMapping("/api/v1/smarthome")
public class SmartHomeController {

    private final SmartHomeService service;

    @Autowired
    public SmartHomeController(SmartHomeService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(service.getStatus());
    }

    @GetMapping("/devices")
    public ResponseEntity<List<SmartDevice>> listDevices() {
        return ResponseEntity.ok(service.getStore().listDevices());
    }

    @GetMapping("/devices/{entityId}")
    public ResponseEntity<SmartDevice> getDevice(@PathVariable String entityId) {
        return service.getStore().findDevice(entityId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record DeviceActionRequest(String action, Map<String, Object> params) {}

    @PostMapping("/devices/{entityId}/action")
    public ResponseEntity<SmartDevice> executeAction(
            @PathVariable String entityId,
            @RequestBody DeviceActionRequest request
    ) {
        try {
            SmartDevice updated = service.executeDeviceAction(
                    entityId,
                    request.action(),
                    request.params() != null ? request.params() : Map.of()
            );
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/scenes")
    public ResponseEntity<List<SmartScene>> listScenes() {
        return ResponseEntity.ok(service.getStore().listScenes());
    }

    @PostMapping("/scenes/{sceneId}/activate")
    public ResponseEntity<Map<String, Object>> activateScene(@PathVariable String sceneId) {
        try {
            return ResponseEntity.ok(service.activateScene(sceneId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/config")
    public ResponseEntity<SmartHomeConfig> getConfig() {
        return ResponseEntity.ok(service.getConfig());
    }

    @PostMapping("/config")
    public ResponseEntity<SmartHomeConfig> updateConfig(@RequestBody SmartHomeConfig newConfig) {
        service.updateConfig(newConfig);
        return ResponseEntity.ok(service.getConfig());
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncEntities() {
        int count = service.syncFromHomeAssistant();
        return ResponseEntity.ok(Map.of("syncedCount", count, "status", "success"));
    }

    @GetMapping("/events")
    public ResponseEntity<List<SmartHomeEvent>> getEvents() {
        return ResponseEntity.ok(service.getStore().getRecentEvents());
    }

    public record SmartHomeCommandRequest(String command, Boolean isTamil) {}
    public record SmartHomeCommandResult(boolean matched, String response, String message) {}

    @PostMapping("/command")
    public ResponseEntity<SmartHomeCommandResult> executeVoiceCommand(@RequestBody SmartHomeCommandRequest request) {
        if (request == null || request.command() == null || request.command().isBlank()) {
            return ResponseEntity.badRequest().body(new SmartHomeCommandResult(false, null, "Command text is required"));
        }
        boolean isTamil = Boolean.TRUE.equals(request.isTamil());
        Optional<String> response = service.handleVoiceCommand(request.command(), isTamil);

        return response
                .map(s -> ResponseEntity.ok(new SmartHomeCommandResult(true, s, null)))
                .orElseGet(() -> ResponseEntity.ok(new SmartHomeCommandResult(false, null, "Query not recognized as a smart home command.")));
    }
}

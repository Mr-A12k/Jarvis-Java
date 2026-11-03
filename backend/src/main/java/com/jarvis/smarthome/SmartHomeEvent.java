package com.jarvis.smarthome;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Audit log and live telemetry event representing a smart home action or state update.
 */
public record SmartHomeEvent(
        String id,
        String timestamp,
        String source,    // "VOICE", "REST_API", "HOME_ASSISTANT", "MQTT", "SCENE"
        String action,    // "TURN_ON", "TURN_OFF", "SET_SCENE", "SET_BRIGHTNESS", etc.
        String target,    // Entity ID or scene ID
        String message,
        boolean success,
        Map<String, Object> details
) {
    public static SmartHomeEvent of(String source, String action, String target, String message, boolean success, Map<String, Object> details) {
        return new SmartHomeEvent(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                source,
                action,
                target,
                message,
                success,
                details != null ? details : Map.of()
        );
    }
}

package com.jarvis.smarthome;

import java.util.List;

/**
 * Represents an aggregated smart home scene or macro (e.g. Movie mode, Work mode, Night mode).
 */
public record SmartScene(
        String id,
        String name,
        String icon,
        String description,
        List<SceneAction> actions,
        boolean active
) {
    public record SceneAction(
            String deviceId,
            String action, // "turn_on", "turn_off", "set_brightness", "set_color", "set_temperature"
            Object value
    ) {}
}

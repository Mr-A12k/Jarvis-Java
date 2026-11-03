package com.jarvis.smarthome;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a smart IoT device (light, switch/power strip, climate, sensor, etc.).
 * Supports Home Assistant entity IDs (e.g. light.room_lighting, switch.desk_power_strip)
 * and MQTT topic conventions.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SmartDevice(
        String id,
        String entityId,
        String name,
        DeviceType type,
        String room,
        String state,                  // "on", "off", "cool", "heat", "idle"
        Protocol protocol,             // HOME_ASSISTANT, MQTT, SIMULATED
        Map<String, Object> attributes, // brightness: 0-100, color, power_watts, temp
        boolean active,
        String lastUpdated
) {
    public enum DeviceType {
        LIGHT, SWITCH, CLIMATE, SENSOR, SCENE, OTHER
    }

    public enum Protocol {
        HOME_ASSISTANT, MQTT, SIMULATED
    }

    public SmartDevice withState(String newState, Map<String, Object> newAttributes) {
        Map<String, Object> merged = new HashMap<>(this.attributes != null ? this.attributes : Map.of());
        if (newAttributes != null) {
            merged.putAll(newAttributes);
        }
        return new SmartDevice(
                this.id,
                this.entityId,
                this.name,
                this.type,
                this.room,
                newState,
                this.protocol,
                merged,
                this.active,
                Instant.now().toString()
        );
    }
}

package com.jarvis.smarthome;

/**
 * Configuration for Smart Home & IoT connectivity.
 * Supports Home Assistant REST/WebSocket API and local MQTT brokers (Mosquitto/EMQX).
 */
public record SmartHomeConfig(
        boolean haEnabled,
        String haBaseUrl,
        String haToken,
        boolean mqttEnabled,
        String mqttBrokerUrl,
        String mqttClientId,
        String mqttUsername,
        String mqttPassword,
        String mqttBaseTopic,
        boolean simulationMode
) {
    public static SmartHomeConfig defaultConfig() {
        return new SmartHomeConfig(
                false,
                "http://homeassistant.local:8123",
                "",
                false,
                "tcp://localhost:1883",
                "jarvis-ai-gateway",
                "",
                "",
                "homeassistant",
                true // Default to simulated mode so all voice commands work immediately out of the box
        );
    }
}

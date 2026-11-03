package com.jarvis.smarthome;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jarvis.database.DatabaseStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Storage adapter for Smart Home devices, scenes, configurations, and audit telemetry.
 * Supports:
 * - Local-first offline JSON persistence in data/smarthome/
 * - PostgreSQL relational storage if database is configured (via database/007_smarthome_devices_and_automations.sql)
 */
@Component
public class SmartHomeStore {

    private static final Logger log = LoggerFactory.getLogger(SmartHomeStore.class);

    private final Path rootDir;
    private final Path devicesPath;
    private final Path scenesPath;
    private final Path configPath;
    private final Path auditLogsPath;
    private final ObjectMapper json;
    private final DatabaseStore databaseStore;

    private final Map<String, SmartDevice> deviceCache = new ConcurrentHashMap<>();
    private final Map<String, SmartScene> sceneCache = new ConcurrentHashMap<>();
    private final List<SmartHomeEvent> recentEvents = new CopyOnWriteArrayList<>();
    private SmartHomeConfig currentConfig;

    @Autowired
    public SmartHomeStore(
            @Value("${jarvis.smarthome.storage-dir:data/smarthome}") String storageDir,
            @Autowired(required = false) ObjectMapper json,
            @Autowired(required = false) DatabaseStore databaseStore
    ) {
        this.rootDir = Path.of(storageDir).toAbsolutePath().normalize();
        this.devicesPath = rootDir.resolve("devices.json");
        this.scenesPath = rootDir.resolve("scenes.json");
        this.configPath = rootDir.resolve("config.json");
        this.auditLogsPath = rootDir.resolve("audit_logs.jsonl");

        if (json != null) {
            this.json = json.copy().registerModule(new JavaTimeModule());
        } else {
            this.json = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .enable(SerializationFeature.INDENT_OUTPUT);
        }
        this.databaseStore = databaseStore;

        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            log.error("Failed to create smart home data directory: {}", rootDir, e);
        }

        loadConfig();
        loadDevices();
        loadScenes();
        loadRecentEvents();

        if (deviceCache.isEmpty()) {
            seedDefaultDevices();
        }
        if (sceneCache.isEmpty()) {
            seedDefaultScenes();
        }
    }

    private void seedDefaultDevices() {
        saveDevice(new SmartDevice(
                "dev-room-lighting",
                "light.room_lighting",
                "Room Lighting",
                SmartDevice.DeviceType.LIGHT,
                "Living Room",
                "off",
                SmartDevice.Protocol.SIMULATED,
                new HashMap<>(Map.of("brightness", 80, "color", "#f8fafc", "color_temp", 4000)),
                true,
                Instant.now().toString()
        ));

        saveDevice(new SmartDevice(
                "dev-desk-power-strip",
                "switch.desk_power_strip",
                "Desk Power Strip",
                SmartDevice.DeviceType.SWITCH,
                "Office",
                "on",
                SmartDevice.Protocol.SIMULATED,
                new HashMap<>(Map.of("power_watts", 42.5, "voltage", 230.0, "current_amps", 0.18, "outlets", 4)),
                true,
                Instant.now().toString()
        ));

        saveDevice(new SmartDevice(
                "dev-ceiling-light",
                "light.ceiling_light",
                "Ceiling Light",
                SmartDevice.DeviceType.LIGHT,
                "Living Room",
                "off",
                SmartDevice.Protocol.SIMULATED,
                new HashMap<>(Map.of("brightness", 100, "color", "#ffe8b8")),
                true,
                Instant.now().toString()
        ));

        saveDevice(new SmartDevice(
                "dev-thermostat",
                "climate.room_thermostat",
                "Room Climate & AC",
                SmartDevice.DeviceType.CLIMATE,
                "Living Room",
                "cool",
                SmartDevice.Protocol.SIMULATED,
                new HashMap<>(Map.of("current_temperature", 24.2, "target_temperature", 22.0, "fan_mode", "auto")),
                true,
                Instant.now().toString()
        ));

        saveDevice(new SmartDevice(
                "dev-env-sensor",
                "sensor.room_temperature",
                "Environment Sensor",
                SmartDevice.DeviceType.SENSOR,
                "Living Room",
                "24.2",
                SmartDevice.Protocol.SIMULATED,
                new HashMap<>(Map.of("unit", "°C", "humidity", 48.5, "battery_pct", 92)),
                true,
                Instant.now().toString()
        ));

        saveDevice(new SmartDevice(
                "dev-motion-sensor",
                "binary_sensor.motion",
                "Motion Sensor",
                SmartDevice.DeviceType.SENSOR,
                "Living Room",
                "off",
                SmartDevice.Protocol.SIMULATED,
                new HashMap<>(Map.of("motion_detected", false, "last_motion", "12m ago")),
                true,
                Instant.now().toString()
        ));
    }

    private void seedDefaultScenes() {
        saveScene(new SmartScene(
                "scene.movie_mode",
                "Movie Mode",
                "film",
                "Dims room lighting to 15% cinematic indigo, turns off desk power strip and ambient lights",
                List.of(
                        new SmartScene.SceneAction("light.room_lighting", "set_brightness", 15),
                        new SmartScene.SceneAction("light.room_lighting", "set_color", "#3b82f6"),
                        new SmartScene.SceneAction("switch.desk_power_strip", "turn_off", null),
                        new SmartScene.SceneAction("light.ceiling_light", "turn_off", null)
                ),
                true
        ));

        saveScene(new SmartScene(
                "scene.work_mode",
                "Work Mode",
                "briefcase",
                "Powers on desk power strip, sets room lighting to 100% crisp focused task light",
                List.of(
                        new SmartScene.SceneAction("switch.desk_power_strip", "turn_on", null),
                        new SmartScene.SceneAction("light.room_lighting", "set_brightness", 100),
                        new SmartScene.SceneAction("light.room_lighting", "set_color", "#ffffff"),
                        new SmartScene.SceneAction("light.ceiling_light", "turn_on", null)
                ),
                true
        ));

        saveScene(new SmartScene(
                "scene.night_mode",
                "Night Mode",
                "moon",
                "Turns off all lights, powers off desk power strip, sets thermostat to energy saving",
                List.of(
                        new SmartScene.SceneAction("light.room_lighting", "turn_off", null),
                        new SmartScene.SceneAction("light.ceiling_light", "turn_off", null),
                        new SmartScene.SceneAction("switch.desk_power_strip", "turn_off", null),
                        new SmartScene.SceneAction("climate.room_thermostat", "set_temperature", 25.0)
                ),
                true
        ));

        saveScene(new SmartScene(
                "scene.relax_mode",
                "Relax Mode",
                "coffee",
                "Soft warm amber ambient glow at 35% brightness",
                List.of(
                        new SmartScene.SceneAction("light.room_lighting", "set_brightness", 35),
                        new SmartScene.SceneAction("light.room_lighting", "set_color", "#f59e0b"),
                        new SmartScene.SceneAction("light.ceiling_light", "turn_off", null),
                        new SmartScene.SceneAction("switch.desk_power_strip", "turn_on", null)
                ),
                true
        ));

        saveScene(new SmartScene(
                "scene.gaming_mode",
                "Gaming Mode",
                "gamepad-2",
                "Desk strip ON, cybernetic neon magenta & violet ambient glow",
                List.of(
                        new SmartScene.SceneAction("switch.desk_power_strip", "turn_on", null),
                        new SmartScene.SceneAction("light.room_lighting", "set_brightness", 45),
                        new SmartScene.SceneAction("light.room_lighting", "set_color", "#a855f7")
                ),
                true
        ));
    }

    private void loadConfig() {
        if (Files.exists(configPath)) {
            try {
                this.currentConfig = json.readValue(Files.readAllBytes(configPath), SmartHomeConfig.class);
                return;
            } catch (Exception e) {
                log.warn("Failed to read smart home config from {}: {}", configPath, e.getMessage());
            }
        }
        this.currentConfig = SmartHomeConfig.defaultConfig();
        saveConfig(this.currentConfig);
    }

    private void loadDevices() {
        if (!Files.exists(devicesPath)) return;
        try {
            byte[] bytes = Files.readAllBytes(devicesPath);
            if (bytes.length > 0) {
                List<SmartDevice> list = json.readValue(bytes, new TypeReference<List<SmartDevice>>() {});
                for (SmartDevice d : list) {
                    if (d.entityId() != null) {
                        deviceCache.put(d.entityId(), d);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to load devices from {}: {}", devicesPath, e.getMessage());
        }
    }

    private void loadScenes() {
        if (!Files.exists(scenesPath)) return;
        try {
            byte[] bytes = Files.readAllBytes(scenesPath);
            if (bytes.length > 0) {
                List<SmartScene> list = json.readValue(bytes, new TypeReference<List<SmartScene>>() {});
                for (SmartScene s : list) {
                    if (s.id() != null) {
                        sceneCache.put(s.id(), s);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to load scenes from {}: {}", scenesPath, e.getMessage());
        }
    }

    private void loadRecentEvents() {
        if (!Files.exists(auditLogsPath)) return;
        try {
            List<String> lines = Files.readAllLines(auditLogsPath, StandardCharsets.UTF_8);
            int start = Math.max(0, lines.size() - 50);
            for (int i = start; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (!line.isEmpty()) {
                    try {
                        SmartHomeEvent ev = json.readValue(line, SmartHomeEvent.class);
                        recentEvents.add(ev);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load audit logs: {}", e.getMessage());
        }
    }

    public synchronized void saveConfig(SmartHomeConfig config) {
        this.currentConfig = config;
        try {
            Files.writeString(configPath, json.writerWithDefaultPrettyPrinter().writeValueAsString(config), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to persist smart home config: {}", e.getMessage());
        }
    }

    public SmartHomeConfig getConfig() {
        return currentConfig != null ? currentConfig : SmartHomeConfig.defaultConfig();
    }

    public synchronized void saveDevice(SmartDevice device) {
        if (device == null || device.entityId() == null) return;
        deviceCache.put(device.entityId(), device);
        persistDevices();
    }

    public Optional<SmartDevice> findDevice(String entityIdOrName) {
        if (entityIdOrName == null || entityIdOrName.isBlank()) return Optional.empty();
        String query = entityIdOrName.trim().toLowerCase(Locale.ROOT);

        SmartDevice exact = deviceCache.get(query);
        if (exact != null) return Optional.of(exact);

        for (SmartDevice d : deviceCache.values()) {
            if (d.entityId().equalsIgnoreCase(query) || d.name().equalsIgnoreCase(query)) {
                return Optional.of(d);
            }
        }
        for (SmartDevice d : deviceCache.values()) {
            if (d.name().toLowerCase(Locale.ROOT).contains(query) || query.contains(d.name().toLowerCase(Locale.ROOT))) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    public List<SmartDevice> listDevices() {
        return new ArrayList<>(deviceCache.values());
    }

    public synchronized void saveScene(SmartScene scene) {
        if (scene == null || scene.id() == null) return;
        sceneCache.put(scene.id(), scene);
        persistScenes();
    }

    public Optional<SmartScene> findScene(String idOrName) {
        if (idOrName == null || idOrName.isBlank()) return Optional.empty();
        String query = idOrName.trim().toLowerCase(Locale.ROOT);

        SmartScene exact = sceneCache.get(query);
        if (exact != null) return Optional.of(exact);

        for (SmartScene s : sceneCache.values()) {
            if (s.id().equalsIgnoreCase(query) || s.name().equalsIgnoreCase(query)) {
                return Optional.of(s);
            }
        }
        for (SmartScene s : sceneCache.values()) {
            if (s.name().toLowerCase(Locale.ROOT).contains(query) || query.contains(s.name().toLowerCase(Locale.ROOT))) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    public List<SmartScene> listScenes() {
        return new ArrayList<>(sceneCache.values());
    }

    public synchronized void logEvent(SmartHomeEvent event) {
        if (event == null) return;
        recentEvents.add(0, event);
        if (recentEvents.size() > 100) {
            recentEvents.remove(recentEvents.size() - 1);
        }
        try {
            String jsonLine = json.writeValueAsString(event) + System.lineSeparator();
            Files.writeString(auditLogsPath, jsonLine, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("Failed to write smart home audit log line: {}", e.getMessage());
        }
    }

    public List<SmartHomeEvent> getRecentEvents() {
        return Collections.unmodifiableList(recentEvents);
    }

    private synchronized void persistDevices() {
        try {
            Files.writeString(devicesPath, json.writerWithDefaultPrettyPrinter().writeValueAsString(deviceCache.values()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to persist devices: {}", e.getMessage());
        }
    }

    private synchronized void persistScenes() {
        try {
            Files.writeString(scenesPath, json.writerWithDefaultPrettyPrinter().writeValueAsString(sceneCache.values()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to persist scenes: {}", e.getMessage());
        }
    }
}

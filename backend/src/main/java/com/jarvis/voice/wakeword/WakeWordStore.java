package com.jarvis.voice.wakeword;

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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Storage and Real-time Dispatcher for Background Wake-Word Events.
 * Supports Dual-Mode Operation:
 * - Mode A: PostgreSQL / pgAdmin functions (when database is configured)
 * - Mode B: Local-first JSON persistence in data/wakeword/config.json & events.json
 */
@Component
public class WakeWordStore {

    private static final Logger log = LoggerFactory.getLogger(WakeWordStore.class);

    private final Path configPath;
    private final Path eventsPath;
    private final ObjectMapper json;
    private final DatabaseStore databaseStore;

    private final AtomicReference<WakeWordConfig> cachedConfig = new AtomicReference<>();
    private final List<WakeWordEvent> cachedEvents = new CopyOnWriteArrayList<>();
    private final Sinks.Many<WakeWordEvent> eventSink = Sinks.many().multicast().onBackpressureBuffer(128, false);

    @Autowired
    public WakeWordStore(
            @Value("${jarvis.wakeword.storage-dir:data/wakeword}") String storageDir,
            @Autowired(required = false) ObjectMapper mapper,
            @Autowired(required = false) DatabaseStore databaseStore
    ) {
        Path baseDir = Path.of(storageDir).toAbsolutePath().normalize();
        this.configPath = baseDir.resolve("config.json");
        this.eventsPath = baseDir.resolve("events.json");

        if (mapper != null) {
            this.json = mapper.copy().registerModule(new JavaTimeModule());
        } else {
            this.json = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .enable(SerializationFeature.INDENT_OUTPUT);
        }
        this.databaseStore = databaseStore;

        loadLocalState();
    }

    private String toJson(Object obj) {
        try {
            return json.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void loadLocalState() {
        try {
            if (Files.exists(configPath)) {
                byte[] bytes = Files.readAllBytes(configPath);
                if (bytes.length > 0) {
                    cachedConfig.set(json.readValue(bytes, WakeWordConfig.class));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read local wake-word config from {}: {}", configPath, e.getMessage());
        }

        if (cachedConfig.get() == null) {
            cachedConfig.set(WakeWordConfig.createDefault());
            saveLocalConfig(cachedConfig.get());
        }

        try {
            if (Files.exists(eventsPath)) {
                byte[] bytes = Files.readAllBytes(eventsPath);
                if (bytes.length > 0) {
                    List<WakeWordEvent> list = json.readValue(bytes, new TypeReference<List<WakeWordEvent>>() {});
                    cachedEvents.addAll(list);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read local wake-word events from {}: {}", eventsPath, e.getMessage());
        }
    }

    private synchronized void saveLocalConfig(WakeWordConfig config) {
        try {
            Files.createDirectories(configPath.getParent());
            byte[] bytes = json.writerWithDefaultPrettyPrinter().writeValueAsBytes(config);
            Files.write(configPath, bytes);
        } catch (IOException e) {
            log.warn("Unable to save local wake-word config: {}", e.getMessage());
        }
    }

    private synchronized void saveLocalEvents() {
        try {
            Files.createDirectories(eventsPath.getParent());
            // Keep recent 500 events locally
            int size = cachedEvents.size();
            List<WakeWordEvent> toPersist = size > 500 ? cachedEvents.subList(size - 500, size) : cachedEvents;
            byte[] bytes = json.writerWithDefaultPrettyPrinter().writeValueAsBytes(toPersist);
            Files.write(eventsPath, bytes);
        } catch (IOException e) {
            log.warn("Unable to save local wake-word events: {}", e.getMessage());
        }
    }

    public WakeWordConfig getConfig() {
        if (databaseStore != null && databaseStore.configured()) {
            try {
                return databaseStore.execute(c -> {
                    try (PreparedStatement s = c.prepareStatement("SELECT * FROM jarvis.wakeword_config_get()")) {
                        try (ResultSet r = s.executeQuery()) {
                            if (r.next()) {
                                WakeWordConfig cfg = new WakeWordConfig(
                                        r.getBoolean("enabled"),
                                        r.getString("engine"),
                                        r.getString("model_name"),
                                        r.getDouble("sensitivity"),
                                        r.getDouble("vad_threshold"),
                                        r.getBoolean("auto_popup"),
                                        r.getBoolean("chime_sound"),
                                        r.getString("audio_device_name"),
                                        r.getBoolean("bring_to_front"),
                                        r.getBoolean("start_with_windows"),
                                        Map.of(),
                                        r.getTimestamp("updated_at") != null ? r.getTimestamp("updated_at").toInstant() : Instant.now()
                                );
                                cachedConfig.set(cfg);
                                return cfg;
                            }
                        }
                    }
                    return cachedConfig.get();
                });
            } catch (Exception e) {
                log.debug("Falling back to cached wake-word config (DB call failed: {})", e.getMessage());
            }
        }
        return cachedConfig.get();
    }

    public WakeWordConfig saveConfig(WakeWordConfig newConfig) {
        WakeWordConfig sanitized = new WakeWordConfig(
                newConfig.enabled(),
                newConfig.engine() != null && !newConfig.engine().isBlank() ? newConfig.engine() : "openwakeword",
                newConfig.modelName() != null && !newConfig.modelName().isBlank() ? newConfig.modelName() : "hey_jarvis",
                Math.max(0.05, Math.min(0.99, newConfig.sensitivity() > 0 ? newConfig.sensitivity() : 0.50)),
                Math.max(0.05, Math.min(0.99, newConfig.vadThreshold() > 0 ? newConfig.vadThreshold() : 0.50)),
                newConfig.autoPopup(),
                newConfig.chimeSound(),
                newConfig.audioDeviceName() != null ? newConfig.audioDeviceName() : "Default System Input",
                newConfig.bringToFront(),
                newConfig.startWithWindows(),
                newConfig.metadata() != null ? newConfig.metadata() : Map.of(),
                Instant.now()
        );

        cachedConfig.set(sanitized);
        saveLocalConfig(sanitized);

        if (databaseStore != null && databaseStore.configured()) {
            try {
                databaseStore.execute(c -> {
                    try (PreparedStatement s = c.prepareStatement(
                            "SELECT * FROM jarvis.wakeword_config_upsert(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)")) {
                        s.setBoolean(1, sanitized.enabled());
                        s.setString(2, sanitized.engine());
                        s.setString(3, sanitized.modelName());
                        s.setDouble(4, sanitized.sensitivity());
                        s.setDouble(5, sanitized.vadThreshold());
                        s.setBoolean(6, sanitized.autoPopup());
                        s.setBoolean(7, sanitized.chimeSound());
                        s.setString(8, sanitized.audioDeviceName());
                        s.setBoolean(9, sanitized.bringToFront());
                        s.setBoolean(10, sanitized.startWithWindows());
                        s.setString(11, toJson(sanitized.metadata()));
                        s.execute();
                    }
                    return null;
                });
            } catch (Exception e) {
                log.warn("Database upsert failed for wake-word config; preserved in local cache: {}", e.getMessage());
            }
        }

        return sanitized;
    }

    public WakeWordEvent logEvent(WakeWordEvent event) {
        cachedEvents.add(event);
        saveLocalEvents();

        // Broadcast immediately to reactive SSE stream listeners
        eventSink.tryEmitNext(event);

        if (databaseStore != null && databaseStore.configured()) {
            try {
                databaseStore.execute(c -> {
                    try (PreparedStatement s = c.prepareStatement(
                            "SELECT * FROM jarvis.wakeword_event_log(?, ?, ?, ?, ?, ?, ?::jsonb)")) {
                        s.setString(1, event.wakeWord());
                        s.setDouble(2, event.confidence());
                        s.setString(3, event.engine());
                        s.setInt(4, event.audioDurationMs());
                        s.setString(5, event.foregroundApp());
                        s.setString(6, event.actionTaken());
                        s.setString(7, toJson(event.metadata()));
                        s.execute();
                    }
                    return null;
                });
            } catch (Exception e) {
                log.debug("Database log failed for wake event; preserved in memory/local: {}", e.getMessage());
            }
        }

        return event;
    }

    public List<WakeWordEvent> listEvents(int limit, int offset) {
        int safeLimit = Math.max(1, Math.min(200, limit));
        int safeOffset = Math.max(0, offset);

        if (databaseStore != null && databaseStore.configured()) {
            try {
                return databaseStore.execute(c -> {
                    try (PreparedStatement s = c.prepareStatement("SELECT * FROM jarvis.wakeword_event_read(?, ?)")) {
                        s.setInt(1, safeLimit);
                        s.setInt(2, safeOffset);
                        try (ResultSet r = s.executeQuery()) {
                            List<WakeWordEvent> list = new ArrayList<>();
                            while (r.next()) {
                                list.add(new WakeWordEvent(
                                        r.getObject("id", UUID.class),
                                        r.getString("wake_word"),
                                        r.getDouble("confidence"),
                                        r.getString("engine"),
                                        r.getInt("audio_duration_ms"),
                                        r.getString("foreground_app"),
                                        r.getString("action_taken"),
                                        Map.of(),
                                        r.getTimestamp("created_at") != null ? r.getTimestamp("created_at").toInstant() : Instant.now()
                                ));
                            }
                            return list;
                        }
                    }
                });
            } catch (Exception e) {
                log.debug("Failed querying wake events from DB: {}. Reading from local cache.", e.getMessage());
            }
        }

        // Return from cached list sorted descending
        List<WakeWordEvent> reversed = new ArrayList<>(cachedEvents);
        Collections.reverse(reversed);

        if (safeOffset >= reversed.size()) return List.of();
        int toIndex = Math.min(safeOffset + safeLimit, reversed.size());
        return reversed.subList(safeOffset, toIndex);
    }

    public Map<String, Object> getStats() {
        if (databaseStore != null && databaseStore.configured()) {
            try {
                return databaseStore.execute(c -> {
                    try (PreparedStatement s = c.prepareStatement("SELECT * FROM jarvis.wakeword_stats_get()")) {
                        try (ResultSet r = s.executeQuery()) {
                            if (r.next()) {
                                Map<String, Object> stats = new HashMap<>();
                                stats.put("totalWakes", r.getLong("total_wakes"));
                                stats.put("wakesToday", r.getLong("wakes_today"));
                                stats.put("averageConfidence", r.getDouble("average_confidence"));
                                stats.put("primaryEngine", r.getString("primary_engine"));
                                Timestamp ts = r.getTimestamp("last_wake_at");
                                stats.put("lastWakeAt", ts != null ? ts.toInstant().toString() : null);
                                stats.put("activeEngine", cachedConfig.get().engine());
                                stats.put("serviceStatus", "online");
                                return stats;
                            }
                        }
                    }
                    return null;
                });
            } catch (Exception e) {
                log.debug("Database stats query failed: {}", e.getMessage());
            }
        }

        // Fallback calculation from memory/cache
        long total = cachedEvents.size();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        long todayCount = cachedEvents.stream()
                .filter(ev -> ev.createdAt().atZone(ZoneId.systemDefault()).toLocalDate().equals(today))
                .count();
        double avgConf = cachedEvents.stream()
                .mapToDouble(WakeWordEvent::confidence)
                .average()
                .orElse(0.0);
        Instant lastWake = cachedEvents.isEmpty() ? null : cachedEvents.get(cachedEvents.size() - 1).createdAt();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalWakes", total);
        stats.put("wakesToday", todayCount);
        stats.put("averageConfidence", Math.round(avgConf * 100.0) / 100.0);
        stats.put("primaryEngine", cachedConfig.get().engine());
        stats.put("lastWakeAt", lastWake != null ? lastWake.toString() : null);
        stats.put("activeEngine", cachedConfig.get().engine());
        stats.put("serviceStatus", "online");
        return stats;
    }

    public Flux<WakeWordEvent> streamEvents() {
        return eventSink.asFlux();
    }
}

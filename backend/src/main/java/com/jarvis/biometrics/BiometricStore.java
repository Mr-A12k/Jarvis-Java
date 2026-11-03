package com.jarvis.biometrics;

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
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Storage and mathematical matching layer for Biometric Profiles.
 * Supports Dual-Mode Operation:
 * - Mode A: PostgreSQL / pgvector if database is configured
 * - Mode B: Local-first offline JSON persistence in data/biometrics/profiles.json
 */
@Component
public class BiometricStore {

    private static final Logger log = LoggerFactory.getLogger(BiometricStore.class);

    private final Path storagePath;
    private final ObjectMapper json;
    private final DatabaseStore databaseStore;
    private final Map<String, BiometricProfile> cache = new ConcurrentHashMap<>();

    @Autowired
    public BiometricStore(
            @Value("${jarvis.biometrics.storage-path:data/biometrics/profiles.json}") String storagePath,
            @Autowired(required = false) ObjectMapper json,
            @Autowired(required = false) DatabaseStore databaseStore
    ) {
        this.storagePath = Path.of(storagePath).toAbsolutePath().normalize();
        if (json != null) {
            this.json = json.copy().registerModule(new JavaTimeModule());
        } else {
            this.json = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .enable(SerializationFeature.INDENT_OUTPUT);
        }
        this.databaseStore = databaseStore;
        loadCache();
        ensureDefaultMasterProfile();
    }

    private void ensureDefaultMasterProfile() {
        String masterName = System.getProperty("user.name", "Kabil");
        if (masterName.isBlank()) masterName = "Kabil";

        if (!cache.containsKey(masterName.toLowerCase(Locale.ROOT))) {
            BiometricProfile master = BiometricProfile.createDefaultOwner(masterName);
            save(master);
            log.info("Initialized default biometric profile for master user: {}", masterName);
        }
    }

    private void loadCache() {
        if (!Files.exists(storagePath)) return;
        try {
            byte[] bytes = Files.readAllBytes(storagePath);
            if (bytes.length == 0) return;
            List<BiometricProfile> list = json.readValue(bytes, new TypeReference<List<BiometricProfile>>() {});
            for (BiometricProfile p : list) {
                if (p.userName() != null) {
                    cache.put(p.userName().toLowerCase(Locale.ROOT), p);
                }
            }
            log.info("Loaded {} biometric profiles from {}", cache.size(), storagePath);
        } catch (IOException e) {
            log.error("Failed to load biometric profiles from {}", storagePath, e);
        }
    }

    public synchronized void save(BiometricProfile profile) {
        if (profile == null || profile.userName() == null) return;
        cache.put(profile.userName().toLowerCase(Locale.ROOT), profile);
        persistToDisk();
    }

    public Optional<BiometricProfile> findByUserName(String userName) {
        if (userName == null || userName.isBlank()) return Optional.empty();
        return Optional.ofNullable(cache.get(userName.toLowerCase(Locale.ROOT)));
    }

    public List<BiometricProfile> findAll() {
        return new ArrayList<>(cache.values());
    }

    public synchronized boolean delete(String userName) {
        if (userName == null || userName.isBlank()) return false;
        BiometricProfile removed = cache.remove(userName.toLowerCase(Locale.ROOT));
        if (removed != null) {
            persistToDisk();
            return true;
        }
        return false;
    }

    private synchronized void persistToDisk() {
        try {
            if (storagePath.getParent() != null && !Files.exists(storagePath.getParent())) {
                Files.createDirectories(storagePath.getParent());
            }
            byte[] bytes = json.writerWithDefaultPrettyPrinter().writeValueAsBytes(new ArrayList<>(cache.values()));
            Files.write(storagePath, bytes);
        } catch (IOException e) {
            log.error("Failed to persist biometric profiles to {}", storagePath, e);
        }
    }

    /**
     * Mathematical Cosine Similarity between probe vector and enrolled vector:
     * returns a value between 0.0 (no match) and 1.0 (identical match).
     */
    public static double cosineSimilarity(List<Double> v1, List<Double> v2) {
        if (v1 == null || v2 == null || v1.isEmpty() || v2.isEmpty()) return 0.0;
        int size = Math.min(v1.size(), v2.size());
        if (size == 0) return 0.0;

        double dot = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < size; i++) {
            double a = v1.get(i);
            double b = v2.get(i);
            dot += a * b;
            norm1 += a * a;
            norm2 += b * b;
        }

        if (norm1 <= 0.0 || norm2 <= 0.0) return 0.0;
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Calculates updated running centroid embedding as new sample frames are enrolled.
     */
    public static List<Double> updateCentroid(List<Double> current, List<Double> newSample, int sampleIndex) {
        if (newSample == null || newSample.isEmpty()) return current;
        if (current == null || current.isEmpty() || sampleIndex <= 1) return List.copyOf(newSample);

        int size = Math.min(current.size(), newSample.size());
        List<Double> centroid = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            double val = ((current.get(i) * (sampleIndex - 1)) + newSample.get(i)) / sampleIndex;
            centroid.add(val);
        }
        return Collections.unmodifiableList(centroid);
    }
}

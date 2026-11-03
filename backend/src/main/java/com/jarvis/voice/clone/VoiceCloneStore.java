package com.jarvis.voice.clone;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VoiceCloneStore {

    private static final Logger log = LoggerFactory.getLogger(VoiceCloneStore.class);

    private final Path storagePath;
    private final ObjectMapper json;
    private final Map<String, VoiceCloneProfile> cache = new ConcurrentHashMap<>();

    @Autowired
    public VoiceCloneStore(
            @Value("${jarvis.voice.clone.storage-path:data/voices/profiles.json}") String storagePath,
            @Autowired(required = false) ObjectMapper json
    ) {
        this.storagePath = Path.of(storagePath).toAbsolutePath().normalize();
        if (json != null) {
            this.json = json.copy().registerModule(new JavaTimeModule());
        } else {
            this.json = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .enable(SerializationFeature.INDENT_OUTPUT);
        }
        loadCache();
    }

    private synchronized void loadCache() {
        if (!Files.exists(storagePath)) {
            try {
                if (storagePath.getParent() != null) {
                    Files.createDirectories(storagePath.getParent());
                }
                Files.writeString(storagePath, "[]", StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("Failed to create voice profiles storage file: {}", storagePath, e);
            }
            return;
        }

        try {
            String content = Files.readString(storagePath, StandardCharsets.UTF_8).trim();
            if (content.isBlank() || content.equals("null")) {
                content = "[]";
            }
            List<VoiceCloneProfile> profiles = json.readValue(content, new TypeReference<List<VoiceCloneProfile>>() {});
            cache.clear();
            for (VoiceCloneProfile p : profiles) {
                if (p.id() != null) {
                    cache.put(p.id(), p);
                }
            }
            log.info("Loaded {} voice clone profiles from {}", cache.size(), storagePath);
        } catch (Exception e) {
            log.error("Failed to read voice clone profiles from disk: {}", storagePath, e);
        }
    }

    private synchronized void persist() {
        try {
            if (storagePath.getParent() != null) {
                Files.createDirectories(storagePath.getParent());
            }
            String content = json.writerWithDefaultPrettyPrinter().writeValueAsString(cache.values());
            Files.writeString(storagePath, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to persist voice clone profiles to disk: {}", storagePath, e);
        }
    }

    public List<VoiceCloneProfile> findAll() {
        return new ArrayList<>(cache.values());
    }

    public Optional<VoiceCloneProfile> findById(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(cache.get(id));
    }

    public Optional<VoiceCloneProfile> findDefault() {
        return cache.values().stream().filter(VoiceCloneProfile::isDefault).findFirst();
    }

    public synchronized VoiceCloneProfile save(VoiceCloneProfile profile) {
        if (profile == null || profile.id() == null) {
            throw new IllegalArgumentException("Profile and ID must not be null");
        }
        if (profile.isDefault()) {
            // Unset default on any other profiles
            for (Map.Entry<String, VoiceCloneProfile> entry : cache.entrySet()) {
                if (!entry.getKey().equals(profile.id()) && entry.getValue().isDefault()) {
                    entry.setValue(entry.getValue().withDefault(false));
                }
            }
        }
        cache.put(profile.id(), profile);
        persist();
        return profile;
    }

    public synchronized boolean delete(String id) {
        if (id == null) return false;
        VoiceCloneProfile removed = cache.remove(id);
        if (removed != null) {
            persist();
            return true;
        }
        return false;
    }
}

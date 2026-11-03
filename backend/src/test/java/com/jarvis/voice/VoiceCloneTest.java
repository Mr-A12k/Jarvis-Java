package com.jarvis.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.voice.clone.VoiceCloneProfile;
import com.jarvis.voice.clone.VoiceCloneService;
import com.jarvis.voice.clone.VoiceCloneStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VoiceCloneTest {

    @TempDir
    Path tempDir;

    private VoiceCloneStore store;
    private VoiceCloneService service;

    @BeforeEach
    void setup() {
        Path storePath = tempDir.resolve("profiles.json");
        Path samplesDir = tempDir.resolve("samples");
        Path cacheDir = tempDir.resolve("cache");
        ObjectMapper mapper = new ObjectMapper();

        store = new VoiceCloneStore(storePath.toString(), mapper);
        service = new VoiceCloneService(store, mapper, samplesDir.toString(), cacheDir.toString(), "http://127.0.0.1:18088");
    }

    @Test
    void canEnrollAndRetrieveVoiceCloneProfile() throws Exception {
        byte[] dummyWav = new byte[1024];
        VoiceCloneProfile profile = service.saveSample("Test Voice", "Sample script", dummyWav, "wav");

        assertNotNull(profile.id());
        assertEquals("Test Voice", profile.name());
        assertTrue(profile.isDefault(), "First enrolled profile should be default");

        List<VoiceCloneProfile> all = service.listProfiles();
        assertEquals(1, all.size());
        assertEquals(profile.id(), all.get(0).id());

        // Test retrieval
        assertTrue(service.getProfile(profile.id()).isPresent());
        assertTrue(service.getDefaultProfile().isPresent());
    }

    @Test
    void canDeleteProfile() throws Exception {
        byte[] dummyWav = new byte[512];
        VoiceCloneProfile profile = service.saveSample("To Delete", "test", dummyWav, "wav");
        assertEquals(1, service.listProfiles().size());

        boolean deleted = service.deleteProfile(profile.id());
        assertTrue(deleted);
        assertEquals(0, service.listProfiles().size());
    }

    @Test
    void fallbackAcousticSynthesisWorksOffline() throws Exception {
        byte[] dummyWav = new byte[512];
        VoiceCloneProfile profile = service.saveSample("Kabil Offline", "test", dummyWav, "wav");

        var result = service.synthesize(profile.id(), "JARVIS diagnostic check online.");
        assertNotNull(result);
        assertNotNull(result.audioUrl());
        assertTrue(result.audioUrl().startsWith("/api/v1/voice/clone/audio/"));
        assertNotNull(service.getAudioFile(result.fileName()));
    }
}

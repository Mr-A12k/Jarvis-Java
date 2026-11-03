package com.jarvis.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.voice.wakeword.WakeWordConfig;
import com.jarvis.voice.wakeword.WakeWordController;
import com.jarvis.voice.wakeword.WakeWordEvent;
import com.jarvis.voice.wakeword.WakeWordStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WakeWordTest {

    @TempDir
    Path tempDir;

    private WakeWordStore store;
    private WakeWordController controller;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        store = new WakeWordStore(tempDir.toString(), mapper, null);
        controller = new WakeWordController(store);
    }

    @Test
    void testDefaultConfigAndSave() {
        WakeWordConfig cfg = store.getConfig();
        assertNotNull(cfg);
        assertTrue(cfg.enabled());
        assertEquals("openwakeword", cfg.engine());
        assertEquals("hey_jarvis", cfg.modelName());
        assertEquals(0.50, cfg.sensitivity(), 0.001);

        WakeWordConfig updated = new WakeWordConfig(
                true,
                "silero_vad",
                "hey_jarvis",
                0.75,
                0.60,
                true,
                true,
                "Microphone Array",
                true,
                false,
                Map.of("customKey", "customValue"),
                null
        );

        WakeWordConfig saved = store.saveConfig(updated);
        assertEquals("silero_vad", saved.engine());
        assertEquals(0.75, saved.sensitivity(), 0.001);

        WakeWordConfig retrieved = store.getConfig();
        assertEquals("silero_vad", retrieved.engine());
        assertEquals(0.75, retrieved.sensitivity(), 0.001);
    }

    @Test
    void testLogAndListEvents() {
        WakeWordEvent ev1 = WakeWordEvent.create(
                "hey_jarvis",
                0.92,
                "openwakeword",
                1280,
                "Code.exe",
                "POPUP_AND_LISTEN",
                Map.of()
        );

        WakeWordEvent ev2 = WakeWordEvent.create(
                "hey_jarvis",
                0.88,
                "openwakeword",
                1280,
                "chrome.exe",
                "POPUP_AND_LISTEN",
                Map.of()
        );

        store.logEvent(ev1);
        store.logEvent(ev2);

        List<WakeWordEvent> events = store.listEvents(10, 0);
        assertEquals(2, events.size());
        assertEquals("chrome.exe", events.get(0).foregroundApp());
        assertEquals("Code.exe", events.get(1).foregroundApp());

        Map<String, Object> stats = store.getStats();
        assertEquals(2L, stats.get("totalWakes"));
        assertEquals(2L, stats.get("wakesToday"));
        assertEquals("online", stats.get("serviceStatus"));
    }

    @Test
    void testControllerTrigger() {
        WakeWordController.WakeTriggerRequest req = new WakeWordController.WakeTriggerRequest(
                "hey_jarvis",
                0.95,
                "openwakeword",
                1280,
                "Valorant.exe",
                "POPUP_AND_LISTEN",
                Map.of()
        );

        Map<String, Object> response = controller.trigger(req).block();
        assertNotNull(response);
        assertEquals(true, response.get("success"));
        assertEquals("POPUP_AND_LISTEN", response.get("action"));

        List<WakeWordEvent> events = controller.listEvents(10, 0).block();
        assertNotNull(events);
        assertFalse(events.isEmpty());
        assertEquals("Valorant.exe", events.get(0).foregroundApp());
    }
}

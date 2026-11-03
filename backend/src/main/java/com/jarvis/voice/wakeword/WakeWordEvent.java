package com.jarvis.voice.wakeword;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Audit log entry for a background wake-word trigger event.
 */
public record WakeWordEvent(
        UUID id,
        String wakeWord,
        double confidence,
        String engine,
        int audioDurationMs,
        String foregroundApp,
        String actionTaken,
        Map<String, Object> metadata,
        Instant createdAt
) {
    public static WakeWordEvent create(
            String wakeWord,
            double confidence,
            String engine,
            int audioDurationMs,
            String foregroundApp,
            String actionTaken,
            Map<String, Object> metadata
    ) {
        return new WakeWordEvent(
                UUID.randomUUID(),
                wakeWord != null && !wakeWord.isBlank() ? wakeWord : "hey_jarvis",
                confidence > 0 ? confidence : 0.50,
                engine != null && !engine.isBlank() ? engine : "openwakeword",
                audioDurationMs > 0 ? audioDurationMs : 1280,
                foregroundApp != null && !foregroundApp.isBlank() ? foregroundApp : "desktop",
                actionTaken != null && !actionTaken.isBlank() ? actionTaken : "POPUP_AND_LISTEN",
                metadata != null ? metadata : Map.of(),
                Instant.now()
        );
    }
}

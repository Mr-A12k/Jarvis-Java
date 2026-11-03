package com.jarvis.voice.wakeword;

import java.time.Instant;
import java.util.Map;

/**
 * Model representing background wake-word listener configuration and preferences.
 */
public record WakeWordConfig(
        boolean enabled,
        String engine,
        String modelName,
        double sensitivity,
        double vadThreshold,
        boolean autoPopup,
        boolean chimeSound,
        String audioDeviceName,
        boolean bringToFront,
        boolean startWithWindows,
        Map<String, Object> metadata,
        Instant updatedAt
) {
    public static WakeWordConfig createDefault() {
        return new WakeWordConfig(
                true,
                "openwakeword",
                "hey_jarvis",
                0.50,
                0.50,
                true,
                true,
                "Default System Input",
                true,
                false,
                Map.of(),
                Instant.now()
        );
    }
}

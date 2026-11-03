package com.jarvis.voice.clone;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VoiceCloneProfile(
        String id,
        String name,
        String audioFileName,
        String refText,
        double durationSeconds,
        boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {
    public VoiceCloneProfile withDefault(boolean def) {
        return new VoiceCloneProfile(
                this.id,
                this.name,
                this.audioFileName,
                this.refText,
                this.durationSeconds,
                def,
                this.createdAt,
                Instant.now()
        );
    }
}

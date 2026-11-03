package com.jarvis.biometrics;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents a user's biometric identity profile, containing mathematical feature embeddings
 * for Face Recognition (video stream) and Voice Speaker Identification (mic stream).
 *
 * Privacy Guarantee:
 * Never stores raw video frames, photos, or raw audio waveforms.
 * Persists only normalized mathematical feature vectors (128-d / 192-d floats).
 */
public record BiometricProfile(
        String id,
        String userName,
        String role, // "OWNER", "FAMILY", "GUEST", "ADMIN"
        boolean isPrimaryUser,
        boolean faceEnrolled,
        List<Double> faceEmbedding,
        int faceSamplesCount,
        boolean voiceEnrolled,
        List<Double> voiceEmbedding,
        int voiceSamplesCount,
        double confidenceThreshold,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public static BiometricProfile createDefaultOwner(String userName) {
        String name = (userName == null || userName.isBlank()) ? "Kabil" : userName.trim();
        return new BiometricProfile(
                "bio-" + java.util.UUID.randomUUID().toString().substring(0, 8),
                name,
                "OWNER",
                true,
                false,
                null,
                0,
                false,
                null,
                0,
                0.80,
                Map.of("created_by", "JARVIS System", "platform", "Desktop Biometrics"),
                Instant.now(),
                Instant.now()
        );
    }

    public BiometricProfile withFaceEmbedding(List<Double> embedding, int samplesCount) {
        return new BiometricProfile(
                id,
                userName,
                role,
                isPrimaryUser,
                true,
                embedding != null ? List.copyOf(embedding) : null,
                samplesCount,
                voiceEnrolled,
                voiceEmbedding,
                voiceSamplesCount,
                confidenceThreshold,
                metadata,
                createdAt,
                Instant.now()
        );
    }

    public BiometricProfile withVoiceEmbedding(List<Double> embedding, int samplesCount) {
        return new BiometricProfile(
                id,
                userName,
                role,
                isPrimaryUser,
                faceEnrolled,
                faceEmbedding,
                faceSamplesCount,
                true,
                embedding != null ? List.copyOf(embedding) : null,
                samplesCount,
                confidenceThreshold,
                metadata,
                createdAt,
                Instant.now()
        );
    }
}

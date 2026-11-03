package com.jarvis.biometrics;

import java.time.Instant;

/**
 * Biometric match assessment result returned after face, voice, or multimodal evaluation.
 */
public record BiometricMatchResult(
        boolean matched,
        String status, // "MASTER_AUTHENTICATED", "USER_AUTHENTICATED", "VISUAL_VERIFIED", "VOICE_VERIFIED", "UNRECOGNIZED_GUEST"
        String userName,
        String role,
        boolean isPrimaryUser,
        double faceConfidence,
        double voiceConfidence,
        double overallConfidence,
        String modality, // "FACE", "VOICE", "MULTIMODAL", "NONE"
        String tamilGreeting,
        String englishGreeting,
        Instant timestamp
) {
    public static BiometricMatchResult unrecognized(double faceConfidence, double voiceConfidence) {
        return new BiometricMatchResult(
                false,
                "UNRECOGNIZED_GUEST",
                "Guest",
                "GUEST",
                false,
                faceConfidence,
                voiceConfidence,
                Math.max(faceConfidence, voiceConfidence),
                "NONE",
                "அடையாளம் தெரியாத பயனர். விருந்தினர் பாதுகாப்பு பயன்முறை இயங்குகிறது.",
                "Unrecognized user. Guest mode active.",
                Instant.now()
        );
    }
}

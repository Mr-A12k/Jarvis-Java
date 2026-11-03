package com.jarvis.biometrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class BiometricService {

    private static final Logger log = LoggerFactory.getLogger(BiometricService.class);
    private static final double DEFAULT_THRESHOLD = 0.75;

    private final BiometricStore store;
    private volatile BiometricProfile activeUser;

    public BiometricService(BiometricStore store) {
        this.store = store;
        this.activeUser = store.findByUserName("Kabil")
                .orElseGet(() -> BiometricProfile.createDefaultOwner("Kabil"));
    }

    public List<BiometricProfile> getAllProfiles() {
        return store.findAll();
    }

    public Optional<BiometricProfile> getProfile(String userName) {
        return store.findByUserName(userName);
    }

    public BiometricProfile getActiveUser() {
        return activeUser;
    }

    public void setActiveUser(BiometricProfile profile) {
        if (profile != null) {
            this.activeUser = profile;
            log.info("Active user switched to: {} ({})", profile.userName(), profile.role());
        }
    }

    public void resetActiveUserToMaster() {
        this.activeUser = store.findByUserName("Kabil")
                .orElseGet(() -> BiometricProfile.createDefaultOwner("Kabil"));
        log.info("Active user reset to master: {}", activeUser.userName());
    }

    public boolean deleteProfile(String userName) {
        return store.delete(userName);
    }

    /**
     * Enrolls or updates face vector for the specified user.
     */
    public BiometricProfile enrollFace(String userName, String role, List<Double> faceVector) {
        if (userName == null || userName.isBlank()) {
            throw new IllegalArgumentException("User name is required for biometric face enrollment");
        }
        if (faceVector == null || faceVector.isEmpty()) {
            throw new IllegalArgumentException("Face embedding vector is required");
        }

        String trimmedName = userName.trim();
        BiometricProfile existing = store.findByUserName(trimmedName).orElse(null);

        BiometricProfile updated;
        if (existing == null) {
            boolean isPrimary = trimmedName.equalsIgnoreCase("kabil");
            String assignedRole = (role != null && !role.isBlank()) ? role : (isPrimary ? "OWNER" : "USER");
            updated = new BiometricProfile(
                    "bio-" + UUID.randomUUID().toString().substring(0, 8),
                    trimmedName,
                    assignedRole,
                    isPrimary,
                    true,
                    List.copyOf(faceVector),
                    1,
                    false,
                    null,
                    0,
                    DEFAULT_THRESHOLD,
                    Map.of("enrolled_via", "Video Stream"),
                    Instant.now(),
                    Instant.now()
            );
        } else {
            int newCount = existing.faceSamplesCount() + 1;
            List<Double> newCentroid = BiometricStore.updateCentroid(existing.faceEmbedding(), faceVector, newCount);
            updated = existing.withFaceEmbedding(newCentroid, newCount);
        }

        store.save(updated);
        log.info("Enrolled face embedding for user '{}' (sample count: {})", trimmedName, updated.faceSamplesCount());
        return updated;
    }

    /**
     * Enrolls or updates voice vector for the specified user.
     */
    public BiometricProfile enrollVoice(String userName, String role, List<Double> voiceVector) {
        if (userName == null || userName.isBlank()) {
            throw new IllegalArgumentException("User name is required for biometric voice enrollment");
        }
        if (voiceVector == null || voiceVector.isEmpty()) {
            throw new IllegalArgumentException("Voice embedding vector is required");
        }

        String trimmedName = userName.trim();
        BiometricProfile existing = store.findByUserName(trimmedName).orElse(null);

        BiometricProfile updated;
        if (existing == null) {
            boolean isPrimary = trimmedName.equalsIgnoreCase("kabil");
            String assignedRole = (role != null && !role.isBlank()) ? role : (isPrimary ? "OWNER" : "USER");
            updated = new BiometricProfile(
                    "bio-" + UUID.randomUUID().toString().substring(0, 8),
                    trimmedName,
                    assignedRole,
                    isPrimary,
                    false,
                    null,
                    0,
                    true,
                    List.copyOf(voiceVector),
                    1,
                    DEFAULT_THRESHOLD,
                    Map.of("enrolled_via", "Microphone Stream"),
                    Instant.now(),
                    Instant.now()
            );
        } else {
            int newCount = existing.voiceSamplesCount() + 1;
            List<Double> newCentroid = BiometricStore.updateCentroid(existing.voiceEmbedding(), voiceVector, newCount);
            updated = existing.withVoiceEmbedding(newCentroid, newCount);
        }

        store.save(updated);
        log.info("Enrolled voice embedding for user '{}' (sample count: {})", trimmedName, updated.voiceSamplesCount());
        return updated;
    }

    /**
     * Matches face vector against enrolled profiles.
     */
    public BiometricMatchResult verifyFace(List<Double> probeFace) {
        if (probeFace == null || probeFace.isEmpty()) {
            return BiometricMatchResult.unrecognized(0.0, 0.0);
        }

        BiometricProfile bestProfile = null;
        double bestSimilarity = 0.0;

        for (BiometricProfile p : store.findAll()) {
            if (p.faceEnrolled() && p.faceEmbedding() != null) {
                double sim = BiometricStore.cosineSimilarity(probeFace, p.faceEmbedding());
                if (sim > bestSimilarity) {
                    bestSimilarity = sim;
                    bestProfile = p;
                }
            }
        }

        if (bestProfile != null && bestSimilarity >= bestProfile.confidenceThreshold()) {
            setActiveUser(bestProfile);
            String tamilName = bestProfile.userName().equalsIgnoreCase("kabil") ? "கபில்" : bestProfile.userName();
            String status = bestProfile.isPrimaryUser() ? "MASTER_AUTHENTICATED" : "VISUAL_VERIFIED";
            String tamilGreeting = bestProfile.isPrimaryUser()
                    ? "வணக்கம் " + tamilName + " ஐயா, உங்கள் முகம் வெற்றிகரமாக அடையாளம் காணப்பட்டது."
                    : "வணக்கம் " + tamilName + ", முகம் அடையாளம் காணப்பட்டது.";
            String englishGreeting = "Face verified: " + bestProfile.userName() + " (" + Math.round(bestSimilarity * 1000.0) / 10.0 + "% match).";

            return new BiometricMatchResult(
                    true,
                    status,
                    bestProfile.userName(),
                    bestProfile.role(),
                    bestProfile.isPrimaryUser(),
                    bestSimilarity,
                    0.0,
                    bestSimilarity,
                    "FACE",
                    tamilGreeting,
                    englishGreeting,
                    Instant.now()
            );
        }

        return BiometricMatchResult.unrecognized(bestSimilarity, 0.0);
    }

    /**
     * Matches voice vector against enrolled profiles.
     */
    public BiometricMatchResult verifyVoice(List<Double> probeVoice) {
        if (probeVoice == null || probeVoice.isEmpty()) {
            return BiometricMatchResult.unrecognized(0.0, 0.0);
        }

        BiometricProfile bestProfile = null;
        double bestSimilarity = 0.0;

        for (BiometricProfile p : store.findAll()) {
            if (p.voiceEnrolled() && p.voiceEmbedding() != null) {
                double sim = BiometricStore.cosineSimilarity(probeVoice, p.voiceEmbedding());
                if (sim > bestSimilarity) {
                    bestSimilarity = sim;
                    bestProfile = p;
                }
            }
        }

        if (bestProfile != null && bestSimilarity >= bestProfile.confidenceThreshold()) {
            setActiveUser(bestProfile);
            String tamilName = bestProfile.userName().equalsIgnoreCase("kabil") ? "கபில்" : bestProfile.userName();
            String status = bestProfile.isPrimaryUser() ? "MASTER_AUTHENTICATED" : "VOICE_VERIFIED";
            String tamilGreeting = bestProfile.isPrimaryUser()
                    ? "வணக்கம் " + tamilName + " ஐயா, உங்கள் குரல் வெற்றிகரமாக அடையாளம் காணப்பட்டது."
                    : "வணக்கம் " + tamilName + ", குரல் அடையாளம் காணப்பட்டது.";
            String englishGreeting = "Voice verified: " + bestProfile.userName() + " (" + Math.round(bestSimilarity * 1000.0) / 10.0 + "% match).";

            return new BiometricMatchResult(
                    true,
                    status,
                    bestProfile.userName(),
                    bestProfile.role(),
                    bestProfile.isPrimaryUser(),
                    0.0,
                    bestSimilarity,
                    bestSimilarity,
                    "VOICE",
                    tamilGreeting,
                    englishGreeting,
                    Instant.now()
            );
        }

        return BiometricMatchResult.unrecognized(0.0, bestSimilarity);
    }

    /**
     * Multimodal verification fusing both face and voice vector probes.
     */
    public BiometricMatchResult verifyMultimodal(List<Double> probeFace, List<Double> probeVoice) {
        boolean hasFace = probeFace != null && !probeFace.isEmpty();
        boolean hasVoice = probeVoice != null && !probeVoice.isEmpty();

        if (!hasFace && !hasVoice) {
            return BiometricMatchResult.unrecognized(0.0, 0.0);
        }
        if (!hasVoice) {
            return verifyFace(probeFace);
        }
        if (!hasFace) {
            return verifyVoice(probeVoice);
        }

        // Both modalities present: compute matches
        BiometricProfile bestFaceProfile = null;
        double bestFaceSim = 0.0;
        BiometricProfile bestVoiceProfile = null;
        double bestVoiceSim = 0.0;

        for (BiometricProfile p : store.findAll()) {
            if (p.faceEnrolled() && p.faceEmbedding() != null) {
                double sim = BiometricStore.cosineSimilarity(probeFace, p.faceEmbedding());
                if (sim > bestFaceSim) {
                    bestFaceSim = sim;
                    bestFaceProfile = p;
                }
            }
            if (p.voiceEnrolled() && p.voiceEmbedding() != null) {
                double sim = BiometricStore.cosineSimilarity(probeVoice, p.voiceEmbedding());
                if (sim > bestVoiceSim) {
                    bestVoiceSim = sim;
                    bestVoiceProfile = p;
                }
            }
        }

        boolean facePass = bestFaceProfile != null && bestFaceSim >= bestFaceProfile.confidenceThreshold();
        boolean voicePass = bestVoiceProfile != null && bestVoiceSim >= bestVoiceProfile.confidenceThreshold();

        // 1. Both match the same person: Master / User Authenticated
        if (facePass && voicePass && bestFaceProfile.id().equals(bestVoiceProfile.id())) {
            setActiveUser(bestFaceProfile);
            double fusedScore = (bestFaceSim * 0.5) + (bestVoiceSim * 0.5);
            String status = bestFaceProfile.isPrimaryUser() ? "MASTER_AUTHENTICATED" : "USER_AUTHENTICATED";
            String tamilName = bestFaceProfile.userName().equalsIgnoreCase("kabil") ? "கபில்" : bestFaceProfile.userName();

            String tamilGreeting = bestFaceProfile.isPrimaryUser()
                    ? "வணக்கம் " + tamilName + " ஐயா, உங்கள் குரல் மற்றும் முகம் இரண்டும் வெற்றிகரமாக சரிபார்க்கப்பட்டது. கணினி தயார் நிலையில் உள்ளது."
                    : "வணக்கம் " + tamilName + ", உங்கள் பயோமெட்ரிக் அங்கீகரிக்கப்பட்டது.";
            String englishGreeting = bestFaceProfile.isPrimaryUser()
                    ? "Welcome back, Master " + bestFaceProfile.userName() + ". Face and voice biometric authentication confirmed. All systems online."
                    : "Welcome, " + bestFaceProfile.userName() + ". Multimodal biometric verification complete.";

            return new BiometricMatchResult(
                    true,
                    status,
                    bestFaceProfile.userName(),
                    bestFaceProfile.role(),
                    bestFaceProfile.isPrimaryUser(),
                    bestFaceSim,
                    bestVoiceSim,
                    fusedScore,
                    "MULTIMODAL",
                    tamilGreeting,
                    englishGreeting,
                    Instant.now()
            );
        }

        // 2. Face passes
        if (facePass) {
            setActiveUser(bestFaceProfile);
            String status = bestFaceProfile.isPrimaryUser() ? "MASTER_AUTHENTICATED" : "VISUAL_VERIFIED";
            String tamilGreeting = "வணக்கம் " + bestFaceProfile.userName() + " ஐயா, உங்கள் முகம் உறுதிப்படுத்தப்பட்டது.";
            String englishGreeting = "Visual recognition verified for " + bestFaceProfile.userName() + ".";

            return new BiometricMatchResult(
                    true,
                    status,
                    bestFaceProfile.userName(),
                    bestFaceProfile.role(),
                    bestFaceProfile.isPrimaryUser(),
                    bestFaceSim,
                    bestVoiceSim,
                    bestFaceSim,
                    "FACE",
                    tamilGreeting,
                    englishGreeting,
                    Instant.now()
            );
        }

        // 3. Voice passes
        if (voicePass) {
            setActiveUser(bestVoiceProfile);
            String status = bestVoiceProfile.isPrimaryUser() ? "MASTER_AUTHENTICATED" : "VOICE_VERIFIED";
            String tamilGreeting = "வணக்கம் " + bestVoiceProfile.userName() + " ஐயா, உங்கள் குரல் அடையாளம் காணப்பட்டது.";
            String englishGreeting = "Voice recognition verified for " + bestVoiceProfile.userName() + ".";

            return new BiometricMatchResult(
                    true,
                    status,
                    bestVoiceProfile.userName(),
                    bestVoiceProfile.role(),
                    bestVoiceProfile.isPrimaryUser(),
                    bestFaceSim,
                    bestVoiceSim,
                    bestVoiceSim,
                    "VOICE",
                    tamilGreeting,
                    englishGreeting,
                    Instant.now()
            );
        }

        // 4. Neither passed threshold
        return BiometricMatchResult.unrecognized(bestFaceSim, bestVoiceSim);
    }
}

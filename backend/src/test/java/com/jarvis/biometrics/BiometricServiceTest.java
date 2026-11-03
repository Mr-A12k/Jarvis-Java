package com.jarvis.biometrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BiometricServiceTest {

    @TempDir
    Path tempDir;

    private BiometricStore store;
    private BiometricService service;

    @BeforeEach
    void setUp() {
        Path testFile = tempDir.resolve("test-profiles.json");
        store = new BiometricStore(testFile.toString(), null, null);
        service = new BiometricService(store);
    }

    @Test
    void testCosineSimilarity() {
        List<Double> v1 = List.of(1.0, 0.0, 0.0);
        List<Double> v2 = List.of(1.0, 0.0, 0.0);
        List<Double> v3 = List.of(0.0, 1.0, 0.0);

        assertThat(BiometricStore.cosineSimilarity(v1, v2)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(BiometricStore.cosineSimilarity(v1, v3)).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(BiometricStore.cosineSimilarity(null, v2)).isEqualTo(0.0);
    }

    @Test
    void testUpdateCentroid() {
        List<Double> s1 = List.of(1.0, 3.0);
        List<Double> s2 = List.of(3.0, 5.0);

        // sample 1: centroid is s1
        List<Double> centroid = BiometricStore.updateCentroid(null, s1, 1);
        assertThat(centroid).containsExactly(1.0, 3.0);

        // sample 2: centroid is average of s1 and s2 => (2.0, 4.0)
        List<Double> centroid2 = BiometricStore.updateCentroid(centroid, s2, 2);
        assertThat(centroid2).containsExactly(2.0, 4.0);
    }

    @Test
    void testEnrollAndVerifyFace() {
        List<Double> faceVector = List.of(0.5, 0.5, 0.5, 0.5);
        BiometricProfile profile = service.enrollFace("Kabil", "OWNER", faceVector);

        assertThat(profile.faceEnrolled()).isTrue();
        assertThat(profile.faceSamplesCount()).isEqualTo(1);
        assertThat(profile.isPrimaryUser()).isTrue();

        // Exact match probe
        BiometricMatchResult match = service.verifyFace(faceVector);
        assertThat(match.matched()).isTrue();
        assertThat(match.userName()).isEqualTo("Kabil");
        assertThat(match.status()).isEqualTo("MASTER_AUTHENTICATED");
        assertThat(match.overallConfidence()).isGreaterThan(0.99);
        assertThat(match.tamilGreeting()).contains("கபில் ஐயா");

        // Orthogonal probe -> Unrecognized
        List<Double> strangerProbe = List.of(-0.5, -0.5, 0.5, 0.5);
        BiometricMatchResult failMatch = service.verifyFace(strangerProbe);
        assertThat(failMatch.matched()).isFalse();
        assertThat(failMatch.status()).isEqualTo("UNRECOGNIZED_GUEST");
    }

    @Test
    void testEnrollAndVerifyVoice() {
        List<Double> voiceVector = List.of(0.2, 0.8, 0.4, 0.1);
        BiometricProfile profile = service.enrollVoice("Kabil", "OWNER", voiceVector);

        assertThat(profile.voiceEnrolled()).isTrue();
        assertThat(profile.voiceSamplesCount()).isEqualTo(1);

        // Similar voice probe (e.g. 98% cosine match)
        List<Double> voiceProbe = List.of(0.21, 0.79, 0.41, 0.09);
        BiometricMatchResult match = service.verifyVoice(voiceProbe);

        assertThat(match.matched()).isTrue();
        assertThat(match.userName()).isEqualTo("Kabil");
        assertThat(match.status()).isEqualTo("MASTER_AUTHENTICATED");
        assertThat(match.tamilGreeting()).contains("குரல்");
    }

    @Test
    void testMultimodalVerificationBothMatch() {
        List<Double> faceVector = List.of(0.6, 0.8);
        List<Double> voiceVector = List.of(0.3, 0.4);

        service.enrollFace("Kabil", "OWNER", faceVector);
        service.enrollVoice("Kabil", "OWNER", voiceVector);

        BiometricMatchResult result = service.verifyMultimodal(faceVector, voiceVector);

        assertThat(result.matched()).isTrue();
        assertThat(result.status()).isEqualTo("MASTER_AUTHENTICATED");
        assertThat(result.modality()).isEqualTo("MULTIMODAL");
        assertThat(result.faceConfidence()).isGreaterThan(0.99);
        assertThat(result.voiceConfidence()).isGreaterThan(0.99);
        assertThat(result.tamilGreeting()).contains("குரல் மற்றும் முகம்");
        assertThat(result.englishGreeting()).contains("Master Kabil");
    }

    @Test
    void testMultimodalStrangerRejection() {
        List<Double> faceVector = List.of(0.6, 0.8);
        List<Double> voiceVector = List.of(0.3, 0.4);

        service.enrollFace("Kabil", "OWNER", faceVector);
        service.enrollVoice("Kabil", "OWNER", voiceVector);

        // Stranger probe completely different
        List<Double> unknownFace = List.of(-0.8, 0.6);
        List<Double> unknownVoice = List.of(-0.4, 0.3);

        BiometricMatchResult result = service.verifyMultimodal(unknownFace, unknownVoice);

        assertThat(result.matched()).isFalse();
        assertThat(result.status()).isEqualTo("UNRECOGNIZED_GUEST");
    }
}

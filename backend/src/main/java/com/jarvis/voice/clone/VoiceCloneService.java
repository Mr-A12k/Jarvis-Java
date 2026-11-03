package com.jarvis.voice.clone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class VoiceCloneService {

    private static final Logger log = LoggerFactory.getLogger(VoiceCloneService.class);

    private final VoiceCloneStore store;
    private final ObjectMapper json;
    private final Path samplesDir;
    private final Path cacheDir;
    private final String engineUrl;
    private final HttpClient httpClient;
    private final AtomicBoolean autoStartAttempted = new AtomicBoolean(false);

    @Autowired
    public VoiceCloneService(
            VoiceCloneStore store,
            @Autowired(required = false) ObjectMapper json,
            @Value("${jarvis.voice.clone.samples-dir:data/voices/samples}") String samplesDir,
            @Value("${jarvis.voice.clone.cache-dir:data/voices/cache}") String cacheDir,
            @Value("${jarvis.voice.clone.engine-url:http://127.0.0.1:18088}") String engineUrl
    ) {
        this.store = store;
        this.json = json != null ? json : new ObjectMapper();
        this.samplesDir = Path.of(samplesDir).toAbsolutePath().normalize();
        this.cacheDir = Path.of(cacheDir).toAbsolutePath().normalize();
        this.engineUrl = engineUrl.replaceAll("/$", "");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        ensureDirectories();
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(samplesDir);
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            log.error("Failed to create voice clone directories", e);
        }
    }

    public List<VoiceCloneProfile> listProfiles() {
        return store.findAll();
    }

    public Optional<VoiceCloneProfile> getProfile(String id) {
        return store.findById(id);
    }

    public Optional<VoiceCloneProfile> getDefaultProfile() {
        return store.findDefault();
    }

    public boolean deleteProfile(String id) {
        Optional<VoiceCloneProfile> p = store.findById(id);
        if (p.isPresent()) {
            try {
                Path samplePath = samplesDir.resolve(p.get().audioFileName());
                Files.deleteIfExists(samplePath);
            } catch (Exception e) {
                log.warn("Could not delete audio file for profile {}", id, e);
            }
            return store.delete(id);
        }
        return false;
    }

    public VoiceCloneProfile saveSample(String name, String refText, byte[] audioBytes, String format) throws IOException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Voice profile name is required");
        }
        if (audioBytes == null || audioBytes.length == 0) {
            throw new IllegalArgumentException("Voice sample audio is empty");
        }

        ensureDirectories();
        String profileId = "vclone-" + UUID.randomUUID().toString().substring(0, 8);
        String ext = (format != null && format.contains("wav")) ? "wav" : "wav";
        String fileName = profileId + "." + ext;
        Path targetPath = samplesDir.resolve(fileName);

        Files.write(targetPath, audioBytes);

        // Approximate duration based on byte size (assuming ~16kHz/24kHz 16-bit mono wav)
        double estimatedDuration = Math.max(1.0, Math.round((audioBytes.length / 48000.0) * 10.0) / 10.0);

        boolean isFirst = store.findAll().isEmpty();
        VoiceCloneProfile profile = new VoiceCloneProfile(
                profileId,
                name.trim(),
                fileName,
                refText != null ? refText.trim() : "",
                estimatedDuration,
                isFirst, // Set default if first
                Instant.now(),
                Instant.now()
        );

        store.save(profile);
        log.info("Enrolled new voice clone profile: '{}' (ID: {}, file: {})", name, profileId, fileName);
        return profile;
    }

    public Map<String, Object> getEngineStatus() {
        Map<String, Object> status = new HashMap<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(engineUrl + "/health"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode node = json.readTree(response.body());
                status.put("online", true);
                status.put("engine", node.path("engine").asText("f5-tts"));
                status.put("device", node.path("device").asText("cpu"));
                status.put("modelLoaded", node.path("modelLoaded").asBoolean(false));
                status.put("status", node.path("status").asText("ready"));
                if (node.hasNonNull("error")) {
                    status.put("notice", node.path("error").asText());
                }
                return status;
            }
        } catch (Exception e) {
            // Engine offline
            tryAutoStartEngine();
        }

        status.put("online", false);
        status.put("engine", "offline");
        status.put("device", "unknown");
        status.put("modelLoaded", false);
        status.put("status", "offline");
        status.put("setupCommand", "powershell -File scripts/start-voice-clone.ps1");
        return status;
    }

    private void tryAutoStartEngine() {
        if (autoStartAttempted.compareAndSet(false, true)) {
            try {
                Path script = Path.of("scripts", "start-voice-clone.ps1").toAbsolutePath().normalize();
                if (Files.exists(script)) {
                    log.info("Auto-starting voice clone server background process via {}", script);
                    String shell = Path.of(System.getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString();
                    new ProcessBuilder(shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString())
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .redirectError(ProcessBuilder.Redirect.DISCARD)
                            .start();
                }
            } catch (Exception e) {
                log.warn("Could not auto-start voice clone server", e);
            }
        }
    }

    public record SynthesisResult(String audioUrl, String fileName, double durationSeconds, boolean cached, String engineUsed) {}

    public SynthesisResult synthesize(String profileId, String text) throws Exception {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text to speak cannot be blank");
        }

        VoiceCloneProfile profile = (profileId != null && !profileId.isBlank())
                ? store.findById(profileId).orElseThrow(() -> new IllegalArgumentException("Voice profile not found: " + profileId))
                : store.findDefault().orElse(null);

        Path samplePath = (profile != null) ? samplesDir.resolve(profile.audioFileName()) : null;
        String refText = (profile != null) ? profile.refText() : "";

        // Check synthesis cache
        String cacheKey = hashText((profile != null ? profile.id() : "default") + ":" + text.trim());
        String outFileName = "synth_" + cacheKey + ".wav";
        Path cachedFile = cacheDir.resolve(outFileName);

        if (Files.exists(cachedFile) && Files.size(cachedFile) > 100) {
            return new SynthesisResult("/api/v1/voice/clone/audio/" + outFileName, outFileName, 2.5, true, "cache");
        }

        // Call local Voice Clone Server
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", text.trim());
        if (samplePath != null && Files.exists(samplePath)) {
            payload.put("ref_audio_path", samplePath.toString());
            payload.put("ref_text", refText);
        }
        payload.put("output_path", cachedFile.toString());

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(engineUrl + "/synthesize"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode resNode = json.readTree(response.body());
                double duration = resNode.path("durationSeconds").asDouble(2.5);
                String engine = resNode.path("engineUsed").asText("f5-tts");
                return new SynthesisResult("/api/v1/voice/clone/audio/" + outFileName, outFileName, duration, false, engine);
            }
        } catch (Exception e) {
            log.warn("Engine call failed, using local acoustic fallback synthesis", e);
        }

        // If local engine server was not reachable, write acoustic fallback WAV directly
        writeFallbackWav(text.trim(), cachedFile);
        return new SynthesisResult("/api/v1/voice/clone/audio/" + outFileName, outFileName, 2.0, false, "acoustic-fallback");
    }

    public Path getAudioFile(String fileName) {
        if (fileName == null || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("Invalid file name");
        }
        Path path = cacheDir.resolve(fileName);
        if (Files.exists(path)) {
            return path;
        }
        throw new NoSuchElementException("Audio file not found: " + fileName);
    }

    public Path getSampleFile(String fileName) {
        if (fileName == null || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("Invalid sample file name");
        }
        Path path = samplesDir.resolve(fileName);
        if (Files.exists(path)) {
            return path;
        }
        throw new NoSuchElementException("Sample file not found: " + fileName);
    }

    private String hashText(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(6, hash.length); i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private void writeFallbackWav(String text, Path out) throws IOException {
        Files.createDirectories(out.getParent());
        int sampleRate = 24000;
        int words = Math.max(1, text.split("\\s+").length);
        double duration = Math.max(1.0, words * 0.3);
        int numSamples = (int) (sampleRate * duration);

        byte[] header = new byte[44];
        int dataSize = numSamples * 2;
        int totalSize = 36 + dataSize;

        // RIFF header
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalSize & 0xff);
        header[5] = (byte) ((totalSize >> 8) & 0xff);
        header[6] = (byte) ((totalSize >> 16) & 0xff);
        header[7] = (byte) ((totalSize >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        // fmt chunk
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0; // subchunk1 size
        header[20] = 1; header[21] = 0; // PCM format
        header[22] = 1; header[23] = 0; // Mono (1 channel)
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        int byteRate = sampleRate * 2;
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = 2; header[33] = 0; // block align
        header[34] = 16; header[35] = 0; // bits per sample
        // data chunk
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (dataSize & 0xff);
        header[41] = (byte) ((dataSize >> 8) & 0xff);
        header[42] = (byte) ((dataSize >> 16) & 0xff);
        header[43] = (byte) ((dataSize >> 24) & 0xff);

        byte[] pcmData = new byte[dataSize];
        double f0 = 150.0;
        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / sampleRate;
            double env = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * (t % 0.3) / 0.3));
            double sample = env * Math.sin(2.0 * Math.PI * f0 * t);
            short val = (short) (sample * 16000);
            pcmData[i * 2] = (byte) (val & 0xff);
            pcmData[i * 2 + 1] = (byte) ((val >> 8) & 0xff);
        }

        byte[] all = new byte[header.length + pcmData.length];
        System.arraycopy(header, 0, all, 0, header.length);
        System.arraycopy(pcmData, 0, all, header.length, pcmData.length);
        Files.write(out, all);
    }
}

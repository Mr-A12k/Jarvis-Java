package com.jarvis.voice.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.ApiException;
import com.jarvis.voice.model.SpeechRecognitionResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Single Responsibility Principle (SRP):
 * Manages Windows desktop speech subsystem (SAPI) execution for voice input and output.
 */
@Component
public class WindowsSpeechEngine implements SpeechRecognizer, SpeechSynthesizer {
    private final ObjectMapper json;
    private final Semaphore slot = new Semaphore(1);
    private Path script;

    public WindowsSpeechEngine(ObjectMapper json) {
        this.json = json;
    }

    private synchronized Path script() throws Exception {
        if (script == null) {
            Path path = Files.createTempFile("jarvis-voice-", ".ps1");
            try (var input = getClass().getResourceAsStream("/voice.ps1")) {
                Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING);
            }
            path.toFile().deleteOnExit();
            script = path;
        }
        return script;
    }

    public JsonNode executeRaw(String action, String text) {
        if (!slot.tryAcquire()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "VOICE_BUSY", "Voice is already active. Stop it before retrying.");
        }
        Process process = null;
        try {
            String shell = Path.of(System.getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString();
            process = new ProcessBuilder(shell, "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-File", script().toString(), "-Action", action)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try (var input = process.getOutputStream()) {
                if (text != null) {
                    input.write(json.writeValueAsBytes(Map.of("text", text)));
                }
            }
            if (!process.waitFor(action.equals("speak") ? 180 : 30, TimeUnit.SECONDS)) {
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "VOICE_TIMEOUT", "Voice timed out. Try a shorter question.");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Speech engine failed");
            }
            return json.readTree(new String(process.getInputStream().readNBytes(16384), StandardCharsets.UTF_8));
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "VOICE_STOPPED", "Voice stopped.");
        } catch (Exception e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "VOICE_UNAVAILABLE", "Check Windows speech language, microphone permissions and audio output device.");
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            slot.release();
        }
    }

    @Override
    public SpeechRecognitionResult recognize(String languageTag) {
        JsonNode result = executeRaw("listen", null);
        String text = result.path("text").asText("");
        double confidence = result.path("confidence").asDouble(0.0);
        return new SpeechRecognitionResult(text, confidence, languageTag);
    }

    @Override
    public boolean synthesize(String text, String languageTag) {
        JsonNode result = executeRaw("speak", text);
        return result.path("spoken").asBoolean(false);
    }

    @Override
    public boolean supportsLanguage(String languageTag) {
        return languageTag != null && (languageTag.toLowerCase().startsWith("en") || languageTag.toLowerCase().startsWith("ta"));
    }
}

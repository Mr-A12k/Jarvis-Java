package com.jarvis.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.ApiException;
import com.jarvis.voice.agent.VoiceAgentRegistry;
import com.jarvis.voice.model.SpeechRecognitionResult;
import com.jarvis.voice.tamil.TamilVoiceAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

@Service
public class VoiceService {
    private final ObjectMapper json;
    private final VoiceAgentRegistry registry;
    private final TamilVoiceAgent tamilAgent;
    private final Semaphore slot = new Semaphore(1);
    private Path script;

    public VoiceService(ObjectMapper json) {
        this(json, null, null);
    }

    @Autowired
    public VoiceService(ObjectMapper json,
                        @Autowired(required = false) VoiceAgentRegistry registry,
                        @Autowired(required = false) TamilVoiceAgent tamilAgent) {
        this.json = json;
        this.registry = registry;
        this.tamilAgent = tamilAgent;
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

    public JsonNode run(String action, String text) {
        return run(action, text, null, null);
    }

    public JsonNode run(String action, String text, String voiceName, Integer rate) {
        if (!slot.tryAcquire())
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "VOICE_BUSY", "Voice is already active. Stop it before retrying.");
        Process process = null;
        try {
            String shell = Path.of(System.getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString();
            process = new ProcessBuilder(shell, "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script().toString(), "-Action", action)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try (var input = process.getOutputStream()) {
                if (text != null) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("text", text);
                    if (voiceName != null) payload.put("voice", voiceName);
                    if (rate != null) payload.put("rate", rate);
                    input.write(json.writeValueAsBytes(payload));
                }
            }
            if (!process.waitFor(action.equals("speak") ? 180 : 30, TimeUnit.SECONDS))
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "VOICE_TIMEOUT", "Voice timed out. Try a shorter question.");
            if (process.exitValue() != 0) throw new IllegalStateException("Speech engine failed");
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

    public List<Map<String, String>> languages() {
        if (registry != null) {
            return registry.availableLanguages();
        }
        return List.of(
                Map.of("tag", "en-US", "name", "English (US)"),
                Map.of("tag", "ta-IN", "name", "தமிழ் (Tamil)")
        );
    }

    public SpeechRecognitionResult listen(String languageTag) {
        if (registry != null) {
            return registry.resolve(languageTag).listen();
        }
        JsonNode node = run("listen", null);
        return new SpeechRecognitionResult(node.path("text").asText(""), node.path("confidence").asDouble(0.0), languageTag != null ? languageTag : "en-US");
    }

    public boolean speak(String text, String languageTag) {
        if (registry != null) {
            return registry.resolve(languageTag).speak(text);
        }
        JsonNode node = run("speak", text);
        return node.path("spoken").asBoolean(true);
    }

    public Optional<String> directTamilAnswer(String text) {
        if (tamilAgent != null) {
            return tamilAgent.answerDirectly(text);
        }
        return Optional.empty();
    }
}

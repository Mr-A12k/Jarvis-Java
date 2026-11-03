package com.jarvis.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jarvis.ai.SegmentRouter;
import com.jarvis.chat.ChatRequest;
import com.jarvis.chat.ChatService;
import com.jarvis.config.JarvisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Multimodal Vision Service for JARVIS.
 * Enables screen inspection, error detection, code debugging, and visual understanding.
 *
 * Architecture:
 * 1. Checks local Ollama instance for multimodal vision models (moondream:1.8b, llava, llama3.2-vision, etc.).
 * 2. If a vision model is active, streams end-to-end multimodal reasoning directly via Ollama's vision API.
 * 3. If a vision model is not yet installed, automatically falls back to the Screen Code & Error
 *    Extractor (Screen OCR) which extracts visible stack traces, code errors, and UI text,
 *    feeding them into the coding model (qwen2.5-coder / qwen3) with full DeepSeek-style reasoning trace.
 * 4. Provides one-click installation for lightweight vision models like moondream:1.8b (~829MB).
 */
@Service
public class VisionService {

    private static final Logger log = LoggerFactory.getLogger(VisionService.class);

    private static final List<String> KNOWN_VISION_MODELS = List.of(
            "moondream:1.8b",
            "moondream:latest",
            "moondream",
            "llava:latest",
            "llava:7b",
            "llava",
            "llama3.2-vision:latest",
            "llama3.2-vision:11b",
            "llama3.2-vision",
            "minicpm-v:latest",
            "minicpm-v",
            "qwen2-vl",
            "bakllava"
    );

    private static final String RECOMMENDED_VISION_MODEL = "moondream:1.8b";

    private final JarvisProperties settings;
    private final ScreenCaptureService screenCaptureService;
    private final ScreenOcrService ocrService;
    private final ChatService chatService;
    private final SegmentRouter segmentRouter;
    private final WebClient http;
    private final ObjectMapper json;

    private final Set<String> installedModels = ConcurrentHashMap.newKeySet();
    private volatile long lastModelCheckTime = 0;

    @Autowired
    public VisionService(
            JarvisProperties settings,
            ScreenCaptureService screenCaptureService,
            ScreenOcrService ocrService,
            @Autowired(required = false) @org.springframework.context.annotation.Lazy ChatService chatService,
            @Autowired(required = false) SegmentRouter segmentRouter,
            ObjectMapper json
    ) {
        this.settings = settings;
        this.screenCaptureService = screenCaptureService;
        this.ocrService = ocrService;
        this.chatService = chatService;
        this.segmentRouter = segmentRouter;
        this.json = json != null ? json : new ObjectMapper();

        // High-memory WebClient configured for large multimodal image payloads (16MB buffer)
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        this.http = WebClient.builder()
                .baseUrl(settings.ollamaUrl().toString())
                .exchangeStrategies(strategies)
                .build();

        refreshInstalledModelsAsync();
    }

    public VisionModelInfo getVisionStatus() {
        refreshInstalledModelsIfStale();
        List<String> foundVisionModels = new ArrayList<>();
        for (String m : installedModels) {
            for (String vm : KNOWN_VISION_MODELS) {
                if (m.equalsIgnoreCase(vm) || m.toLowerCase().startsWith(vm.toLowerCase())) {
                    if (!foundVisionModels.contains(m)) {
                        foundVisionModels.add(m);
                    }
                }
            }
        }

        boolean hasVision = !foundVisionModels.isEmpty();
        String activeModel = hasVision ? foundVisionModels.get(0) : settings.chatModel();

        return new VisionModelInfo(
                hasVision,
                activeModel,
                foundVisionModels,
                RECOMMENDED_VISION_MODEL,
                screenCaptureService.isHostCaptureSupported()
        );
    }

    public ScreenCaptureResult captureHostScreen() {
        return screenCaptureService.captureScreen();
    }

    public Flux<String> streamVisionAnalysis(VisionAnalysisRequest request) {
        return Flux.defer(() -> {
            String imageBase64 = cleanBase64(request.imageBase64());

            // If auto-capture requested or no image passed, attempt host screen capture
            if ((imageBase64.isBlank() && request.autoCapture()) || (imageBase64.isBlank() && request.prompt().toLowerCase().contains("screen"))) {
                ScreenCaptureResult captured = screenCaptureService.captureScreen();
                if (captured.isAvailable()) {
                    imageBase64 = cleanBase64(captured.imageBase64());
                }
            }

            VisionModelInfo status = getVisionStatus();

            // 1. If native vision model is installed, use direct Multimodal inference via Ollama
            if (status.visionModelAvailable() && !imageBase64.isBlank()) {
                String modelName = status.activeModel();
                log.info("VisionService: Streaming multimodal vision analysis via [{}]", modelName);
                return streamViaOllamaVisionModel(modelName, request.prompt(), imageBase64, request.history());
            }

            // 2. If no native vision model is installed, use Screen OCR & Code Debugging Pipeline
            log.info("VisionService: No native vision model installed. Using Screen Code & Error Extractor fallback.");
            return streamViaOcrAndCodeModel(request.prompt(), imageBase64, request.history(), status);
        });
    }

    private Flux<String> streamViaOllamaVisionModel(String model, String userPrompt, String imageBase64, List<ChatRequest.Turn> history) {
        try {
            ObjectNode root = json.createObjectNode();
            root.put("model", model);
            root.put("stream", true);

            ObjectNode options = root.putObject("options");
            options.put("temperature", 0.2);

            ArrayNode messages = root.putArray("messages");

            // System prompt
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content",
                    "You are JARVIS, Tony Stark's elite engineering and debugging assistant. " +
                    "Analyze the provided screenshot with sharp technical precision. " +
                    "Look for code errors, stack traces, compiler output, terminal warnings, UI glitches, or syntax mistakes. " +
                    "Explain what is visible on the user's screen, identify the root cause of the error or bug, and provide " +
                    "the exact step-by-step fix and corrected code in syntax-highlighted blocks. " +
                    "Be direct, crisp, and authoritative."
            );

            // History context
            if (history != null) {
                for (ChatRequest.Turn turn : history) {
                    ObjectNode h = messages.addObject();
                    h.put("role", turn.role() == ChatRequest.Role.user ? "user" : "assistant");
                    h.put("content", turn.content());
                }
            }

            // Current prompt with image attached
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            ArrayNode images = userMsg.putArray("images");
            images.add(imageBase64);

            return http.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(root)
                    .retrieve()
                    .bodyToFlux(JsonNode.class)
                    .map(node -> {
                        JsonNode msg = node.path("message");
                        if (!msg.isMissingNode() && msg.has("content")) {
                            return msg.path("content").asText("");
                        }
                        return node.path("response").asText("");
                    })
                    .filter(text -> !text.isEmpty())
                    .onErrorResume(e -> {
                        log.warn("Ollama /api/chat vision streaming failed, trying /api/generate: {}", e.getMessage());
                        return streamViaOllamaGenerate(model, userPrompt, imageBase64);
                    });
        } catch (Exception e) {
            log.error("Failed to construct Ollama vision request: {}", e.getMessage());
            return Flux.just("Error preparing vision request: " + e.getMessage());
        }
    }

    private Flux<String> streamViaOllamaGenerate(String model, String userPrompt, String imageBase64) {
        try {
            ObjectNode root = json.createObjectNode();
            root.put("model", model);
            root.put("stream", true);
            root.put("prompt", "Analyze this screenshot and help debug:\n" + userPrompt);

            ArrayNode images = root.putArray("images");
            images.add(imageBase64);

            return http.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(root)
                    .retrieve()
                    .bodyToFlux(JsonNode.class)
                    .map(node -> node.path("response").asText(""))
                    .filter(text -> !text.isEmpty());
        } catch (Exception e) {
            return Flux.just("Vision inference failed: " + e.getMessage());
        }
    }

    private Flux<String> streamViaOcrAndCodeModel(
            String userPrompt,
            String imageBase64,
            List<ChatRequest.Turn> history,
            VisionModelInfo status
    ) {
        return Mono.fromCallable(() -> {
            byte[] imageBytes = null;
            if (!imageBase64.isBlank()) {
                try {
                    imageBytes = Base64.getDecoder().decode(imageBase64);
                } catch (Exception ignored) {}
            }

            String extractedText = "";
            if (imageBytes != null && imageBytes.length > 0) {
                extractedText = ocrService.extractTextFromImage(imageBytes);
            }

            return extractedText;
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(extractedText -> {
            boolean hasExtractedText = !extractedText.isBlank();

            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("[SCREEN CAPTURE DEBUGGING REQUEST]\n");
            promptBuilder.append("User query: \"").append(userPrompt).append("\"\n\n");

            if (hasExtractedText) {
                promptBuilder.append("The user captured their screen. Here is the text and code extracted directly from the display:\n");
                promptBuilder.append("----------------------------------------------------------------------\n");
                promptBuilder.append(extractedText).append("\n");
                promptBuilder.append("----------------------------------------------------------------------\n\n");
                promptBuilder.append("Analyze the code, stack traces, compiler output, or error messages shown above. ");
                promptBuilder.append("Explain the problem clearly, pinpoint the exact issue or bug, and provide the corrected code.\n");
            } else {
                promptBuilder.append("The user shared a screen capture for debugging: \"").append(userPrompt).append("\". ");
                promptBuilder.append("Please provide a structured debugging checklist, common root causes for this scenario, and step-by-step diagnostic instructions.\n");
            }

            ChatRequest augmentedRequest = new ChatRequest(promptBuilder.toString(), history);

            // Prefix with thinking trace
            String thinkingHeader = "<think>\nJARVIS Vision Engine: " +
                    (hasExtractedText ? "Scanning screen layout and analyzing " + extractedText.length() + " characters of code/stack traces..."
                                      : "Analyzing screen capture context and formulating diagnostic steps...") +
                    "\n</think>\n\n";

            String modelTip = "\n\n---\n> 💡 **JARVIS Vision Engine**: *Analyzed via Intelligent Screen Code & Error Extractor. " +
                    "To enable direct pixel-level multimodal vision, run: `powershell -File scripts/pull-model.ps1 -Model moondream:1.8b`.*";

            Flux<String> coreStream = chatService != null ? chatService.stream(augmentedRequest) : Flux.just("ChatService unavailable.");

            return Flux.concat(
                    Flux.just(thinkingHeader),
                    coreStream,
                    Flux.just(modelTip)
            );
        });
    }

    public Mono<Void> pullVisionModel(String modelName) {
        String target = (modelName == null || modelName.isBlank()) ? RECOMMENDED_VISION_MODEL : modelName.trim();
        log.info("VisionService: Initiating pull for vision model [{}]", target);

        ObjectNode body = json.createObjectNode();
        body.put("name", target);
        body.put("stream", false);

        return http.post()
                .uri("/api/pull")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> {
                    log.info("VisionService: Successfully pulled vision model [{}]", target);
                    refreshInstalledModelsAsync();
                })
                .doOnError(e -> log.error("VisionService: Failed to pull vision model [{}]: {}", target, e.getMessage()));
    }

    private String cleanBase64(String input) {
        if (input == null) return "";
        String s = input.trim();
        if (s.startsWith("data:") && s.contains(",")) {
            return s.substring(s.indexOf(",") + 1).trim();
        }
        return s;
    }

    private void refreshInstalledModelsIfStale() {
        if (System.currentTimeMillis() - lastModelCheckTime > 20_000) {
            refreshInstalledModelsAsync();
        }
    }

    public void refreshInstalledModelsAsync() {
        lastModelCheckTime = System.currentTimeMillis();
        http.get().uri("/api/tags").retrieve().bodyToMono(JsonNode.class)
                .doOnNext(body -> {
                    if (body.path("models").isArray()) {
                        Set<String> fresh = ConcurrentHashMap.newKeySet();
                        for (JsonNode m : body.path("models")) {
                            String name = m.path("name").asText("");
                            if (!name.isBlank()) fresh.add(name);
                        }
                        installedModels.clear();
                        installedModels.addAll(fresh);
                    }
                })
                .doOnError(e -> log.debug("VisionService: Failed to refresh Ollama models: {}", e.getMessage()))
                .subscribe();
    }
}

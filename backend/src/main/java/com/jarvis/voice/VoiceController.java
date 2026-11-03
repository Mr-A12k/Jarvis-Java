package com.jarvis.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.voice.model.SpeechRecognitionResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/voice")
public class VoiceController {
    private final VoiceService voice;
    private final ObjectMapper json;
    private final LanguagePreferenceService languageService;

    public VoiceController(VoiceService voice, ObjectMapper json, LanguagePreferenceService languageService) {
        this.voice = voice;
        this.json = json;
        this.languageService = languageService;
    }

    public record Speech(@NotBlank @Size(max=2000) String text, String lang, String voice, Integer rate) {
        public Speech(String text, String lang) {
            this(text, lang, null, null);
        }
    }
    public record TamilAnalysisRequest(@NotBlank @Size(max=2000) String text) {}
    public record TamilAnalysisResponse(String input, String reply, boolean directMatch) {}
    public record LanguageUpdateRequest(@NotBlank String language) {}

    @GetMapping("/language")
    public Mono<Map<String, String>> getLanguage() {
        return Mono.fromCallable(() -> Map.of(
                "activeLanguage", languageService != null ? languageService.getActiveLanguage() : "en"
        )).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/language")
    public Mono<Map<String, Object>> setLanguage(@Valid @RequestBody LanguageUpdateRequest req) {
        return Mono.fromCallable(() -> {
            if (languageService != null) languageService.setActiveLanguage(req.language());
            String active = languageService != null ? languageService.getActiveLanguage() : req.language();
            return Map.<String, Object>of("success", true, "activeLanguage", active);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/languages")
    public Mono<List<Map<String, String>>> languages() {
        return Mono.fromCallable(voice::languages).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/status")
    public Mono<JsonNode> status() {
        return call("status", null);
    }

    @PostMapping("/listen")
    public Mono<JsonNode> listen(@RequestParam(value = "lang", required = false) String lang) {
        if (lang != null && !lang.isBlank() && !lang.equalsIgnoreCase("en-US")) {
            return Mono.fromCallable(() -> {
                SpeechRecognitionResult result = voice.listen(lang);
                return (JsonNode) json.valueToTree(Map.of("text", result.text(), "confidence", result.confidence(), "language", result.language()));
            }).subscribeOn(Schedulers.boundedElastic());
        }
        return call("listen", null);
    }

    @PostMapping("/speak")
    public Mono<JsonNode> speak(@Valid @RequestBody Speech speech) {
        if (speech.lang() != null && !speech.lang().isBlank() && !speech.lang().equalsIgnoreCase("en-US")) {
            return Mono.fromCallable(() -> {
                boolean spoken = voice.speak(speech.text(), speech.lang());
                return (JsonNode) json.valueToTree(Map.of("spoken", spoken, "language", speech.lang()));
            }).subscribeOn(Schedulers.boundedElastic());
        }
        if (speech.voice() == null && speech.rate() == null) {
            return Mono.fromCallable(() -> voice.run("speak", speech.text())).subscribeOn(Schedulers.boundedElastic());
        }
        return Mono.fromCallable(() -> voice.run("speak", speech.text(), speech.voice(), speech.rate())).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/voices")
    public Mono<JsonNode> voices() {
        return call("status", null).map(status -> status.path("voices"));
    }

    @PostMapping("/tamil/understand")
    public Mono<TamilAnalysisResponse> understandTamil(@Valid @RequestBody TamilAnalysisRequest request) {
        return Mono.fromCallable(() -> {
            var direct = voice.directTamilAnswer(request.text());
            return new TamilAnalysisResponse(request.text(), direct.orElse(""), direct.isPresent());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<JsonNode> call(String action, String text) {
        return Mono.fromCallable(() -> voice.run(action, text)).subscribeOn(Schedulers.boundedElastic());
    }
}

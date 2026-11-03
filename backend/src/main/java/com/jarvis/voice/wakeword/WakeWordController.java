package com.jarvis.voice.wakeword;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * REST & SSE controller for Native Background Wake-Word integration.
 * Receives wake signals from the native system tray listener and broadcasts
 * them to browser UIs for instant desktop immersion.
 */
@RestController
@RequestMapping("/api/v1/voice/wakeword")
public class WakeWordController {

    private final WakeWordStore store;

    public WakeWordController(WakeWordStore store) {
        this.store = store;
    }

    public record WakeTriggerRequest(
            String wakeWord,
            Double confidence,
            String engine,
            Integer audioDurationMs,
            String foregroundApp,
            String actionTaken,
            Map<String, Object> metadata
    ) {}

    @GetMapping("/config")
    public Mono<WakeWordConfig> getConfig() {
        return Mono.fromCallable(store::getConfig)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/config")
    public Mono<WakeWordConfig> updateConfig(@RequestBody WakeWordConfig newConfig) {
        return Mono.fromCallable(() -> store.saveConfig(newConfig))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/trigger")
    public Mono<Map<String, Object>> trigger(@RequestBody(required = false) WakeTriggerRequest req) {
        return Mono.fromCallable(() -> {
            WakeWordEvent event = WakeWordEvent.create(
                    req != null ? req.wakeWord() : "hey_jarvis",
                    req != null && req.confidence() != null ? req.confidence() : 0.85,
                    req != null ? req.engine() : "openwakeword",
                    req != null && req.audioDurationMs() != null ? req.audioDurationMs() : 1280,
                    req != null ? req.foregroundApp() : "desktop",
                    req != null ? req.actionTaken() : "POPUP_AND_LISTEN",
                    req != null ? req.metadata() : Map.of()
            );

            WakeWordEvent logged = store.logEvent(event);
            return Map.<String, Object>of(
                    "success", true,
                    "action", logged.actionTaken(),
                    "event", logged
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/events")
    public Mono<List<WakeWordEvent>> listEvents(
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset
    ) {
        return Mono.fromCallable(() -> store.listEvents(limit, offset))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/stats")
    public Mono<Map<String, Object>> getStats() {
        return Mono.fromCallable(store::getStats)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<WakeWordEvent>> streamEvents() {
        return store.streamEvents()
                .map(event -> ServerSentEvent.<WakeWordEvent>builder()
                        .event("wake")
                        .id(event.id().toString())
                        .data(event)
                        .build());
    }
}

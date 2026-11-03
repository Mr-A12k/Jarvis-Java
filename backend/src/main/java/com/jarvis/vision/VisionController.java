package com.jarvis.vision;

import com.jarvis.common.ApiErrors;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * Controller exposing REST & Server-Sent Events (SSE) endpoints
 * for Screen Capture and Multimodal Vision.
 */
@RestController
@RequestMapping("/api/v1/vision")
public class VisionController {

    private static final Logger log = LoggerFactory.getLogger(VisionController.class);

    public record StreamData(String text, String code, String message, String segment, String model) {
        public StreamData(String text) {
            this(text, null, null, null, null);
        }
        public StreamData(String text, String segment, String model) {
            this(text, null, null, segment, model);
        }
    }

    private final VisionService visionService;

    @Autowired
    public VisionController(VisionService visionService) {
        this.visionService = visionService;
    }

    @GetMapping("/status")
    public Mono<VisionModelInfo> getStatus() {
        return Mono.fromCallable(visionService::getVisionStatus).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/capture")
    public Mono<ScreenCaptureResult> captureScreen() {
        return Mono.fromCallable(visionService::captureHostScreen).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<StreamData>> streamAnalysis(@Valid @RequestBody VisionAnalysisRequest request) {
        VisionModelInfo status = visionService.getVisionStatus();
        String activeModel = status.activeModel();
        String activeSegment = status.visionModelAvailable() ? "VISION" : "CODE";

        var routeEvent = Flux.just(event("route", new StreamData(null, null, null, activeSegment, activeModel)));

        return routeEvent.concatWith(
                visionService.streamVisionAnalysis(request)
                        .map(delta -> event("delta", new StreamData(delta, activeSegment, activeModel)))
                        .concatWith(Mono.fromSupplier(() -> event("done", new StreamData(null, null, null, activeSegment, activeModel))))
                        .onErrorResume(error -> {
                            var safe = ApiErrors.modelError(error);
                            return Mono.just(event("error", new StreamData(null, safe.code(), safe.getMessage(), activeSegment, activeModel)));
                        })
        );
    }

    @PostMapping("/pull")
    public Mono<Map<String, Object>> pullModel(@RequestParam(value = "model", required = false, defaultValue = "moondream:1.8b") String model) {
        return visionService.pullVisionModel(model)
                .thenReturn(Map.<String, Object>of(
                        "success", true,
                        "model", model,
                        "message", "Vision model pull initiated for " + model
                ))
                .onErrorResume(e -> Mono.just(Map.of(
                        "success", false,
                        "model", model,
                        "message", "Failed to initiate pull: " + e.getMessage()
                )));
    }

    private ServerSentEvent<StreamData> event(String type, StreamData data) {
        return ServerSentEvent.<StreamData>builder(data).event(type).build();
    }
}

package com.jarvis.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.config.JarvisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VisionControllerTest {

    private VisionController controller;

    @BeforeEach
    void setUp() {
        JarvisProperties settings = new JarvisProperties("", URI.create("http://127.0.0.1:11434"),
                "qwen3:1.7b", 8192, 2048, 1,
                Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(1), "30s");
        ScreenCaptureService capture = new ScreenCaptureService();
        ScreenOcrService ocr = new ScreenOcrService();
        VisionService vision = new VisionService(settings, capture, ocr, null, null, new ObjectMapper());
        controller = new VisionController(vision);
    }

    @Test
    void getStatusReturnsMonoWithModelInfo() {
        Mono<VisionModelInfo> statusMono = controller.getStatus();
        StepVerifier.create(statusMono)
                .assertNext(info -> {
                    assertThat(info.recommendedModel()).isEqualTo("moondream:1.8b");
                })
                .verifyComplete();
    }

    @Test
    void captureScreenReturnsCaptureResult() {
        Mono<ScreenCaptureResult> resultMono = controller.captureScreen();
        StepVerifier.create(resultMono)
                .assertNext(res -> {
                    assertThat(res).isNotNull();
                    assertThat(res.mimeType()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void streamAnalysisEmitsRouteAndDeltas() {
        String testPixel = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        VisionAnalysisRequest req = new VisionAnalysisRequest("Look at my screen and help me debug", testPixel, List.of(), false);

        Flux<ServerSentEvent<VisionController.StreamData>> stream = controller.streamAnalysis(req);
        StepVerifier.create(stream)
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("route");
                })
                .expectNextMatches(event -> "delta".equals(event.event()))
                .thenConsumeWhile(event -> true)
                .verifyComplete();
    }
}

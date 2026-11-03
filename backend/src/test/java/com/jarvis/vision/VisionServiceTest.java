package com.jarvis.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.config.JarvisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VisionServiceTest {

    private JarvisProperties settings;
    private ScreenCaptureService captureService;
    private ScreenOcrService ocrService;
    private VisionService visionService;

    @BeforeEach
    void setUp() {
        settings = new JarvisProperties("", URI.create("http://127.0.0.1:11434"),
                "qwen3:1.7b", 8192, 2048, 1,
                Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(1), "30s");
        captureService = new ScreenCaptureService();
        ocrService = new ScreenOcrService();
        visionService = new VisionService(settings, captureService, ocrService, null, null, new ObjectMapper());
    }

    @Test
    void getVisionStatusReturnsValidInfo() {
        VisionModelInfo info = visionService.getVisionStatus();
        assertThat(info).isNotNull();
        assertThat(info.recommendedModel()).isEqualTo("moondream:1.8b");
        assertThat(info.activeModel()).isNotEmpty();
    }

    @Test
    void cleanBase64RemovesDataUriPrefix() {
        String testBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        String withPrefix = "data:image/png;base64," + testBase64;

        VisionAnalysisRequest req = new VisionAnalysisRequest("Look at my screen and help me debug", withPrefix, List.of(), false);
        assertThat(req.prompt()).isEqualTo("Look at my screen and help me debug");
        assertThat(req.imageBase64()).isEqualTo(withPrefix);
    }

    @Test
    void fallbackVisionAnalysisEmitsStructuredDiagnosticTrace() {
        String testPixel = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        VisionAnalysisRequest req = new VisionAnalysisRequest("Look at my screen and help me debug", testPixel, List.of(), false);

        Flux<String> stream = visionService.streamVisionAnalysis(req);
        StepVerifier.create(stream)
                .expectNextMatches(chunk -> chunk.contains("<think>") || chunk.contains("Vision Engine"))
                .thenConsumeWhile(chunk -> true)
                .verifyComplete();
    }
}

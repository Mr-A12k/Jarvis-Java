package com.jarvis.ai;

import com.jarvis.config.JarvisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SegmentRouterTest {

    private JarvisProperties settings;
    private WebClient webClient;
    private SegmentRouter router;

    @BeforeEach
    void setUp() {
        settings = new JarvisProperties("", URI.create("http://127.0.0.1:11434"),
                "qwen3:1.7b", 8192, 2048, 1,
                Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(1), "30s");

        webClient = WebClient.builder().exchangeFunction(req -> Mono.just(ClientResponse.create(org.springframework.http.HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body("{\"models\":[{\"name\":\"qwen3:1.7b\"},{\"name\":\"qwen2.5-coder:1.5b\"},{\"name\":\"deepseek-r1:1.5b\"},{\"name\":\"jarvis-researcher:latest\"}]}")
                .build())).build();

        router = new SegmentRouter(settings, webClient);
    }

    @Test
    void routesResearchAndRealtimeQueriesToResearchSegment() {
        var stockDecision = router.route("What are the current stock market details for Nifty 50 and Sensex?");
        assertThat(stockDecision.segment()).isEqualTo(SegmentRouter.Segment.RESEARCH);
        assertThat(stockDecision.model()).isEqualTo("jarvis-researcher:latest");

        var goldDecision = router.route("What is the gold price in India today?");
        assertThat(goldDecision.segment()).isEqualTo(SegmentRouter.Segment.RESEARCH);
        assertThat(goldDecision.model()).isEqualTo("jarvis-researcher:latest");

        var commodityTamil = router.route("இன்றைய தங்கம் மற்றும் வெள்ளி விலை என்ன?");
        assertThat(commodityTamil.segment()).isEqualTo(SegmentRouter.Segment.RESEARCH);

        var ottDecision = router.route("What movies are released on OTT this week?");
        assertThat(ottDecision.segment()).isEqualTo(SegmentRouter.Segment.RESEARCH);

        var dateDecision = router.route("What is today's date and time?");
        assertThat(dateDecision.segment()).isEqualTo(SegmentRouter.Segment.RESEARCH);

        var tamilDecision = router.route("இன்றைய தேதி மற்றும் பங்குச்சந்தை விவரம் என்ன?");
        assertThat(tamilDecision.segment()).isEqualTo(SegmentRouter.Segment.RESEARCH);

        var explicitDecision = router.route("/research latest artificial intelligence news");
        assertThat(explicitDecision.segment()).isEqualTo(SegmentRouter.Segment.RESEARCH);

        var goldSlash = router.route("/gold 24k rate in chennai");
        assertThat(goldSlash.segment()).isEqualTo(SegmentRouter.Segment.RESEARCH);
        assertThat(goldSlash.model()).isEqualTo("jarvis-researcher:latest");
    }

    @Test
    void routesVisionAndScreenDebuggingQueries() {
        var decision = router.route("Look at my screen and help me debug this error");
        assertThat(decision.segment()).isEqualTo(SegmentRouter.Segment.VISION);
        assertThat(decision.temperature()).isEqualTo(0.2);

        var explicitDecision = router.route("/vision explain this diagram");
        assertThat(explicitDecision.segment()).isEqualTo(SegmentRouter.Segment.VISION);

        var tamilDecision = router.route("என் திரையைப் பார்த்து பிழையை சரி செய்");
        assertThat(tamilDecision.segment()).isEqualTo(SegmentRouter.Segment.VISION);
    }

    @Test
    void routesCodingQueryToCoderModel() {
        var decision = router.route("Write a TypeScript function to calculate Fibonacci numbers with memoization");
        assertThat(decision.segment()).isEqualTo(SegmentRouter.Segment.CODE);
        assertThat(decision.model()).isEqualTo("qwen2.5-coder:1.5b");
        assertThat(decision.temperature()).isEqualTo(0.2);
    }

    @Test
    void routesMathQueryToDeepSeekModel() {
        var decision = router.route("Calculate the integral of 3x^2 + 2x with respect to x");
        assertThat(decision.segment()).isEqualTo(SegmentRouter.Segment.MATH);
        assertThat(decision.model()).isEqualTo("deepseek-r1:1.5b");
        assertThat(decision.temperature()).isEqualTo(0.2);
    }

    @Test
    void routesExplicitCommands() {
        var codeDecision = router.route("/code build a simple web server");
        assertThat(codeDecision.segment()).isEqualTo(SegmentRouter.Segment.CODE);

        var mathDecision = router.route("/math solve 2x + 5 = 15");
        assertThat(mathDecision.segment()).isEqualTo(SegmentRouter.Segment.MATH);

        var r1Decision = router.route("/r1 prove that the square root of 2 is irrational");
        assertThat(r1Decision.segment()).isEqualTo(SegmentRouter.Segment.MATH);
        assertThat(r1Decision.model()).isEqualTo("deepseek-r1:1.5b");

        var thinkDecision = router.route("/think analyze the algorithmic complexity of merge sort");
        assertThat(thinkDecision.segment()).isEqualTo(SegmentRouter.Segment.MATH);

        var generalDecision = router.route("/chat tell me a story");
        assertThat(generalDecision.segment()).isEqualTo(SegmentRouter.Segment.GENERAL);
    }

    @Test
    void routesCognitiveReasoningQueriesToDeepSeekModel() {
        var puzzle = router.route("Think through this logical puzzle step by step: A says B is lying, B says C is lying");
        assertThat(puzzle.segment()).isEqualTo(SegmentRouter.Segment.MATH);
        assertThat(puzzle.model()).isEqualTo("deepseek-r1:1.5b");

        var tamilReasoning = router.route("இந்த கணக்கை தர்க்கரீதியாக படி படியாக யோசித்து விடை கூறு");
        assertThat(tamilReasoning.segment()).isEqualTo(SegmentRouter.Segment.MATH);
        assertThat(tamilReasoning.model()).isEqualTo("deepseek-r1:1.5b");
    }

    @Test
    void reportsAvailableSegments() {
        var segments = router.getAvailableSegments();
        assertThat(segments).isNotEmpty();
        assertThat(segments).anyMatch(s -> s.id().equals("MATH") && s.defaultModel().equals("deepseek-r1:1.5b"));
        assertThat(segments).anyMatch(s -> s.id().equals("RESEARCH") && s.defaultModel().equals("jarvis-researcher:latest"));
    }

    @Test
    void routesGeneralConversationToDefaultChatModel() {
        var decision = router.route("Good morning Jarvis, are you operational?");
        assertThat(decision.segment()).isEqualTo(SegmentRouter.Segment.GENERAL);
        assertThat(decision.model()).isEqualTo("qwen3:1.7b");
        assertThat(decision.temperature()).isEqualTo(0.7);
    }

    @Test
    void fallsBackGracefullyWhenSpecializedModelNotInstalled() {
        // Setup client with only default model installed
        WebClient limitedClient = WebClient.builder().exchangeFunction(req -> Mono.just(ClientResponse.create(org.springframework.http.HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body("{\"models\":[{\"name\":\"qwen3:1.7b\"}]}")
                .build())).build();

        SegmentRouter limitedRouter = new SegmentRouter(settings, limitedClient);
        var decision = limitedRouter.route("Write a Python script to sort a list");
        assertThat(decision.segment()).isEqualTo(SegmentRouter.Segment.CODE);
        // Falls back to settings.chatModel()
        assertThat(decision.model()).isEqualTo("qwen3:1.7b");
    }
}

package com.jarvis;

import com.jarvis.ai.AiProvider;
import com.jarvis.chat.ChatRequest;
import com.jarvis.chat.ChatService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;

/** Opt-in REAL inference. No fake runtime or model is used by this test. */
@EnabledIfEnvironmentVariable(named="JARVIS_LIVE_TEST", matches="true")
@SpringBootTest(properties="jarvis.api-token=live-test-token-012345678901234567890")
class LiveOllamaTest {
    @Autowired AiProvider provider;
    @Autowired ChatService service;
    @Test void realLocalModelCanAnswer() {
        assertThat(provider.health().block(Duration.ofSeconds(5)).status()).isEqualTo("ready");
        long start = System.nanoTime();
        var answer = service.complete(new ChatRequest("Reply with the word READY only.", null)).block(Duration.ofSeconds(130));
        assertThat(answer).isNotBlank().containsIgnoringCase("READY");
        System.out.printf("LIVE_INFERENCE durationMs=%d responseChars=%d%n", (System.nanoTime()-start)/1_000_000, answer.length());
    }
}

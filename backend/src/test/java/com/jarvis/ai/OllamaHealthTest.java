package com.jarvis.ai;

import com.jarvis.config.JarvisProperties;
import java.net.URI;
import java.net.InetSocketAddress;
import java.time.Duration;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

class OllamaHealthTest {
    @Test void unavailableRuntimeReturnsUsefulHealthInsteadOfFailingTheApi() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int unusedPort = server.getAddress().getPort(); server.start(); server.stop(0);
        var url = URI.create("http://127.0.0.1:" + unusedPort);
        var p = new JarvisProperties("test-token-012345678901234567890123", url, "qwen3:1.7b", 2048,384,1,
                Duration.ofSeconds(1),Duration.ofSeconds(2),Duration.ofSeconds(1),"30s");
        var provider = new OllamaProvider(null, WebClient.builder().baseUrl(url.toString()).build(), p);
        StepVerifier.create(provider.health()).expectNextMatches(h -> h.status().equals("unavailable") && h.detail().contains("Start Ollama"))
                .verifyComplete();
    }
}

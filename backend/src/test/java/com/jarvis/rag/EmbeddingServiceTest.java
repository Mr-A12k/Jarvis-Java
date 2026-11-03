package com.jarvis.rag;

import com.jarvis.config.JarvisProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingServiceTest {
    private HttpServer server;
    private EmbeddingService embeddingService;
    private JarvisProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();

        int port = server.getAddress().getPort();
        URI baseUrl = URI.create("http://127.0.0.1:" + port);

        properties = new JarvisProperties(
                "test-token-012345678901234567890123",
                baseUrl,
                "qwen3:1.7b",
                "all-minilm:22m",
                384,
                2048,
                384,
                1,
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                Duration.ofSeconds(2),
                "30s"
        );

        WebClient webClient = WebClient.builder().baseUrl(baseUrl.toString()).build();
        embeddingService = new EmbeddingService(webClient, properties);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void returnsEmptyArrayForBlankInput() {
        StepVerifier.create(embeddingService.embed(""))
                .assertNext(arr -> assertThat(arr).isEmpty())
                .verifyComplete();

        StepVerifier.create(embeddingService.embed(null))
                .assertNext(arr -> assertThat(arr).isEmpty())
                .verifyComplete();
    }

    @Test
    void generatesEmbeddingFromOllamaResponse() {
        server.createContext("/api/embeddings", exchange -> {
            byte[] responseBytes = "{\"embedding\": [0.1, -0.2, 0.35, 0.42]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });

        StepVerifier.create(embeddingService.embed("semantic search prompt"))
                .assertNext(arr -> {
                    assertThat(arr).hasSize(4);
                    assertThat(arr[0]).isEqualTo(0.1f);
                    assertThat(arr[1]).isEqualTo(-0.2f);
                    assertThat(arr[2]).isEqualTo(0.35f);
                    assertThat(arr[3]).isEqualTo(0.42f);
                })
                .verifyComplete();
    }

    @Test
    void handlesEmptyEmbeddingError() {
        server.createContext("/api/embeddings", exchange -> {
            byte[] responseBytes = "{\"embedding\": []}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });

        StepVerifier.create(embeddingService.embed("bad response"))
                .expectError()
                .verify();
    }
}

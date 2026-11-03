package com.jarvis;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;
import java.util.Map;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"jarvis.auth-mode=local","jarvis.api-token=","jarvis.db-url="})
class LocalAccessTest {
    @LocalServerPort int port;
    WebTestClient client() { return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:"+port).build(); }
    @Test void localRequestsNeedNoSecret() {
        client().get().uri("/api/v1/session").header("X-Jarvis-Local","1").exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.mode").isEqualTo("local");
        client().get().uri("/api/v1/diagnostics").header("X-Jarvis-Local","1").exchange().expectStatus().isOk();
        client().post().uri("/api/v1/chat").header("X-Jarvis-Local","1")
            .header("Origin","http://127.0.0.1:"+port).bodyValue(Map.of("message","calculate: 2+3"))
            .exchange().expectStatus().isOk();
    }
    @Test void rejectsBrowserCrossSiteAndRebindingRequests() {
        client().get().uri("/api/v1/files/memory").exchange().expectStatus().isForbidden();
        client().get().uri("/api/v1/files/memory").header("X-Jarvis-Local","1")
            .header("Origin","https://example.com").exchange().expectStatus().isForbidden();
        client().get().uri("/api/v1/diagnostics").header("X-Jarvis-Local","1")
            .header("Host","evil.example:"+port).exchange().expectStatus().isForbidden();
        client().get().uri("/api/v1/diagnostics").header("X-Jarvis-Local","1")
            .header("Sec-Fetch-Site","cross-site").exchange().expectStatus().isForbidden();
    }
}

package com.jarvis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;
import java.util.Map;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "jarvis.auth-mode=token", "jarvis.api-token=database-test-token-01234567890123456789", "jarvis.db-url="})
@AutoConfigureWebTestClient
class DatabaseApiTest {
    @Autowired WebTestClient client;
    private static final String AUTH="Bearer database-test-token-01234567890123456789";
    @Test void requiresToken() {
        client.get().uri("/api/v1/database/memories").exchange().expectStatus().isUnauthorized();
        client.post().uri("/api/v1/database/snapshots").bodyValue(Map.of()).exchange().expectStatus().isUnauthorized();
    }
    @Test void reportsUnconfiguredWithoutBreakingApp() {
        client.get().uri("/api/v1/database/status").header("Authorization",AUTH).exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.ready").isEqualTo(false);
        client.get().uri("/api/v1/database/memories").header("Authorization",AUTH).exchange().expectStatus().isEqualTo(503)
            .expectBody().jsonPath("$.code").isEqualTo("DATABASE_NOT_CONFIGURED");
    }
    @Test void validatesBeforeConnecting() {
        client.get().uri("/api/v1/database/memories?limit=101").header("Authorization",AUTH).exchange().expectStatus().isBadRequest();
        client.post().uri("/api/v1/database/memories").header("Authorization",AUTH).bodyValue(Map.of("title","Test")).exchange().expectStatus().isBadRequest();
        client.post().uri("/api/v1/database/snapshots").header("Authorization",AUTH)
            .bodyValue(Map.of("title","Test","messages",java.util.List.of(Map.of("role","system","content","No"))))
            .exchange().expectStatus().isBadRequest();
    }
}

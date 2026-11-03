package com.jarvis.rag;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jarvis.common.ApiException;
import com.jarvis.config.JarvisProperties;
import com.jarvis.ai.ModelAdmission;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class EmbeddingService {
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingService.class);
    private final WebClient client;
    private final JarvisProperties properties;
    private final ModelAdmission admission;

    public EmbeddingService(WebClient ollamaWebClient, JarvisProperties properties) {
        this(ollamaWebClient, properties, new ModelAdmission(properties));
    }

    @Autowired
    public EmbeddingService(WebClient ollamaWebClient, JarvisProperties properties, ModelAdmission admission) {
        this.client = ollamaWebClient;
        this.properties = properties;
        this.admission = admission;
    }

    public record EmbeddingRequest(String model, String prompt) {}
    public record EmbeddingResponse(@JsonProperty("embedding") List<Float> embedding) {}

    public String modelName() { return properties.embeddingModel(); }
    public int dimensions() { return properties.embeddingDimensions(); }

    public Mono<float[]> embed(String text) {
        if (text == null || text.isBlank()) return Mono.just(new float[0]);
        return Mono.using(admission::embedding, lease -> {
            var req = new EmbeddingRequest(properties.embeddingModel(), text);
            return client.post()
                    .uri("/api/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(EmbeddingResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .map(resp -> {
                        if (resp.embedding() == null || resp.embedding().isEmpty()) {
                            throw new ApiException(HttpStatus.BAD_GATEWAY, "EMPTY_EMBEDDING", "Ollama returned empty embedding.");
                        }
                        float[] result = new float[resp.embedding().size()];
                        for (int i = 0; i < resp.embedding().size(); i++) {
                            result[i] = resp.embedding().get(i);
                        }
                        return result;
                    })
                    .doOnError(e -> LOG.warn("Embedding generation failed: {}", e.getMessage()));
        }, ModelAdmission.Lease::close, true);
    }
}

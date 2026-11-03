package com.jarvis.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.jarvis.config.JarvisProperties;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class OllamaProvider implements AiProvider {
    private final OllamaChatModel model;
    private final WebClient http;
    private final JarvisProperties settings;
    public OllamaProvider(OllamaChatModel model, WebClient ollamaWebClient, JarvisProperties settings) {
        this.model = model; this.http = ollamaWebClient; this.settings = settings;
    }
    @Override public Flux<String> stream(Prompt prompt) {
        return model.stream(prompt).handle((response, sink) -> {
            if (response.getResult() != null && response.getResult().getOutput().getText() != null) {
                var text = response.getResult().getOutput().getText();
                if (!text.isEmpty()) sink.next(text);
            }
        });
    }
    @Override public Mono<ModelHealth> health() {
        return http.get().uri("/api/tags").retrieve().bodyToMono(JsonNode.class).map(body -> {
            if (!body.path("models").isArray()) return new ModelHealth("unavailable", settings.chatModel(), "Ollama returned an invalid model list.");
            for (JsonNode model : body.path("models")) {
                if (settings.chatModel().equals(model.path("name").asText()))
                    return new ModelHealth("ready", settings.chatModel(), "Runtime reachable and model installed; inference has not been probed.");
            }
            return new ModelHealth("missing_model", settings.chatModel(), "Run: ollama pull " + settings.chatModel());
        }).switchIfEmpty(Mono.just(new ModelHealth("unavailable", settings.chatModel(), "Ollama returned an empty response.")))
          .timeout(settings.healthTimeout())
          .onErrorReturn(new ModelHealth("unavailable", settings.chatModel(), "Start Ollama, then retry. No cloud fallback is enabled."));
    }
}

package com.jarvis.ai;

import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Model-independent boundary. No external provider or implicit fallback is registered. */
public interface AiProvider {
    Flux<String> stream(Prompt prompt);
    Mono<ModelHealth> health();
    record ModelHealth(String status, String model, String detail) {}
}

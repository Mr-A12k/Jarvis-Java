package com.jarvis.ai;

import com.jarvis.chat.*;
import com.jarvis.common.ApiException;
import com.jarvis.config.JarvisProperties;
import com.jarvis.rag.EmbeddingService;
import com.jarvis.system.SystemAppLauncher;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;

class ModelAdmissionTest {
    private final JarvisProperties settings = new JarvisProperties("", URI.create("http://127.0.0.1:11434"),
        "qwen3:1.7b",8192,384,1,Duration.ofSeconds(1),Duration.ofSeconds(5),Duration.ofSeconds(1),"30s");
    private final ModelAdmission admission = new ModelAdmission(settings);
    private final ChatRequest request = new ChatRequest("Explain photosynthesis",null);
    private final AtomicInteger chatCalls = new AtomicInteger();
    private final AtomicInteger embeddingCalls = new AtomicInteger();
    private Flux<String> chatResponse = Flux.never();
    private Mono<ClientResponse> embeddingResponse = Mono.never();

    private ChatService chat() {
        AiProvider provider = new AiProvider() {
            public Flux<String> stream(Prompt prompt) { chatCalls.incrementAndGet(); return chatResponse; }
            public Mono<ModelHealth> health() { return Mono.empty(); }
        };
        return new ChatService(provider,new PromptFactory(settings),settings,new SystemAppLauncher(false),admission);
    }
    private EmbeddingService embeddings() {
        var client=WebClient.builder().exchangeFunction(req -> {
            embeddingCalls.incrementAndGet(); return embeddingResponse;
        }).build();
        return new EmbeddingService(client,settings,admission);
    }
    private static void busy(Throwable e, String code) {
        assertThat(e).isInstanceOf(ApiException.class);
        assertThat(((ApiException)e).code()).isEqualTo(code);
    }
    private void validEmbedding() {
        embeddingResponse=Mono.fromSupplier(() -> ClientResponse.create(HttpStatus.OK)
            .header("Content-Type","application/json").body("{\"embedding\":[0.1,0.2]}").build());
    }

    @Test void chatBlocksEmbeddingAndCancellationReleasesIt() {
        var active=chat().stream(request).subscribe();
        try {
            StepVerifier.create(embeddings().embed("document")).expectErrorSatisfies(e -> busy(e,"EMBEDDING_BUSY")).verify();
            assertThat(embeddingCalls.get()).isZero();
        } finally { active.dispose(); }
        validEmbedding();
        assertThat(embeddings().embed("document").block()).hasSize(2);
    }

    @Test void embeddingBlocksChatAndOtherEmbeddingsUntilCancelled() {
        var active=embeddings().embed("document").subscribe();
        try {
            StepVerifier.create(chat().stream(request)).expectErrorSatisfies(e -> busy(e,"MODEL_BUSY")).verify();
            StepVerifier.create(embeddings().embed("second")).expectErrorSatisfies(e -> busy(e,"EMBEDDING_BUSY")).verify();
            assertThat(chatCalls.get()).isZero();
            assertThat(embeddingCalls.get()).isEqualTo(1);
        } finally { active.dispose(); }
        chatResponse=Flux.just("answer");
        assertThat(chat().complete(request).block()).isEqualTo("answer");
    }

    @Test void completionReleasesBeforeTheNextEmbeddingOrChatSubscribes() {
        validEmbedding(); chatResponse=Flux.just("answer");
        var embed=embeddings(); var chat=chat();
        StepVerifier.create(embed.embed("first").then(embed.embed("second")).then(chat.complete(request)))
            .expectNext("answer").verifyComplete();
        assertThat(embeddingCalls.get()).isEqualTo(2);
    }

    @Test void errorsReleaseBothKindsOfRequest() {
        embeddingResponse=Mono.error(new IllegalStateException("test failure"));
        StepVerifier.create(embeddings().embed("document")).expectError().verify();
        chatResponse=Flux.error(new IllegalStateException("test failure"));
        StepVerifier.create(chat().stream(request)).expectError().verify();
        validEmbedding();
        assertThat(embeddings().embed("retry").block()).hasSize(2);
    }

    @Test void timeoutsReleaseBothKindsOfRequest() {
        StepVerifier.withVirtualTime(() -> embeddings().embed("document"))
            .thenAwait(Duration.ofSeconds(31)).expectError(java.util.concurrent.TimeoutException.class).verify();
        StepVerifier.withVirtualTime(() -> chat().stream(request))
            .thenAwait(Duration.ofSeconds(6)).expectErrorSatisfies(e -> busy(e,"MODEL_TIMEOUT")).verify();
        validEmbedding();
        assertThat(embeddings().embed("retry").block()).hasSize(2);
    }

    @Test void embeddingRequiresAllConfiguredChatSlotsAndLeasesCloseOnlyOnce() {
        var settings2=new JarvisProperties("",settings.ollamaUrl(),settings.chatModel(),8192,384,2,
            settings.connectTimeout(),settings.responseTimeout(),settings.healthTimeout(),"30s");
        var gate=new ModelAdmission(settings2);
        var first=gate.chat(); var second=gate.chat();
        assertThatThrownBy(gate::embedding).satisfies(e -> busy(e,"EMBEDDING_BUSY"));
        first.close(); first.close();
        assertThatThrownBy(gate::embedding).satisfies(e -> busy(e,"EMBEDDING_BUSY"));
        second.close();
        try(var embedding=gate.embedding()) {
            assertThatThrownBy(gate::chat).satisfies(e -> busy(e,"MODEL_BUSY"));
        }
        try(var recovered=gate.chat()) { assertThat(recovered).isNotNull(); }
    }
}

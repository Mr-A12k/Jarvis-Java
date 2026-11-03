package com.jarvis.web;

import com.jarvis.chat.*;
import com.jarvis.common.ApiErrors;
import com.jarvis.knowledge.KnowledgeService;
import com.jarvis.knowledge.LearnedKnowledge;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.*;

@RestController
@RequestMapping("/api/v1/web")
public class WebController {
    private final WebSearch search;
    private final ChatService chat;
    private final KnowledgeService knowledgeService;

    public WebController(WebSearch search, ChatService chat) {
        this(search, chat, null);
    }

    @Autowired
    public WebController(WebSearch search, ChatService chat, @Autowired(required = false) KnowledgeService knowledgeService) {
        this.search = search;
        this.chat = chat;
        this.knowledgeService = knowledgeService;
    }

    public record Request(
            @NotBlank @Size(max=300) String message,
            @Size(max=6) List<ChatRequest.Turn> history
    ) {
        public Request {
            history = history == null ? List.of() : List.copyOf(history);
        }
        public Request(String message) {
            this(message, List.of());
        }
    }
    public record Data(String text, List<WebSearch.Source> sources, String code, String message) {}

    public record ProviderInfo(String id, String displayName, String description, boolean active) {}
    public record ProviderUpdateRequest(String provider) {}

    @GetMapping("/providers")
    public List<ProviderInfo> getProviders() {
        SearchProvider current = search.getActiveProvider();
        return java.util.Arrays.stream(SearchProvider.values())
                .map(p -> new ProviderInfo(p.name(), p.displayName(), p.description(), p == current))
                .toList();
    }

    @PostMapping("/provider")
    public ProviderInfo setProvider(@RequestBody ProviderUpdateRequest req) {
        SearchProvider selected = SearchProvider.fromString(req != null ? req.provider() : null);
        search.setActiveProvider(selected);
        return new ProviderInfo(selected.name(), selected.displayName(), selected.description(), true);
    }

    @PostMapping(value="/stream", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Data>> stream(@Valid @RequestBody Request request) {
        String resolvedQuery = ChatController.resolveContextualQuery(request.message(), request.history());
        var fact = new LocalFacts().answer(resolvedQuery);
        if (fact.isPresent()) {
            return Flux.just(event("delta", new Data(fact.get(), null, null, null)), event("done", new Data(null, null, null, null)));
        }

        // 1. Check if JARVIS already researched and learned this from the internet previously
        if (knowledgeService != null) {
            Optional<LearnedKnowledge> recalled = knowledgeService.recall(resolvedQuery);
            if (recalled.isPresent()) {
                boolean isTamil = PromptFactory.isTamil(resolvedQuery);
                String recalledText = knowledgeService.formatRecalledAnswer(recalled.get(), isTamil);
                return Flux.just(
                        event("delta", new Data(recalledText, null, null, null)),
                        event("done", new Data(null, null, null, null))
                );
            }
        }

        // 2. Perform fresh Internet Research
        return search.search(resolvedQuery)
                .flatMap(sources -> new LocalWebDecision(chat).select(resolvedQuery, sources))
                .flatMapMany(sources -> {
                    var first = Flux.just(event("sources", new Data(null, sources, null, null)));
                    if (sources.isEmpty()) {
                        return first.concatWith(Flux.just(
                                event("delta", new Data("No verified web search evidence was found for this query.", null, null, null))
                        ));
                    }

                    // Keep snippets within the small local model's conservative UTF-8 budget.
                    String context = LocalWebDecision.context(sources, 320);
                    StringBuilder answerBuffer = new StringBuilder();

                    return first.concatWith(
                            chat.stream(new ChatRequest(resolvedQuery, request.history()), context)
                                    .doOnNext(answerBuffer::append)
                                    .map(text -> event("delta", new Data(text, null, null, null)))
                                    .doFinally(signal -> {
                                        // 3. Automatically learn and commit the newly synthesized answer to long-term memory!
                                        if (knowledgeService != null && !answerBuffer.isEmpty() && !sources.isEmpty()) {
                                            List<String> urls = sources.stream().map(WebSearch.Source::url).filter(Objects::nonNull).toList();
                                            String lang = PromptFactory.isTamil(resolvedQuery) ? "ta" : "en";
                                            knowledgeService.learn(resolvedQuery, answerBuffer.toString(), urls, lang);
                                        }
                                    })
                    );
                })
                .concatWithValues(event("done", new Data(null, null, null, null)))
                .onErrorResume(error -> {
                    var safe = ApiErrors.modelError(error);
                    return Mono.just(event("error", new Data(null, null, safe.code(), safe.getMessage())));
                });
    }

    private static ServerSentEvent<Data> event(String type, Data data) {
        return ServerSentEvent.<Data>builder(data).event(type).build();
    }
}

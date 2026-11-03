package com.jarvis.knowledge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for inspecting and querying learned knowledge.
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    public record LearnRequest(
            @NotBlank String query,
            @NotBlank String answer,
            List<String> sources,
            String language
    ) {}

    public record RecallRequest(@NotBlank String query) {}

    @GetMapping
    public Mono<List<LearnedKnowledge>> list(@RequestParam(defaultValue = "30") int limit) {
        return Mono.fromCallable(() -> knowledgeService.listRecent(limit))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/recall")
    public Mono<Map<String, Object>> recall(@Valid @RequestBody RecallRequest request) {
        return Mono.fromCallable(() -> {
            Optional<LearnedKnowledge> match = knowledgeService.recall(request.query());
            if (match.isPresent()) {
                return Map.<String, Object>of("found", true, "knowledge", match.get());
            } else {
                return Map.<String, Object>of("found", false);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/learn")
    public Mono<LearnedKnowledge> learn(@Valid @RequestBody LearnRequest request) {
        return Mono.fromCallable(() -> knowledgeService.learn(
                request.query(),
                request.answer(),
                request.sources(),
                request.language()
        )).subscribeOn(Schedulers.boundedElastic());
    }
}

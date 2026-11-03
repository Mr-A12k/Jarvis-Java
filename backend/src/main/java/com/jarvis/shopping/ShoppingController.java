package com.jarvis.shopping;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/shopping")
public class ShoppingController {

    private final ShoppingService shoppingService;

    public ShoppingController(ShoppingService shoppingService) {
        this.shoppingService = shoppingService;
    }

    public record CriteriaRequest(@NotBlank @Size(max = 1000) String text) {}
    public record CompareRequest(ShoppingCriteria criteria, List<ProductItem> items) {}

    @PostMapping("/criteria")
    public Mono<ShoppingCriteria> parseCriteria(@Valid @RequestBody CriteriaRequest request) {
        return Mono.fromCallable(() -> shoppingService.parseCriteria(request.text()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/search")
    public Mono<List<ProductItem>> search(@RequestBody ShoppingCriteria criteria) {
        return Mono.fromCallable(() -> shoppingService.searchProducts(criteria))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/compare")
    public Mono<ComparisonMatrix> compare(@RequestBody CompareRequest request) {
        return Mono.fromCallable(() -> {
            var items = (request.items() != null && !request.items().isEmpty())
                    ? request.items()
                    : shoppingService.searchProducts(request.criteria());
            return shoppingService.compare(items, request.criteria());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/markdown")
    public Mono<Map<String, String>> markdown(@RequestBody ComparisonMatrix matrix) {
        return Mono.fromCallable(() -> Map.of("markdown", shoppingService.formatMarkdownTable(matrix)))
                .subscribeOn(Schedulers.boundedElastic());
    }
}

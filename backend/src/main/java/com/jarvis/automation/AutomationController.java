package com.jarvis.automation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/automation")
public class AutomationController {

    private final BrowserService browserService;

    public AutomationController(BrowserService browserService) {
        this.browserService = browserService;
    }

    public record NavigateRequest(
            @NotBlank String url,
            @Min(1000) @Max(60000) Integer timeoutMs
    ) {}

    public record SnapshotRequest(
            @NotBlank String url,
            Boolean fullPage,
            @Min(1000) @Max(60000) Integer timeoutMs
    ) {}

    public record StepInput(
            @jakarta.validation.constraints.NotNull BrowserService.ActionType type,
            String selector,
            String value,
            Integer timeoutMs
    ) {}

    public record InteractRequest(
            @NotBlank String url,
            @jakarta.validation.constraints.NotEmpty @jakarta.validation.constraints.Size(max = 10) List<@Valid StepInput> steps,
            @Min(1000) @Max(60000) Integer timeoutMs
    ) {}

    private static <T> Mono<T> io(Callable<T> work) {
        return Mono.fromCallable(work).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/navigate")
    public Mono<BrowserService.NavigationResult> navigate(@Valid @RequestBody NavigateRequest request) {
        return io(() -> browserService.navigate(request.url(), request.timeoutMs()));
    }

    @PostMapping("/snapshot")
    public Mono<BrowserService.SnapshotResult> snapshot(@Valid @RequestBody SnapshotRequest request) {
        return io(() -> browserService.snapshot(request.url(), request.fullPage(), request.timeoutMs()));
    }

    @PostMapping("/interact")
    public Mono<BrowserService.InteractiveResult> interact(@Valid @RequestBody InteractRequest request) {
        List<BrowserService.ActionStep> steps = request.steps().stream()
                .map(s -> new BrowserService.ActionStep(s.type(), s.selector(), s.value(), s.timeoutMs()))
                .toList();
        return io(() -> browserService.interact(request.url(), steps, request.timeoutMs()));
    }

    @GetMapping("/audit")
    public Mono<List<Map<String, Object>>> audit(@RequestParam(defaultValue = "100") int limit) {
        return io(() -> browserService.getAuditEvents(limit));
    }
}

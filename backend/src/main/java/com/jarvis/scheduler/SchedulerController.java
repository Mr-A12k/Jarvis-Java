package com.jarvis.scheduler;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.concurrent.Callable;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/scheduler")
public class SchedulerController {

    private final SchedulerService schedulerService;

    public SchedulerController(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    public record CreateTaskRequest(
            @NotNull SchedulerService.TaskType type,
            String name,
            @Min(10) long intervalSeconds,
            String target
    ) {}

    private static <T> Mono<T> io(Callable<T> work) {
        return Mono.fromCallable(work).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/tasks")
    public Mono<List<SchedulerService.ScheduledTask>> listTasks() {
        return io(schedulerService::listTasks);
    }

    @PostMapping("/tasks")
    public Mono<SchedulerService.ScheduledTask> createTask(@Valid @RequestBody CreateTaskRequest request) {
        return io(() -> schedulerService.createTask(request.type(), request.name(), request.intervalSeconds(), request.target()));
    }

    @DeleteMapping("/tasks/{id}")
    public Mono<Boolean> deleteTask(@PathVariable String id) {
        return io(() -> schedulerService.deleteTask(id));
    }

    @PostMapping("/tasks/{id}/run")
    public Mono<SchedulerService.ExecutionRecord> runTask(@PathVariable String id) {
        return io(() -> schedulerService.runNow(id));
    }

    @GetMapping("/history")
    public Mono<List<SchedulerService.ExecutionRecord>> getHistory(@RequestParam(defaultValue = "50") int limit) {
        return io(() -> schedulerService.getHistory(limit));
    }
}

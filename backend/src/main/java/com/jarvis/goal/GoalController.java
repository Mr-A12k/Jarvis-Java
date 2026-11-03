package com.jarvis.goal;

import com.jarvis.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing endpoints for Autonomous Goal Planning and multi-agent parallel execution.
 */
@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    public record StartGoalRequest(
            @NotBlank(message = "Objective cannot be blank")
            @Size(max = 2000, message = "Objective cannot exceed 2000 characters")
            String objective,
            String executionMode
    ) {
        public StartGoalRequest(String objective) {
            this(objective, "MULTI_AGENT_PARALLEL");
        }
    }

    @PostMapping
    public Mono<GoalRun> startGoal(@Valid @RequestBody StartGoalRequest request) {
        return Mono.fromCallable(() -> goalService.startGoal(request.objective(), request.executionMode()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping
    public Mono<List<GoalRun>> listGoals() {
        return Mono.fromCallable(goalService::listGoals)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{id}")
    public Mono<GoalRun> getGoal(@PathVariable String id) {
        return Mono.fromCallable(() -> goalService.getGoal(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GOAL_NOT_FOUND", "Goal run not found: " + id)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{id}/cancel")
    public Mono<Map<String, Object>> cancelGoal(@PathVariable String id) {
        return Mono.fromCallable(() -> {
            boolean cancelled = goalService.cancelGoal(id);
            return Map.<String, Object>of("id", id, "cancelled", cancelled);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}

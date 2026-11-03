package com.jarvis.goal;

import java.time.Instant;
import java.util.List;

/**
 * Represents the state and history of an autonomous goal execution session.
 * Supports multi-agent parallel execution across specialized sub-agents.
 */
public record GoalRun(
        String id,
        String title,
        String objective,
        String executionMode, // "MULTI_AGENT_PARALLEL", "SEQUENTIAL"
        List<String> activeAgents,
        List<PlanStep> steps,
        String status, // "PLANNING", "RUNNING", "COMPLETED", "FAILED", "CANCELLED", "WAITING_APPROVAL"
        String finalReport,
        Instant createdAt,
        Instant completedAt
) {
    // Backward-compatible constructor for 8-arg calls
    public GoalRun(
            String id,
            String title,
            String objective,
            List<PlanStep> steps,
            String status,
            String finalReport,
            Instant createdAt,
            Instant completedAt
    ) {
        this(
                id,
                title,
                objective,
                "MULTI_AGENT_PARALLEL",
                extractActiveAgents(steps),
                steps,
                status,
                finalReport,
                createdAt,
                completedAt
        );
    }

    public GoalRun withSteps(List<PlanStep> nextSteps, String nextStatus, String report, Instant completed) {
        return new GoalRun(
                id,
                title,
                objective,
                executionMode != null ? executionMode : "MULTI_AGENT_PARALLEL",
                extractActiveAgents(nextSteps),
                nextSteps,
                nextStatus,
                report,
                createdAt,
                completed
        );
    }

    public GoalRun withStepsAndAgents(List<PlanStep> nextSteps, String nextStatus, String report, Instant completed, List<String> agents) {
        return new GoalRun(
                id,
                title,
                objective,
                executionMode != null ? executionMode : "MULTI_AGENT_PARALLEL",
                agents != null ? agents : extractActiveAgents(nextSteps),
                nextSteps,
                nextStatus,
                report,
                createdAt,
                completed
        );
    }

    public static List<String> extractActiveAgents(List<PlanStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of("Researcher Agent", "Coder Agent", "Validator Agent");
        }
        return steps.stream()
                .map(PlanStep::agentName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
    }
}

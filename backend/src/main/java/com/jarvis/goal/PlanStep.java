package com.jarvis.goal;

import java.util.List;
import java.util.Map;

/**
 * A single discrete, verifiable task within an autonomous multi-agent goal plan.
 * Executed by specialized parallel sub-agents (e.g., RESEARCHER, CODER, VALIDATOR, SYSTEM_ANALYST).
 */
public record PlanStep(
        int stepIndex,
        int stage,
        String assignedAgent, // "RESEARCHER", "CODER", "VALIDATOR", "SYSTEM_ANALYST", "COORDINATOR"
        String agentName,     // "Researcher Agent", "Coder Agent", "Validator Agent", etc.
        String title,
        String actionType,    // "RESEARCH_WEB", "CODE_GENERATE", "CODE_VALIDATE", "SECURITY_AUDIT", "FACT_VERIFY", "SHOPPING_SEARCH", "FILE_SEARCH", "SYSTEM_METRICS", "TOOL_CALL", "SYNTHESIZE"
        Map<String, Object> input,
        String status,        // "PENDING", "RUNNING", "COMPLETED", "FAILED", "WAITING_APPROVAL"
        String output,
        String error,
        Long durationMs,
        List<Integer> dependencies,
        int parallelGroup
) {
    // Backward-compatible constructor for 8-arg legacy calls
    public PlanStep(
            int stepIndex,
            String title,
            String actionType,
            Map<String, Object> input,
            String status,
            String output,
            String error,
            Long durationMs
    ) {
        this(
                stepIndex,
                1,
                deriveAgent(actionType),
                deriveAgentName(actionType),
                title,
                actionType,
                input != null ? input : Map.of(),
                status,
                output,
                error,
                durationMs,
                List.of(),
                1
        );
    }

    public PlanStep withStatus(String newStatus, String newOutput, String newError, Long duration) {
        return new PlanStep(
                stepIndex,
                stage,
                assignedAgent,
                agentName,
                title,
                actionType,
                input,
                newStatus,
                newOutput,
                newError,
                duration,
                dependencies,
                parallelGroup
        );
    }

    public static String deriveAgent(String actionType) {
        if (actionType == null) return "RESEARCHER";
        return switch (actionType) {
            case "CODE_GENERATE" -> "CODER";
            case "CODE_VALIDATE", "SECURITY_AUDIT", "FACT_VERIFY" -> "VALIDATOR";
            case "SYSTEM_METRICS" -> "SYSTEM_ANALYST";
            case "SYNTHESIZE" -> "COORDINATOR";
            case "SHOPPING_SEARCH", "FILE_SEARCH", "RESEARCH_WEB", "TOOL_CALL" -> "RESEARCHER";
            default -> "RESEARCHER";
        };
    }

    public static String deriveAgentName(String actionType) {
        return switch (deriveAgent(actionType)) {
            case "CODER" -> "Coder Agent";
            case "VALIDATOR" -> "Validator Agent";
            case "SYSTEM_ANALYST" -> "System Analyst Agent";
            case "COORDINATOR" -> "Lead Coordinator Agent";
            default -> "Researcher Agent";
        };
    }
}

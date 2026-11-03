package com.jarvis.coding;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Context Compactor & Sliding Window Manager for Local Small Models (1.5B / 7B parameters).
 * Ensures that multi-hour, multi-turn coding sessions do not exhaust the context window
 * by summarizing past turns into structured milestone checkpoints while keeping recent turns verbatim.
 */
@Component
public class ContextCompactor {

    public record TurnSummary(int stepIndex, String action, String outcome) {}

    public record CompactedContext(
            String objective,
            String milestoneSummary,
            List<TurnSummary> recentTurns,
            String activeErrorTrace
    ) {}

    private static final int SLIDING_WINDOW_SIZE = 5;

    public String buildPromptContext(
            String objective,
            List<CodingAgentService.StepRecord> history,
            String repoSummary,
            String activeErrorTrace) {

        StringBuilder sb = new StringBuilder();
        sb.append("### MISSION OBJECTIVE:\n").append(objective).append("\n\n");

        if (repoSummary != null && !repoSummary.isBlank()) {
            sb.append("### REPOSITORY OVERVIEW:\n").append(repoSummary).append("\n\n");
        }

        if (history != null && !history.isEmpty()) {
            int total = history.size();
            int cutoff = Math.max(0, total - SLIDING_WINDOW_SIZE);

            // Summarize older steps
            if (cutoff > 0) {
                sb.append("### COMPLETED MILESTONE SUMMARY (Steps 1 to ").append(cutoff).append("):\n");
                for (int i = 0; i < cutoff; i++) {
                    var step = history.get(i);
                    sb.append("- Step ").append(step.index()).append(" [").append(step.type()).append("]: ")
                            .append(step.thoughtSummary() != null ? step.thoughtSummary() : step.action())
                            .append(" -> ").append(step.status()).append("\n");
                }
                sb.append("\n");
            }

            // Provide recent steps in full detail
            sb.append("### RECENT EXECUTION TRACE (Last ").append(Math.min(total, SLIDING_WINDOW_SIZE)).append(" Steps):\n");
            for (int i = cutoff; i < total; i++) {
                var step = history.get(i);
                sb.append("#### [Step ").append(step.index()).append("] ").append(step.type()).append(" (Status: ").append(step.status()).append(")\n");
                if (step.thought() != null && !step.thought().isBlank()) {
                    sb.append("**Reasoning:** ").append(step.thought()).append("\n");
                }
                sb.append("**Action:** ").append(step.action()).append("\n");
                if (step.output() != null && !step.output().isBlank()) {
                    String out = step.output().length() > 600 ? step.output().substring(0, 600) + "...[truncated]" : step.output();
                    sb.append("**Observation:** ").append(out).append("\n");
                }
                sb.append("\n");
            }
        }

        if (activeErrorTrace != null && !activeErrorTrace.isBlank()) {
            sb.append("### ACTIVE ERROR / TEST FAILURE TO FIX:\n");
            sb.append("```\n").append(activeErrorTrace.length() > 1000 ? activeErrorTrace.substring(0, 1000) + "...[truncated]" : activeErrorTrace).append("\n```\n\n");
            sb.append("Analyze this error carefully, locate the bug in the code, and apply a surgical fix.\n");
        }

        return sb.toString();
    }
}

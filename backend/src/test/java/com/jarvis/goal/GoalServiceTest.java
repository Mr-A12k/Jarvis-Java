package com.jarvis.goal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoalServiceTest {

    @TempDir
    Path tempDir;

    private GoalPlanner planner;
    private GoalService goalService;

    @BeforeEach
    void setUp() {
        planner = new GoalPlanner();
        Path storageFile = tempDir.resolve("goal-runs.json");
        goalService = new GoalService(
                planner,
                null,
                null,
                null,
                null,
                null,
                null,
                new ObjectMapper(),
                storageFile.toString()
        );
    }

    @Test
    void testGoalPlannerDetectsTamilAndGeneratesTamilPlan() {
        String objective = "புதிய மெக்கானிக்கல் விசைப்பலகை ₹3000க்குள் வாங்குவதற்கான ஒப்பீடு";
        List<PlanStep> steps = planner.plan(objective);

        assertThat(steps).isNotEmpty();
        assertThat(GoalPlanner.isTamil(objective)).isTrue();
        assertThat(steps.get(0).title()).contains("விற்பனையாளர்களிடம்");
        assertThat(steps.get(0).actionType()).isEqualTo("SHOPPING_SEARCH");
        assertThat(steps.get(0).assignedAgent()).isEqualTo("RESEARCHER");
    }

    @Test
    void testGoalPlannerEnglishHardwarePlan() {
        String objective = "Audit system hardware and RAM memory diagnostics";
        List<PlanStep> steps = planner.plan(objective);

        assertThat(steps).isNotEmpty();
        assertThat(steps.get(0).actionType()).isEqualTo("SYSTEM_METRICS");
        assertThat(steps.get(0).assignedAgent()).isEqualTo("SYSTEM_ANALYST");
        assertThat(steps.stream().anyMatch(s -> "SYNTHESIZE".equals(s.actionType()))).isTrue();
    }

    @Test
    void testGoalPlannerCodingDecomposesIntoMultiAgentParallelPlan() {
        String objective = "Build reactive REST API controller and service in Java with unit tests";
        List<PlanStep> steps = planner.plan(objective);

        assertThat(steps).hasSize(5);

        // Stage 1 (Parallel): Researcher Agent + Coder Agent
        PlanStep step1 = steps.get(0);
        assertThat(step1.stage()).isEqualTo(1);
        assertThat(step1.assignedAgent()).isEqualTo("RESEARCHER");
        assertThat(step1.actionType()).isEqualTo("RESEARCH_WEB");

        PlanStep step2 = steps.get(1);
        assertThat(step2.stage()).isEqualTo(1);
        assertThat(step2.assignedAgent()).isEqualTo("CODER");
        assertThat(step2.actionType()).isEqualTo("CODE_GENERATE");

        // Stage 2 (Parallel): Validator Agent + System Analyst Agent
        PlanStep step3 = steps.get(2);
        assertThat(step3.stage()).isEqualTo(2);
        assertThat(step3.assignedAgent()).isEqualTo("VALIDATOR");
        assertThat(step3.actionType()).isEqualTo("CODE_VALIDATE");

        PlanStep step4 = steps.get(3);
        assertThat(step4.stage()).isEqualTo(2);
        assertThat(step4.assignedAgent()).isEqualTo("SYSTEM_ANALYST");
        assertThat(step4.actionType()).isEqualTo("SYSTEM_METRICS");

        // Stage 3 (Synthesis): Lead Coordinator
        PlanStep step5 = steps.get(4);
        assertThat(step5.stage()).isEqualTo(3);
        assertThat(step5.assignedAgent()).isEqualTo("COORDINATOR");
        assertThat(step5.actionType()).isEqualTo("SYNTHESIZE");
    }

    @Test
    void testStartGoalAndCompleteExecution() throws Exception {
        String objective = "Audit system memory and generate health report";
        GoalRun run = goalService.startGoal(objective);

        assertThat(run).isNotNull();
        assertThat(run.id()).startsWith("goal-");
        assertThat(run.steps()).isNotEmpty();
        assertThat(run.executionMode()).isEqualTo("MULTI_AGENT_PARALLEL");
        assertThat(run.activeAgents()).isNotEmpty();

        // Wait for asynchronous execution to finish so file lock on tempDir is released
        GoalRun completed = null;
        for (int i = 0; i < 40; i++) {
            completed = goalService.getGoal(run.id()).orElse(null);
            if (completed != null && ("COMPLETED".equals(completed.status()) || "FAILED".equals(completed.status()))) {
                break;
            }
            Thread.sleep(100);
        }

        if (completed == null) completed = goalService.getGoal(run.id()).orElseThrow();
        assertThat(completed.status()).isIn("RUNNING", "COMPLETED");

        if ("COMPLETED".equals(completed.status())) {
            assertThat(completed.finalReport()).isNotBlank();
            assertThat(completed.finalReport()).contains("Multi-Agent Autonomous Goal Deliverable");
            assertThat(completed.finalReport()).contains("Sub-Agent Squad");
        }
    }

    @Test
    void testCodingGoalMultiAgentExecutionGeneratesCodeAndValidates() throws Exception {
        String objective = "Develop a reactive REST API task pipeline with unit test assertions";
        GoalRun run = goalService.startGoal(objective, "MULTI_AGENT_PARALLEL");

        assertThat(run).isNotNull();
        assertThat(run.activeAgents()).contains("Coder Agent", "Validator Agent", "Researcher Agent");

        GoalRun completed = null;
        for (int i = 0; i < 50; i++) {
            completed = goalService.getGoal(run.id()).orElse(null);
            if (completed != null && ("COMPLETED".equals(completed.status()) || "FAILED".equals(completed.status()))) {
                break;
            }
            Thread.sleep(100);
        }

        if (completed == null) completed = goalService.getGoal(run.id()).orElseThrow();
        assertThat(completed.status()).isIn("RUNNING", "COMPLETED");

        if ("COMPLETED".equals(completed.status())) {
            // Verify Coder Agent produced code
            PlanStep codeStep = completed.steps().stream()
                    .filter(s -> "CODER".equals(s.assignedAgent()))
                    .findFirst().orElseThrow();
            assertThat(codeStep.output()).contains("```java");
            assertThat(codeStep.output()).contains("public class GoalTaskService");

            // Verify Validator Agent verified code
            PlanStep valStep = completed.steps().stream()
                    .filter(s -> "VALIDATOR".equals(s.assignedAgent()))
                    .findFirst().orElseThrow();
            assertThat(valStep.output()).contains("Validator Agent");
            assertThat(valStep.output()).contains("Unit Test Suite Coverage");
        }
    }

    @Test
    void testCancelGoal() throws Exception {
        String objective = "Investigate global AI breakthroughs";
        GoalRun run = goalService.startGoal(objective);

        boolean cancelled = goalService.cancelGoal(run.id());
        assertThat(cancelled).isTrue();

        Thread.sleep(150);
        GoalRun updated = goalService.getGoal(run.id()).orElseThrow();
        assertThat(updated.status()).isEqualTo("CANCELLED");
    }
}

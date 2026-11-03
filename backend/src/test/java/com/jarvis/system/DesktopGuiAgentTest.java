package com.jarvis.system;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DesktopGuiAgentTest {

    private DesktopGuiAgentService agentService;
    private DesktopController controller;

    @BeforeEach
    void setUp() {
        SystemAppLauncher launcher = new SystemAppLauncher(false);
        agentService = new DesktopGuiAgentService(null, launcher);
        DesktopSystemService desktopService = new DesktopSystemService(false);
        desktopService.setAgentService(agentService);
        controller = new DesktopController(desktopService, agentService);
    }

    @Test
    void testCursorAndScreenInfo() {
        var info = agentService.getCursorAndScreen();
        assertThat(info).isNotNull();
        assertThat(info.screenWidth()).isGreaterThan(0);
        assertThat(info.screenHeight()).isGreaterThan(0);
    }

    @Test
    void testMouseAndKeyboardActions() {
        var moveRes = agentService.mouseMove(100, 100, false);
        assertThat(moveRes.actionType()).isEqualTo("MOUSE_MOVE");

        var clickRes = agentService.mouseClick("left", 1, 100, 100);
        assertThat(clickRes.actionType()).isEqualTo("MOUSE_CLICK");

        var scrollRes = agentService.mouseScroll(-2);
        assertThat(scrollRes.actionType()).isEqualTo("MOUSE_SCROLL");

        var typeRes = agentService.typeText("Test Text", 0);
        assertThat(typeRes.actionType()).isEqualTo("TYPE_TEXT");

        var keyRes = agentService.sendKey("ENTER");
        assertThat(keyRes.actionType()).isEqualTo("KEY_PRESS");

        var hotkeyRes = agentService.sendHotkey("Ctrl+S");
        assertThat(hotkeyRes.actionType()).isEqualTo("HOTKEY");
    }

    @Test
    void testActiveWindowsAndShowDesktop() {
        var windows = agentService.listActiveWindows();
        assertThat(windows).isNotEmpty();

        var desktopRes = agentService.showDesktop();
        assertThat(desktopRes.actionType()).isEqualTo("SHOW_DESKTOP");
    }

    @Test
    void testActionSequenceExecution() {
        List<DesktopGuiAgentService.DesktopGuiAction> actions = List.of(
                new DesktopGuiAgentService.DesktopGuiAction("WAIT", null, null, null, null, null, null, null, null, null, null, null, 10),
                new DesktopGuiAgentService.DesktopGuiAction("HOTKEY", null, null, null, null, null, null, null, null, null, "Ctrl+S", null, 0)
        );

        var seqRes = agentService.executeSequence("00000000-0000-0000-0000-000000000004", "Save Test", actions);
        assertThat(seqRes.success()).isTrue();
        assertThat(seqRes.actionType()).isEqualTo("SEQUENCE");
    }

    @Test
    void testSequenceStopsImmediatelyOnStepFailure() {
        // Sequence with failed focus step followed by typing step
        List<DesktopGuiAgentService.DesktopGuiAction> actions = List.of(
                new DesktopGuiAgentService.DesktopGuiAction("FOCUS_WINDOW", null, null, null, null, null, null, null, null, null, null, "", 0),
                new DesktopGuiAgentService.DesktopGuiAction("TYPE_TEXT", null, null, null, null, null, null, null, "Should never type into another app", null, null, null, 0)
        );

        var seqRes = agentService.executeSequence("00000000-0000-0000-0000-000000000005", "Failed Focus Guard Test", actions);
        assertThat(seqRes.success()).isFalse();
        assertThat(seqRes.message()).contains("Sequence failed at step 1");
        assertThat(seqRes.details().get("executedSteps")).isEqualTo(1); // Halts at step 1!
    }

    @Test
    void testCloseWindowMissingTarget() {
        var res = agentService.closeWindow("");
        assertThat(res.success()).isFalse();
        assertThat(res.message()).contains("No target window specified");
    }

    @Test
    void testWorkflowsCrudAndAudit() {
        var list = agentService.listWorkflows();
        assertThat(list).isNotEmpty();

        var saved = agentService.saveWorkflow(
                "Custom Unit Test Workflow",
                "Unit testing workflow persistence",
                "test workflow",
                List.of(new DesktopGuiAgentService.DesktopGuiAction("SHOW_DESKTOP", null, null, null, null, null, null, null, null, null, null, null, 0)),
                true
        );
        assertThat(saved).isNotNull();
        assertThat(saved.name()).isEqualTo("Custom Unit Test Workflow");

        boolean deleted = agentService.deleteWorkflow(saved.id());
        assertThat(deleted).isTrue();

        var audit = agentService.getRecentAudit(10);
        assertThat(audit).isNotNull();
    }

    @Test
    void testControllerAgentEndpoints() {
        StepVerifier.create(controller.getCursorInfo())
                .assertNext(info -> {
                    assertThat(info.screenWidth()).isGreaterThan(0);
                    assertThat(info.screenHeight()).isGreaterThan(0);
                })
                .verifyComplete();

        StepVerifier.create(controller.getActiveWindows())
                .assertNext(windows -> assertThat(windows).isNotEmpty())
                .verifyComplete();

        StepVerifier.create(controller.getWorkflows())
                .assertNext(workflows -> assertThat(workflows).isNotEmpty())
                .verifyComplete();

        StepVerifier.create(controller.executeGuiAction(
                new DesktopGuiAgentService.DesktopGuiAction("WAIT", null, null, null, null, null, null, null, null, null, null, null, 5)
        ))
                .assertNext(res -> assertThat(res.success()).isTrue())
                .verifyComplete();
    }
}

package com.jarvis.system;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class DesktopControllerTest {

    private DesktopController controller;
    private DesktopSystemService desktopService;

    @BeforeEach
    void setUp() {
        desktopService = new DesktopSystemService(false);
        controller = new DesktopController(desktopService);
    }

    @Test
    void testMediaEndpoint() {
        StepVerifier.create(controller.controlMedia(new DesktopController.MediaRequest("play_pause")))
                .assertNext(res -> {
                    assertThat(res.success()).isTrue();
                    assertThat(res.action()).isEqualTo("play_pause");
                })
                .verifyComplete();

        StepVerifier.create(controller.controlMedia(new DesktopController.MediaRequest("next")))
                .assertNext(res -> {
                    assertThat(res.success()).isTrue();
                    assertThat(res.action()).isEqualTo("next");
                })
                .verifyComplete();
    }

    @Test
    void testVolumeEndpoint() {
        StepVerifier.create(controller.controlVolume(new DesktopController.VolumeRequest("set", 65, null)))
                .assertNext(res -> {
                    assertThat(res.volume()).isEqualTo(65);
                    assertThat(res.isMuted()).isFalse();
                })
                .verifyComplete();

        StepVerifier.create(controller.getVolume())
                .assertNext(res -> assertThat(res.volume()).isEqualTo(65))
                .verifyComplete();

        StepVerifier.create(controller.controlVolume(new DesktopController.VolumeRequest("mute", null, null)))
                .assertNext(res -> assertThat(res.isMuted()).isTrue())
                .verifyComplete();
    }

    @Test
    void testAppsEndpoint() {
        StepVerifier.create(controller.getApps())
                .assertNext(apps -> {
                    assertThat(apps).isNotEmpty();
                    assertThat(apps).anyMatch(a -> a.id().equals("chrome"));
                })
                .verifyComplete();
    }

    @Test
    void testLaunchEndpoint() {
        StepVerifier.create(controller.launchApp(new DesktopController.LaunchRequest("notepad")))
                .assertNext(res -> {
                    assertThat(res.success()).isTrue();
                    assertThat(res.target()).isEqualTo("Notepad");
                })
                .verifyComplete();
    }

    @Test
    void testCommandEndpoint() {
        StepVerifier.create(controller.executeCommand(new DesktopController.CommandRequest("play music", "en")))
                .assertNext(res -> {
                    assertThat(res.handled()).isTrue();
                    assertThat(res.reply()).contains("Play/Pause");
                })
                .verifyComplete();

        StepVerifier.create(controller.executeCommand(new DesktopController.CommandRequest("What is 2+2?", "en")))
                .assertNext(res -> assertThat(res.handled()).isFalse())
                .verifyComplete();
    }
}

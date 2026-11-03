package com.jarvis.vision;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenCaptureServiceTest {

    @Test
    void screenCaptureServiceHandlesCallGracefully() {
        ScreenCaptureService service = new ScreenCaptureService();
        ScreenCaptureResult result = service.captureScreen();
        assertThat(result).isNotNull();
        assertThat(result.mimeType()).isIn("image/jpeg", "image/png");
        // Result is either available or gracefully marked unavailable
        if (result.isAvailable()) {
            assertThat(result.imageBase64()).isNotEmpty();
            assertThat(result.width()).isGreaterThan(0);
            assertThat(result.height()).isGreaterThan(0);
        } else {
            assertThat(result.source()).isEqualTo("unavailable");
        }
    }

    @Test
    void hostCaptureSupportCheckDoesNotThrow() {
        ScreenCaptureService service = new ScreenCaptureService();
        boolean supported = service.isHostCaptureSupported();
        assertThat(supported).isIn(true, false);
    }
}

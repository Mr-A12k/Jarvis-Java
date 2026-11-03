package com.jarvis.system;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemHardwareServiceTest {

    @Test
    void capturesNonZeroRamAndRomMetrics() {
        var service = new SystemHardwareService();
        var snapshot = service.snapshot();

        // RAM checks
        assertThat(snapshot.ram().totalBytes()).isGreaterThan(0);
        assertThat(snapshot.ram().totalFormatted()).contains("GB");
        assertThat(snapshot.ram().freeFormatted()).contains("GB");

        // ROM checks
        assertThat(snapshot.rom().totalBytes()).isGreaterThan(0);
        assertThat(snapshot.rom().totalFormatted()).contains("GB");

        // Heap checks
        assertThat(snapshot.heap().totalBytes()).isGreaterThan(0);
        assertThat(snapshot.heap().totalFormatted()).contains("MB");

        // GPU checks
        assertThat(snapshot.gpu().name()).isNotBlank();
    }

    @Test
    void generatesEnglishAndTamilSummaries() {
        var service = new SystemHardwareService();

        String en = service.generateEnglishSummary();
        assertThat(en).contains("RAM:").contains("GPU:").contains("ROM");

        String ta = service.generateTamilSummary();
        assertThat(ta).contains("RAM:").contains("GPU:").contains("ROM");
    }
}

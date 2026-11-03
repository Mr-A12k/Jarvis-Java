package com.jarvis.coding;

import com.jarvis.common.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodingExecutionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void testExecuteSafeEchoCommand() {
        CodingExecutionService service = new CodingExecutionService(tempDir.toString(), 15);
        var result = service.execute("Write-Output 'JARVIS_CODING_TEST_OK'");

        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("JARVIS_CODING_TEST_OK");
        assertThat(result.timedOut()).isFalse();
    }

    @Test
    void testDangerousCommandsAreBlocked() {
        CodingExecutionService service = new CodingExecutionService(tempDir.toString(), 15);

        assertThatThrownBy(() -> service.execute("rm -rf /"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("potentially destructive");

        assertThatThrownBy(() -> service.execute("format c:"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("potentially destructive");
    }
}

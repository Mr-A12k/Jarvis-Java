package com.jarvis.coding;

import com.jarvis.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodingWorkspaceServiceTest {

    @TempDir
    Path tempDir;

    private CodingWorkspaceService workspace;

    @BeforeEach
    void setUp() throws IOException {
        workspace = new CodingWorkspaceService(tempDir.toString());
        Files.createDirectories(tempDir.resolve("src/main/java"));
        Files.writeString(tempDir.resolve("src/main/java/Sample.java"), "public class Sample {\n    int value = 42;\n}\n");
    }

    @Test
    void testWorkspaceTreeAndSafePath() {
        var tree = workspace.getWorkspaceTree("", 3);
        assertThat(tree).isNotNull();
        assertThat(tree.isDirectory()).isTrue();
        assertThat(tree.children()).isNotEmpty();

        Path safe = workspace.resolveSafePath("src/main/java/Sample.java");
        assertThat(safe).exists();

        // Path traversal should be strictly blocked
        assertThatThrownBy(() -> workspace.resolveSafePath("../../outside.txt"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void testReadFile() {
        var res = workspace.readFile("src/main/java/Sample.java", 1, 3);
        assertThat(res.totalLines()).isGreaterThanOrEqualTo(3);
        assertThat(res.content()).contains("public class Sample");
    }

    @Test
    void testSurgicalEditFileAndDiff() {
        var writeRes = workspace.editFile("src/main/java/Sample.java", "int value = 42;", "int value = 100;");
        assertThat(writeRes.success()).isTrue();
        assertThat(writeRes.changeType()).isEqualTo("MODIFY");
        assertThat(writeRes.unifiedDiff()).contains("-    int value = 42;");
        assertThat(writeRes.unifiedDiff()).contains("+    int value = 100;");

        // Verify content on disk
        var readBack = workspace.readFile("src/main/java/Sample.java", 1, 3);
        assertThat(readBack.content()).contains("int value = 100;");
    }

    @Test
    void testWriteFileAndRollback() {
        var writeRes = workspace.writeFile("src/new/TestUtil.java", "public class TestUtil {}", false);
        assertThat(writeRes.success()).isTrue();
        assertThat(writeRes.changeType()).isEqualTo("CREATE");
        assertThat(Files.exists(tempDir.resolve("src/new/TestUtil.java"))).isTrue();

        // Rollback creation (should delete newly created file)
        boolean rollbackOk = workspace.rollbackFile("src/new/TestUtil.java", null);
        assertThat(rollbackOk).isTrue();
        assertThat(Files.exists(tempDir.resolve("src/new/TestUtil.java"))).isFalse();
    }
}

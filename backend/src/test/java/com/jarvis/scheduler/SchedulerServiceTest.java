package com.jarvis.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.ApiException;
import com.jarvis.system.SystemHardwareService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchedulerServiceTest {

    private Path tempTasksFile;
    private Path tempHistoryFile;
    private ObjectMapper json;
    private SchedulerService service;

    @BeforeEach
    void setUp() throws IOException {
        tempTasksFile = Files.createTempFile("scheduler-tasks-test", ".json");
        tempHistoryFile = Files.createTempFile("scheduler-history-test", ".jsonl");
        json = new ObjectMapper();
        service = new SchedulerService(json, null, new SystemHardwareService(), tempTasksFile.toString(), tempHistoryFile.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        service.shutdown();
        Files.deleteIfExists(tempTasksFile);
        Files.deleteIfExists(tempHistoryFile);
    }

    @Test
    void createsAndListsTasks() {
        var task = service.createTask(SchedulerService.TaskType.SYSTEM_METRICS_LOG, "Metrics Logger", 60, null);
        assertThat(task.id()).isNotNull();
        assertThat(task.name()).isEqualTo("Metrics Logger");
        assertThat(task.type()).isEqualTo(SchedulerService.TaskType.SYSTEM_METRICS_LOG);

        List<SchedulerService.ScheduledTask> list = service.listTasks();
        assertThat(list).hasSize(1);
        assertThat(list.getFirst().id()).isEqualTo(task.id());
    }

    @Test
    void rejectsInvalidInterval() {
        assertThatThrownBy(() -> service.createTask(SchedulerService.TaskType.CLEANUP_AUDIT, "Audit", 5, null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("INVALID_INTERVAL"));
    }

    @Test
    void rejectsIndexFolderWithoutTarget() {
        assertThatThrownBy(() -> service.createTask(SchedulerService.TaskType.INDEX_FOLDER, "Index", 60, ""))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("INVALID_TARGET"));
    }

    @Test
    void executesAuditCleanupAndAppendsHistory() {
        var task = service.createTask(SchedulerService.TaskType.CLEANUP_AUDIT, "Cleanup", 3600, null);
        var record = service.runNow(task.id());

        assertThat(record.status()).isEqualTo("SUCCESS");
        assertThat(record.taskId()).isEqualTo(task.id());
        assertThat(record.message()).contains("Audit logs");

        List<SchedulerService.ExecutionRecord> history = service.getHistory(10);
        assertThat(history).isNotEmpty();
        assertThat(history.getFirst().taskId()).isEqualTo(task.id());
    }

    @Test
    void executesMetricsSnapshot() {
        var task = service.createTask(SchedulerService.TaskType.SYSTEM_METRICS_LOG, "Metrics", 120, null);
        var record = service.runNow(task.id());

        assertThat(record.status()).isEqualTo("SUCCESS");
        assertThat(record.message()).contains("System hardware snapshot");
    }

    @Test
    void deletesTask() {
        var task = service.createTask(SchedulerService.TaskType.CLEANUP_AUDIT, "To Delete", 60, null);
        assertThat(service.listTasks()).hasSize(1);

        boolean deleted = service.deleteTask(task.id());
        assertThat(deleted).isTrue();
        assertThat(service.listTasks()).isEmpty();
    }
}

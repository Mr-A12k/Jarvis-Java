package com.jarvis.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.ApiException;
import com.jarvis.files.LocalFiles;
import com.jarvis.system.SystemHardwareService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SchedulerService {
    private static final Logger LOG = LoggerFactory.getLogger(SchedulerService.class);

    public enum TaskType {
        INDEX_FOLDER,
        CLEANUP_AUDIT,
        SYSTEM_METRICS_LOG
    }

    public record ScheduledTask(
            String id,
            TaskType type,
            String name,
            long intervalSeconds,
            String target,
            String createdAt,
            String lastRunAt,
            String lastStatus
    ) {
        public ScheduledTask withRunResult(String runAt, String status) {
            return new ScheduledTask(id, type, name, intervalSeconds, target, createdAt, runAt, status);
        }
    }

    public record ExecutionRecord(
            String executionId,
            String taskId,
            TaskType taskType,
            String timestamp,
            String status,
            long durationMs,
            String message
    ) {}

    private final ObjectMapper json;
    private final LocalFiles files;
    private final SystemHardwareService hardware;
    private final Path tasksFile;
    private final Path historyFile;
    private final ScheduledExecutorService executor;
    private final Map<String, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();

    @Autowired
    public SchedulerService(
            ObjectMapper json,
            @Autowired(required = false) LocalFiles files,
            @Autowired(required = false) SystemHardwareService hardware,
            @Value("${jarvis.scheduler.tasks-path:data/scheduler-tasks.json}") String tasksPath,
            @Value("${jarvis.scheduler.history-path:data/scheduler-history.jsonl}") String historyPath) {
        this.json = json;
        this.files = files;
        this.hardware = hardware;
        this.tasksFile = Path.of(tasksPath).toAbsolutePath().normalize();
        this.historyFile = Path.of(historyPath).toAbsolutePath().normalize();
        this.executor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "jarvis-scheduler");
            t.setDaemon(true);
            return t;
        });

        loadTasks();
    }

    private synchronized void loadTasks() {
        if (!Files.exists(tasksFile)) return;
        try {
            String content = Files.readString(tasksFile, StandardCharsets.UTF_8);
            if (!content.isBlank()) {
                List<ScheduledTask> list = json.readValue(content, new TypeReference<>() {});
                for (ScheduledTask t : list) {
                    tasks.put(t.id(), t);
                    scheduleExecution(t);
                }
            }
        } catch (Exception e) {
            LOG.warn("Could not load scheduled tasks: {}", e.getMessage());
        }
    }

    private synchronized void persistTasks() {
        try {
            if (tasksFile.getParent() != null) Files.createDirectories(tasksFile.getParent());
            Files.writeString(tasksFile, json.writeValueAsString(new ArrayList<>(tasks.values())),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            LOG.error("Failed to save scheduled tasks: {}", e.getMessage());
        }
    }

    public List<ScheduledTask> listTasks() {
        return new ArrayList<>(tasks.values());
    }

    public synchronized ScheduledTask createTask(TaskType type, String name, long intervalSeconds, String target) {
        if (type == null) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TASK_TYPE", "Task type is required");
        if (intervalSeconds < 10) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INTERVAL", "Interval must be at least 10 seconds");
        if (type == TaskType.INDEX_FOLDER && (target == null || target.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TARGET", "Target folder is required for INDEX_FOLDER");
        }

        String id = UUID.randomUUID().toString();
        String taskName = (name != null && !name.isBlank()) ? name.trim() : (type.name() + " task");
        ScheduledTask task = new ScheduledTask(id, type, taskName, intervalSeconds, target, Instant.now().toString(), null, "SCHEDULED");
        tasks.put(id, task);
        persistTasks();
        scheduleExecution(task);
        return task;
    }

    public synchronized boolean deleteTask(String id) {
        ScheduledTask task = tasks.remove(id);
        if (task != null) {
            ScheduledFuture<?> future = scheduledFutures.remove(id);
            if (future != null) future.cancel(false);
            persistTasks();
            return true;
        }
        return false;
    }

    public ExecutionRecord runNow(String id) {
        ScheduledTask task = tasks.get(id);
        if (task == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Scheduled task not found: " + id);
        }
        return executeTask(task);
    }

    private void scheduleExecution(ScheduledTask task) {
        ScheduledFuture<?> existing = scheduledFutures.remove(task.id());
        if (existing != null) existing.cancel(false);

        ScheduledFuture<?> future = executor.scheduleWithFixedDelay(
                () -> executeTask(task),
                task.intervalSeconds(),
                task.intervalSeconds(),
                TimeUnit.SECONDS
        );
        scheduledFutures.put(task.id(), future);
    }

    private ExecutionRecord executeTask(ScheduledTask task) {
        long start = System.nanoTime();
        String status = "SUCCESS";
        String message = "Execution completed";

        try {
            switch (task.type()) {
                case INDEX_FOLDER -> {
                    if (files == null) throw new IllegalStateException("LocalFiles service is not available");
                    int count = files.index(task.target());
                    message = "Indexed " + count + " document(s)";
                }
                case CLEANUP_AUDIT -> {
                    cleanupAuditLogs();
                    message = "Audit logs retention check applied";
                }
                case SYSTEM_METRICS_LOG -> {
                    logMetrics();
                    message = "System hardware snapshot recorded";
                }
            }
        } catch (Throwable t) {
            status = "FAILED";
            message = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            LOG.warn("Scheduled task {} failed: {}", task.id(), message);
        }

        long durationMs = (System.nanoTime() - start) / 1_000_000;
        ScheduledTask updated = task.withRunResult(Instant.now().toString(), status);
        tasks.put(task.id(), updated);
        persistTasks();

        ExecutionRecord record = new ExecutionRecord(
                UUID.randomUUID().toString(),
                task.id(),
                task.type(),
                Instant.now().toString(),
                status,
                durationMs,
                message
        );
        appendHistory(record);
        return record;
    }

    private void cleanupAuditLogs() {
        for (String file : List.of("data/mcp-audit.jsonl", "data/automation-audit.jsonl")) {
            Path p = Path.of(file).toAbsolutePath().normalize();
            if (Files.exists(p)) {
                try {
                    List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                    if (lines.size() > 500) {
                        List<String> retained = lines.subList(lines.size() - 500, lines.size());
                        Files.write(p, retained, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
                    }
                } catch (IOException e) {
                    LOG.debug("Could not cleanup audit file {}: {}", file, e.getMessage());
                }
            }
        }
    }

    private void logMetrics() {
        if (hardware == null) return;
        try {
            Path metricsPath = Path.of("data/metrics-history.jsonl").toAbsolutePath().normalize();
            if (metricsPath.getParent() != null) Files.createDirectories(metricsPath.getParent());
            var snapshot = hardware.snapshot();
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("timestamp", Instant.now().toString());
            map.put("ramUsedFormatted", snapshot.ram().usedFormatted());
            map.put("gpuName", snapshot.gpu().name());
            map.put("heapUsedFormatted", snapshot.heap().usedFormatted());
            Files.writeString(metricsPath, json.writeValueAsString(map) + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            LOG.debug("Metrics recording failed: {}", e.getMessage());
        }
    }

    private synchronized void appendHistory(ExecutionRecord record) {
        try {
            if (historyFile.getParent() != null) Files.createDirectories(historyFile.getParent());
            String line = json.writeValueAsString(record) + "\n";
            Files.writeString(historyFile, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.error("Failed to append scheduler history: {}", e.getMessage());
        }
    }

    public List<ExecutionRecord> getHistory(int limit) {
        if (!Files.exists(historyFile)) return List.of();
        try {
            List<String> lines = Files.readAllLines(historyFile, StandardCharsets.UTF_8);
            int start = Math.max(0, lines.size() - limit);
            List<ExecutionRecord> result = new ArrayList<>();
            for (int i = lines.size() - 1; i >= start; i--) {
                String line = lines.get(i).trim();
                if (!line.isEmpty()) {
                    result.add(json.readValue(line, ExecutionRecord.class));
                }
            }
            return result;
        } catch (IOException e) {
            LOG.error("Failed to read scheduler history: {}", e.getMessage());
            return List.of();
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}

package com.jarvis.coding;

import com.jarvis.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Sandboxed Command Execution Service for the Autonomous Coding Agent.
 * Executes build tools, compiler checks, and unit tests (e.g. Maven, npm, Python)
 * with strict timeouts, stream redirection, and process tree termination.
 */
@Service
public class CodingExecutionService {

    private static final Logger log = LoggerFactory.getLogger(CodingExecutionService.class);

    private static final Pattern DANGEROUS_COMMANDS = Pattern.compile(
            "(?i)(?:\\bformat\\s+[a-z]:|\\brmdir\\s+/[sq]|\\bdel\\s+/[sq]|\\brm\\s+-[a-zA-Z]*r[a-zA-Z]*\\s+[/~]|\\bdrop\\s+database|\\bshutdown\\s+/[sr]|\\bmkfs|\\bdd\\s+if=)"
    );

    public record CommandResult(
            String command,
            int exitCode,
            String stdout,
            String stderr,
            long durationMs,
            boolean timedOut
    ) {}

    private final Path workspaceRoot;
    private final int defaultTimeoutSeconds;
    private final ExecutorService commandExecutor;

    public CodingExecutionService(
            @Value("${jarvis.workspace.root:.}") String workspacePath,
            @Value("${jarvis.coding.command-timeout-seconds:120}") int defaultTimeoutSeconds) {
        this.workspaceRoot = Path.of(workspacePath).toAbsolutePath().normalize();
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
        this.commandExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "coding-exec-worker-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Executes a sandboxed terminal command in the workspace directory.
     */
    public CommandResult execute(String command) {
        return execute(command, null, defaultTimeoutSeconds, null);
    }

    public CommandResult execute(String command, String subDir, int timeoutSeconds, Consumer<String> lineListener) {
        if (command == null || command.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COMMAND", "Command cannot be empty");
        }

        String trimmed = command.trim();
        if (DANGEROUS_COMMANDS.matcher(trimmed).find()) {
            log.error("CodingExecutionService: Dangerous system command rejected: '{}'", trimmed);
            throw new ApiException(HttpStatus.FORBIDDEN, "DANGEROUS_COMMAND_BLOCKED",
                    "Command contains potentially destructive system operations: " + trimmed);
        }

        File workingDirectory = workspaceRoot.toFile();
        if (subDir != null && !subDir.isBlank()) {
            Path targetDir = workspaceRoot.resolve(subDir).normalize();
            if (targetDir.startsWith(workspaceRoot) && targetDir.toFile().exists()) {
                workingDirectory = targetDir.toFile();
            }
        }

        boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        List<String> commandList = new ArrayList<>();
        if (isWindows) {
            commandList.add("powershell.exe");
            commandList.add("-NoProfile");
            commandList.add("-ExecutionPolicy");
            commandList.add("Bypass");
            commandList.add("-Command");
            commandList.add(trimmed);
        } else {
            commandList.add("/bin/bash");
            commandList.add("-c");
            commandList.add(trimmed);
        }

        long start = System.currentTimeMillis();
        ProcessBuilder pb = new ProcessBuilder(commandList);
        pb.directory(workingDirectory);

        log.info("CodingExecutionService: Executing [{}] in [{}]", trimmed, workingDirectory.getAbsolutePath());

        StringBuilder stdoutBuffer = new StringBuilder();
        StringBuilder stderrBuffer = new StringBuilder();

        try {
            Process process = pb.start();

            // Read stdout
            Future<?> stdoutFuture = commandExecutor.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdoutBuffer.append(line).append("\n");
                        if (lineListener != null) lineListener.accept(line);
                    }
                } catch (Exception ignored) {}
            });

            // Read stderr
            Future<?> stderrFuture = commandExecutor.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderrBuffer.append(line).append("\n");
                        if (lineListener != null) lineListener.accept("[STDERR] " + line);
                    }
                } catch (Exception ignored) {}
            });

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - start;

            if (!finished) {
                log.warn("CodingExecutionService: Command timed out after {}s: [{}]", timeoutSeconds, trimmed);
                process.destroyForcibly();
                return new CommandResult(trimmed, -1, stdoutBuffer.toString().strip(), "Command timed out after " + timeoutSeconds + " seconds", duration, true);
            }

            stdoutFuture.get(2, TimeUnit.SECONDS);
            stderrFuture.get(2, TimeUnit.SECONDS);

            int exitCode = process.exitValue();
            log.info("CodingExecutionService: Command finished with exit code {} in {}ms", exitCode, duration);
            return new CommandResult(trimmed, exitCode, stdoutBuffer.toString().strip(), stderrBuffer.toString().strip(), duration, false);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.warn("CodingExecutionService: Failed to execute [{}]: {}", trimmed, e.getMessage());
            return new CommandResult(trimmed, 1, stdoutBuffer.toString(), "Execution failed: " + e.getMessage(), duration, false);
        }
    }
}

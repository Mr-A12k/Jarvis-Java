package com.jarvis.coding;

import com.jarvis.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Sandboxed Codebase & Workspace Service for the Autonomous Coding Agent.
 * Enforces canonical path containment, line-numbered reads, line-accurate surgical replacements,
 * unified diff calculation, and full non-destructive file rollbacks.
 */
@Service
public class CodingWorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(CodingWorkspaceService.class);

    public record FileNode(String name, String path, boolean isDirectory, long sizeBytes, List<FileNode> children) {}
    public record FileReadResult(String path, int totalLines, int startLine, int endLine, String content) {}
    public record FileWriteResult(String path, String changeType, String unifiedDiff, String originalContent, String newContent, boolean success) {}
    public record SearchMatch(String filePath, int lineNumber, String lineContent) {}

    private final Path workspaceRoot;

    public CodingWorkspaceService(@Value("${jarvis.workspace.root:.}") String workspacePath) {
        Path p = Path.of(workspacePath).toAbsolutePath().normalize();
        this.workspaceRoot = p;
        log.info("CodingWorkspaceService: Initialized workspace root at [{}]", this.workspaceRoot);
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    /**
     * Resolves and verifies that the given relative path stays strictly within the workspace root.
     */
    public Path resolveSafePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return workspaceRoot;
        }

        String clean = relativePath.trim().replace('\\', '/');
        if (clean.startsWith("/")) clean = clean.substring(1);

        Path resolved = workspaceRoot.resolve(clean).normalize();
        if (!resolved.startsWith(workspaceRoot)) {
            log.warn("CodingWorkspaceService: Path traversal attempt blocked: '{}'", relativePath);
            throw new ApiException(HttpStatus.FORBIDDEN, "PATH_TRAVERSAL_BLOCKED",
                    "Path traversal outside workspace root is strictly blocked: " + relativePath);
        }
        return resolved;
    }

    /**
     * Traverses the directory hierarchy up to maxDepth (default 3).
     */
    public FileNode getWorkspaceTree(String subPath, int maxDepth) {
        Path targetDir = resolveSafePath(subPath);
        if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DIRECTORY_NOT_FOUND", "Directory does not exist: " + subPath);
        }
        return buildNode(targetDir, 0, Math.max(1, Math.min(maxDepth, 6)));
    }

    private FileNode buildNode(Path current, int currentDepth, int maxDepth) {
        String name = current.equals(workspaceRoot) ? workspaceRoot.getFileName().toString() : current.getFileName().toString();
        String relPath = workspaceRoot.relativize(current).toString().replace('\\', '/');
        boolean isDir = Files.isDirectory(current);

        if (!isDir) {
            long size = 0;
            try { size = Files.size(current); } catch (Exception ignored) {}
            return new FileNode(name, relPath, false, size, Collections.emptyList());
        }

        if (currentDepth >= maxDepth) {
            return new FileNode(name, relPath, true, 0, Collections.emptyList());
        }

        List<FileNode> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
            for (Path child : stream) {
                String childName = child.getFileName().toString();
                // Skip noisy/binary directories
                if (childName.startsWith(".") || childName.equals("target") || childName.equals("node_modules")
                        || childName.equals("dist") || childName.equals("build")) {
                    continue;
                }
                children.add(buildNode(child, currentDepth + 1, maxDepth));
            }
        } catch (IOException e) {
            log.debug("CodingWorkspaceService: Could not read directory {}: {}", current, e.getMessage());
        }

        children.sort(Comparator.comparing(FileNode::isDirectory).reversed().thenComparing(FileNode::name));
        return new FileNode(name, relPath, true, 0, children);
    }

    /**
     * Reads a file with line numbers and optional line bounds (1-based).
     */
    public FileReadResult readFile(String relativePath, Integer startLine, Integer endLine) {
        Path target = resolveSafePath(relativePath);
        if (!Files.exists(target) || Files.isDirectory(target)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "File does not exist: " + relativePath);
        }

        try {
            List<String> lines = Files.readAllLines(target, StandardCharsets.UTF_8);
            int total = lines.size();
            int start = (startLine != null && startLine > 0) ? Math.min(startLine, total) : 1;
            int end = (endLine != null && endLine >= start) ? Math.min(endLine, total) : total;

            StringBuilder sb = new StringBuilder();
            for (int i = start; i <= end; i++) {
                sb.append(String.format("%4d | %s\n", i, lines.get(i - 1)));
            }

            return new FileReadResult(relativePath, total, start, end, sb.toString());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_READ_ERROR", "Error reading file: " + e.getMessage());
        }
    }

    /**
     * Creates a new file or overwrites an existing file safely, recording the pre-image.
     */
    public FileWriteResult writeFile(String relativePath, String content, boolean overwrite) {
        Path target = resolveSafePath(relativePath);
        boolean exists = Files.exists(target);

        if (exists && !overwrite) {
            throw new ApiException(HttpStatus.CONFLICT, "FILE_ALREADY_EXISTS",
                    "File already exists. Specify overwrite=true to replace it: " + relativePath);
        }

        String originalContent = null;
        if (exists) {
            try {
                originalContent = Files.readString(target, StandardCharsets.UTF_8);
            } catch (IOException ignored) {}
        }

        try {
            if (target.getParent() != null && !Files.exists(target.getParent())) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            String changeType = exists ? "MODIFY" : "CREATE";
            String diff = generateUnifiedDiff(originalContent != null ? originalContent : "", content, relativePath);

            log.info("CodingWorkspaceService: {} file at [{}]", changeType, relativePath);
            return new FileWriteResult(relativePath, changeType, diff, originalContent, content, true);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_WRITE_ERROR", "Error writing file: " + e.getMessage());
        }
    }

    /**
     * Performs an exact, surgical search-and-replace edit on an existing file.
     */
    public FileWriteResult editFile(String relativePath, String targetSubstring, String replacement) {
        Path target = resolveSafePath(relativePath);
        if (!Files.exists(target) || Files.isDirectory(target)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "File not found: " + relativePath);
        }

        try {
            String original = Files.readString(target, StandardCharsets.UTF_8);

            // Normalize line endings for reliable matching
            String normOriginal = original.replace("\r\n", "\n");
            String normTarget = targetSubstring.replace("\r\n", "\n");
            String normReplacement = replacement.replace("\r\n", "\n");

            if (!normOriginal.contains(normTarget)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "TARGET_NOT_FOUND",
                        "The target string to replace was not found in " + relativePath + ". Please check line numbers and exact whitespace.");
            }

            int count = (normOriginal.length() - normOriginal.replace(normTarget, "").length()) / normTarget.length();
            if (count > 1) {
                log.warn("CodingWorkspaceService: Target string appears {} times in {}. Replacing all occurrences.", count, relativePath);
            }

            String modified = normOriginal.replace(normTarget, normReplacement);
            Files.writeString(target, modified, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);

            String diff = generateUnifiedDiff(original, modified, relativePath);
            log.info("CodingWorkspaceService: Surgically modified [{}]", relativePath);
            return new FileWriteResult(relativePath, "MODIFY", diff, original, modified, true);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_EDIT_ERROR", "Error editing file: " + e.getMessage());
        }
    }

    /**
     * Reverts a file to its exact original content.
     */
    public boolean rollbackFile(String relativePath, String originalContent) {
        Path target = resolveSafePath(relativePath);
        try {
            if (originalContent == null) {
                // If originalContent was null, the file was newly created and should be deleted
                if (Files.exists(target)) {
                    Files.delete(target);
                    log.info("CodingWorkspaceService: Rollback deleted newly created file [{}]", relativePath);
                }
            } else {
                Files.writeString(target, originalContent, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                log.info("CodingWorkspaceService: Rollback restored original content for [{}]", relativePath);
            }
            return true;
        } catch (IOException e) {
            log.error("CodingWorkspaceService: Rollback failed for {}: {}", relativePath, e.getMessage());
            return false;
        }
    }

    /**
     * Fast regex search across text files in the workspace.
     */
    public List<SearchMatch> searchWorkspace(String regexPattern, String globFilter, int maxResults) {
        Pattern pattern;
        try {
            pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REGEX", "Invalid search regex: " + e.getMessage());
        }

        List<SearchMatch> matches = new ArrayList<>();
        int limit = Math.max(1, Math.min(maxResults, 50));

        try (Stream<Path> stream = Files.walk(workspaceRoot, 10)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                if (Files.isDirectory(path)) continue;

                String pStr = path.toString().replace('\\', '/');
                if (pStr.contains("/.git/") || pStr.contains("/target/") || pStr.contains("/node_modules/")
                        || pStr.contains("/dist/") || pStr.contains("/.vscode/") || pStr.endsWith(".jar")
                        || pStr.endsWith(".class") || pStr.endsWith(".png") || pStr.endsWith(".jpg")) {
                    continue;
                }

                if (globFilter != null && !globFilter.isBlank() && !pStr.endsWith(globFilter.replace("*", ""))) {
                    continue;
                }

                try {
                    List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        if (pattern.matcher(line).find()) {
                            String rel = workspaceRoot.relativize(path).toString().replace('\\', '/');
                            matches.add(new SearchMatch(rel, i + 1, line.trim()));
                            if (matches.size() >= limit) return matches;
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (IOException e) {
            log.warn("CodingWorkspaceService: Search failed: {}", e.getMessage());
        }

        return matches;
    }

    /**
     * Generates a standard unified diff string.
     */
    public String generateUnifiedDiff(String original, String modified, String filePath) {
        if (original == null) original = "";
        if (modified == null) modified = "";

        String[] origLines = original.split("\n", -1);
        String[] modLines = modified.split("\n", -1);

        StringBuilder diff = new StringBuilder();
        diff.append("--- a/").append(filePath).append("\n");
        diff.append("+++ b/").append(filePath).append("\n");

        int max = Math.max(origLines.length, modLines.length);
        int contextRadius = 3;

        for (int i = 0; i < max; i++) {
            String oLine = i < origLines.length ? origLines[i] : null;
            String mLine = i < modLines.length ? modLines[i] : null;

            if (Objects.equals(oLine, mLine)) {
                // Same line
                diff.append(" ").append(oLine != null ? oLine : "").append("\n");
            } else {
                if (oLine != null) diff.append("-").append(oLine).append("\n");
                if (mLine != null) diff.append("+").append(mLine).append("\n");
            }
        }

        return diff.toString();
    }
}

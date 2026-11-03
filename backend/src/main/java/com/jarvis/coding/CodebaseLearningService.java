package com.jarvis.coding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Codebase Learning Service.
 * Scans workspace architecture, maps classes, interfaces, and methods,
 * and maintains an in-memory repository topology for fast targeted context retrieval.
 */
@Service
public class CodebaseLearningService {

    private static final Logger log = LoggerFactory.getLogger(CodebaseLearningService.class);

    public record CodeSymbol(String name, String type, String filePath, int lineNumber, String signature) {}
    public record RepoSummary(int totalFiles, int totalSymbols, List<String> keyPackages, List<CodeSymbol> topSymbols) {}

    private final CodingWorkspaceService workspace;
    private final Map<String, List<CodeSymbol>> fileSymbols = new ConcurrentHashMap<>();
    private volatile RepoSummary cachedSummary = null;
    private volatile long lastScanTime = 0;

    private static final Pattern CLASS_PATTERN = Pattern.compile("\\b(?:public\\s+|protected\\s+|private\\s+)?(?:class|interface|enum|record)\\s+([A-Za-z0-9_]+)");
    private static final Pattern METHOD_PATTERN = Pattern.compile("(?:public|protected|private|static|final|native|synchronized|abstract|transient)+\\s+([A-Za-z0-9_<>,\\[\\]\\s]+?)\\s+([A-Za-z0-9_]+)\\s*\\(");
    private static final Pattern TS_EXPORT_PATTERN = Pattern.compile("export\\s+(?:default\\s+)?(?:function|const|class|interface|type)\\s+([A-Za-z0-9_]+)");

    @Autowired
    public CodebaseLearningService(CodingWorkspaceService workspace) {
        this.workspace = workspace;
    }

    /**
     * Scans and indexes the workspace symbols.
     */
    public synchronized RepoSummary scanAndIndex() {
        long now = System.currentTimeMillis();
        if (cachedSummary != null && (now - lastScanTime < 60_000)) {
            return cachedSummary;
        }

        Path root = workspace.getWorkspaceRoot();
        fileSymbols.clear();
        Set<String> packages = new TreeSet<>();
        List<CodeSymbol> allSymbols = new ArrayList<>();
        int fileCount = 0;

        try (Stream<Path> stream = Files.walk(root, 8)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                if (Files.isDirectory(path)) continue;

                String pStr = path.toString().replace('\\', '/');
                if (pStr.contains("/.git/") || pStr.contains("/target/") || pStr.contains("/node_modules/")
                        || pStr.contains("/dist/") || pStr.contains("/build/") || pStr.contains("/.venv/")) {
                    continue;
                }

                String ext = getExtension(path.getFileName().toString());
                if (isCodeExtension(ext)) {
                    fileCount++;
                    String relPath = root.relativize(path).toString().replace('\\', '/');
                    List<CodeSymbol> symbols = extractSymbols(path, relPath);
                    if (!symbols.isEmpty()) {
                        fileSymbols.put(relPath, symbols);
                        allSymbols.addAll(symbols);
                    }
                    if (path.getParent() != null) {
                        packages.add(root.relativize(path.getParent()).toString().replace('\\', '/'));
                    }
                }
            }
        } catch (IOException e) {
            log.warn("CodebaseLearningService: Scan encountered error: {}", e.getMessage());
        }

        this.lastScanTime = now;
        this.cachedSummary = new RepoSummary(fileCount, allSymbols.size(), new ArrayList<>(packages), allSymbols.stream().limit(100).toList());
        log.info("CodebaseLearningService: Indexed {} code files and {} symbols across {} packages",
                fileCount, allSymbols.size(), packages.size());
        return cachedSummary;
    }

    /**
     * Finds files most relevant to a user task or query.
     */
    public List<String> findRelevantFiles(String query, int topK) {
        if (query == null || query.isBlank()) return Collections.emptyList();
        scanAndIndex();

        String lowerQuery = query.toLowerCase(Locale.ROOT);
        String[] keywords = lowerQuery.split("[\\s_.,;:/\\\\-]+");

        Map<String, Integer> fileScores = new HashMap<>();

        for (var entry : fileSymbols.entrySet()) {
            String file = entry.getKey();
            int score = 0;

            // Filename matching
            for (String kw : keywords) {
                if (kw.length() >= 3) {
                    if (file.toLowerCase(Locale.ROOT).contains(kw)) score += 5;
                    for (CodeSymbol sym : entry.getValue()) {
                        if (sym.name().toLowerCase(Locale.ROOT).contains(kw)) score += 3;
                    }
                }
            }

            if (score > 0) {
                fileScores.put(file, score);
            }
        }

        return fileScores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(Math.max(1, topK))
                .map(Map.Entry::getKey)
                .toList();
    }

    private List<CodeSymbol> extractSymbols(Path path, String relPath) {
        List<CodeSymbol> list = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")) continue;

                // Match Java class/interface/record
                Matcher classMatcher = CLASS_PATTERN.matcher(line);
                if (classMatcher.find()) {
                    list.add(new CodeSymbol(classMatcher.group(1), "TYPE", relPath, i + 1, line));
                    continue;
                }

                // Match TypeScript/JavaScript export
                Matcher tsMatcher = TS_EXPORT_PATTERN.matcher(line);
                if (tsMatcher.find()) {
                    list.add(new CodeSymbol(tsMatcher.group(1), "EXPORT", relPath, i + 1, line));
                    continue;
                }

                // Match Java methods
                Matcher methodMatcher = METHOD_PATTERN.matcher(line);
                if (methodMatcher.find()) {
                    list.add(new CodeSymbol(methodMatcher.group(2), "METHOD", relPath, i + 1, line));
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    private boolean isCodeExtension(String ext) {
        return Set.of("java", "ts", "tsx", "js", "jsx", "py", "rs", "go", "cpp", "c", "h", "cs", "sql", "html", "css").contains(ext.toLowerCase(Locale.ROOT));
    }

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1) : "";
    }
}

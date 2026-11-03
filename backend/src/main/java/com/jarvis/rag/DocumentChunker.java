package com.jarvis.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Splits text and markdown files into overlapping chunks suitable for embedding models (~256 tokens).
 * Preserves source file path, 1-based line numbers, and SHA-256 hashes.
 */
@Component
public class DocumentChunker {
    public record Chunk(int index, String content, int startLine, int endLine, Map<String, Object> metadata) {}

    public static final int TARGET_TOKENS = 256;
    public static final int OVERLAP_TOKENS = 32;

    // Approximately 4 characters per token for standard English / markdown text
    private static final int TARGET_CHUNK_CHARS = TARGET_TOKENS * 4; // ~1024 chars
    private static final int OVERLAP_CHARS = OVERLAP_TOKENS * 4;      // ~128 chars

    public static String sha256(String text) {
        if (text == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing", e);
        }
    }

    public List<Chunk> chunk(String filePath, String content) {
        if (content == null || content.isBlank()) return List.of();
        String[] lines = content.split("\\R");
        List<Chunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int startLine = 1;
        int chunkIndex = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (current.length() + line.length() + 1 > TARGET_CHUNK_CHARS && current.length() > 0) {
                int endLine = Math.max(startLine, i);
                String chunkText = current.toString().trim();
                if (!chunkText.isBlank()) {
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("filePath", filePath);
                    meta.put("startLine", startLine);
                    meta.put("endLine", endLine);
                    meta.put("contentHash", sha256(chunkText));
                    meta.put("estimatedTokens", (chunkText.length() + 3) / 4);
                    chunks.add(new Chunk(chunkIndex++, chunkText, startLine, endLine, meta));
                }

                // Retain overlap from end of current chunk
                String currentStr = current.toString();
                int overlapStart = Math.max(0, currentStr.length() - OVERLAP_CHARS);
                current = new StringBuilder(currentStr.substring(overlapStart));
                startLine = Math.max(1, i);
            }
            if (current.length() > 0) current.append("\n");
            current.append(line);
        }

        if (current.length() > 0 && !current.toString().isBlank()) {
            String chunkText = current.toString().trim();
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("filePath", filePath);
            meta.put("startLine", startLine);
            meta.put("endLine", lines.length);
            meta.put("contentHash", sha256(chunkText));
            meta.put("estimatedTokens", (chunkText.length() + 3) / 4);
            chunks.add(new Chunk(chunkIndex, chunkText, startLine, lines.length, meta));
        }

        return chunks;
    }
}

package com.jarvis.rag;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkerTest {
    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    void chunksEmptyContentGracefully() {
        assertThat(chunker.chunk("empty.txt", "")).isEmpty();
        assertThat(chunker.chunk("null.txt", null)).isEmpty();
        assertThat(chunker.chunk("spaces.txt", "   \n\t  \n  ")).isEmpty();
    }

    @Test
    void chunksSmallContentSingleChunk() {
        String text = "Line 1: JARVIS AI is a personal workspace.\nLine 2: It runs on local hardware.";
        var chunks = chunker.chunk("readme.md", text);
        assertThat(chunks).hasSize(1);
        var chunk = chunks.getFirst();
        assertThat(chunk.index()).isEqualTo(0);
        assertThat(chunk.content()).isEqualTo(text);
        assertThat(chunk.startLine()).isEqualTo(1);
        assertThat(chunk.endLine()).isEqualTo(2);
        assertThat(chunk.metadata()).containsEntry("filePath", "readme.md");
        assertThat(chunk.metadata()).containsEntry("startLine", 1);
        assertThat(chunk.metadata()).containsEntry("endLine", 2);
        assertThat(chunk.metadata().get("contentHash")).isNotNull();
        assertThat(chunk.metadata().get("estimatedTokens")).isNotNull();
    }

    @Test
    void chunksLargeContentWithOverlap() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 60; i++) {
            sb.append("This is line number ").append(i).append(" with substantial informative text describing architecture component ").append(i).append(".\n");
        }
        var chunks = chunker.chunk("architecture.md", sb.toString());
        assertThat(chunks.size()).isGreaterThan(1);

        for (int i = 0; i < chunks.size(); i++) {
            var c = chunks.get(i);
            assertThat(c.index()).isEqualTo(i);
            assertThat(c.content()).isNotEmpty();
            assertThat(c.startLine()).isPositive();
            assertThat(c.endLine()).isGreaterThanOrEqualTo(c.startLine());
            assertThat(c.metadata().get("filePath")).isEqualTo("architecture.md");
            assertThat(c.metadata().get("contentHash")).isNotNull();
        }

        // Verify that overlap provides content continuity
        String chunk0 = chunks.get(0).content();
        String chunk1 = chunks.get(1).content();
        // The start of chunk 1 should contain trailing text from chunk 0
        String endOfChunk0 = chunk0.substring(Math.max(0, chunk0.length() - 80));
        assertThat(chunk1).contains(endOfChunk0.trim());
    }

    @Test
    void sha256ComputesDeterministicHash() {
        String hash1 = DocumentChunker.sha256("test-content");
        String hash2 = DocumentChunker.sha256("test-content");
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);

        String hashDifferent = DocumentChunker.sha256("different-content");
        assertThat(hashDifferent).isNotEqualTo(hash1);
    }

    @Test
    void handlesNullSha256Safely() {
        assertThat(DocumentChunker.sha256(null)).isEmpty();
    }
}

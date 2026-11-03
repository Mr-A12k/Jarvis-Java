package com.jarvis.knowledge;

import java.util.Map;

/**
 * Node in the Obsidian-Style Neural Knowledge Graph.
 * Types: CHAT, MEMORY, DOCUMENT, CODE_SESSION, CODE_FILE, LEARNED_FACT.
 */
public record GraphNode(
        String id,
        String label,
        String type,
        int size,
        Map<String, Object> metadata
) {}

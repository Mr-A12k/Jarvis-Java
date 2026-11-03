package com.jarvis.knowledge;

/**
 * Synaptic Edge connecting two nodes in the Neural Knowledge Graph.
 * Relationships: REFERENCES, MODIFIES, SHARES_TOPIC, SEMANTIC_SIMILARITY.
 */
public record GraphEdge(
        String source,
        String target,
        String relationship,
        double weight
) {}

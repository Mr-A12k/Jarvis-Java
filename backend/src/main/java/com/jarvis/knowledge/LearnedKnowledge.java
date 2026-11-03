package com.jarvis.knowledge;

import java.time.Instant;
import java.util.List;

/**
 * Represents a discrete factual knowledge item synthesized and learned from web research or user instruction.
 */
public record LearnedKnowledge(
        String id,
        String query,
        String answer,
        List<String> sources,
        Instant learnedAt,
        String language
) {}

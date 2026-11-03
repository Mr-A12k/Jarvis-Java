package com.jarvis.realtime;

import java.time.Instant;
import java.util.List;

/**
 * Encapsulates gathered real-time factual observations and citations.
 */
public record RealtimeFactReport(
        String summaryText,
        List<String> sources,
        Category category,
        Instant gatheredAt
) {
    public enum Category {
        DATETIME,
        STOCK_MARKET,
        OTT_RELEASES,
        FACT_CHECK,
        GENERAL_RESEARCH
    }
}

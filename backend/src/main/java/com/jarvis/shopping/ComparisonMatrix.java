package com.jarvis.shopping;

import java.util.List;

/**
 * Structured product comparison matrix detailing side-by-side spec comparisons,
 * best-value determination, top-rated badge, and recommendation summary.
 */
public record ComparisonMatrix(
        ShoppingCriteria criteria,
        List<ProductItem> items,
        List<String> comparedSpecs,
        String bestValueId,
        String topRatedId,
        String recommendationSummary
) {}

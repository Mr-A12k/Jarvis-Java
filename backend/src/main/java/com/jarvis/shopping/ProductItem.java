package com.jarvis.shopping;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Concrete product candidate with retailer attribution and verifiable attributes.
 * Missing / unknown fields are strictly null / omitted; never hallucinated.
 */
public record ProductItem(
        String id,
        String title,
        BigDecimal price,
        String currency,
        Double rating,
        Integer reviewCount,
        String retailer,
        String url,
        Map<String, String> specs,
        List<String> highlights,
        boolean inStock
) {}

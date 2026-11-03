package com.jarvis.shopping;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed user shopping criteria including budget, target product, brand constraints, and required features.
 */
public record ShoppingCriteria(
        String query,
        BigDecimal maxPrice,
        String currency,
        List<String> requiredFeatures,
        List<String> preferredBrands,
        String sortPreference
) {
    private static final Pattern BUDGET_PATTERN = Pattern.compile(
            "(?:under|below|less than|max|budget(?: of)?|upto|up to|within|around)\\s*([$€£₹])?\\s*(\\d+(?:,\\d+)*(?:\\.\\d+)?)\\s*(usd|eur|gbp|inr|dollars|euros|rupees|bucks)?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern STANDALONE_PRICE = Pattern.compile(
            "([$€£₹])\\s*(\\d+(?:,\\d+)*(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final List<String> KNOWN_BRANDS = List.of(
            "apple", "samsung", "sony", "logitech", "keychron", "dell", "hp", "lenovo",
            "asus", "acer", "bose", "sennheiser", "razer", "corsair", "steelseries",
            "anker", "microsoft", "google", "lg", "oneplus", "nothing", "bose"
    );

    private static final List<String> COMMON_FEATURES = List.of(
            "wireless", "bluetooth", "mechanical", "noise cancelling", "anc", "rgb",
            "hot-swappable", "usb-c", "4k", "oled", "lightweight", "portable", "waterproof",
            "rechargeable", "backlit", "aluminum", "low profile"
    );

    public static ShoppingCriteria parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return new ShoppingCriteria("", null, "USD", List.of(), List.of(), "best_value");
        }

        String text = rawText.trim();
        String lower = text.toLowerCase(Locale.ROOT);

        BigDecimal maxPrice = null;
        String currency = "USD";

        Matcher m = BUDGET_PATTERN.matcher(text);
        if (m.find()) {
            String sym = m.group(1);
            String amount = m.group(2).replace(",", "");
            String curWord = m.group(3);

            try {
                maxPrice = new BigDecimal(amount);
            } catch (Exception ignored) {}

            currency = resolveCurrency(sym, curWord, lower);
        } else {
            Matcher m2 = STANDALONE_PRICE.matcher(text);
            if (m2.find()) {
                String sym = m2.group(1);
                String amount = m2.group(2).replace(",", "");
                try {
                    maxPrice = new BigDecimal(amount);
                } catch (Exception ignored) {}
                currency = resolveCurrency(sym, null, lower);
            }
        }

        // Brands
        List<String> brands = new ArrayList<>();
        for (String brand : KNOWN_BRANDS) {
            if (lower.matches(".*\\b" + Pattern.quote(brand) + "\\b.*")) {
                brands.add(Character.toUpperCase(brand.charAt(0)) + brand.substring(1));
            }
        }

        // Features
        List<String> features = new ArrayList<>();
        for (String feat : COMMON_FEATURES) {
            if (lower.contains(feat)) {
                features.add(feat);
            }
        }

        // Sort preference
        String sort = "best_value";
        if (lower.contains("cheapest") || lower.contains("lowest price")) {
            sort = "price_asc";
        } else if (lower.contains("best rated") || lower.contains("top rated") || lower.contains("highest rated")) {
            sort = "rating_desc";
        }

        // Clean product query: strip shopping prefixes and budget fragments
        String cleaned = text
                .replaceAll("(?i)^(?:/shop|recommend|find|buy|search for|look for|compare)\\s+", "")
                .replaceAll("(?i)(?:under|below|less than|max|budget of|within)\\s*[$€£₹]?\\s*\\d+(?:,\\d+)*(?:\\.\\d+)?\\s*(?:usd|eur|gbp|inr|dollars|euros|rupees|bucks)?", "")
                .replaceAll("(?i)[$€£₹]\\s*\\d+(?:,\\d+)*(?:\\.\\d+)?", "")
                .replaceAll("(?i)\\b(?:with|having|that has|features?)\\b.*", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleaned.isBlank()) cleaned = text;

        return new ShoppingCriteria(cleaned, maxPrice, currency, features, brands, sort);
    }

    private static String resolveCurrency(String sym, String word, String fullText) {
        if ("₹".equals(sym) || "inr".equalsIgnoreCase(word) || fullText.contains("rupee") || fullText.contains("₹")) return "INR";
        if ("€".equals(sym) || "eur".equalsIgnoreCase(word) || fullText.contains("euro") || fullText.contains("€")) return "EUR";
        if ("£".equals(sym) || "gbp".equalsIgnoreCase(word) || fullText.contains("pound") || fullText.contains("£")) return "GBP";
        return "USD";
    }
}

package com.jarvis.web;

/**
 * Supported search provider options for JARVIS web research.
 * Allows users to choose between real Chromium browser automation and lightweight HTTP endpoints.
 */
public enum SearchProvider {
    /**
     * Automatic: Attempts Chromium browser search first; gracefully falls back to DuckDuckGo HTTP
     * if the browser is busy or unavailable.
     */
    AUTO("Auto (Chromium with HTTP Fallback)", "Attempts Chromium browser rendering first, falls back to lightweight HTTP"),

    /**
     * Real Chromium browser rendering Google Search with JavaScript execution.
     */
    CHROMIUM_GOOGLE("Chromium (Google Search)", "Real Chromium browser navigating Google Search with JavaScript execution"),

    /**
     * Real Chromium browser rendering DuckDuckGo with JavaScript execution.
     */
    CHROMIUM_DUCKDUCKGO("Chromium (DuckDuckGo)", "Real Chromium browser rendering DuckDuckGo web interface"),

    /**
     * Real Chromium browser rendering Bing Search with JavaScript execution.
     */
    CHROMIUM_BING("Chromium (Bing Search)", "Real Chromium browser navigating Bing Search with JavaScript execution"),

    /**
     * Fast, lightweight DuckDuckGo HTML endpoint without browser rendering.
     */
    DUCKDUCKGO_HTTP("DuckDuckGo HTTP (Lightweight)", "Fast direct HTTP scraper without browser rendering");

    private final String displayName;
    private final String description;

    SearchProvider(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public static SearchProvider fromString(String val) {
        if (val == null || val.isBlank()) return AUTO;
        String clean = val.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        for (SearchProvider p : values()) {
            if (p.name().equalsIgnoreCase(clean)) return p;
        }
        return AUTO;
    }
}

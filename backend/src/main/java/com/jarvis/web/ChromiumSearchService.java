package com.jarvis.web;

import com.jarvis.common.ApiException;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Dedicated Chromium Browser Internet Search and Deep Technical Documentation Scraper.
 * Uses Microsoft Playwright to execute real browser rendering, JavaScript execution,
 * and deep documentation extraction (MDN, GitHub, StackOverflow) with zero external API fees.
 */
@Service
public class ChromiumSearchService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ChromiumSearchService.class);

    private final boolean headless;
    private final int timeoutMs;
    private final ExecutorService executor;

    private Playwright playwright;
    private Browser browser;
    private boolean initialized = false;

    public record CleanPageResult(String url, String title, String markdownContent, long durationMs) {}

    public ChromiumSearchService(
            @Value("${jarvis.search.chromium.headless:true}") boolean headless,
            @Value("${jarvis.search.chromium.timeout-ms:15000}") int timeoutMs) {
        this.headless = headless;
        this.timeoutMs = timeoutMs;
        // Dedicated single thread ensures Playwright thread isolation
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "chromium-search-worker");
            t.setDaemon(true);
            return t;
        });
    }

    private synchronized void ensureInitialized() {
        if (!initialized) {
            try {
                this.playwright = Playwright.create();
                BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                        .setHeadless(headless)
                        .setArgs(List.of("--no-sandbox", "--disable-setuid-sandbox", "--disable-dev-shm-usage"));
                this.browser = playwright.chromium().launch(options);
                this.initialized = true;
                log.info("ChromiumSearchService: Initialized Playwright Chromium engine (headless: {})", headless);
            } catch (Exception e) {
                log.error("ChromiumSearchService: Failed to initialize Playwright: {}", e.getMessage());
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CHROMIUM_INIT_FAILED",
                        "Failed to initialize Chromium browser: " + e.getMessage());
            }
        }
    }

    /**
     * Executes an internet search using a real Chromium browser page.
     */
    public List<WebSearch.Source> search(String query, SearchProvider provider, int maxResults) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        int limit = Math.max(1, Math.min(maxResults, 10));
        long start = System.currentTimeMillis();

        try {
            return executor.submit(() -> {
                ensureInitialized();
                BrowserContext context = null;
                Page page = null;
                try {
                    context = browser.newContext(new Browser.NewContextOptions()
                            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                            .setViewportSize(1280, 800));
                    page = context.newPage();
                    page.setDefaultTimeout(timeoutMs);
                    page.setDefaultNavigationTimeout(timeoutMs);

                    String searchUrl = buildSearchUrl(query, provider);
                    log.info("ChromiumSearchService: Navigating to [{}] via {}", searchUrl, provider);

                    page.navigate(searchUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

                    // Wait briefly for dynamic result rendering
                    page.waitForTimeout(1000);

                    String html = page.content();
                    List<WebSearch.Source> results = parseSearchResults(html, provider, limit);

                    long duration = System.currentTimeMillis() - start;
                    log.info("ChromiumSearchService: Search returned {} results in {}ms", results.size(), duration);
                    return results;
                } finally {
                    if (page != null) {
                        try { page.close(); } catch (Exception ignored) {}
                    }
                    if (context != null) {
                        try { context.close(); } catch (Exception ignored) {}
                    }
                }
            }).get(timeoutMs + 5000L, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("ChromiumSearchService: Search timed out for '{}'", query);
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "SEARCH_TIMEOUT", "Chromium search timed out");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("ChromiumSearchService: Search failed for '{}': {}", query, cause.getMessage());
            if (cause instanceof ApiException apiEx) throw apiEx;
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SEARCH_FAILED", "Chromium search failed: " + cause.getMessage());
        }
    }

    /**
     * Navigates to a specific technical documentation URL, renders the page,
     * strips boilerplate/ads, and extracts clean Markdown with preserved code blocks.
     */
    public CleanPageResult fetchCleanMarkdown(String url, int maxChars) {
        if (url == null || url.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_URL", "Target URL cannot be empty");
        }

        long start = System.currentTimeMillis();
        int limit = maxChars > 0 ? maxChars : 16000;

        try {
            return executor.submit(() -> {
                ensureInitialized();
                BrowserContext context = null;
                Page page = null;
                try {
                    context = browser.newContext(new Browser.NewContextOptions()
                            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                            .setViewportSize(1280, 800));
                    page = context.newPage();
                    page.setDefaultTimeout(timeoutMs);
                    page.setDefaultNavigationTimeout(timeoutMs);

                    page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                    page.waitForTimeout(800);

                    String title = page.title();
                    String html = page.content();
                    String markdown = convertHtmlToCleanMarkdown(html, limit);

                    long duration = System.currentTimeMillis() - start;
                    return new CleanPageResult(url, title, markdown, duration);
                } finally {
                    if (page != null) {
                        try { page.close(); } catch (Exception ignored) {}
                    }
                    if (context != null) {
                        try { context.close(); } catch (Exception ignored) {}
                    }
                }
            }).get(timeoutMs + 5000L, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("ChromiumSearchService: Failed to fetch documentation from {}: {}", url, cause.getMessage());
            return new CleanPageResult(url, "Error Fetching", "Could not fetch content: " + cause.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private String buildSearchUrl(String query, SearchProvider provider) {
        String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        return switch (provider) {
            case CHROMIUM_GOOGLE -> "https://www.google.com/search?q=" + encoded + "&hl=en";
            case CHROMIUM_BING -> "https://www.bing.com/search?q=" + encoded;
            case CHROMIUM_DUCKDUCKGO, AUTO, DUCKDUCKGO_HTTP -> "https://duckduckgo.com/?q=" + encoded;
        };
    }

    private List<WebSearch.Source> parseSearchResults(String html, SearchProvider provider, int limit) {
        Document doc = Jsoup.parse(html);
        List<WebSearch.Source> results = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        // 1. Selector strategy for DuckDuckGo
        Elements ddgRows = doc.select("article, [data-testid='result'], .result");
        for (Element row : ddgRows) {
            Element link = row.selectFirst("a[data-testid='result-title-a'], a.result__a, h2 a");
            if (link == null) continue;
            String href = cleanUrl(link.attr("href"));
            if (href == null || href.isBlank() || !seenUrls.add(href)) continue;

            String title = link.text();
            Element snippetEl = row.selectFirst("[data-result='snippet'], .result__snippet, [style*='-webkit-line-clamp']");
            String snippet = snippetEl != null ? snippetEl.text() : "";

            results.add(new WebSearch.Source(results.size() + 1, clip(title, 140), href, clip(snippet, 300)));
            if (results.size() >= limit) return results;
        }

        // 2. Selector strategy for Google / General search
        Elements generalLinks = doc.select(".g, #rso .MjjYud, .b_algo");
        for (Element item : generalLinks) {
            Element link = item.selectFirst("a[href^='http']");
            if (link == null) continue;
            String href = link.attr("href");
            if (href.contains("google.com/") || href.contains("bing.com/") || !seenUrls.add(href)) continue;

            Element h3 = item.selectFirst("h3, h2");
            String title = h3 != null ? h3.text() : link.text();
            Element snippetEl = item.selectFirst(".VwiC3b, .b_caption p");
            String snippet = snippetEl != null ? snippetEl.text() : "";

            results.add(new WebSearch.Source(results.size() + 1, clip(title, 140), href, clip(snippet, 300)));
            if (results.size() >= limit) return results;
        }

        // 3. Fallback: Any prominent anchor tag linking to external sites
        if (results.isEmpty()) {
            Elements allAnchors = doc.select("a[href^='http']");
            for (Element a : allAnchors) {
                String href = a.attr("href");
                if (href.contains("google.") || href.contains("duckduckgo.") || href.contains("bing.") || !seenUrls.add(href)) {
                    continue;
                }
                String text = a.text().trim();
                if (text.length() > 15) {
                    results.add(new WebSearch.Source(results.size() + 1, clip(text, 140), href, "Web reference link."));
                    if (results.size() >= limit) break;
                }
            }
        }

        return results;
    }

    private String convertHtmlToCleanMarkdown(String html, int maxChars) {
        Document doc = Jsoup.parse(html);

        // Remove non-content elements
        doc.select("script, style, nav, header, footer, iframe, noscript, svg, .ad, .advertisement, #cookie-banner, .cookie-notice").remove();

        StringBuilder sb = new StringBuilder();

        // Extract main headings and paragraphs / code blocks
        for (Element el : doc.select("h1, h2, h3, h4, p, pre, code, li")) {
            String tagName = el.tagName().toLowerCase(Locale.ROOT);
            String text = el.text().trim();
            if (text.isBlank()) continue;

            switch (tagName) {
                case "h1" -> sb.append("\n# ").append(text).append("\n\n");
                case "h2" -> sb.append("\n## ").append(text).append("\n\n");
                case "h3" -> sb.append("\n### ").append(text).append("\n\n");
                case "h4" -> sb.append("\n#### ").append(text).append("\n\n");
                case "pre" -> sb.append("\n```\n").append(el.wholeText().trim()).append("\n```\n\n");
                case "li" -> sb.append("- ").append(text).append("\n");
                case "p" -> sb.append(text).append("\n\n");
            }

            if (sb.length() >= maxChars) {
                sb.setLength(maxChars);
                sb.append("\n\n...[Content truncated for length]...");
                break;
            }
        }

        return sb.toString().strip();
    }

    private static String cleanUrl(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            if (raw.startsWith("//")) raw = "https:" + raw;
            if (raw.startsWith("/l/?uddg=")) {
                String encoded = raw.substring(raw.indexOf("uddg=") + 5);
                int amp = encoded.indexOf("&");
                if (amp > 0) encoded = encoded.substring(0, amp);
                return java.net.URLDecoder.decode(encoded, StandardCharsets.UTF_8);
            }
            URI uri = URI.create(raw);
            if (uri.getScheme() != null && uri.getScheme().startsWith("http")) {
                return raw;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String clip(String val, int max) {
        if (val == null) return "";
        String clean = val.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "...";
    }

    @Override
    @PreDestroy
    public synchronized void close() {
        if (initialized) {
            try {
                if (browser != null) browser.close();
            } catch (Exception ignored) {}
            try {
                if (playwright != null) playwright.close();
            } catch (Exception ignored) {}
            initialized = false;
        }
        executor.shutdownNow();
    }
}

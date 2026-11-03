package com.jarvis.web;

import com.jarvis.common.ApiException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import org.springframework.http.client.reactive.JdkClientHttpConnector;

/**
 * High-performance Web Search and Documentation Retrieval Service.
 * Dynamically routes queries across:
 * - Real Chromium browser engine (Playwright: Google, DuckDuckGo, Bing) with JavaScript execution
 * - Fast lightweight DuckDuckGo HTTP fallback
 * Configurable dynamically at runtime by the user.
 */
@Service
public class WebSearch {

    private static final Logger log = LoggerFactory.getLogger(WebSearch.class);

    public record Source(int id, String title, String url, String snippet) {}

    private final java.util.concurrent.Semaphore slots = new java.util.concurrent.Semaphore(1);
    private final WebClient client;
    private final ChromiumSearchService chromiumSearchService;
    private volatile SearchProvider activeProvider;

    public WebSearch() {
        this(null, "AUTO");
    }

    @Autowired
    public WebSearch(
            @Autowired(required = false) ChromiumSearchService chromiumSearchService,
            @Value("${jarvis.search.provider:AUTO}") String defaultProvider) {
        this.chromiumSearchService = chromiumSearchService;
        this.activeProvider = SearchProvider.fromString(defaultProvider);
        this.client = WebClient.builder()
                .baseUrl("https://html.duckduckgo.com")
                .clientConnector(new JdkClientHttpConnector(java.net.http.HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                        .build()))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(512 * 1024))
                .build();
    }

    public SearchProvider getActiveProvider() {
        return activeProvider;
    }

    public void setActiveProvider(SearchProvider provider) {
        if (provider != null) {
            this.activeProvider = provider;
            log.info("WebSearch: Active search provider dynamically changed to [{}]", provider);
        }
    }

    /**
     * Executes web search dynamically based on active search provider.
     */
    public Mono<List<Source>> search(String query) {
        return search(query, this.activeProvider);
    }

    public Mono<List<Source>> search(String query, SearchProvider provider) {
        SearchProvider targetProvider = provider != null ? provider : this.activeProvider;

        // 1. Direct lightweight HTTP scraper
        if (targetProvider == SearchProvider.DUCKDUCKGO_HTTP || chromiumSearchService == null) {
            return searchHttp(query);
        }

        // 2. Real Chromium Browser Search (Google, DuckDuckGo, Bing)
        if (targetProvider == SearchProvider.CHROMIUM_GOOGLE
                || targetProvider == SearchProvider.CHROMIUM_DUCKDUCKGO
                || targetProvider == SearchProvider.CHROMIUM_BING) {
            return Mono.fromCallable(() -> chromiumSearchService.search(query, targetProvider, 5))
                    .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                    .onErrorResume(e -> {
                        log.warn("WebSearch: Chromium search via {} failed: {}. Falling back to HTTP.", targetProvider, e.getMessage());
                        return searchHttp(query);
                    });
        }

        // 3. AUTO: Chromium first with automatic DuckDuckGo HTTP fallback
        return Mono.fromCallable(() -> chromiumSearchService.search(query, SearchProvider.CHROMIUM_DUCKDUCKGO, 5))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .flatMap(results -> {
                    if (results != null && !results.isEmpty()) {
                        return Mono.just(results);
                    }
                    log.info("WebSearch: Chromium returned empty results for '{}', falling back to HTTP.", query);
                    return searchHttp(query);
                })
                .onErrorResume(e -> {
                    log.info("WebSearch: Chromium search failed for '{}' ({}), falling back to HTTP.", query, e.getMessage());
                    return searchHttp(query);
                });
    }

    private Mono<List<Source>> searchHttp(String query) {
        return Mono.using(() -> {
            if (!slots.tryAcquire()) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "SEARCH_BUSY", "A web search is already running. Wait or cancel it first.");
            }
            return slots;
        }, permit -> client.get().uri(b -> b.path("/html/").queryParam("q", query).build())
                .header("User-Agent", "JARVIS/0.1 (local personal search)")
                .retrieve().bodyToMono(String.class).timeout(Duration.ofSeconds(20))
                .map(WebSearch::parse)
                .onErrorMap(e -> e instanceof ApiException ? e : unavailable()),
                java.util.concurrent.Semaphore::release);
    }

    static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SEARCH_UNAVAILABLE", "Web search is unavailable or blocked by the provider. Try again later; no offline answer was substituted.");
    }

    static List<Source> parse(String html) {
        var doc = Jsoup.parse(html);
        if (doc.select(".anomaly-modal, #challenge-form").size() > 0) throw unavailable();
        var results = new ArrayList<Source>();
        var seen = new HashSet<String>();
        for (var row : doc.select(".result")) {
            var link = row.selectFirst("a.result__a");
            if (link == null) continue;
            String url = safeUrl(link.attr("href"));
            if (url == null || !seen.add(url)) continue;
            results.add(new Source(results.size() + 1, clip(link.text(), 120), url, clip(row.select(".result__snippet").text(), 260)));
            if (results.size() == 3) break;
        }
        if (results.isEmpty() && doc.select(".no-results").isEmpty()) throw unavailable();
        return List.copyOf(results);
    }

    public static String safeUrl(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            URI uri = URI.create(raw.startsWith("//") ? "https:" + raw : raw);
            if ("duckduckgo.com".equals(uri.getHost()) && uri.getRawQuery() != null) {
                for (String part : uri.getRawQuery().split("&")) {
                    if (part.startsWith("uddg=")) {
                        uri = URI.create(URLDecoder.decode(part.substring(5), StandardCharsets.UTF_8));
                    }
                }
            }
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null) {
                return null;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("0.0.0.0")
                    || host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".localhost")
                    || host.startsWith("10.") || host.startsWith("192.168.") || (host.startsWith("172.") && host.length() > 6)) {
                return null;
            }
            return uri.toASCIIString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fetches page content. If Chromium is available, renders page in browser
     * and extracts clean Markdown; otherwise falls back to lightweight HTTP.
     */
    public Mono<String> fetchPageContent(String url) {
        String safe = safeUrl(url);
        if (safe == null) return Mono.just("");

        if (chromiumSearchService != null) {
            return Mono.fromCallable(() -> chromiumSearchService.fetchCleanMarkdown(safe, 8000).markdownContent())
                    .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                    .onErrorResume(e -> fetchPageContentHttp(safe));
        }

        return fetchPageContentHttp(safe);
    }

    private Mono<String> fetchPageContentHttp(String safeUrl) {
        return client.get().uri(URI.create(safeUrl))
                .header("User-Agent", "JARVIS/0.1 (local personal research)")
                .retrieve().bodyToMono(String.class).timeout(Duration.ofSeconds(5))
                .map(WebSearch::extractPageText)
                .onErrorReturn("");
    }

    static String extractPageText(String html) {
        if (html == null || html.isBlank()) return "";
        try {
            var doc = Jsoup.parse(html);
            doc.select("script, style, nav, footer, header, noscript, svg, form").remove();
            String text = doc.body() != null ? doc.body().text() : doc.text();
            return clip(text.replaceAll("\\s+", " ").strip(), 1200);
        } catch (Exception e) {
            return "";
        }
    }

    private static String clip(String value, int limit) {
        if (value == null) return "";
        return value.substring(0, Math.min(limit, value.length()));
    }
}

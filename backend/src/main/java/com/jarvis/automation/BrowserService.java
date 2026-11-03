package com.jarvis.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.ApiException;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Thread-isolated Playwright instance with domain allowlist validation,
 * ephemeral browser context per run, explicit timeouts, strict resource disposal,
 * and audit logging to data/automation-audit.jsonl.
 */
@Service
public class BrowserService {

    private static final Logger log = LoggerFactory.getLogger(BrowserService.class);

    public record NavigationResult(
            String requestedUrl,
            String finalUrl,
            String title,
            int statusCode,
            String content,
            long durationMs
    ) {}

    public record SnapshotResult(
            String requestedUrl,
            String finalUrl,
            String title,
            String screenshotBase64,
            String textContent,
            long durationMs
    ) {}

    public enum ActionType {
        CLICK,
        FILL,
        PRESS,
        WAIT_FOR
    }

    public record ActionStep(
            ActionType type,
            String selector,
            String value,
            Integer timeoutMs
    ) {}

    public record InteractiveResult(
            String requestedUrl,
            String finalUrl,
            String title,
            String screenshotBase64,
            String textContent,
            List<String> stepResults,
            long durationMs
    ) {}

    private final ObjectMapper json;
    private final Path auditFilePath;
    private final Set<String> allowedDomains;
    private final int defaultTimeoutMs;
    private final ExecutorService executor;

    private Playwright playwright;
    private Browser browser;
    private boolean initialized = false;

    public BrowserService(
            ObjectMapper json,
            @Value("${jarvis.automation.audit-path:data/automation-audit.jsonl}") String auditPath,
            @Value("${jarvis.automation.allowed-domains:}") String allowedDomainsStr,
            @Value("${jarvis.automation.default-timeout-ms:15000}") int defaultTimeoutMs) {
        this.json = json;
        this.auditFilePath = Path.of(auditPath).toAbsolutePath().normalize();
        this.defaultTimeoutMs = defaultTimeoutMs;

        Set<String> domains = new HashSet<>();
        if (allowedDomainsStr != null && !allowedDomainsStr.isBlank()) {
            for (String d : allowedDomainsStr.split(",")) {
                String trimmed = d.trim().toLowerCase(Locale.ROOT);
                if (!trimmed.isEmpty()) domains.add(trimmed);
            }
        }
        this.allowedDomains = Collections.unmodifiableSet(domains);

        // Dedicated single thread ensures Playwright thread isolation
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "playwright-worker");
            t.setDaemon(true);
            return t;
        });
    }

    public static boolean isAllowed(String urlString) {
        if (urlString == null || urlString.isBlank()) return false;
        try {
            URI uri = URI.create(urlString.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return false;
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) return false;
            host = host.toLowerCase(Locale.ROOT);
            if (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("0.0.0.0")
                    || host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".localhost")
                    || host.startsWith("10.") || host.startsWith("192.168.") || (host.startsWith("172.") && host.length() > 6)) {
                return false;
            }
            Set<String> defaultAllowed = Set.of("duckduckgo.com", "wikipedia.org", "github.com", "spring.io", "developer.mozilla.org", "arxiv.org", "docs.oracle.com");
            for (String allowed : defaultAllowed) {
                if (host.equals(allowed) || host.endsWith("." + allowed)) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validates that the URL uses HTTP/HTTPS, matches allowlist if configured,
     * and strictly blocks internal/private/loopback/cloud-metadata IPs (SSRF prevention).
     */
    public void validateUrl(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTOMATION_INVALID_URL", "URL must not be empty");
        }

        URI uri;
        try {
            uri = URI.create(urlString.trim());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTOMATION_INVALID_URL", "Malformed URL: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTOMATION_INVALID_SCHEME", "Only HTTP and HTTPS protocols are allowed");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTOMATION_INVALID_HOST", "Host cannot be empty");
        }
        host = host.toLowerCase(Locale.ROOT);

        // 1. Check blocked hostnames and suffixes
        if (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("0.0.0.0")
                || host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".localhost")) {
            logAudit("DOMAIN_CHECK", urlString, "BLOCKED", null, 0, "Blocked localhost or local domain: " + host);
            throw new ApiException(HttpStatus.FORBIDDEN, "AUTOMATION_INTERNAL_IP_BLOCKED", "Access to internal host is blocked: " + host);
        }

        // 2. Domain allowlist validation (if configured)
        if (!allowedDomains.isEmpty()) {
            boolean allowed = false;
            for (String allowedDomain : allowedDomains) {
                if (host.equals(allowedDomain) || host.endsWith("." + allowedDomain)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                logAudit("DOMAIN_CHECK", urlString, "BLOCKED", null, 0, "Domain not in allowlist: " + host);
                throw new ApiException(HttpStatus.FORBIDDEN, "AUTOMATION_DOMAIN_NOT_ALLOWED", "Domain not permitted by allowlist: " + host);
            }
        }

        // 3. DNS resolution & IP SSRF check
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (isPrivateOrInternal(addr)) {
                    logAudit("DOMAIN_CHECK", urlString, "BLOCKED", addr.getHostAddress(), 0, "Blocked internal IP resolution: " + addr.getHostAddress());
                    throw new ApiException(HttpStatus.FORBIDDEN, "AUTOMATION_INTERNAL_IP_BLOCKED",
                            "Access to internal IP address blocked: " + addr.getHostAddress());
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            logAudit("DOMAIN_CHECK", urlString, "FAILED", null, 0, "Failed to resolve host: " + host);
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTOMATION_DNS_FAILED", "DNS resolution failed for host: " + host);
        }

        logAudit("DOMAIN_CHECK", urlString, "ALLOWED", null, 0, "Domain validation passed: " + host);
    }

    public static boolean isPrivateOrInternal(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;

            // 10.0.0.0/8
            if (first == 10) return true;
            // 172.16.0.0/12
            if (first == 172 && (second >= 16 && second <= 31)) return true;
            // 192.168.0.0/16
            if (first == 192 && second == 168) return true;
            // 169.254.0.0/16 (Link Local & AWS/Cloud Metadata 169.254.169.254)
            if (first == 169 && second == 254) return true;
            // 127.0.0.0/8
            if (first == 127) return true;
            // 0.0.0.0/8
            if (first == 0) return true;
        } else if (address instanceof Inet6Address) {
            // Unique local address fc00::/7
            if ((bytes[0] & 0xFE) == 0xFC) return true;
            // Link local fe80::/10
            if ((bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xC0) == 0x80) return true;
        }
        return false;
    }

    private synchronized void ensureBrowserInitialized() {
        if (!initialized) {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            initialized = true;
        }
    }

    /**
     * Navigates to a validated URL in an ephemeral browser context, returns title, final URL, and text snippet.
     */
    public NavigationResult navigate(String url, Integer timeoutMs) {
        validateUrl(url);
        int timeout = (timeoutMs != null && timeoutMs > 0) ? timeoutMs : defaultTimeoutMs;
        long start = System.currentTimeMillis();

        try {
            return executor.submit(() -> {
                ensureBrowserInitialized();
                BrowserContext context = null;
                Page page = null;
                try {
                    context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 800));
                    page = context.newPage();
                    page.setDefaultTimeout(timeout);
                    page.setDefaultNavigationTimeout(timeout);

                    Response response = page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                    int statusCode = response != null ? response.status() : 200;
                    String finalUrl = page.url();
                    String title = page.title();
                    String text = page.innerText("body");
                    if (text != null && text.length() > 5000) {
                        text = text.substring(0, 5000) + "...";
                    }

                    long duration = System.currentTimeMillis() - start;
                    logAudit("NAVIGATE", url, "SUCCESS", null, duration, "Navigated successfully to " + finalUrl);
                    return new NavigationResult(url, finalUrl, title, statusCode, text, duration);
                } finally {
                    if (page != null) {
                        try { page.close(); } catch (Exception ignored) {}
                    }
                    if (context != null) {
                        try { context.close(); } catch (Exception ignored) {}
                    }
                }
            }).get(timeout + 5000L, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            long duration = System.currentTimeMillis() - start;
            logAudit("NAVIGATE", url, "TIMEOUT", null, duration, "Operation timed out");
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "AUTOMATION_TIMEOUT", "Navigation timed out after " + timeout + "ms");
        } catch (ExecutionException ee) {
            long duration = System.currentTimeMillis() - start;
            Throwable cause = ee.getCause();
            if (cause instanceof ApiException apiEx) throw apiEx;
            logAudit("NAVIGATE", url, "FAILED", null, duration, "Error: " + cause.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUTOMATION_ERROR", "Browser navigation failed: " + cause.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUTOMATION_INTERRUPTED", "Browser task was interrupted");
        }
    }

    /**
     * Captures a screenshot and HTML/text snapshot in an ephemeral context.
     */
    public SnapshotResult snapshot(String url, Boolean fullPage, Integer timeoutMs) {
        validateUrl(url);
        int timeout = (timeoutMs != null && timeoutMs > 0) ? timeoutMs : defaultTimeoutMs;
        boolean fp = fullPage != null && fullPage;
        long start = System.currentTimeMillis();

        try {
            return executor.submit(() -> {
                ensureBrowserInitialized();
                BrowserContext context = null;
                Page page = null;
                try {
                    context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 800));
                    page = context.newPage();
                    page.setDefaultTimeout(timeout);
                    page.setDefaultNavigationTimeout(timeout);

                    page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                    String finalUrl = page.url();
                    String title = page.title();
                    String text = page.innerText("body");
                    if (text != null && text.length() > 5000) {
                        text = text.substring(0, 5000) + "...";
                    }

                    byte[] screenshotBytes = page.screenshot(new Page.ScreenshotOptions().setFullPage(fp));
                    String base64 = Base64.getEncoder().encodeToString(screenshotBytes);

                    long duration = System.currentTimeMillis() - start;
                    logAudit("SNAPSHOT", url, "SUCCESS", null, duration, "Captured snapshot of " + finalUrl);
                    return new SnapshotResult(url, finalUrl, title, base64, text, duration);
                } finally {
                    if (page != null) {
                        try { page.close(); } catch (Exception ignored) {}
                    }
                    if (context != null) {
                        try { context.close(); } catch (Exception ignored) {}
                    }
                }
            }).get(timeout + 5000L, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            long duration = System.currentTimeMillis() - start;
            logAudit("SNAPSHOT", url, "TIMEOUT", null, duration, "Snapshot timed out");
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "AUTOMATION_TIMEOUT", "Snapshot timed out after " + timeout + "ms");
        } catch (ExecutionException ee) {
            long duration = System.currentTimeMillis() - start;
            Throwable cause = ee.getCause();
            if (cause instanceof ApiException apiEx) throw apiEx;
            logAudit("SNAPSHOT", url, "FAILED", null, duration, "Error: " + cause.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUTOMATION_ERROR", "Browser snapshot failed: " + cause.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUTOMATION_INTERRUPTED", "Snapshot task was interrupted");
        }
    }

    /**
     * Executes an allowlisted interactive action sequence (CLICK, FILL, PRESS, WAIT_FOR)
     * in an ephemeral context and returns the final state, steps log, and screenshot.
     */
    public InteractiveResult interact(String url, List<ActionStep> steps, Integer timeoutMs) {
        validateUrl(url);
        if (steps == null || steps.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTOMATION_EMPTY_STEPS", "At least one action step must be provided");
        }
        if (steps.size() > 10) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTOMATION_TOO_MANY_STEPS", "Action sequence capped at 10 steps per interaction");
        }

        for (ActionStep step : steps) {
            if (step.type() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AUTOMATION_INVALID_STEP", "Action step type is required");
            }
            if ((step.type() == ActionType.CLICK || step.type() == ActionType.FILL || step.type() == ActionType.WAIT_FOR)
                    && (step.selector() == null || step.selector().isBlank())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AUTOMATION_INVALID_SELECTOR", "Selector is required for action " + step.type());
            }
            if (step.selector() != null && step.selector().length() > 200) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AUTOMATION_INVALID_SELECTOR", "Selector length exceeds 200 characters");
            }
            if (step.type() == ActionType.FILL && step.value() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AUTOMATION_INVALID_VALUE", "Value is required for FILL action");
            }
            if (step.value() != null && step.value().length() > 1000) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AUTOMATION_INVALID_VALUE", "Action value exceeds 1000 characters");
            }
        }

        int totalTimeout = (timeoutMs != null && timeoutMs > 0) ? timeoutMs : (defaultTimeoutMs * 2);
        long start = System.currentTimeMillis();
        List<String> logs = new ArrayList<>();

        try {
            return executor.submit(() -> {
                ensureBrowserInitialized();
                BrowserContext context = null;
                Page page = null;
                try {
                    context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 800));
                    page = context.newPage();
                    page.setDefaultTimeout(Math.min(10000, defaultTimeoutMs));
                    page.setDefaultNavigationTimeout(totalTimeout);

                    page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                    logs.add("NAVIGATE: Loaded " + url);

                    for (int i = 0; i < steps.size(); i++) {
                        ActionStep step = steps.get(i);
                        int stepTimeout = (step.timeoutMs() != null && step.timeoutMs() > 0) ? step.timeoutMs() : 5000;
                        switch (step.type()) {
                            case CLICK -> {
                                page.locator(step.selector()).first().click(new Locator.ClickOptions().setTimeout(stepTimeout));
                                logs.add("CLICK: " + step.selector());
                            }
                            case FILL -> {
                                page.locator(step.selector()).first().fill(step.value(), new Locator.FillOptions().setTimeout(stepTimeout));
                                logs.add("FILL: " + step.selector() + " = " + step.value());
                            }
                            case PRESS -> {
                                String key = step.value() != null ? step.value() : "Enter";
                                page.keyboard().press(key);
                                logs.add("PRESS: " + key);
                            }
                            case WAIT_FOR -> {
                                page.locator(step.selector()).first().waitFor(new Locator.WaitForOptions().setTimeout(stepTimeout));
                                logs.add("WAIT_FOR: " + step.selector());
                            }
                        }
                    }

                    String finalUrl = page.url();
                    String title = page.title();
                    String text = page.innerText("body");
                    if (text != null && text.length() > 5000) {
                        text = text.substring(0, 5000) + "...";
                    }

                    byte[] screenshotBytes = page.screenshot(new Page.ScreenshotOptions().setFullPage(false));
                    String base64 = Base64.getEncoder().encodeToString(screenshotBytes);

                    long duration = System.currentTimeMillis() - start;
                    logAudit("INTERACT", url, "SUCCESS", null, duration, "Executed " + steps.size() + " step(s) on " + finalUrl);
                    return new InteractiveResult(url, finalUrl, title, base64, text, logs, duration);
                } finally {
                    if (page != null) {
                        try { page.close(); } catch (Exception ignored) {}
                    }
                    if (context != null) {
                        try { context.close(); } catch (Exception ignored) {}
                    }
                }
            }).get(totalTimeout + 5000L, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            long duration = System.currentTimeMillis() - start;
            logAudit("INTERACT", url, "TIMEOUT", null, duration, "Interactive sequence timed out");
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "AUTOMATION_TIMEOUT", "Interaction timed out after " + totalTimeout + "ms");
        } catch (ExecutionException ee) {
            long duration = System.currentTimeMillis() - start;
            Throwable cause = ee.getCause();
            if (cause instanceof ApiException apiEx) throw apiEx;
            logAudit("INTERACT", url, "FAILED", null, duration, "Error: " + cause.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUTOMATION_ERROR", "Browser interaction failed: " + cause.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUTOMATION_INTERRUPTED", "Browser interaction task was interrupted");
        }
    }

    public synchronized void logAudit(String action, String url, String status, String ip, long durationMs, String details) {
        try {
            if (auditFilePath.getParent() != null && !Files.exists(auditFilePath.getParent())) {
                Files.createDirectories(auditFilePath.getParent());
            }

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("timestamp", Instant.now().toString());
            event.put("action", action);
            event.put("url", url);
            event.put("status", status);
            if (ip != null) event.put("ip", ip);
            event.put("durationMs", durationMs);
            event.put("details", details);

            String line = json.writeValueAsString(event) + "\n";
            Files.writeString(auditFilePath, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to write to automation audit log: {}", e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> getAuditEvents(int limit) {
        if (!Files.exists(auditFilePath)) {
            return Collections.emptyList();
        }
        try {
            List<String> lines = Files.readAllLines(auditFilePath, StandardCharsets.UTF_8);
            int start = Math.max(0, lines.size() - limit);
            List<Map<String, Object>> events = new ArrayList<>();
            for (int i = lines.size() - 1; i >= start; i--) {
                String line = lines.get(i).trim();
                if (!line.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = json.readValue(line, Map.class);
                    events.add(map);
                }
            }
            return events;
        } catch (IOException e) {
            log.error("Failed to read automation audit log: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            executor.submit(() -> {
                if (browser != null) {
                    try { browser.close(); } catch (Exception ignored) {}
                }
                if (playwright != null) {
                    try { playwright.close(); } catch (Exception ignored) {}
                }
            }).get(3, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        } finally {
            executor.shutdownNow();
        }
    }
}

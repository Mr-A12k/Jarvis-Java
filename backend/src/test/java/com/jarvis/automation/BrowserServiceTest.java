package com.jarvis.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.ApiException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrowserServiceTest {

    private Path tempAuditFile;
    private ObjectMapper json;
    private BrowserService service;

    @BeforeEach
    void setUp() throws IOException {
        tempAuditFile = Files.createTempFile("automation-audit-test", ".jsonl");
        json = new ObjectMapper();
        service = new BrowserService(json, tempAuditFile.toString(), "example.com,wikipedia.org", 5000);
    }

    @AfterEach
    void tearDown() throws IOException {
        service.cleanup();
        Files.deleteIfExists(tempAuditFile);
    }

    @Test
    void rejectsInvalidUrlSchemes() {
        assertThatThrownBy(() -> service.validateUrl("file:///etc/passwd"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiEx = (ApiException) e;
                    assertThat(apiEx.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.code()).isEqualTo("AUTOMATION_INVALID_SCHEME");
                });

        assertThatThrownBy(() -> service.validateUrl("ftp://example.com/test"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiEx = (ApiException) e;
                    assertThat(apiEx.code()).isEqualTo("AUTOMATION_INVALID_SCHEME");
                });
    }

    @Test
    void rejectsEmptyOrMalformedUrls() {
        assertThatThrownBy(() -> service.validateUrl(""))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiEx = (ApiException) e;
                    assertThat(apiEx.code()).isEqualTo("AUTOMATION_INVALID_URL");
                });

        assertThatThrownBy(() -> service.validateUrl("not a url at all"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void blocksLocalhostAndInternalHostnames() {
        assertThatThrownBy(() -> service.validateUrl("http://localhost:8080"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiEx = (ApiException) e;
                    assertThat(apiEx.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.code()).isEqualTo("AUTOMATION_INTERNAL_IP_BLOCKED");
                });

        assertThatThrownBy(() -> service.validateUrl("http://127.0.0.1:9000"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiEx = (ApiException) e;
                    assertThat(apiEx.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.code()).isEqualTo("AUTOMATION_INTERNAL_IP_BLOCKED");
                });

        assertThatThrownBy(() -> service.validateUrl("http://internal.service.local"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiEx = (ApiException) e;
                    assertThat(apiEx.code()).isEqualTo("AUTOMATION_INTERNAL_IP_BLOCKED");
                });
    }

    @Test
    void enforcesDomainAllowlist() {
        // Disallowed domain
        assertThatThrownBy(() -> service.validateUrl("https://evil-site.com/steal"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiEx = (ApiException) e;
                    assertThat(apiEx.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.code()).isEqualTo("AUTOMATION_DOMAIN_NOT_ALLOWED");
                    assertThat(apiEx.getMessage()).contains("evil-site.com");
                });

        // Allowed domain passes validation
        service.validateUrl("https://example.com/page");
        service.validateUrl("https://en.wikipedia.org/wiki/Main_Page");
    }

    @Test
    void identifiesPrivateAndInternalIps() throws UnknownHostException {
        assertThat(BrowserService.isPrivateOrInternal(InetAddress.getByName("127.0.0.1"))).isTrue();
        assertThat(BrowserService.isPrivateOrInternal(InetAddress.getByName("10.0.1.20"))).isTrue();
        assertThat(BrowserService.isPrivateOrInternal(InetAddress.getByName("192.168.0.1"))).isTrue();
        assertThat(BrowserService.isPrivateOrInternal(InetAddress.getByName("172.16.1.1"))).isTrue();
        assertThat(BrowserService.isPrivateOrInternal(InetAddress.getByName("172.31.255.255"))).isTrue();
        assertThat(BrowserService.isPrivateOrInternal(InetAddress.getByName("169.254.169.254"))).isTrue(); // Cloud metadata
        assertThat(BrowserService.isPrivateOrInternal(InetAddress.getByName("0.0.0.0"))).isTrue();

        // Public IPs
        assertThat(BrowserService.isPrivateOrInternal(InetAddress.getByName("8.8.8.8"))).isFalse();
        assertThat(BrowserService.isPrivateOrInternal(InetAddress.getByName("1.1.1.1"))).isFalse();
    }

    @Test
    void rejectsEmptyOrInvalidInteractiveSteps() {
        assertThatThrownBy(() -> service.interact("https://example.com", List.of(), 5000))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("AUTOMATION_EMPTY_STEPS"));

        assertThatThrownBy(() -> service.interact("https://example.com", List.of(new BrowserService.ActionStep(BrowserService.ActionType.CLICK, "", null, null)), 5000))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("AUTOMATION_INVALID_SELECTOR"));

        assertThatThrownBy(() -> service.interact("https://example.com", List.of(new BrowserService.ActionStep(BrowserService.ActionType.FILL, "#input", null, null)), 5000))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("AUTOMATION_INVALID_VALUE"));
    }

    @Test
    void auditLogCapturesDomainValidationEvents() throws IOException {
        try {
            service.validateUrl("https://blocked-example.org");
        } catch (ApiException ignored) {}

        service.validateUrl("https://example.com");

        List<String> lines = Files.readAllLines(tempAuditFile);
        assertThat(lines).isNotEmpty();

        List<Map<String, Object>> auditEvents = service.getAuditEvents(10);
        assertThat(auditEvents).isNotEmpty();
        assertThat(auditEvents).anyMatch(e -> "DOMAIN_CHECK".equals(e.get("action")));
    }
}

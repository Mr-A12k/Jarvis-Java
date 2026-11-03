package com.jarvis.automation;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BrowserAllowlistTest {

    @Test
    void allowsWhitelistedDomains() {
        assertThat(BrowserService.isAllowed("https://duckduckgo.com")).isTrue();
        assertThat(BrowserService.isAllowed("https://html.duckduckgo.com/html/")).isTrue();
        assertThat(BrowserService.isAllowed("https://en.wikipedia.org/wiki/Artificial_intelligence")).isTrue();
        assertThat(BrowserService.isAllowed("https://github.com/spring-projects")).isTrue();
    }

    @Test
    void blocksPrivateAddressesAndLocalhost() {
        assertThat(BrowserService.isAllowed("http://localhost:8080")).isFalse();
        assertThat(BrowserService.isAllowed("http://127.0.0.1:18080")).isFalse();
        assertThat(BrowserService.isAllowed("http://192.168.1.1")).isFalse();
        assertThat(BrowserService.isAllowed("http://10.0.0.1")).isFalse();
        assertThat(BrowserService.isAllowed("http://0.0.0.0")).isFalse();
    }

    @Test
    void blocksInvalidAndNonHttpSchemes() {
        assertThat(BrowserService.isAllowed("file:///C:/Windows/System32")).isFalse();
        assertThat(BrowserService.isAllowed("javascript:alert(1)")).isFalse();
        assertThat(BrowserService.isAllowed("data:text/plain;base64,SGVsbG8=")).isFalse();
        assertThat(BrowserService.isAllowed("not-a-valid-url")).isFalse();
    }

    @Test
    void blocksUnlistedPublicDomains() {
        assertThat(BrowserService.isAllowed("https://untrusted-unknown-site.xyz")).isFalse();
    }
}

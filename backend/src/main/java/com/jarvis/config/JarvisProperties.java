package com.jarvis.config;

import java.net.URI;
import java.time.Duration;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("jarvis")
public record JarvisProperties(
        @NotNull @Pattern(regexp="(?:|.{32,256})") String apiToken,
        @NotNull URI ollamaUrl,
        @NotBlank @Pattern(regexp="[a-zA-Z0-9._:/-]{1,120}") String chatModel,
        @NotBlank @Pattern(regexp="[a-zA-Z0-9._:/-]{1,120}") @DefaultValue("all-minilm:22m") String embeddingModel,
        @Min(1) @Max(4096) @DefaultValue("384") int embeddingDimensions,
        @Min(1024) @Max(16384) int contextLength,
        @Min(32) @Max(4096) int outputTokens,
        @Min(1) @Max(2) int concurrency,
        @NotNull Duration connectTimeout,
        @NotNull Duration responseTimeout,
        @NotNull Duration healthTimeout,
        @NotBlank String keepAlive) {

    @ConstructorBinding
    public JarvisProperties {
    }

    public JarvisProperties(String apiToken, URI ollamaUrl, String chatModel, int contextLength,
                            int outputTokens, int concurrency, Duration connectTimeout,
                            Duration responseTimeout, Duration healthTimeout, String keepAlive) {
        this(apiToken, ollamaUrl, chatModel, "all-minilm:22m", 384, contextLength, outputTokens,
                concurrency, connectTimeout, responseTimeout, healthTimeout, keepAlive);
    }

    @AssertTrue(message="Ollama must use a loopback HTTP address; remote/cloud fallback is disabled")
    public boolean isLocalRuntime() {
        return ollamaUrl != null && ollamaUrl.getHost() != null && "http".equals(ollamaUrl.getScheme())
                && java.util.Set.of("localhost", "127.0.0.1", "[::1]").contains(ollamaUrl.getHost())
                && ollamaUrl.getUserInfo() == null && ollamaUrl.getQuery() == null
                && ollamaUrl.getFragment() == null
                && (ollamaUrl.getPath().isEmpty() || "/".equals(ollamaUrl.getPath()));
    }

    @AssertTrue(message="Output limit must leave at least 512 context tokens for input")
    public boolean isContextBudgetValid() { return contextLength - outputTokens >= 512; }

    @AssertTrue(message="Timeouts must be positive and at most 10 minutes")
    public boolean isTimeoutsValid() {
        return java.util.stream.Stream.of(connectTimeout, responseTimeout, healthTimeout)
                .allMatch(d -> d != null && d.compareTo(Duration.ZERO) > 0 && d.compareTo(Duration.ofMinutes(10)) <= 0);
    }

    // Never let diagnostic logging of configuration include the bearer secret.
    @Override public String toString() { return "JarvisProperties[chatModel=" + chatModel + ", embeddingModel=" + embeddingModel + ", contextLength=" + contextLength + "]"; }
}

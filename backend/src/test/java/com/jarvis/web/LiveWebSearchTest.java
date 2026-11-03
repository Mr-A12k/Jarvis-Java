package com.jarvis.web;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="JARVIS_WEB_TEST",matches="true")
class LiveWebSearchTest {
    @Test void realProviderReturnsSources() {
        var sources=new WebSearch().search("PostgreSQL official documentation").block(Duration.ofSeconds(25));
        assertThat(sources).isNotEmpty().hasSizeLessThanOrEqualTo(3);
        assertThat(sources).allSatisfy(s -> { assertThat(s.title()).isNotBlank(); assertThat(s.url()).startsWith("https://"); });
    }
}

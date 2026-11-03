package com.jarvis.web;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class LocalWebDecisionTest {
    private final List<WebSearch.Source> sources=List.of(new WebSearch.Source(1,"Song","https://example.com/1","Song"),new WebSearch.Source(2,"Docs","https://example.com/2","Documentation"));
    @Test void selectsOnlyExistingSources() {
        assertThat(LocalWebDecision.parse("2",sources)).containsExactly(sources.get(1));
        assertThat(LocalWebDecision.parse("NONE",sources)).isEmpty();
        for(String bad:List.of("3","2,2","Answer: 2","1,2,3","https://evil.example"))
            assertThatThrownBy(() -> LocalWebDecision.parse(bad,sources)).isInstanceOf(com.jarvis.common.ApiException.class);
    }
    @Test void preservesIdsAndUnicodeBudget() {
        assertThat(LocalWebDecision.context(List.of(sources.get(1)),320)).startsWith("[2]");
        assertThat(LocalWebDecision.clip("😀😀",5)).isEqualTo("😀");
    }
}

package com.jarvis.web;

import com.jarvis.chat.ChatService;
import com.jarvis.common.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSearchTest {
    @Test void extractsSourcesAndRejectsExecutableLinks() {
        var results=WebSearch.parse("""
          <div class="result"><a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.postgresql.org%2Fdocs%2F">Postgres &amp; SQL</a><div class="result__snippet">Official docs</div></div>
          <div class="result"><a class="result__a" href="javascript:alert(1)">Bad link</a></div>
          <div class="result"><a class="result__a" href="https://www.postgresql.org/docs/">Duplicate</a></div>
          """);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().url()).isEqualTo("https://www.postgresql.org/docs/");
        assertThat(results.getFirst().title()).isEqualTo("Postgres & SQL");
    }
    @Test void recognizesBlocksAndUnknownMarkup() {
        assertThatThrownBy(() -> WebSearch.parse("<form id='challenge-form'></form>")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> WebSearch.parse("<html>Maintenance</html>")).isInstanceOf(ApiException.class);
        assertThat(WebSearch.parse("<div class='no-results'>Nothing</div>")).isEmpty();
    }
    @Test void rejectsNonWebUrls() {
        for (String url : List.of("file:///etc/passwd", "data:text/html,test", "https://user:pass@example.com/", "/relative", "http://localhost:8080/", "http://127.0.0.1/", "http://192.168.1.1/"))
            assertThat(WebSearch.safeUrl(url)).isNull();
    }
    @Test void extractsCleanPageTextWithoutNoise() {
        String html = "<html><body><header>Header</header><script>console.log(1)</script><p>Important research content.</p><footer>Footer</footer></body></html>";
        String extracted = WebSearch.extractPageText(html);
        assertThat(extracted).isEqualTo("Important research content.");
    }
    @Test void streamsSourcesBeforeAnswer() {
        var search=mock(WebSearch.class); var chat=mock(ChatService.class);
        var sources=List.of(new WebSearch.Source(1,"Docs","https://example.com/","Evidence"));
        when(search.search("question")).thenReturn(Mono.just(sources));
        when(chat.stream(any(), anyString())).thenReturn(Flux.just("1"),Flux.just("Answer [1]"));
        StepVerifier.create(new WebController(search,chat).stream(new WebController.Request("question")))
            .assertNext(e -> { assertThat(e.event()).isEqualTo("sources"); assertThat(e.data().sources()).isEqualTo(sources); })
            .assertNext(e -> assertThat(e.data().text()).isEqualTo("Answer [1]"))
            .assertNext(e -> assertThat(e.event()).isEqualTo("done")).verifyComplete();
    }
    @Test void searchFailureDoesNotCallModelOrEmitDone() {
        var search=mock(WebSearch.class); var chat=mock(ChatService.class);
        when(search.search(anyString())).thenReturn(Mono.error(WebSearch.unavailable()));
        StepVerifier.create(new WebController(search,chat).stream(new WebController.Request("question")))
            .assertNext(e -> { assertThat(e.event()).isEqualTo("error"); assertThat(e.data().code()).isEqualTo("SEARCH_UNAVAILABLE"); }).verifyComplete();
        verifyNoInteractions(chat);
    }
    @Test void noResultsDoesNotCallModel() {
        var search=mock(WebSearch.class); var chat=mock(ChatService.class);
        when(search.search(anyString())).thenReturn(Mono.just(List.of()));
        var events=new WebController(search,chat).stream(new WebController.Request("question")).collectList().block();
        assertThat(events).extracting(e -> e.event()).containsExactly("sources","delta","done");
        verifyNoInteractions(chat);
    }
}

package com.jarvis.chat;

import com.jarvis.config.JarvisProperties;
import com.jarvis.database.DatabaseStore;
import com.jarvis.files.LocalFiles;
import com.jarvis.knowledge.KnowledgeService;
import com.jarvis.web.WebSearch;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ChatRealTimeSearchTest {

    @Test
    void detectsRealTimeAndWebSearchQueries() {
        // User's exact prompt with shorthand & typos
        assertThat(ChatController.isWebSearchQuery("this week ott releas tam movs")).isTrue();
        assertThat(ChatController.isWebSearchQuery("ott release this week")).isTrue();
        assertThat(ChatController.isWebSearchQuery("tamil movies ott release this week")).isTrue();
        assertThat(ChatController.isWebSearchQuery("today weather in chennai")).isTrue();
        assertThat(ChatController.isWebSearchQuery("current gold rate today")).isTrue();
        assertThat(ChatController.isWebSearchQuery("what iss the goald price today")).isTrue();
        assertThat(ChatController.isWebSearchQuery("who won the match yesterday")).isTrue();
        assertThat(ChatController.isWebSearchQuery("latest breaking news")).isTrue();
        assertThat(ChatController.isWebSearchQuery("new movies released this month")).isTrue();
        assertThat(ChatController.isWebSearchQuery("இந்த வாரம் ஓடிடி ரிலீஸ் படங்கள்")).isTrue();
        assertThat(ChatController.isWebSearchQuery("intha vaaram ott release")).isTrue();

        // Explicit search commands
        assertThat(ChatController.isWebSearchQuery("/search best laptops 2026")).isTrue();
        assertThat(ChatController.isWebSearchQuery("google who is the ceo of google")).isTrue();

        // Real-time leadership, metrics, products & global developments
        assertThat(ChatController.isWebSearchQuery("who is the prime minister of UK")).isTrue();
        assertThat(ChatController.isWebSearchQuery("who is Google CEO")).isTrue();
        assertThat(ChatController.isWebSearchQuery("what is the population of India")).isTrue();
        assertThat(ChatController.isWebSearchQuery("latest version of React")).isTrue();
        assertThat(ChatController.isWebSearchQuery("what is the price of Bitcoin")).isTrue();
        assertThat(ChatController.isWebSearchQuery("what happened in the news today")).isTrue();
        assertThat(ChatController.isWebSearchQuery("who won the election")).isTrue();
        assertThat(ChatController.isWebSearchQuery("what is the current inflation rate in US")).isTrue();

        // Non-real-time queries should not trigger web search
        assertThat(ChatController.isWebSearchQuery("write a python function to reverse a linked list")).isFalse();
        assertThat(ChatController.isWebSearchQuery("solve 2x + 5 = 15")).isFalse();
        assertThat(ChatController.isWebSearchQuery("what is your name")).isFalse();
        assertThat(ChatController.isWebSearchQuery("vanakkam")).isFalse();
        assertThat(ChatController.isWebSearchQuery("explain quantum computing")).isFalse();
    }

    @Test
    void detectsDisputesAndNormalizesQueryTypos() {
        assertThat(ChatController.isCorrectionOrDispute("you are wrong")).isTrue();
        assertThat(ChatController.isCorrectionOrDispute("are you sure is this the goald price in india?? its wrong...")).isTrue();
        assertThat(ChatController.isCorrectionOrDispute("you are wrong search it in web and tell me the correct ans")).isTrue();
        assertThat(ChatController.isCorrectionOrDispute("it's wrong fix it")).isTrue();
        assertThat(ChatController.isCorrectionOrDispute("wrong ans")).isTrue();

        assertThat(ChatController.normalizeQueryTypos("what iss the goald price today"))
                .isEqualTo("what is the gold price today");
        assertThat(ChatController.normalizeQueryTypos("wht iss qntm phys"))
                .isEqualTo("what is quantum physics");
    }

    @Test
    void normalizesWebSearchQueryCleansShorthandAndTypos() {
        assertThat(ChatController.normalizeWebSearchQuery("this week ott releas tam movs"))
                .isEqualTo("this week ott release tamil movies");

        assertThat(ChatController.normalizeWebSearchQuery("/search this week ott releas tam movs"))
                .isEqualTo("this week ott release tamil movies");

        assertThat(ChatController.normalizeWebSearchQuery("search internet for todays gold rate"))
                .isEqualTo("today gold rate");

        assertThat(ChatController.normalizeWebSearchQuery("latest update in qntm phys"))
                .isEqualTo("latest update in quantum physics");
    }

    @Test
    void resolvesContextualAnaphoraFromConversationHistory() {
        // User's exact prompt scenario:
        // Turn 1: User asks "Explain a concept: qntm physics", Assistant explains QM.
        // Turn 2: User asks "latest update in that" -> should resolve to "latest update in quantum physics"
        var history = List.of(
                new ChatRequest.Turn(ChatRequest.Role.user, "Explain a concept: qntm physics"),
                new ChatRequest.Turn(ChatRequest.Role.assistant, "Quantum Mechanics (QM) is a fundamental theory in physics that describes the behavior of particles...")
        );

        String resolved = ChatController.resolveContextualQuery("latest update in that", history);
        assertThat(resolved).isEqualTo("latest update in quantum physics");

        // Follow-up queries with "it", "this", "about that"
        assertThat(ChatController.resolveContextualQuery("tell me more about it", history))
                .isEqualTo("tell me more about quantum physics");

        assertThat(ChatController.resolveContextualQuery("what are the applications of this", history))
                .isEqualTo("what are the applications of quantum physics");

        // Terse follow-up queries without pronouns
        assertThat(ChatController.resolveContextualQuery("latest updates", history))
                .isEqualTo("latest updates regarding quantum physics");

        // Tamil anaphora resolution
        var tamilHistory = List.of(
                new ChatRequest.Turn(ChatRequest.Role.user, "சந்திரயான் 3 பற்றி கூறு"),
                new ChatRequest.Turn(ChatRequest.Role.assistant, "சந்திரயான்-3 என்பது நிலவை ஆய்வு செய்ய இஸ்ரோ அனுப்பிய விண்கலம் ஆகும்.")
        );
        assertThat(ChatController.resolveContextualQuery("அதைப் பற்றி மேலும் சொல்", tamilHistory))
                .contains("சந்திரயான் 3 பற்றி மேலும் சொல்");
    }

    @Test
    void reportsRetrievalFailureExplicitlyWhenWebEvidenceIsEmpty() throws Exception {
        var chat = mock(ChatService.class);
        var settings = mock(JarvisProperties.class);
        var files = mock(LocalFiles.class);
        var db = mock(DatabaseStore.class);
        var webSearch = mock(WebSearch.class);
        var knowledgeService = mock(KnowledgeService.class);

        when(files.state()).thenReturn(new LocalFiles.State(List.of(), "", "1"));
        when(settings.chatModel()).thenReturn("qwen3:1.7b");

        // Web search returns empty list (no evidence found online)
        when(webSearch.search(anyString())).thenReturn(Mono.just(List.of()));

        var controller = new ChatController(chat, settings, files, db, knowledgeService, webSearch);

        var history = List.of(
                new ChatRequest.Turn(ChatRequest.Role.user, "Explain a concept: qntm physics"),
                new ChatRequest.Turn(ChatRequest.Role.assistant, "Quantum Mechanics (QM) is a fundamental theory...")
        );

        // User asks follow-up: "latest update in that" -> resolveContextualQuery resolves it to quantum physics
        // Since no web results are found, it must NOT fall back to hallucinating offline answers!
        StepVerifier.create(controller.stream(new ChatRequest("latest update in that", history)))
                .assertNext(e -> {
                    assertThat(e.event()).isEqualTo("delta");
                    assertThat(e.data().text()).contains("No verified web search results were found");
                    assertThat(e.data().text()).contains("No unsupported offline answer was substituted");
                })
                .assertNext(e -> assertThat(e.event()).isEqualTo("done"))
                .verifyComplete();

        // Verify that chat was NOT invoked to synthesize hallucinated answers
        verify(chat, never()).stream(any());
    }

    @Test
    void reportsRetrievalFailureExplicitlyWhenWebSearchErrors() throws Exception {
        var chat = mock(ChatService.class);
        var settings = mock(JarvisProperties.class);
        var files = mock(LocalFiles.class);
        var db = mock(DatabaseStore.class);
        var webSearch = mock(WebSearch.class);
        var knowledgeService = mock(KnowledgeService.class);

        when(files.state()).thenReturn(new LocalFiles.State(List.of(), "", "1"));
        when(settings.chatModel()).thenReturn("qwen3:1.7b");

        // Web search throws an exception (e.g. rate limit, connection timeout)
        when(webSearch.search(anyString())).thenReturn(Mono.error(new RuntimeException("Rate limit exceeded")));

        var controller = new ChatController(chat, settings, files, db, knowledgeService, webSearch);

        StepVerifier.create(controller.stream(new ChatRequest("/search official python tutorial", List.of())))
                .assertNext(e -> {
                    assertThat(e.event()).isEqualTo("delta");
                    assertThat(e.data().text()).contains("Web search retrieval failed");
                    assertThat(e.data().text()).contains("Rate limit exceeded");
                    assertThat(e.data().text()).contains("No unsupported offline answer was substituted");
                })
                .assertNext(e -> assertThat(e.event()).isEqualTo("done"))
                .verifyComplete();

        // Verify that chat was NOT invoked to hallucinate offline answers
        verify(chat, never()).stream(any());
    }

    @Test
    void filtersOutIrrelevantWebSources() {
        var pySource = new WebSearch.Source(1, "The Python Tutorial — Python 3.12 documentation", "https://docs.python.org/3/tutorial/", "Python is an easy to learn, powerful programming language.");
        var cppSource = new WebSearch.Source(2, "C++ Standard Library Tutorial", "https://cppreference.com/tutorial", "C++ is a compiled systems programming language with manual memory management.");

        assertThat(com.jarvis.web.LocalWebDecision.isSourceRelevant("official python tutorial", pySource)).isTrue();
        assertThat(com.jarvis.web.LocalWebDecision.isSourceRelevant("official python tutorial", cppSource)).isFalse();
    }

    @Test
    void detectsModelRealTimeRefusalPhrases() {
        String refusal1 = "I cannot provide information about movie releases this week as I do not have access to real-time data. However, you can check movie release schedules on platforms like IMDb.";
        assertThat(ChatController.isRealTimeRefusal(refusal1)).isTrue();

        String refusal2 = "<think>Checking real time info</think> As an AI, I do not have access to real-time data.";
        assertThat(ChatController.isRealTimeRefusal(refusal2)).isTrue();

        String refusal3 = "I do not possess real-time information as of my knowledge cutoff.";
        assertThat(ChatController.isRealTimeRefusal(refusal3)).isTrue();

        String regularAnswer = "<think>Processing</think> Python is a versatile, high-level programming language.";
        assertThat(ChatController.isRealTimeRefusal(regularAnswer)).isFalse();
    }

    @Test
    void streamsSourcesAndSynthesizedAnswerForRealTimeQuery() throws Exception {
        var chat = mock(ChatService.class);
        var settings = mock(JarvisProperties.class);
        var files = mock(LocalFiles.class);
        var db = mock(DatabaseStore.class);
        var webSearch = mock(WebSearch.class);
        var knowledgeService = mock(KnowledgeService.class);

        when(files.state()).thenReturn(new LocalFiles.State(List.of(), "", "1"));
        when(settings.chatModel()).thenReturn("qwen3:1.7b");

        var sources = List.of(new WebSearch.Source(1, "Filmibeat Tamil OTT", "https://filmibeat.com/tamil-ott", "New OTT Releases This Week In Tamil 2026"));
        when(webSearch.search(anyString())).thenReturn(Mono.just(sources));
        when(chat.stream(any(), anyString())).thenReturn(Flux.just("1"), Flux.just("Here are this week's releases: 1. Amaran on Netflix."));

        var controller = new ChatController(chat, settings, files, db, knowledgeService, webSearch);

        StepVerifier.create(controller.stream(new ChatRequest("this week ott releas tam movs", List.of())))
                .assertNext(e -> {
                    assertThat(e.event()).isEqualTo("sources");
                    assertThat(e.data().sources()).isEqualTo(sources);
                })
                .assertNext(e -> {
                    assertThat(e.event()).isEqualTo("delta");
                    assertThat(e.data().text()).contains("Amaran on Netflix");
                })
                .assertNext(e -> {
                    assertThat(e.event()).isEqualTo("delta");
                    assertThat(e.data().text()).contains("Verified Sources");
                })
                .assertNext(e -> assertThat(e.event()).isEqualTo("done"))
                .verifyComplete();
    }
}

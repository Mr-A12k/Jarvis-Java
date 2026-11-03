package com.jarvis.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.web.WebSearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RealtimeFactServiceTest {

    private WebSearch webSearch;
    private Clock fixedClock;
    private RealtimeFactService factService;

    @BeforeEach
    void setUp() {
        webSearch = mock(WebSearch.class);
        // Fixed at Saturday, 24 October 2026, 12:45:00 UTC
        fixedClock = Clock.fixed(Instant.parse("2026-10-24T12:45:00Z"), ZoneId.of("Asia/Kolkata"));

        var mockSources = List.of(
                new WebSearch.Source(1, "New OTT Movies This Week", "https://example.com/ott", "Movies releasing on Netflix and Prime Video this week")
        );
        when(webSearch.search(anyString())).thenReturn(Mono.just(mockSources));

        factService = new RealtimeFactService(webSearch, new ObjectMapper(), fixedClock);
    }

    @Test
    void detectsRealTimeAndFactCheckQueries() {
        assertThat(factService.isRealtimeQuery("current stock market details")).isTrue();
        assertThat(factService.isRealtimeQuery("what are nifty 50 and sensex share price")).isTrue();
        assertThat(factService.isRealtimeQuery("what iss the goald price today")).isTrue();
        assertThat(factService.isRealtimeQuery("gold price in india")).isTrue();
        assertThat(factService.isRealtimeQuery("silver rate today")).isTrue();
        assertThat(factService.isRealtimeQuery("crude oil price")).isTrue();
        assertThat(factService.isRealtimeQuery("தங்கம் விலை நிலவரம்")).isTrue();
        assertThat(factService.isRealtimeQuery("this week ott released movies")).isTrue();
        assertThat(factService.isRealtimeQuery("what is today's date and time")).isTrue();
        assertThat(factService.isRealtimeQuery("what time is it right now")).isTrue();
        assertThat(factService.isRealtimeQuery("fact check if aliens exist")).isTrue();
        assertThat(factService.isRealtimeQuery("/stock btc")).isTrue();
        assertThat(factService.isRealtimeQuery("/gold")).isTrue();
        assertThat(factService.isRealtimeQuery("/ott new releases")).isTrue();
        assertThat(factService.isRealtimeQuery("இன்றைய தேதி மற்றும் பங்குச்சந்தை")).isTrue();

        // Non-realtime queries
        assertThat(factService.isRealtimeQuery("write a java class for customer")).isFalse();
        assertThat(factService.isRealtimeQuery("calculate 45 * 90")).isFalse();
    }

    @Test
    void formatsTemporalGroundingCorrectly() {
        String english = factService.getTemporalSummary(false);
        assertThat(english).contains("Saturday").contains("24 October 2026").contains("Asia/Kolkata");

        String tamil = factService.getTemporalSummary(true);
        assertThat(tamil).contains("சனிக்கிழமை").contains("24 அக்டோபர் 2026").contains("Asia/Kolkata");
    }

    @Test
    void gathersRealtimeFactsForCombinedQuery() {
        // Query combining both date/time and OTT movies
        String query = "what is today's date and time and what movies released on OTT this week";
        RealtimeFactReport report = factService.gatherRealtimeFacts(query).block();

        assertThat(report).isNotNull();
        assertThat(report.summaryText()).contains("24 October 2026");
        assertThat(report.summaryText()).contains("New OTT Movies This Week");
        assertThat(report.sources()).isNotEmpty();
    }
}

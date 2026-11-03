package com.jarvis.chat;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class LocalFactsTest {
    @Test void answersReportedQuestionFromClock() {
        var facts=new LocalFacts(Clock.fixed(Instant.parse("2026-10-16T20:00:00Z"),ZoneId.of("Asia/Kolkata")));
        assertThat(facts.answer("Hello whats your name?? and what is the date of today")).hasValue("Hello! I'm JARVIS, your local personal assistant. Today is Saturday, 17 October 2026 (Asia/Kolkata).");
    }
    @Test void doesNotSwallowOtherRequirements() {
        var facts=new LocalFacts();
        assertThat(facts.answer("What's your name and explain gravity")).isEmpty();
        assertThat(facts.answer("Find a song called what's your name")).isEmpty();
    }
    @Test void answersUserIdentity() {
        var facts=new LocalFacts();
        assertThat(facts.answer("who am I").orElseThrow()).contains("authorized user of this workspace");
        assertThat(facts.answer("whats my name").orElseThrow()).contains("authorized user of this workspace");
    }
    @Test void answersTamilGreetingsAndIdentity() {
        var facts=new LocalFacts();
        assertThat(facts.answer("வணக்கம்").orElseThrow()).contains("ஜார்விஸ்");
        assertThat(facts.answer("நான் யார்").orElseThrow()).contains("முதன்மை மற்றும் அங்கீகரிக்கப்பட்ட பயனர்");
        assertThat(facts.answer("vanakkam").orElseThrow()).contains("ஜார்விஸ்");
    }
    @Test void answersHardwareQueries() {
        var facts=new LocalFacts();
        assertThat(facts.answer("how much mem available").orElseThrow()).contains("RAM").contains("GPU");
        assertThat(facts.answer("how much ram and gpu do i have").orElseThrow()).contains("RAM").contains("ROM");
        assertThat(facts.answer("நினைவகம் எவ்வளவு உள்ளது").orElseThrow()).contains("RAM").contains("GPU");
    }
    @Test void followsDateRollover() {
        var facts=new LocalFacts(Clock.fixed(Instant.parse("2026-10-17T20:00:00Z"),ZoneId.of("Asia/Kolkata")));
        assertThat(facts.answer("Today's date?").orElseThrow()).contains("18 October 2026");
    }
}

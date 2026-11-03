package com.jarvis.voice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LanguagePreferenceServiceTest {

    private LanguagePreferenceService service;

    @BeforeEach
    void setUp() {
        service = new LanguagePreferenceService();
    }

    @Test
    void testDefaultLanguageIsEnglish() {
        assertEquals("en", service.getActiveLanguage());
    }

    @Test
    void testSwitchToEnglishCommand() {
        Optional<String> ack = service.handleLanguageSwitchCommand("switch to english please");
        assertTrue(ack.isPresent());
        assertEquals("en", service.getActiveLanguage());
        assertTrue(ack.get().contains("switched to English"));
        assertTrue(ack.get().contains("<think>"));
    }

    @Test
    void testSwitchToTamilCommand() {
        Optional<String> ack = service.handleLanguageSwitchCommand("தமிழுக்கு மாறு");
        assertTrue(ack.isPresent());
        assertEquals("ta", service.getActiveLanguage());
        assertTrue(ack.get().contains("தமிழுக்கு மாறிவிட்டேன்"));
        assertTrue(ack.get().contains("<think>"));
    }

    @Test
    void testSwitchToHindiCommand() {
        Optional<String> ack = service.handleLanguageSwitchCommand("switch to hindi");
        assertTrue(ack.isPresent());
        assertEquals("hi", service.getActiveLanguage());
        assertTrue(ack.get().contains("हिन्दी"));
    }

    @Test
    void testSwitchToFrenchCommand() {
        Optional<String> ack = service.handleLanguageSwitchCommand("speak in french");
        assertTrue(ack.isPresent());
        assertEquals("fr", service.getActiveLanguage());
        assertTrue(ack.get().contains("français"));
    }

    @Test
    void testSwitchToSpanishCommand() {
        Optional<String> ack = service.handleLanguageSwitchCommand("habla en español");
        assertTrue(ack.isPresent());
        assertEquals("es", service.getActiveLanguage());
        assertTrue(ack.get().contains("español"));
    }

    @Test
    void testSwitchToGermanCommand() {
        Optional<String> ack = service.handleLanguageSwitchCommand("switch to german");
        assertTrue(ack.isPresent());
        assertEquals("de", service.getActiveLanguage());
        assertTrue(ack.get().contains("Deutsch"));
    }

    @Test
    void testSwitchToJapaneseCommand() {
        Optional<String> ack = service.handleLanguageSwitchCommand("日本語で話して");
        assertTrue(ack.isPresent());
        assertEquals("ja", service.getActiveLanguage());
        assertTrue(ack.get().contains("日本語"));
    }

    @Test
    void testEnglishSentenceContainingWordTamilIsNotTamil() {
        assertFalse(service.isTamil("Why are you speaking in Tamil, please speak English"));
        assertTrue(service.isEnglishQuery("Why are you speaking in Tamil, please speak English"));
        assertEquals("en", service.resolveLanguage("Why are you speaking in Tamil, please speak English"));
    }

    @Test
    void testTamilScriptRecognition() {
        assertTrue(service.isTamil("வணக்கம் ஜார்விஸ்"));
        assertFalse(service.isEnglishQuery("வணக்கம் ஜார்விஸ்"));
        assertEquals("ta", service.resolveLanguage("வணக்கம் ஜார்விஸ்"));
    }

    @Test
    void testPromptDirectiveGeneration() {
        String engDirective = service.getPromptLanguageDirective("Explain quantum computing");
        assertTrue(engDirective.contains("The user communicated in English"));

        String tamDirective = service.getPromptLanguageDirective("குவாண்டம் கம்ப்யூட்டிங் என்றால் என்ன?");
        assertTrue(tamDirective.contains("The user communicated in Tamil (தமிழ்)"));
    }
}

package com.jarvis.voice.agent;

import com.jarvis.voice.model.SpeechRecognitionResult;
import java.util.Optional;

/**
 * Liskov Substitution Principle (LSP) & Open/Closed Principle (OCP):
 * Contract for a specialized language voice agent. Concrete language agents (e.g., Tamil, Hindi, Telugu, English)
 * can be substituted wherever this interface is used without modifying consumers.
 */
public interface LanguageVoiceAgent {
    String languageTag();
    String displayName();
    boolean supports(String languageTag);
    SpeechRecognitionResult listen();
    boolean speak(String text);
    String processInput(String text);

    /**
     * Determines whether this language agent detects comprehension of the given text (native script or transliteration).
     */
    default boolean canUnderstand(String text) {
        return false;
    }

    /**
     * Determines zero-latency conversational fast-path answers for common greetings, identity, wellness, or status.
     */
    default Optional<String> answerDirectly(String text) {
        return Optional.empty();
    }
}

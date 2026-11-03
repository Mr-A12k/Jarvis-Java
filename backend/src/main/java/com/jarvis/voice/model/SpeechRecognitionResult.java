package com.jarvis.voice.model;

/**
 * Encapsulates the output of a speech recognition process.
 * Follows the Single Responsibility Principle (SRP) by representing immutable voice data.
 */
public record SpeechRecognitionResult(String text, double confidence, String language) {
    public static SpeechRecognitionResult empty(String language) {
        return new SpeechRecognitionResult("", 0.0, language);
    }

    public boolean hasSpeech() {
        return text != null && !text.isBlank();
    }
}

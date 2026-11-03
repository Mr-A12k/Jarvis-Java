package com.jarvis.voice.engine;

import com.jarvis.voice.model.SpeechRecognitionResult;

/**
 * Interface Segregation Principle (ISP):
 * Focused solely on converting speech input into textual representation.
 */
public interface SpeechRecognizer {
    SpeechRecognitionResult recognize(String languageTag);
    boolean supportsLanguage(String languageTag);
}

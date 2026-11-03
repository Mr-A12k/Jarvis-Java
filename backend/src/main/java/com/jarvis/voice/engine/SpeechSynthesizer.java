package com.jarvis.voice.engine;

/**
 * Interface Segregation Principle (ISP):
 * Focused solely on synthesizing text into audible speech.
 */
public interface SpeechSynthesizer {
    boolean synthesize(String text, String languageTag);
    boolean supportsLanguage(String languageTag);
}

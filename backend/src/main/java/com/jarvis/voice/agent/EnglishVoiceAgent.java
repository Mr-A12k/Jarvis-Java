package com.jarvis.voice.agent;

import com.jarvis.voice.engine.SpeechRecognizer;
import com.jarvis.voice.engine.SpeechSynthesizer;
import com.jarvis.voice.model.SpeechRecognitionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * Concrete implementation of LanguageVoiceAgent for English.
 * Provides sophisticated, articulate JARVIS conversational fast-paths and voice synthesis.
 */
@Component
public class EnglishVoiceAgent implements LanguageVoiceAgent {
    private final SpeechRecognizer recognizer;
    private final SpeechSynthesizer synthesizer;

    public EnglishVoiceAgent(@Autowired(required = false) SpeechRecognizer recognizer,
                             @Autowired(required = false) SpeechSynthesizer synthesizer) {
        this.recognizer = recognizer;
        this.synthesizer = synthesizer;
    }

    @Override
    public String languageTag() {
        return "en-US";
    }

    @Override
    public String displayName() {
        return "English (US)";
    }

    @Override
    public boolean supports(String languageTag) {
        if (languageTag == null) return false;
        String lower = languageTag.toLowerCase(Locale.ROOT).trim();
        return lower.startsWith("en");
    }

    @Override
    public SpeechRecognitionResult listen() {
        return recognizer != null ? recognizer.recognize(languageTag()) : SpeechRecognitionResult.empty(languageTag());
    }

    @Override
    public boolean speak(String text) {
        return synthesizer != null && synthesizer.synthesize(text, languageTag());
    }

    @Override
    public String processInput(String text) {
        return text != null ? text.trim() : "";
    }

    @Override
    public boolean canUnderstand(String text) {
        if (text == null || text.isBlank()) return false;
        String clean = clean(text);
        return clean.contains("hello") || clean.contains("jarvis") || clean.contains("good morning")
                || clean.contains("who am i") || clean.contains("who are you") || clean.contains("how are you")
                || clean.contains("what time") || clean.contains("thank you");
    }

    @Override
    public Optional<String> answerDirectly(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String clean = clean(text);

        // Greetings
        if (clean.contains("good morning")) {
            return Optional.of("Good morning, sir. I trust you slept well. All systems are initialized and standing by for your command.");
        }
        if (clean.contains("good evening")) {
            return Optional.of("Good evening, sir. How may I be of assistance tonight?");
        }
        if (clean.contains("good afternoon")) {
            return Optional.of("Good afternoon, sir. How can I help with your current tasks?");
        }
        if (clean.matches(".*\\b(hello jarvis|hey jarvis|hi jarvis|hello|hey)\\b.*")) {
            return Optional.of("Hello, sir. J.A.R.V.I.S. online and at your service. What are we working on today?");
        }

        // Identity: Who am I
        if (clean.contains("who am i") || clean.contains("what is my name") || clean.contains("whats my name") || clean.contains("do you know me")) {
            String userName = System.getProperty("user.name", "Kabil");
            if (userName.isBlank()) userName = "Kabil";
            return Optional.of("You are " + userName + ", the authorized owner and creator of this workspace. I remain entirely at your command, sir.");
        }

        // Identity: Who are you
        if (clean.contains("who are you") || clean.contains("what is your name") || clean.contains("whats your name")) {
            return Optional.of("I am J.A.R.V.I.S. (Just A Rather Very Intelligent System) — your personal, articulate, and autonomous AI assistant.");
        }

        // Wellness / System Status
        if (clean.contains("how are you") || clean.contains("status report") || clean.contains("system status") || clean.contains("how are you doing")) {
            return Optional.of("All core telemetry and cognitive routines are operating at peak efficiency, sir. Ready to assist with research, tools, and automation.");
        }

        // Presence check
        if (clean.matches(".*\\b(are you there|are you listening|you there jarvis)\\b.*")) {
            return Optional.of("Always here, sir. Listening and ready.");
        }

        // Time check
        if (clean.contains("what time is it") || clean.contains("current time") || clean.contains("what is the time")) {
            String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH));
            return Optional.of("The current time is " + timeStr + ", sir.");
        }

        // Date check
        if (clean.contains("what date is it") || clean.contains("whats today date") || clean.contains("current date") || clean.contains("what day is it")) {
            String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy", Locale.ENGLISH));
            return Optional.of("Today is " + dateStr + ", sir.");
        }

        // Gratitude
        if (clean.contains("thank you") || clean.contains("thanks jarvis") || clean.contains("appreciate it")) {
            return Optional.of("Always an absolute pleasure to assist, sir.");
        }

        // Farewell
        if (clean.matches(".*\\b(goodbye|bye jarvis|see you later|see ya)\\b.*")) {
            return Optional.of("Goodbye, sir. I shall remain standing by should you require anything further.");
        }

        return Optional.empty();
    }

    private String clean(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[?!.,']", "").trim();
    }
}

package com.jarvis.voice.agent;

import com.jarvis.voice.engine.SpeechRecognizer;
import com.jarvis.voice.engine.SpeechSynthesizer;
import com.jarvis.voice.model.SpeechRecognitionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Concrete LanguageVoiceAgent specialized for German (Deutsch).
 */
@Component
public class GermanVoiceAgent implements LanguageVoiceAgent {
    private final SpeechRecognizer recognizer;
    private final SpeechSynthesizer synthesizer;

    private static final Set<String> GERMAN_TRIGGERS = Set.of(
            "guten tag", "guten morgen", "guten abend", "gute nacht", "hallo",
            "wie geht es ihnen", "wie gehts", "wer bist du", "wer bin ich",
            "mein name", "danke", "vielen dank", "auf wiedersehen", "tschuess"
    );

    public GermanVoiceAgent(@Autowired(required = false) SpeechRecognizer recognizer,
                            @Autowired(required = false) SpeechSynthesizer synthesizer) {
        this.recognizer = recognizer;
        this.synthesizer = synthesizer;
    }

    @Override
    public String languageTag() {
        return "de-DE";
    }

    @Override
    public String displayName() {
        return "Deutsch (German)";
    }

    @Override
    public boolean supports(String languageTag) {
        if (languageTag == null) return false;
        String lower = languageTag.toLowerCase(Locale.ROOT).trim();
        return lower.startsWith("de") || lower.contains("german");
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
        String clean = normalize(text);
        for (String trigger : GERMAN_TRIGGERS) {
            if (clean.contains(trigger)) return true;
        }
        return false;
    }

    private String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
                .replaceAll("[?!.,]", "").trim();
    }

    @Override
    public Optional<String> answerDirectly(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String clean = normalize(text);

        if (clean.contains("guten morgen")) {
            return Optional.of("Guten Morgen! Ich wünsche Ihnen einen erfolgreichen Tag. Wie kann ich behilflich sein?");
        }
        if (clean.contains("guten abend")) {
            return Optional.of("Guten Abend! Wie kann ich Ihnen heute Abend behilflich sein?");
        }
        if (clean.contains("gute nacht")) {
            return Optional.of("Gute Nacht! Ruhen Sie sich gut aus.");
        }
        if (clean.matches(".*(hallo|guten tag|gruess gott).*")) {
            return Optional.of("Hallo! Ich bin JARVIS, Ihr persönlicher KI-Assistent. Wie darf ich Ihnen helfen?");
        }

        // Identity
        if (clean.contains("wer bin ich") || clean.contains("mein name")) {
            String userName = System.getProperty("user.name", "Kabil");
            if (userName.isBlank()) userName = "Kabil";
            return Optional.of("Sie sind " + userName + ", der Hauptbenutzer und Eigentümer dieses Systems. Stets zu Ihren Diensten.");
        }
        if (clean.contains("wer bist du") || clean.contains("dein name")) {
            return Optional.of("Ich bin JARVIS — Ihr intelligenter und sicherer KI-Assistent.");
        }

        // Wellness
        if (clean.contains("wie geht es ihnen") || clean.contains("wie gehts")) {
            return Optional.of("Alle Systeme laufen mit maximaler Leistungsfähigkeit, vielen Dank! Und wie geht es Ihnen?");
        }

        // Gratitude
        if (clean.contains("danke") || clean.contains("vielen dank")) {
            return Optional.of("Sehr gerne geschehen! Es ist mir eine Freude zu helfen.");
        }

        // Farewell
        if (clean.contains("auf wiedersehen") || clean.contains("tschuess")) {
            return Optional.of("Auf Wiedersehen! Rufen Sie mich einfach, wenn Sie Unterstützung benötigen.");
        }

        return Optional.empty();
    }
}

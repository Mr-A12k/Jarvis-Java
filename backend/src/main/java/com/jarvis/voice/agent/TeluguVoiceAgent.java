package com.jarvis.voice.agent;

import com.jarvis.voice.engine.SpeechRecognizer;
import com.jarvis.voice.engine.SpeechSynthesizer;
import com.jarvis.voice.model.SpeechRecognitionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Concrete LanguageVoiceAgent specialized for Telugu (తెలుగు).
 * Provides Telugu script processing, Telugish dictionary mapping, and direct conversational fast-paths.
 */
@Component
public class TeluguVoiceAgent implements LanguageVoiceAgent {
    private final SpeechRecognizer recognizer;
    private final SpeechSynthesizer synthesizer;

    private static final Pattern TELUGU_SCRIPT = Pattern.compile("[\\u0C00-\\u0C7F]");
    private static final Map<String, String> TELUGISH_DICTIONARY = Map.ofEntries(
            Map.entry("namaskaram", "నమస్కారం"),
            Map.entry("namaste", "నమస్కారం"),
            Map.entry("shubhodayam", "శుభోదయం"),
            Map.entry("shubharatri", "శుభరాత్రి"),
            Map.entry("ela unnaru", "ఎలా ఉన్నారు?"),
            Map.entry("bagunnara", "బాగున్నారా?"),
            Map.entry("meeru evaru", "మీరు ఎవరు?"),
            Map.entry("nuvvu evaru", "నువ్వు ఎవరు?"),
            Map.entry("naa peru enti", "నా పేరు ఏంటి?"),
            Map.entry("nenu evaru", "నేను ఎవరు?"),
            Map.entry("sahayam cheyandi", "సహాయం చేయండి"),
            Map.entry("dhanyavadalu", "ధన్యవాదాలు"),
            Map.entry("chala dhanyavadalu", "చాలా ధన్యవాదాలు"),
            Map.entry("selavu", "సెలవు"),
            Map.entry("malli kaluddam", "మళ్లీ కలుద్దాం")
    );

    public TeluguVoiceAgent(@Autowired(required = false) SpeechRecognizer recognizer,
                            @Autowired(required = false) SpeechSynthesizer synthesizer) {
        this.recognizer = recognizer;
        this.synthesizer = synthesizer;
    }

    @Override
    public String languageTag() {
        return "te-IN";
    }

    @Override
    public String displayName() {
        return "తెలుగు (Telugu)";
    }

    @Override
    public boolean supports(String languageTag) {
        if (languageTag == null) return false;
        String lower = languageTag.toLowerCase(Locale.ROOT).trim();
        return lower.startsWith("te") || lower.contains("telugu");
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
        if (text == null || text.isBlank()) return "";
        String trimmed = text.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT).replaceAll("[?!.,]", "");
        for (var entry : TELUGISH_DICTIONARY.entrySet()) {
            if (lower.equals(entry.getKey()) || lower.startsWith(entry.getKey() + " ")) {
                return entry.getValue();
            }
        }
        return trimmed;
    }

    @Override
    public boolean canUnderstand(String text) {
        if (text == null || text.isBlank()) return false;
        if (TELUGU_SCRIPT.matcher(text).find()) return true;
        String lower = text.toLowerCase(Locale.ROOT).replaceAll("[?!.,]", "");
        for (String key : TELUGISH_DICTIONARY.keySet()) {
            if (lower.contains(key)) return true;
        }
        return false;
    }

    @Override
    public Optional<String> answerDirectly(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String processed = processInput(text);

        // Greetings
        if (processed.matches(".*శుభోదయం.*")) {
            return Optional.of("శుభోదయం! ఈ రోజు మీకు అద్భుతంగా గడవాలని కోరుకుంటున్నాను. నేను మీకు ఎలా సహాయపడగలను?");
        }
        if (processed.matches(".*శుభరాత్రి.*")) {
            return Optional.of("శుభరాత్రి! విశ్రాంతి తీసుకోండి.");
        }
        if (processed.matches(".*(నమస్కారం|నమస్తే).*")) {
            return Optional.of("నమస్కారం! నేను జార్విస్ (JARVIS), మీ వ్యక్తిగత AI సహాయకుడిని. మీకు ఏమి సహాయం కావాలి?");
        }

        // Identity: Who am I
        if (processed.matches(".*(నేను ఎవరు|నా పేరు ఏంటి|నన్ను గుర్తించారా).*")) {
            String userName = System.getProperty("user.name", "Kabil");
            if (userName.isBlank()) userName = "Kabil";
            return Optional.of("మీరు " + userName + ", ఈ వర్క్‌స్పేస్ ముఖ్య యజమాని. నేను ఎల్లప్పుడూ మీ సేవలో సిద్ధంగా ఉంటాను.");
        }

        // Identity: Who are you
        if (processed.matches(".*(మీరు ఎవరు|నువ్వు ఎవరు|నీ పేరు ఏమిటి).*")) {
            return Optional.of("నేను జార్విస్ (JARVIS) — మీ సురక్షితమైన, వేగవంతమైన ఆర్టిఫిషియల్ ఇంటెలిజెన్స్ సహాయకుడిని.");
        }

        // Wellness / How are you
        if (processed.matches(".*(ఎలా ఉన్నారు|బాగున్నారా|సంగతులు ఏంటి).*")) {
            return Optional.of("నేను పూర్తి సామర్థ్యంతో పని చేస్తున్నాను, ధన్యవాదాలు! మీరు ఎలా ఉన్నారు?");
        }

        // Gratitude
        if (processed.matches(".*(ధన్యవాదాలు|చాలా సంతోషం).*")) {
            return Optional.of("మీకు సంతోషం కలిగించినందుకు ధన్యవాదాలు! ఎల్లప్పుడూ మీ సేవలో ఉంటాను.");
        }

        // Farewell
        if (processed.matches(".*(సెలవు|మళ్లీ కలుద్దాం|బాయ్).*")) {
            return Optional.of("సెలవు! ఏదైనా అవసరమైనప్పుడు నన్ను పిలవండి.");
        }

        return Optional.empty();
    }
}

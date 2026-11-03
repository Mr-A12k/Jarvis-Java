package com.jarvis.voice.agent;

import com.jarvis.voice.engine.SpeechRecognizer;
import com.jarvis.voice.engine.SpeechSynthesizer;
import com.jarvis.voice.model.SpeechRecognitionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Concrete LanguageVoiceAgent specialized for French (Français).
 */
@Component
public class FrenchVoiceAgent implements LanguageVoiceAgent {
    private final SpeechRecognizer recognizer;
    private final SpeechSynthesizer synthesizer;

    private static final Set<String> FRENCH_TRIGGERS = Set.of(
            "bonjour", "bonsoir", "bonne nuit", "salut",
            "comment allez-vous", "comment vas-tu", "qui etes-vous", "qui suis-je",
            "mon nom", "merci", "merci beaucoup", "au revoir", "a bientot"
    );

    public FrenchVoiceAgent(@Autowired(required = false) SpeechRecognizer recognizer,
                            @Autowired(required = false) SpeechSynthesizer synthesizer) {
        this.recognizer = recognizer;
        this.synthesizer = synthesizer;
    }

    @Override
    public String languageTag() {
        return "fr-FR";
    }

    @Override
    public String displayName() {
        return "Français (French)";
    }

    @Override
    public boolean supports(String languageTag) {
        if (languageTag == null) return false;
        String lower = languageTag.toLowerCase(Locale.ROOT).trim();
        return lower.startsWith("fr") || lower.contains("french");
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
        for (String trigger : FRENCH_TRIGGERS) {
            if (clean.contains(trigger)) return true;
        }
        return false;
    }

    private String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replace("é", "e").replace("è", "e").replace("ê", "e").replace("à", "a").replace("ç", "c").replace("ù", "u")
                .replaceAll("[?!.,']", "").trim();
    }

    @Override
    public Optional<String> answerDirectly(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String clean = normalize(text);

        if (clean.contains("bonjour")) {
            return Optional.of("Bonjour ! J'espère que vous passez une agréable journée. Comment puis-je vous aider ?");
        }
        if (clean.contains("bonsoir")) {
            return Optional.of("Bonsoir ! En quoi puis-je vous être utile ce soir ?");
        }
        if (clean.contains("bonne nuit")) {
            return Optional.of("Bonne nuit ! Reposez-vous bien.");
        }
        if (clean.matches(".*(salut|coucou).*")) {
            return Optional.of("Salut ! Je suis JARVIS, votre assistant IA personnel. Que faisons-nous aujourd'hui ?");
        }

        // Identity
        if (clean.contains("qui suis-je") || clean.contains("mon nom")) {
            String userName = System.getProperty("user.name", "Kabil");
            if (userName.isBlank()) userName = "Kabil";
            return Optional.of("Vous êtes " + userName + ", l'administrateur principal de cet espace. Toujours à vos ordres.");
        }
        if (clean.contains("qui etes-vous") || clean.contains("ton nom")) {
            return Optional.of("Je suis JARVIS, votre assistant d'intelligence artificielle personnel.");
        }

        // Wellness
        if (clean.contains("comment allez-vous") || clean.contains("comment vas-tu") || clean.contains("ca va")) {
            return Optional.of("Tous mes systèmes fonctionnent à plein régime, merci ! Et vous, comment vous portez-vous ?");
        }

        // Gratitude
        if (clean.contains("merci")) {
            return Optional.of("Je vous en prie ! C'est un réel plaisir de vous aider.");
        }

        // Farewell
        if (clean.contains("au revoir") || clean.contains("a bientot")) {
            return Optional.of("Au revoir ! Faites appel à moi dès que nécessaire.");
        }

        return Optional.empty();
    }
}

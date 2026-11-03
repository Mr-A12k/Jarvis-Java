package com.jarvis.voice.agent;

import com.jarvis.voice.engine.SpeechRecognizer;
import com.jarvis.voice.engine.SpeechSynthesizer;
import com.jarvis.voice.model.SpeechRecognitionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Concrete LanguageVoiceAgent specialized for Spanish (Español).
 */
@Component
public class SpanishVoiceAgent implements LanguageVoiceAgent {
    private final SpeechRecognizer recognizer;
    private final SpeechSynthesizer synthesizer;

    private static final Set<String> SPANISH_TRIGGERS = Set.of(
            "hola", "buenos dias", "buenas tardes", "buenas noches",
            "como estas", "que tal", "quien eres", "quien soy yo",
            "mi nombre", "gracias", "muchas gracias", "adios", "hasta luego"
    );

    public SpanishVoiceAgent(@Autowired(required = false) SpeechRecognizer recognizer,
                             @Autowired(required = false) SpeechSynthesizer synthesizer) {
        this.recognizer = recognizer;
        this.synthesizer = synthesizer;
    }

    @Override
    public String languageTag() {
        return "es-ES";
    }

    @Override
    public String displayName() {
        return "Español (Spanish)";
    }

    @Override
    public boolean supports(String languageTag) {
        if (languageTag == null) return false;
        String lower = languageTag.toLowerCase(Locale.ROOT).trim();
        return lower.startsWith("es") || lower.contains("spanish");
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
        for (String trigger : SPANISH_TRIGGERS) {
            if (clean.contains(trigger)) return true;
        }
        return false;
    }

    private String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u").replace("ñ", "n")
                .replaceAll("[¿?¡!.,]", "").trim();
    }

    @Override
    public Optional<String> answerDirectly(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String clean = normalize(text);

        if (clean.contains("buenos dias")) {
            return Optional.of("¡Buenos días! Espero que tenga una excelente jornada. ¿En qué puedo asistirle hoy?");
        }
        if (clean.contains("buenas noches")) {
            return Optional.of("¡Buenas noches! Descanse plácidamente.");
        }
        if (clean.contains("buenas tardes")) {
            return Optional.of("¡Buenas tardes! ¿En qué puedo colaborar con usted hoy?");
        }
        if (clean.matches(".*(hola|saludos).*")) {
            return Optional.of("¡Hola! Soy JARVIS, su asistente de inteligencia artificial personal. ¿Cómo puedo ayudarle?");
        }

        // Identity
        if (clean.contains("quien soy yo") || clean.contains("mi nombre") || clean.contains("quien soy")) {
            String userName = System.getProperty("user.name", "Kabil");
            if (userName.isBlank()) userName = "Kabil";
            return Optional.of("Usted es " + userName + ", el usuario principal y propietario de este espacio. Siempre a su servicio.");
        }
        if (clean.contains("quien eres") || clean.contains("tu nombre")) {
            return Optional.of("Soy JARVIS, su asistente de inteligencia artificial dedicado y altamente capaz.");
        }

        // Wellness
        if (clean.contains("como estas") || clean.contains("que tal")) {
            return Optional.of("Todos los sistemas operan al cien por ciento de eficiencia, gracias. ¿Y usted?");
        }

        // Gratitude
        if (clean.contains("gracias")) {
            return Optional.of("¡Es un absoluto placer ayudarle!");
        }

        // Farewell
        if (clean.contains("adios") || clean.contains("hasta luego") || clean.contains("chao")) {
            return Optional.of("¡Hasta pronto! No dude en llamarme cuando me necesite.");
        }

        return Optional.empty();
    }
}

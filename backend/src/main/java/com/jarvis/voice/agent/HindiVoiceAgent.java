package com.jarvis.voice.agent;

import com.jarvis.voice.engine.SpeechRecognizer;
import com.jarvis.voice.engine.SpeechSynthesizer;
import com.jarvis.voice.model.SpeechRecognitionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Concrete LanguageVoiceAgent specialized for Hindi (हिन्दी).
 * Provides Devanagari script processing, Hinglish dictionary mapping, and direct conversational fast-paths.
 */
@Component
public class HindiVoiceAgent implements LanguageVoiceAgent {
    private final SpeechRecognizer recognizer;
    private final SpeechSynthesizer synthesizer;

    private static final Pattern HINDI_SCRIPT = Pattern.compile("[\\u0900-\\u097F]");
    private static final Map<String, String> HINGLISH_DICTIONARY = Map.ofEntries(
            Map.entry("namaste", "नमस्ते"),
            Map.entry("namaskar", "नमस्कार"),
            Map.entry("kaise ho", "कैसे हो?"),
            Map.entry("kya haal hai", "क्या हाल है?"),
            Map.entry("shubh prabhat", "शुभ प्रभात"),
            Map.entry("shubh ratri", "शुभ रात्रि"),
            Map.entry("aap kaun ho", "आप कौन हो?"),
            Map.entry("tum kaun ho", "तुम कौन हो?"),
            Map.entry("mera naam kya hai", "मेरा नाम क्या है?"),
            Map.entry("main kaun hoon", "मैं कौन हूँ?"),
            Map.entry("kya kar rahe ho", "क्या कर रहे हो?"),
            Map.entry("madad karo", "मदद करो"),
            Map.entry("shukriya", "शुक्रिया"),
            Map.entry("dhanyawad", "धन्यवाद"),
            Map.entry("dhanyavad", "धन्यवाद"),
            Map.entry("alvida", "अलविदा"),
            Map.entry("phir milenge", "फिर मिलेंगे")
    );

    public HindiVoiceAgent(@Autowired(required = false) SpeechRecognizer recognizer,
                           @Autowired(required = false) SpeechSynthesizer synthesizer) {
        this.recognizer = recognizer;
        this.synthesizer = synthesizer;
    }

    @Override
    public String languageTag() {
        return "hi-IN";
    }

    @Override
    public String displayName() {
        return "हिन्दी (Hindi)";
    }

    @Override
    public boolean supports(String languageTag) {
        if (languageTag == null) return false;
        String lower = languageTag.toLowerCase(Locale.ROOT).trim();
        return lower.startsWith("hi") || lower.contains("hindi");
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
        for (var entry : HINGLISH_DICTIONARY.entrySet()) {
            if (lower.equals(entry.getKey()) || lower.startsWith(entry.getKey() + " ")) {
                return entry.getValue();
            }
        }
        return trimmed;
    }

    @Override
    public boolean canUnderstand(String text) {
        if (text == null || text.isBlank()) return false;
        if (HINDI_SCRIPT.matcher(text).find()) return true;
        String lower = text.toLowerCase(Locale.ROOT).replaceAll("[?!.,]", "");
        for (String key : HINGLISH_DICTIONARY.keySet()) {
            if (lower.contains(key)) return true;
        }
        return false;
    }

    @Override
    public Optional<String> answerDirectly(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String processed = processInput(text);

        // Morning / evening / night greetings
        if (processed.matches(".*(शुभ प्रभात|सुबह की नमस्ते).*")) {
            return Optional.of("शुभ प्रभात! आशा है आपका दिन बहुत अच्छा रहे। मैं आपकी क्या सेवा कर सकता हूँ?");
        }
        if (processed.matches(".*शुभ रात्रि.*")) {
            return Optional.of("शुभ रात्रि! आराम करें और स्वस्थ रहें।");
        }
        if (processed.matches(".*(नमस्ते|नमस्कार|प्रणाम).*")) {
            return Optional.of("नमस्ते! मैं जार्विस (JARVIS) हूँ, आपका समर्पित AI सहायक। आज मैं आपकी क्या मदद कर सकता हूँ?");
        }

        // Identity: Who am I
        if (processed.matches(".*(मैं कौन हूँ|मेरा नाम क्या है|मुझे जानते हो).*")) {
            String userName = System.getProperty("user.name", "Kabil");
            if (userName.isBlank()) userName = "Kabil";
            return Optional.of("आप " + userName + " हैं, इस वर्कस्पेस के मुख्य और अधिकृत स्वामी। मैं हमेशा आपकी सेवा में तैयार हूँ।");
        }

        // Identity: Who are you
        if (processed.matches(".*(आप कौन हो|तुम कौन हो|तुम्हारा नाम क्या है).*")) {
            return Optional.of("मैं जार्विस (JARVIS) हूँ — आपका बुद्धिमत्तापूर्ण, सुरक्षित और स्वायत्त AI सहायक।");
        }

        // Wellness / How are you
        if (processed.matches(".*(कैसे हो|क्या हाल है|सब ठीक है).*")) {
            return Optional.of("मैं पूरी तरह से सक्रिय और तत्पर हूँ, धन्यवाद! आप कैसे हैं और मैं आपके लिए क्या कर सकता हूँ?");
        }

        // Gratitude / Thank you
        if (processed.matches(".*(शुक्रिया|धन्यवाद|बहुत बहुत धन्यवाद).*")) {
            return Optional.of("आपका स्वागत है! आपकी सहायता करना मेरा परम कर्तव्य है।");
        }

        // Farewell
        if (processed.matches(".*(अलविदा|फिर मिलेंगे|बाय).*")) {
            return Optional.of("अलविदा! जब भी ज़रूरत हो, बस एक आवाज़ दीजिए।");
        }

        return Optional.empty();
    }
}

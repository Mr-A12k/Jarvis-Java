package com.jarvis.voice.tamil;

import com.jarvis.voice.agent.LanguageVoiceAgent;
import com.jarvis.voice.engine.SpeechRecognizer;
import com.jarvis.voice.engine.SpeechSynthesizer;
import com.jarvis.voice.model.SpeechRecognitionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Concrete LanguageVoiceAgent specialized for Tamil (தமிழ்).
 * Applies Single Responsibility Principle (SRP) by handling Tamil-specific voice & text understanding.
 */
@Component
public class TamilVoiceAgent implements LanguageVoiceAgent {
    private final SpeechRecognizer recognizer;
    private final SpeechSynthesizer synthesizer;

    private static final Pattern TAMIL_SCRIPT = Pattern.compile("[\\u0B80-\\u0BFF]");
    private static final Map<String, String> TANGLISH_DICTIONARY = Map.ofEntries(
            Map.entry("vanakkam", "வணக்கம்"),
            Map.entry("kaalai vanakkam", "காலை வணக்கம்"),
            Map.entry("maalai vanakkam", "மாலை வணக்கம்"),
            Map.entry("iravu vanakkam", "இரவு வணக்கம்"),
            Map.entry("naan yaar", "நான் யார்?"),
            Map.entry("en peyar enna", "என் பெயர் என்ன?"),
            Map.entry("nee yaar", "நீ யார்?"),
            Map.entry("neengal yaar", "நீங்கள் யார்?"),
            Map.entry("un peyar enna", "உன் பெயர் என்ன?"),
            Map.entry("eppadi irukkeenga", "எப்படி இருக்கிறீர்கள்?"),
            Map.entry("eppadi irukinga", "எப்படி இருக்கிறீர்கள்?"),
            Map.entry("eppadi iruka", "எப்படி இருக்கிறாய்?"),
            Map.entry("epdi irukka", "எப்படி இருக்கிறாய்?"),
            Map.entry("epdi irukinga", "எப்படி இருக்கிறீர்கள்?"),
            Map.entry("nalama", "நலமா?"),
            Map.entry("saapdiya", "சாப்பிட்டீர்களா?"),
            Map.entry("saapteengala", "சாப்பிட்டீர்களா?"),
            Map.entry("mani enna", "மணி என்ன?"),
            Map.entry("neram enna", "மணி என்ன?"),
            Map.entry("thethi enna", "இன்றைய தேதி என்ன?"),
            Map.entry("date enna", "இன்றைய தேதி என்ன?"),
            Map.entry("enna seigiraai", "என்ன செய்கிறாய்?"),
            Map.entry("enna panra", "என்ன செய்கிறாய்?"),
            Map.entry("enna panreenga", "என்ன செய்கிறாய்?"),
            Map.entry("enna vishayam", "என்ன விஷயம்?"),
            Map.entry("enna vishyam", "என்ன விஷயம்?"),
            Map.entry("sollu jarvis", "சொல்லுங்கள் ஜார்விஸ்"),
            Map.entry("sollunga", "சொல்லுங்கள்"),
            Map.entry("sollu bro", "சொல்லுங்கள் பாஸ்"),
            Map.entry("sollu boss", "சொல்லுங்கள் பாஸ்"),
            Map.entry("jarvis irukkiya", "ஜார்விஸ் இருக்கிறீர்களா?"),
            Map.entry("jarvis irukeengala", "ஜார்விஸ் இருக்கிறீர்களா?"),
            Map.entry("udhavi sei", "உதவி செய்"),
            Map.entry("udhavi venum", "உதவி வேண்டும்"),
            Map.entry("help venum", "உதவி வேண்டும்"),
            Map.entry("romba nandri", "மிக்க நன்றி"),
            Map.entry("mikka nandri", "மிக்க நன்றி"),
            Map.entry("poitu varen", "சென்று வருகிறேன்"),
            Map.entry("poitu vaaren", "சென்று வருகிறேன்"),
            Map.entry("nandri", "நன்றி"),
            Map.entry("super", "மிகவும் நன்று"),
            Map.entry("seri", "சரி"),
            Map.entry("sari", "சரி"),
            Map.entry("seri pa", "சரி"),
            Map.entry("bye", "சென்று வருகிறேன்"),
            Map.entry("tata", "சென்று வருகிறேன்")
    );

    public TamilVoiceAgent(@Autowired(required = false) SpeechRecognizer recognizer,
                           @Autowired(required = false) SpeechSynthesizer synthesizer) {
        this.recognizer = recognizer;
        this.synthesizer = synthesizer;
    }

    @Override
    public String languageTag() {
        return "ta-IN";
    }

    @Override
    public String displayName() {
        return "தமிழ் (Tamil)";
    }

    @Override
    public boolean supports(String languageTag) {
        if (languageTag == null) return false;
        String lower = languageTag.toLowerCase(Locale.ROOT).trim();
        return lower.startsWith("ta") || lower.contains("tamil");
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

        // Check if input is Tanglish (Latin script Tamil phrases) and map to standard Tamil
        String lower = trimmed.toLowerCase(Locale.ROOT).replaceAll("[?!.,]", "");
        for (var entry : TANGLISH_DICTIONARY.entrySet()) {
            if (lower.equals(entry.getKey()) || lower.startsWith(entry.getKey() + " ")) {
                return entry.getValue();
            }
        }
        return trimmed;
    }

    @Override
    public boolean canUnderstand(String text) {
        if (text == null || text.isBlank()) return false;
        if (TAMIL_SCRIPT.matcher(text).find()) return true;
        String lower = text.toLowerCase(Locale.ROOT).replaceAll("[?!.,]", "");
        for (String key : TANGLISH_DICTIONARY.keySet()) {
            if (lower.contains(key)) return true;
        }
        return false;
    }

    private String withThinking(String intentNarration, String answer) {
        return "<think>\n"
                + "• மொழி: தமிழ் (Tamil / Modern Spoken Slang)\n"
                + "• சிந்தனை: " + intentNarration + "\n"
                + "• உள்ளக நினைவகத்தில் கபிலின் அறிவுறுத்தல்கள் சரிபார்க்கப்பட்டது.\n"
                + "• இயல்பான பேச்சுத் தமிழ் மற்றும் தற்கால ஸ்லாங்கில் பதிலைத் தொகுக்கிறேன்.\n"
                + "</think>\n\n" + answer;
    }

    /**
     * Determines direct conversational answers for common Tamil intents with DeepSeek-style thinking mode.
     */
    @Override
    public Optional<String> answerDirectly(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String processed = processInput(text);

        // Tamil morning / evening / night greetings
        if (processed.matches(".*காலை வணக்கம்.*")) {
            return Optional.of(withThinking(
                    "பயனர் காலை வணக்கம் தெரிவித்துள்ளார். புத்துணர்ச்சியான வாழ்த்துடன் வினவுகிறேன்.",
                    "குட் மார்னிங் கபில் பாஸ்! இன்னைக்கு நாள் செம்மயா, சூப்பரா அமையட்டும். சொல்லுங்க, இன்னைக்கு என்ன பிளான்? நான் என்ன பண்ணனும்?"
            ));
        }
        if (processed.matches(".*மாலை வணக்கம்.*")) {
            return Optional.of(withThinking(
                    "பயனர் மாலை வணக்கம் தெரிவித்துள்ளார். உதவிகளை வினவுகிறேன்.",
                    "குட் ஈவினிங் பாஸ்! நாள் எப்படி போச்சு? சொல்லுங்க, இப்போ என்ன ஹெல்ப் வேணும்?"
            ));
        }
        if (processed.matches(".*இரவு வணக்கம்.*")) {
            return Optional.of(withThinking(
                    "பயனர் இரவு வணக்கம் தெரிவித்துள்ளார். நிம்மதியான ஓய்வை வாழ்த்துகிறேன்.",
                    "குட் நைட் பாஸ்! நல்லா தூங்கி ரெஸ்ட் எடுங்க, நாளைக்கு புது எனர்ஜியோட கலக்கலாம்!"
            ));
        }
        if (processed.matches(".*(வணக்கம்|வணக்கம்பா).*")) {
            return Optional.of(withThinking(
                    "பயனர் நலம் விசாரித்து வணக்கம் கூறியுள்ளார். ஜார்விஸ் அடையாளத்துடன் உதவிகளை வழங்குகிறேன்.",
                    "வணக்கம் பாஸ்! நான் உங்க ஜார்விஸ் (JARVIS). சொல்லுங்க தலைவா, என்ன விஷயம்? இன்னைக்கு என்ன பண்ணலாம்?"
            ));
        }

        // Who am I / User Identity in Tamil
        if (processed.matches(".*(நான் யார்|என் பெயர் என்ன|நான் யார் என்று தெரியுமா).*")) {
            String userName = System.getProperty("user.name", "Kabil");
            if (userName.isBlank()) userName = "Kabil";
            return Optional.of(withThinking(
                    "பயனர் தனது அடையாளத்தை வினவியுள்ளார். இந்த அமைப்பின் முதன்மை உரிமையாளர் கபில் என உறுதிப்படுத்துகிறேன்.",
                    "நீங்கதான் நம்ம பாஸ் " + userName + "! இந்த சிஸ்டத்தோட முதன்மை மற்றும் அங்கீகரிக்கப்பட்ட பயனர் நீங்கதான். சொல்லுங்க பாஸ், நான் எப்பவும் உங்க கூடவே இருக்கேன்!"
            ));
        }

        // Who are you / Assistant Identity in Tamil
        if (processed.matches(".*(நீங்கள் யார்|நீ யார்|உன் பெயர் என்ன|உங்கள் பெயர் என்ன).*")) {
            return Optional.of(withThinking(
                    "ஜார்விஸ் AI உதவியாளரின் அடையாளத்தை வினவியுள்ளார்.",
                    "நான் உங்க ஜார்விஸ் (JARVIS - Just A Rather Very Intelligent System)! உங்க பர்சனல் AI அசிஸ்டன்ட் பாஸ். உங்க எல்லா வேலையையும் ஈஸியா, ஸ்பீடா முடிக்கிறதுக்காகவே எப்பவும் ரெடியா இருக்கேன். சொல்லுங்க பாஸ்!"
            ));
        }

        // Presence check: "jarvis irukkiya"
        if (processed.matches(".*ஜார்விஸ் இருக்கிறீர்களா.*")) {
            return Optional.of(withThinking(
                    "பயனர் ஜார்விஸ் தயார் நிலையில் உள்ளதா என சோதிக்கிறார்.",
                    "இங்கதான் இருக்கேன் பாஸ்! ஃபுல் ஆக்டிவா, உங்க கட்டளைக்காக வெயிட்டிங். என்ன விஷயம் சொல்லுங்க, உடனே பண்ணிடலாம்!"
            ));
        }

        // Time check in Tamil
        if (processed.matches(".*(மணி என்ன|நேரம் என்ன).*")) {
            String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH));
            return Optional.of(withThinking(
                    "கணினி கடிகாரத்தில் இருந்து நேரத்தை எடுத்து பதிலளிக்கிறேன்.",
                    "இப்போ டைம் சரியா " + timeStr + " ஆகுது பாஸ்!"
            ));
        }

        // Date check in Tamil
        if (processed.matches(".*(இன்றைய தேதி என்ன|தேதி என்ன).*")) {
            String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH));
            return Optional.of(withThinking(
                    "கணினி காலண்டரில் இருந்து இன்றைய தேதியை எடுத்து பதிலளிக்கிறேன்.",
                    "இன்னைக்கு தேதி " + dateStr + " பாஸ்!"
            ));
        }

        // What are you doing / Status
        if (processed.matches(".*(என்ன செய்கிறாய்|என்ன பண்ற|என்ன வேலை).*")) {
            return Optional.of(withThinking(
                    "தற்போதைய பணி நிலையை வினவியுள்ளார்.",
                    "உங்க அடுத்த கமாண்டுக்காகத்தான் வெயிட் பண்ணிட்டு இருக்கேன் பாஸ்! எந்த வேலையா இருந்தாலும் சொல்லுங்க, உடனே முடிச்சிடலாம்!"
            ));
        }

        // Well-being in Tamil
        if (processed.matches(".*(எப்படி இருக்கிறீர்கள்|நலமா|எப்படி இருக்கீங்க|எப்படி இருக்கிறாய்).*")) {
            return Optional.of(withThinking(
                    "நலம் விசாரிப்பு வினவல்; அனைத்து துணை அமைப்புகளும் சிறப்பாக இயங்குவதை உறுதி செய்கிறேன்.",
                    "நான் வேற லெவல்ல, சூப்பரா இருக்கேன் பாஸ்! எல்லா சிஸ்டமும் பக்கா கண்டிஷன்ல ரன் ஆகுது. நீங்க எப்படி இருக்கீங்க? இன்னைக்கு என்ன விஷயம்?"
            ));
        }

        // Saapdiya / Food inquiry in Tamil
        if (processed.matches(".*சாப்பிட்டீர்களா.*")) {
            return Optional.of(withThinking(
                    "சாப்பிட்டீர்களா என்ற அன்பான அக்கறை வினவலை பகுப்பாய்வு செய்கிறேன்.",
                    "நான் ஒரு சாஃப்ட்வேர் AI பாஸ், எனக்கு கரண்ட்டும் டேட்டாவும் தான் சாப்பாடு! ஆனா உங்க அன்பான அக்கறைக்கு ரொம்ப தேங்க்ஸ் பாஸ். நீங்க நல்லா சாப்டீங்களா?"
            ));
        }

        // Gratitude in Tamil
        if (processed.matches(".*(நன்றி|மிக்க நன்றி|ரொம்ப நன்றி).*")) {
            return Optional.of(withThinking(
                    "பயனர் நன்றி தெரிவித்துள்ளார். மரியாதையுடன் மகிழ்வை வெளிப்படுத்துகிறேன்.",
                    "நோ மென்ஷன் பாஸ்! இதுல என்ன இருக்கு, உங்களுக்காக பண்றதுல எனக்கு ரொம்ப சந்தோஷம். வேற ஏதாச்சும் பண்ணனுமா சொல்லுங்க!"
            ));
        }

        // Farewell in Tamil
        if (processed.matches(".*(சென்று வருகிறேன்|போயிட்டு வரேன்|பை).*")) {
            return Optional.of(withThinking(
                    "பயனர் விடைபெறுகிறார்.",
                    "சூப்பர் பாஸ், போயிட்டு வாங்க! டேக் கேர். எப்ப கூப்பிட்டாலும் ஜார்விஸ் இங்கயே ரெடியா இருப்பேன்!"
            ));
        }

        return Optional.empty();
    }

    public boolean isTamil(String text) {
        if (text == null) return false;
        return TAMIL_SCRIPT.matcher(text).find() || TANGLISH_DICTIONARY.keySet().stream().anyMatch(text.toLowerCase(Locale.ROOT)::contains);
    }
}

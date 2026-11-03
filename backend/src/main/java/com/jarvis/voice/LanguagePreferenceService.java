package com.jarvis.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Service managing user active language preference and dynamic language switching.
 * Supports auto-detection, explicit runtime language switching commands,
 * and language-isolated prompt directives.
 */
@Service
public class LanguagePreferenceService {

    private static final Logger log = LoggerFactory.getLogger(LanguagePreferenceService.class);

    private final AtomicReference<String> activeLanguage = new AtomicReference<>("en");

    private static final Pattern TAMIL_SCRIPT = Pattern.compile("[\\u0B80-\\u0BFF]");
    private static final Pattern HINDI_SCRIPT = Pattern.compile("[\\u0900-\\u097F]");
    private static final Pattern TELUGU_SCRIPT = Pattern.compile("[\\u0C00-\\u0C7F]");
    private static final Pattern JAPANESE_SCRIPT = Pattern.compile("[\\u3040-\\u30FF\\u4E00-\\u9FAF]");

    // Language switch regex patterns
    private static final Pattern SWITCH_ENGLISH = Pattern.compile(
            "(?i).*\\b(?:switch to|speak in|talk in|change to|reply in|respond in)\\s+(?:the\\s+)?(?:english|eng)\\b.*|"
            + "(?i).*\\b(?:english please|only english|use english|talk english)\\b.*|"
            + ".*(?:ஆங்கிலத்தில் பேசு|ஆங்கிலத்திற்கு மாறு|ஆங்கிலத்தில் பதில்).*|"
            + ".*(?:अंग्रेजी में बात करो|अंग्रेजी में बोलो).*"
    );

    private static final Pattern SWITCH_TAMIL = Pattern.compile(
            "(?i).*\\b(?:switch to|speak in|talk in|change to|reply in|respond in)\\s+(?:the\\s+)?(?:tamil|thamizh)\\b.*|"
            + "(?i).*\\b(?:tamil please|only tamil|use tamil|talk tamil)\\b.*|"
            + ".*(?:தமிழில் பேசு|தமிழுக்கு மாறு|தமிழில் பதில் சொல்|தமிழ் மொழிக்கு மாற்று|தமிழில் உரையாடு).*"
    );

    private static final Pattern SWITCH_HINDI = Pattern.compile(
            "(?i).*\\b(?:switch to|speak in|talk in|change to|reply in|respond in)\\s+(?:the\\s+)?(?:hindi)\\b.*|"
            + ".*(?:हिंदी में बात करो|हिंदी में बोलो|हिंदी में उत्तर दो|हिन्दी में बात करो).*"
    );

    private static final Pattern SWITCH_FRENCH = Pattern.compile(
            "(?i).*\\b(?:switch to|speak in|talk in|change to|reply in|respond in)\\s+(?:the\\s+)?(?:french|français)\\b.*|"
            + "(?i).*\\b(?:parle en français|parlez français)\\b.*"
    );

    private static final Pattern SWITCH_SPANISH = Pattern.compile(
            "(?i).*\\b(?:switch to|speak in|talk in|change to|reply in|respond in)\\s+(?:the\\s+)?(?:spanish|español)\\b.*|"
            + "(?i).*\\b(?:habla en español|hable español)\\b.*"
    );

    private static final Pattern SWITCH_GERMAN = Pattern.compile(
            "(?i).*\\b(?:switch to|speak in|talk in|change to|reply in|respond in)\\s+(?:the\\s+)?(?:german|deutsch)\\b.*|"
            + "(?i).*\\b(?:sprich auf deutsch|sprechen sie deutsch)\\b.*"
    );

    private static final Pattern SWITCH_JAPANESE = Pattern.compile(
            "(?i).*\\b(?:switch to|speak in|talk in|change to|reply in|respond in)\\s+(?:the\\s+)?(?:japanese|nihongo)\\b.*|"
            + ".*(?:日本語で話して|日本語で返答して|日本語で).*");

    public String getActiveLanguage() {
        return activeLanguage.get();
    }

    public void setActiveLanguage(String lang) {
        if (lang != null && !lang.isBlank()) {
            String normalized = lang.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("en")) activeLanguage.set("en");
            else if (normalized.startsWith("ta")) activeLanguage.set("ta");
            else if (normalized.startsWith("hi")) activeLanguage.set("hi");
            else if (normalized.startsWith("fr")) activeLanguage.set("fr");
            else if (normalized.startsWith("es")) activeLanguage.set("es");
            else if (normalized.startsWith("de")) activeLanguage.set("de");
            else if (normalized.startsWith("ja")) activeLanguage.set("ja");
            else activeLanguage.set(normalized);
            log.info("Active assistant language updated to: {}", activeLanguage.get());
        }
    }

    /**
     * Checks if the incoming message contains an explicit instruction to switch language.
     * If so, switches active language and returns an acknowledgment formatted with <think> in that target language.
     */
    public Optional<String> handleLanguageSwitchCommand(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        String trimmed = input.trim();

        if (SWITCH_ENGLISH.matcher(trimmed).matches()) {
            setActiveLanguage("en");
            return Optional.of("<think>\n"
                    + "• Language switch requested: User instructed to switch or speak in English.\n"
                    + "• Active working language updated to English (en-US).\n"
                    + "</think>\n\n"
                    + "Certainly, Sir. I have switched to English. All subsequent answers, reasoning, and voice interactions will be conducted in English. How may I assist you?");
        }

        if (SWITCH_TAMIL.matcher(trimmed).matches()) {
            setActiveLanguage("ta");
            return Optional.of("<think>\n"
                    + "• மொழி மாற்றம் கோரிக்கை: பயனர் தமிழில் பேச பணித்துள்ளார்.\n"
                    + "• செயலில் உள்ள மொழி அமைப்பை தமிழாக (ta-IN) மாற்றுகிறேன்.\n"
                    + "• இயல்பான பேச்சுத் தமிழ் மற்றும் தற்கால ஸ்லாங்கில் உரையாடலைத் தொடர்கிறேன்.\n"
                    + "</think>\n\n"
                    + "சரிங்க பாஸ், தமிழுக்கு மாறிவிட்டேன்! இனிமேல் நம்ம பேச்சு இயல்பான பேச்சுத் தமிழ்லயும் லேட்டஸ்ட் ஸ்லாங்லயும் சூப்பரா இருக்கும்! சொல்லுங்க பாஸ், என்ன விஷயம்? என்ன பண்ணலாம்?");
        }

        if (SWITCH_HINDI.matcher(trimmed).matches()) {
            setActiveLanguage("hi");
            return Optional.of("<think>\n"
                    + "• भाषा परिवर्तन: उपयोगकर्ता ने हिन्दी में संवाद करने का निर्देश दिया है।\n"
                    + "• सक्रिय भाषा को हिन्दी (hi-IN) में सेट कर रहा हूँ।\n"
                    + "</think>\n\n"
                    + "नमस्ते सर, मैंने हिन्दी भाषा चुन ली है। अब से मेरे सभी उत्तर, विचार और निर्देश हिन्दी में होंगे। मैं आपकी क्या सहायता कर सकता हूँ?");
        }

        if (SWITCH_FRENCH.matcher(trimmed).matches()) {
            setActiveLanguage("fr");
            return Optional.of("<think>\n"
                    + "• Changement de langue : L'utilisateur a demandé de communiquer en français.\n"
                    + "• Langue active mise à jour vers le français (fr-FR).\n"
                    + "</think>\n\n"
                    + "Bien sûr, Monsieur. Je suis passé au français. Toutes mes prochaines réponses et réflexions seront en français. Comment puis-je vous aider ?");
        }

        if (SWITCH_SPANISH.matcher(trimmed).matches()) {
            setActiveLanguage("es");
            return Optional.of("<think>\n"
                    + "• Cambio de idioma: El usuario solicitó comunicarse en español.\n"
                    + "• Idioma activo actualizado a español (es-ES).\n"
                    + "</think>\n\n"
                    + "Por supuesto, señor. He cambiado a español. Todas las respuestas, análisis y comandos siguientes serán en español. ¿En qué puedo ayudarle?");
        }

        if (SWITCH_GERMAN.matcher(trimmed).matches()) {
            setActiveLanguage("de");
            return Optional.of("<think>\n"
                    + "• Sprachwechsel: Der Benutzer hat angewiesen, auf Deutsch zu kommunizieren.\n"
                    + "• Aktive Sprache auf Deutsch (de-DE) aktualisiert.\n"
                    + "</think>\n\n"
                    + "Sehr gerne, Sir. Ich habe auf Deutsch umgestellt. Alle weiteren Antworten und Überlegungen erfolgen auf Deutsch. Wie kann ich Ihnen helfen?");
        }

        if (SWITCH_JAPANESE.matcher(trimmed).matches()) {
            setActiveLanguage("ja");
            return Optional.of("<think>\n"
                    + "• 言語切り替え：ユーザーから日本語での対話指示を受信しました。\n"
                    + "• アクティブ言語を日本語 (ja-JP) に切り替えました。\n"
                    + "</think>\n\n"
                    + "かしこまりました。日本語に切り替えました。これ以降のすべての応答と思考プロセスは日本語で行われます。どのようなご用件でしょうか？");
        }

        return Optional.empty();
    }

    /**
     * Determines whether the given text is specifically in Tamil.
     * Eliminates false positives when English sentences contain the word 'tamil'.
     */
    public boolean isTamil(String text) {
        if (text == null || text.isBlank()) return false;
        if (TAMIL_SCRIPT.matcher(text).find()) return true;

        String lower = text.toLowerCase(Locale.ROOT).trim();
        // Disqualify if it contains standard English words
        if (isEnglishQuery(lower)) return false;

        return lower.contains("vanakkam") || lower.contains("nandri")
                || lower.contains("eppadi iruk") || lower.contains("nalama") || lower.contains("udhavi");
    }

    /**
     * Checks if the text is predominantly English.
     */
    public boolean isEnglishQuery(String text) {
        if (text == null || text.isBlank()) return false;
        if (TAMIL_SCRIPT.matcher(text).find() || HINDI_SCRIPT.matcher(text).find()
                || TELUGU_SCRIPT.matcher(text).find() || JAPANESE_SCRIPT.matcher(text).find()) {
            return false;
        }

        String lower = text.toLowerCase(Locale.ROOT).trim();
        // Common English words/starters that guarantee English intent
        String[] englishMarkers = {
                "what", "how", "why", "who", "when", "where", "which", "is", "are", "can", "could",
                "explain", "tell", "show", "list", "search", "create", "delete", "status", "verify",
                "hello", "hi", "hey", "please", "thanks", "thank", "good", "morning", "evening",
                "help", "system", "hardware", "memory", "storage", "laptop", "price", "run", "open",
                "speak", "switch", "change", "talk", "english", "write", "code", "weather", "about"
        };

        for (String marker : englishMarkers) {
            if (Pattern.compile("\\b" + marker + "\\b").matcher(lower).find()) {
                return true;
            }
        }

        // If ASCII text with latin characters and words, default to English
        return lower.matches("^[a-zA-Z0-9\\s.,?!'\"/\\-_:;@#$%&*()]+$");
    }

    /**
     * Detects language of input or defaults to current active preference.
     */
    public String resolveLanguage(String text) {
        if (text != null && !text.isBlank()) {
            if (isTamil(text)) return "ta";
            if (HINDI_SCRIPT.matcher(text).find()) return "hi";
            if (TELUGU_SCRIPT.matcher(text).find()) return "te";
            if (JAPANESE_SCRIPT.matcher(text).find()) return "ja";
            if (isEnglishQuery(text)) return "en";
        }
        return activeLanguage.get();
    }

    /**
     * Generates strict, enforceable system prompt directive ensuring zero language bleed.
     */
    public String getPromptLanguageDirective(String text) {
        String lang = resolveLanguage(text);
        return switch (lang) {
            case "ta" -> " CRITICAL LANGUAGE DIRECTIVE (TAMIL FLUENT SPOKEN SLANG MODE): The user communicated in Tamil (தமிழ்). You MUST conduct your reasoning in <think> in Tamil, and you MUST generate your response in natural, fluent conversational spoken Tamil with modern slang (தற்கால இயல்பான பேச்சுத் தமிழ் மற்றும் ஸ்லாங்). Do NOT use stiff, archaic, bookish, or formal pure Tamil (தூய செந்தமிழ் வேண்டாம்). Use lively conversational expressions ('சொல்லுங்க பாஸ்', 'பண்ணிடலாம்', 'கண்டிப்பா', 'சூப்பர் பாஸ்', 'நோ ப்ராப்ளம்', 'டான்', 'என்ன விஷயம்?', 'வேற லெவல்') and natural spoken verb endings ('பண்றேன்', 'இருக்கீங்க', 'ஓபன் பண்றேன்', 'முடிஞ்சிடுச்சு'). Address the user as 'பாஸ்' or 'கபில்'. Transliterate common loan words into Tamil script (சிஸ்டம், பைல், ரெடி, அப்டேட்) for smooth natural speech synthesis. Do NOT answer in English.";
            case "hi" -> " CRITICAL LANGUAGE DIRECTIVE: The user communicated in Hindi (हिन्दी). You MUST conduct your reasoning in <think> in Hindi, and you MUST generate your entire response STRICTLY in natural Devanagari Hindi script (हिन्दी). Do NOT answer in English.";
            case "fr" -> " CRITICAL LANGUAGE DIRECTIVE: The user communicated in French. You MUST conduct your reasoning in <think> in French, and you MUST generate your entire response STRICTLY in natural French. Do NOT answer in English.";
            case "es" -> " CRITICAL LANGUAGE DIRECTIVE: The user communicated in Spanish. You MUST conduct your reasoning in <think> in Spanish, and you MUST generate your entire response STRICTLY in natural Spanish. Do NOT answer in English.";
            case "de" -> " CRITICAL LANGUAGE DIRECTIVE: The user communicated in German. You MUST conduct your reasoning in <think> in German, and you MUST generate your entire response STRICTLY in natural German. Do NOT answer in English.";
            case "ja" -> " CRITICAL LANGUAGE DIRECTIVE: The user communicated in Japanese. You MUST conduct your reasoning in <think> in Japanese, and you MUST generate your entire response STRICTLY in natural Japanese script. Do NOT answer in English.";
            default -> " CRITICAL LANGUAGE DIRECTIVE: The user communicated in English. You MUST conduct your reasoning in <think> in English, and you MUST generate your entire response STRICTLY in clear, articulate English. Do NOT answer in Tamil, Hindi, or any other language unless explicitly asked to translate.";
        };
    }
}

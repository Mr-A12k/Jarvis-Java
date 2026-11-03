package com.jarvis.voice.agent;

import com.jarvis.voice.engine.SpeechRecognizer;
import com.jarvis.voice.engine.SpeechSynthesizer;
import com.jarvis.voice.model.SpeechRecognitionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Concrete LanguageVoiceAgent specialized for Japanese (日本語).
 * Supports Kanji, Hiragana, Katakana, Romaji dictionary mapping, and direct conversational fast-paths.
 */
@Component
public class JapaneseVoiceAgent implements LanguageVoiceAgent {
    private final SpeechRecognizer recognizer;
    private final SpeechSynthesizer synthesizer;

    // Kanji + Hiragana + Katakana regex
    private static final Pattern JAPANESE_SCRIPT = Pattern.compile("[\\u3040-\\u30FF\\u4E00-\\u9FAF]");
    private static final Map<String, String> ROMAJI_DICTIONARY = Map.ofEntries(
            Map.entry("konnichiwa", "こんにちは"),
            Map.entry("ohayou", "おはようございます"),
            Map.entry("ohayou gozaimasu", "おはようございます"),
            Map.entry("konbanwa", "こんばんは"),
            Map.entry("oyasumi", "おやすみなさい"),
            Map.entry("oyasuminasai", "おやすみなさい"),
            Map.entry("ogenki desu ka", "お元気ですか？"),
            Map.entry("anata wa dare", "あなたは誰ですか？"),
            Map.entry("watashi wa dare", "私は誰ですか？"),
            Map.entry("arigatou", "ありがとうございます"),
            Map.entry("arigatou gozaimasu", "ありがとうございます"),
            Map.entry("sayounara", "さようなら"),
            Map.entry("mata ne", "またね"),
            Map.entry("tasukete", "助けてください")
    );

    public JapaneseVoiceAgent(@Autowired(required = false) SpeechRecognizer recognizer,
                             @Autowired(required = false) SpeechSynthesizer synthesizer) {
        this.recognizer = recognizer;
        this.synthesizer = synthesizer;
    }

    @Override
    public String languageTag() {
        return "ja-JP";
    }

    @Override
    public String displayName() {
        return "日本語 (Japanese)";
    }

    @Override
    public boolean supports(String languageTag) {
        if (languageTag == null) return false;
        String lower = languageTag.toLowerCase(Locale.ROOT).trim();
        return lower.startsWith("ja") || lower.contains("japanese");
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
        for (var entry : ROMAJI_DICTIONARY.entrySet()) {
            if (lower.equals(entry.getKey()) || lower.startsWith(entry.getKey() + " ")) {
                return entry.getValue();
            }
        }
        return trimmed;
    }

    @Override
    public boolean canUnderstand(String text) {
        if (text == null || text.isBlank()) return false;
        if (JAPANESE_SCRIPT.matcher(text).find()) return true;
        String lower = text.toLowerCase(Locale.ROOT).replaceAll("[?!.,]", "");
        for (String key : ROMAJI_DICTIONARY.keySet()) {
            if (lower.contains(key)) return true;
        }
        return false;
    }

    @Override
    public Optional<String> answerDirectly(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String processed = processInput(text);

        // Greetings
        if (processed.matches(".*おはようございます.*")) {
            return Optional.of("おはようございます！素晴らしい一日をお過ごしください。本日はどのようなご用件でしょうか？");
        }
        if (processed.matches(".*こんばんは.*")) {
            return Optional.of("こんばんは！何かお手伝いできることはございますか？");
        }
        if (processed.matches(".*おやすみなさい.*")) {
            return Optional.of("おやすみなさい！どうぞごゆっくりお休みください。");
        }
        if (processed.matches(".*こんにちは.*")) {
            return Optional.of("こんにちは！私はジャービス（JARVIS）、あなた専用のAIアシスタントです。何をお手伝いしましょうか？");
        }

        // Identity: Who am I
        if (processed.matches(".*(私は誰|私の名前は何|知っていますか).*")) {
            String userName = System.getProperty("user.name", "Kabil");
            if (userName.isBlank()) userName = "Kabil";
            return Optional.of("あなたは " + userName + " 様、このワークスペースのオーナーです。いつでもお申し付けください。");
        }

        // Identity: Who are you
        if (processed.matches(".*(あなたは誰|名前は何|誰ですか).*")) {
            return Optional.of("私はジャービス（JARVIS）です。あなたのパーソナルAIアシスタントとしてお仕えしております。");
        }

        // Wellness
        if (processed.matches(".*(お元気ですか|調子はどう).*")) {
            return Optional.of("全システム正常稼働中です。お気遣いありがとうございます！あなたのご調子はいかがですか？");
        }

        // Gratitude
        if (processed.matches(".*(ありがとう|感謝).*")) {
            return Optional.of("どういたしまして！お役に立てて光栄です。");
        }

        // Farewell
        if (processed.matches(".*(さようなら|またね|失礼します).*")) {
            return Optional.of("さようなら！必要な時はいつでもお声がけください。");
        }

        return Optional.empty();
    }
}

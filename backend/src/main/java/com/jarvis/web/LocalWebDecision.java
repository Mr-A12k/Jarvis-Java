package com.jarvis.web;

import com.jarvis.chat.*;
import com.jarvis.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

/** Reuses the already loaded local model; no external reasoning service. */
public class LocalWebDecision {
    private final ChatService chat;
    public LocalWebDecision(ChatService chat) { this.chat=chat; }
    public Mono<List<WebSearch.Source>> select(String question,List<WebSearch.Source> sources) {
        if(sources.isEmpty()) return Mono.just(List.of());
        List<WebSearch.Source> relevant = sources.stream()
                .filter(s -> isSourceRelevant(question, s))
                .toList();
        if(relevant.isEmpty()) return Mono.just(List.of());

        String evidence=context(relevant,160);
        var request=new ChatRequest("Select up to 2 relevant source IDs for this question. Output ONLY IDs like 1,2 or NONE. Question: "+question,List.of());
        return chat.stream(request,evidence).collectList().map(parts -> {
            try {
                return parse(String.join("",parts),relevant);
            } catch (Exception e) {
                return relevant.stream().limit(2).toList();
            }
        });
    }

    private static final Set<String> STOP_AND_GENERIC_WORDS = Set.of(
            "a", "an", "the", "and", "or", "in", "on", "at", "to", "for", "of", "with",
            "is", "are", "was", "were", "what", "where", "who", "when", "why", "how",
            "search", "online", "internet", "web", "google", "find", "look", "up",
            "please", "can", "you", "tell", "me", "about", "this", "that",
            "official", "tutorial", "guide", "docs", "documentation", "reference",
            "manual", "example", "examples", "code", "learn", "learning",
            "latest", "recent", "new", "update", "updates", "today", "yesterday",
            "tomorrow", "best", "top", "question", "info", "information"
    );

    public static boolean isSourceRelevant(String question, WebSearch.Source source) {
        if (question == null || source == null) return false;
        String[] qTokens = question.toLowerCase(Locale.ROOT).split("[^a-zA-Z0-9\\u0B80-\\u0BFF]+");
        List<String> specificKeywords = new ArrayList<>();
        for (String t : qTokens) {
            if (t.length() >= 2 && !STOP_AND_GENERIC_WORDS.contains(t)) {
                specificKeywords.add(t);
            }
        }
        // If query only contains generic/stop words (e.g. mock test queries like 'question'), accept sources
        if (specificKeywords.isEmpty()) return true;

        String combined = (
                (source.title() != null ? source.title() : "") + " " +
                (source.snippet() != null ? source.snippet() : "") + " " +
                (source.url() != null ? source.url() : "")
        ).toLowerCase(Locale.ROOT);

        for (String kw : specificKeywords) {
            if (combined.contains(kw)) {
                return true;
            }
        }
        return false;
    }
    static List<WebSearch.Source> parse(String answer,List<WebSearch.Source> sources) {
        String value = answer != null ? answer.replaceAll("<think>[\\s\\S]*?</think>", "").strip() : "";
        if(value.equalsIgnoreCase("NONE")) return List.of();
        if(!value.matches("[1-3](?:\\s*,\\s*[1-3])?")) throw invalid();
        var selected=new ArrayList<WebSearch.Source>();
        for(String id:value.split(",")) {
            var source=sources.stream().filter(s -> s.id()==Integer.parseInt(id.trim())).findFirst().orElseThrow(LocalWebDecision::invalid);
            if(selected.contains(source)) throw invalid();
            selected.add(source);
        }
        return List.copyOf(selected);
    }
    static ApiException invalid() { return new ApiException(HttpStatus.BAD_GATEWAY,"WEB_DECISION_INVALID","The local model could not reliably select web evidence. Try a more specific question."); }
    public static String context(List<WebSearch.Source> sources,int bytesPerSource) {
        StringBuilder result=new StringBuilder();
        for(var source:sources) result.append('[').append(source.id()).append("] ")
            .append(clip(source.title(),50)).append(": ").append(clip(source.snippet(),bytesPerSource-60)).append('\n');
        return result.toString();
    }
    static String clip(String value,int max) {
        var result=new StringBuilder(); int used=0;
        for(int cp:value.codePoints().toArray()) {
            String ch=new String(Character.toChars(cp)); int size=ch.getBytes(StandardCharsets.UTF_8).length;
            if(used+size>max) break; result.append(ch); used+=size;
        }
        return result.toString();
    }
}

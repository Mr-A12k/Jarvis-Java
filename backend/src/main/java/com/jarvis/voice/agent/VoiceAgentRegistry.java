package com.jarvis.voice.agent;

import org.springframework.stereotype.Component;
import java.util.*;

/**
 * Open/Closed Principle (OCP) & Dependency Inversion Principle (DIP):
 * Manages discovery and resolution of language-specific voice agents.
 * New language agents can be introduced by simply creating new Spring beans implementing LanguageVoiceAgent.
 */
@Component
public class VoiceAgentRegistry {
    private final List<LanguageVoiceAgent> agents;
    private final LanguageVoiceAgent defaultAgent;

    public VoiceAgentRegistry(List<LanguageVoiceAgent> agents) {
        List<LanguageVoiceAgent> sorted = new ArrayList<>(agents);
        sorted.sort((a, b) -> {
            boolean aTa = a.supports("ta-IN");
            boolean bTa = b.supports("ta-IN");
            if (aTa && !bTa) return -1;
            if (!aTa && bTa) return 1;
            boolean aEn = a.supports("en-US");
            boolean bEn = b.supports("en-US");
            if (aEn && !bEn) return 1;
            if (!aEn && bEn) return -1;
            return 0;
        });
        this.agents = List.copyOf(sorted);
        this.defaultAgent = this.agents.stream()
                .filter(a -> a.supports("en-US") || a.supports("en"))
                .findFirst()
                .orElse(this.agents.isEmpty() ? null : this.agents.get(0));
    }

    public LanguageVoiceAgent resolve(String languageTag) {
        if (languageTag == null || languageTag.isBlank()) {
            return defaultAgent;
        }
        String clean = languageTag.trim().toLowerCase(Locale.ROOT);
        return agents.stream()
                .filter(a -> a.supports(clean))
                .findFirst()
                .orElse(defaultAgent);
    }

    public Optional<LanguageVoiceAgent> detectAgent(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        for (var agent : agents) {
            if (agent.canUnderstand(text)) {
                return Optional.of(agent);
            }
        }
        return Optional.empty();
    }

    public Optional<String> directMultilingualAnswer(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        for (var agent : agents) {
            var answer = agent.answerDirectly(text);
            if (answer.isPresent()) {
                return answer;
            }
        }
        return Optional.empty();
    }

    public List<Map<String, String>> availableLanguages() {
        var list = new ArrayList<Map<String, String>>();
        for (var agent : agents) {
            list.add(Map.of("tag", agent.languageTag(), "name", agent.displayName()));
        }
        return Collections.unmodifiableList(list);
    }
}

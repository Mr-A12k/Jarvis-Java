package com.jarvis.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.database.DatabaseStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages autonomous learning and recall of real-world knowledge.
 * Automatically saves factual answers synthesized from internet research into long-term memory.
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final Path storagePath;
    private final ObjectMapper json;
    private final DatabaseStore databaseStore;
    private final List<LearnedKnowledge> cache = new CopyOnWriteArrayList<>();

    @Autowired
    public KnowledgeService(
            @Value("${jarvis.knowledge.path:data/learned-knowledge.jsonl}") String storagePath,
            @Autowired(required = false) ObjectMapper json,
            @Autowired(required = false) DatabaseStore databaseStore
    ) {
        this.storagePath = Path.of(storagePath).toAbsolutePath().normalize();
        this.json = json != null ? json : new ObjectMapper();
        this.databaseStore = databaseStore;
        loadCache();
    }

    private void loadCache() {
        if (!Files.exists(storagePath)) return;
        try (BufferedReader reader = Files.newBufferedReader(storagePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    LearnedKnowledge item = json.readValue(line, LearnedKnowledge.class);
                    cache.add(item);
                } catch (Exception e) {
                    log.warn("Skipping corrupt learned knowledge record: {}", e.getMessage());
                }
            }
            log.info("Loaded {} learned knowledge records from {}", cache.size(), storagePath);
        } catch (IOException e) {
            log.error("Failed to load learned knowledge from disk", e);
        }
    }

    /**
     * Commits a newly synthesized factual answer from web research into long-term knowledge.
     */
    public synchronized LearnedKnowledge learn(String query, String answer, List<String> sources, String language) {
        if (query == null || query.isBlank() || answer == null || answer.isBlank()) {
            return null;
        }

        // Never learn user objections, complaints, or corrections as factual answers
        if (isDisputeOrCorrection(query)) {
            log.info("Skipping learned knowledge commit for dispute/correction query: '{}'", query);
            return null;
        }

        // Volatile live topics (gold, silver, market rates, stock prices, weather) fluctuate in real-time
        // and must not be frozen into static memory
        if (isVolatileLiveTopic(query)) {
            log.info("Skipping static knowledge cache for volatile/live market topic: '{}'", query);
            return null;
        }

        String cleanQuery = query.trim();
        String cleanAnswer = answer.trim();

        // Check if an identical or near-identical query already exists to update it with latest factual answer
        for (int i = 0; i < cache.size(); i++) {
            LearnedKnowledge existing = cache.get(i);
            if (computeSimilarity(cleanQuery, existing.query()) > 0.80) {
                log.info("Updating existing learned knowledge for query: {}", cleanQuery);
                LearnedKnowledge updated = new LearnedKnowledge(
                        existing.id(),
                        cleanQuery,
                        cleanAnswer,
                        sources != null ? List.copyOf(sources) : List.of(),
                        Instant.now(),
                        language != null ? language : "en"
                );
                cache.set(i, updated);
                persistAll();
                return updated;
            }
        }

        String id = "know-" + UUID.randomUUID().toString().substring(0, 8);
        LearnedKnowledge item = new LearnedKnowledge(
                id,
                cleanQuery,
                cleanAnswer,
                sources != null ? List.copyOf(sources) : List.of(),
                Instant.now(),
                language != null ? language : "en"
        );

        cache.add(item);
        persistItem(item);

        // Also sync to PostgreSQL database memories if configured
        if (databaseStore != null && databaseStore.configured()) {
            try {
                String title = "Learned: " + (cleanQuery.length() > 60 ? cleanQuery.substring(0, 57) + "..." : cleanQuery);
                String content = cleanAnswer.length() > 3000 ? cleanAnswer.substring(0, 2997) + "..." : cleanAnswer;
                databaseStore.create(DatabaseStore.Kind.memories, title, content);
            } catch (Exception e) {
                log.warn("Could not save learned knowledge to database store: {}", e.getMessage());
            }
        }

        log.info("Successfully learned new fact for: '{}' (ID: {})", cleanQuery, id);
        return item;
    }

    /**
     * Invalidates and removes any learned knowledge matching the given topic or query.
     */
    public synchronized boolean invalidate(String query) {
        if (query == null || query.isBlank() || cache.isEmpty()) return false;
        String clean = query.replaceAll("(?i)^(?:you are wrong|are you sure|its wrong|it's wrong|incorrect|fix it)\\s*", "").trim();
        String target = clean.isBlank() ? query : clean;
        boolean removed = cache.removeIf(item -> computeSimilarity(target, item.query()) >= 0.35
                || (isVolatileLiveTopic(query) && isVolatileLiveTopic(item.query())));
        if (removed) {
            log.info("Invalidated learned knowledge matching query: '{}'", query);
            persistAll();
        }
        return removed;
    }

    /**
     * Recalls a previously learned answer if the query matches existing learned knowledge.
     */
    public Optional<LearnedKnowledge> recall(String query) {
        if (query == null || query.isBlank() || cache.isEmpty()) return Optional.empty();

        // 1. If user is disputing or correcting a prior answer, never return cached memory
        if (isDisputeOrCorrection(query)) {
            log.info("Query '{}' is a dispute/correction. Invalidating related memory and bypassing recall.", query);
            invalidate(query);
            return Optional.empty();
        }

        // 2. Volatile live market prices/rates/weather should always be live-grounded
        if (isVolatileLiveTopic(query)) {
            log.info("Query '{}' is volatile real-time data. Bypassing static knowledge recall.", query);
            return Optional.empty();
        }

        LearnedKnowledge bestMatch = null;
        double highestScore = 0.0;

        for (LearnedKnowledge item : cache) {
            double score = computeSimilarity(query, item.query());
            if (score > highestScore && score >= 0.65) {
                highestScore = score;
                bestMatch = item;
            }
        }

        if (bestMatch != null && isTimeSensitive(query)) {
            long hoursAgo = java.time.Duration.between(bestMatch.learnedAt(), Instant.now()).toHours();
            if (hoursAgo >= 1) {
                log.info("Learned knowledge for '{}' is stale ({} hours old, time-sensitive). Forcing fresh web search.", query, hoursAgo);
                return Optional.empty();
            }
        }

        return Optional.ofNullable(bestMatch);
    }

    public static boolean isDisputeOrCorrection(String query) {
        if (query == null || query.isBlank()) return false;
        String lower = query.toLowerCase(Locale.ROOT).trim();
        return lower.contains("you are wrong") || lower.contains("you're wrong") || lower.contains("your wrong")
                || lower.contains("its wrong") || lower.contains("it's wrong") || lower.contains("is wrong")
                || lower.contains("are you sure") || lower.contains("incorrect") || lower.contains("not correct")
                || lower.contains("tell me the correct") || lower.contains("wrong ans") || lower.contains("wrong data")
                || lower.contains("fix it") || lower.contains("fix this") || lower.contains("mistake")
                || lower.contains("thappu") || lower.contains("தவறு") || lower.contains("பொய்") || lower.contains("சரி இல்லை");
    }

    public static boolean isVolatileLiveTopic(String query) {
        if (query == null || query.isBlank()) return false;
        String lower = query.toLowerCase(Locale.ROOT);
        return lower.contains("gold") || lower.contains("goald") || lower.contains("silver")
                || lower.contains("bullion") || lower.contains("commodity") || lower.contains("commodities")
                || lower.contains("crude") || lower.contains("petrol") || lower.contains("diesel")
                || lower.contains("price") || lower.contains("rate") || lower.contains("rate today")
                || lower.contains("stock") || lower.contains("share") || lower.contains("shares")
                || lower.contains("market") || lower.contains("markets") || lower.contains("sensex")
                || lower.contains("nifty") || lower.contains("crypto") || lower.contains("bitcoin")
                || lower.contains("weather") || lower.contains("temperature") || lower.contains("score")
                || lower.contains("match") || lower.contains("வானிலை") || lower.contains("தங்கம்")
                || lower.contains("வெள்ளி") || lower.contains("பவுன்") || lower.contains("சவரன்")
                || lower.contains("விலை") || lower.contains("பங்குச்சந்தை");
    }

    public static boolean isTimeSensitive(String query) {
        if (query == null) return false;
        String lower = query.toLowerCase(Locale.ROOT);
        return lower.contains("today") || lower.contains("yesterday") || lower.contains("tomorrow")
                || lower.contains("this week") || lower.contains("this month") || lower.contains("current")
                || lower.contains("latest") || lower.contains("now") || lower.contains("upcoming")
                || lower.contains("weather") || lower.contains("score") || lower.contains("ott release")
                || lower.contains("movs") || lower.contains("movie") || lower.contains("film")
                || lower.contains("இந்த வாரம்") || lower.contains("இன்று") || lower.contains("வானிலை");
    }

    /**
     * Formats a recalled knowledge item for display or prompt injection in Tamil or English.
     */
    public String formatRecalledAnswer(LearnedKnowledge item, boolean isTamil) {
        if (item == null) return "";
        if (isTamil) {
            StringBuilder sb = new StringBuilder();
            sb.append("💡 [நான் முன்பு இணையத்தில் தேடி கற்றுக்கொண்ட தகவல்]:\n\n");
            sb.append(item.answer());
            if (!item.sources().isEmpty()) {
                sb.append("\n\nஆதாரங்கள்:\n");
                for (String src : item.sources()) {
                    sb.append("- ").append(src).append("\n");
                }
            }
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("💡 [From Knowledge Memory — Learned previously from Web Research]:\n\n");
            sb.append(item.answer());
            if (!item.sources().isEmpty()) {
                sb.append("\n\nVerified Sources:\n");
                for (String src : item.sources()) {
                    sb.append("- ").append(src).append("\n");
                }
            }
            return sb.toString();
        }
    }

    public List<LearnedKnowledge> listRecent(int limit) {
        return cache.stream()
                .sorted(Comparator.comparing(LearnedKnowledge::learnedAt).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    private synchronized void persistItem(LearnedKnowledge item) {
        try {
            if (storagePath.getParent() != null && !Files.exists(storagePath.getParent())) {
                Files.createDirectories(storagePath.getParent());
            }
            String line = json.writeValueAsString(item) + "\n";
            Files.writeString(
                    storagePath,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            log.error("Failed to append learned knowledge to {}", storagePath, e);
        }
    }

    private synchronized void persistAll() {
        try {
            if (storagePath.getParent() != null && !Files.exists(storagePath.getParent())) {
                Files.createDirectories(storagePath.getParent());
            }
            List<String> lines = new ArrayList<>();
            for (LearnedKnowledge item : cache) {
                lines.add(json.writeValueAsString(item));
            }
            Files.write(storagePath, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to persist learned knowledge cache to {}", storagePath, e);
        }
    }

    private static final Set<String> STOPWORDS = Set.of(
            "what", "whats", "what's", "where", "wheres", "where's", "when", "whens", "who", "whos", "who's",
            "why", "how", "is", "are", "was", "were", "the", "this", "that", "these", "those",
            "tell", "give", "can", "could", "would", "please", "about", "for", "from", "with",
            "பற்றி", "என்ன", "என்பது", "யார்", "எங்கே", "எப்போது", "எப்படி"
    );

    private double computeSimilarity(String q1, String q2) {
        Set<String> words1 = tokenize(q1);
        Set<String> words2 = tokenize(q2);
        if (words1.isEmpty() || words2.isEmpty()) return 0.0;

        int intersection = 0;
        for (String w : words1) {
            if (words2.contains(w)) intersection++;
        }

        int union = words1.size() + words2.size() - intersection;
        return (double) intersection / (double) union;
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        String[] parts = text.toLowerCase(Locale.ROOT).replaceAll("[^a-zA-Z0-9\\u0B80-\\u0BFF\\s]", " ").split("\\s+");
        for (String p : parts) {
            if (p.length() > 2 && !STOPWORDS.contains(p)) {
                tokens.add(p);
            }
        }
        if (tokens.isEmpty()) {
            for (String p : parts) {
                if (p.length() > 2) tokens.add(p);
            }
        }
        return tokens;
    }
}

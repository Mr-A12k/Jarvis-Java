package com.jarvis.chat;

import com.jarvis.common.ApiErrors;
import com.jarvis.config.JarvisProperties;
import com.jarvis.knowledge.KnowledgeService;
import com.jarvis.knowledge.LearnedKnowledge;
import com.jarvis.realtime.RealtimeFactReport;
import com.jarvis.realtime.RealtimeFactService;
import com.jarvis.realtime.RealtimeModelTrainingService;
import com.jarvis.web.LocalWebDecision;
import com.jarvis.web.WebSearch;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chat;
    private final JarvisProperties settings;
    private final com.jarvis.files.LocalFiles files;
    private final com.jarvis.database.DatabaseStore database;
    private final KnowledgeService knowledgeService;
    private final WebSearch webSearch;
    private final RealtimeFactService realtimeFactService;
    private final RealtimeModelTrainingService realtimeTrainingService;
    private final com.jarvis.ai.SegmentRouter segmentRouter;

    public ChatController(ChatService chat, JarvisProperties settings, com.jarvis.files.LocalFiles files, com.jarvis.database.DatabaseStore database) {
        this(chat, settings, files, database, null, null, null, null, null);
    }

    public ChatController(
            ChatService chat,
            JarvisProperties settings,
            com.jarvis.files.LocalFiles files,
            com.jarvis.database.DatabaseStore database,
            KnowledgeService knowledgeService,
            WebSearch webSearch
    ) {
        this(chat, settings, files, database, knowledgeService, webSearch, null, null, null);
    }

    @Autowired
    public ChatController(
            ChatService chat,
            JarvisProperties settings,
            com.jarvis.files.LocalFiles files,
            com.jarvis.database.DatabaseStore database,
            @Autowired(required = false) KnowledgeService knowledgeService,
            @Autowired(required = false) WebSearch webSearch,
            @Autowired(required = false) RealtimeFactService realtimeFactService,
            @Autowired(required = false) RealtimeModelTrainingService realtimeTrainingService,
            @Autowired(required = false) com.jarvis.ai.SegmentRouter segmentRouter
    ) {
        this.chat = chat;
        this.settings = settings;
        this.files = files;
        this.database = database;
        this.knowledgeService = knowledgeService;
        this.webSearch = webSearch;
        this.realtimeFactService = realtimeFactService;
        this.realtimeTrainingService = realtimeTrainingService;
        this.segmentRouter = segmentRouter;
    }

    public static String normalizeQueryTypos(String message) {
        if (message == null || message.isBlank()) return message;
        return message
                .replaceAll("(?i)\\bgoald\\b", "gold")
                .replaceAll("(?i)\\biss\\b", "is")
                .replaceAll("(?i)\\bwht\\b", "what")
                .replaceAll("(?i)\\btodays\\b", "today's")
                .replaceAll("(?i)\\bqntm\\b", "quantum")
                .replaceAll("(?i)\\bphys\\b", "physics");
    }

    private Flux<String> local(ChatRequest request) {
        String normalizedMsg = normalizeQueryTypos(request.message());
        ChatRequest normalizedRequest = Objects.equals(normalizedMsg, request.message()) ? request : new ChatRequest(normalizedMsg, request.history());
        boolean isDispute = isCorrectionOrDispute(normalizedRequest.message());
        if (isDispute && knowledgeService != null) {
            knowledgeService.invalidate(normalizedRequest.message());
        }

        // 1. Dedicated Real-Time Research (Stock, Commodities, Datetime, Live Facts)
        if (realtimeFactService != null && realtimeFactService.isRealtimeQuery(normalizedRequest.message())) {
            return streamRealtimeFacts(normalizedRequest).filter(e -> "delta".equals(e.event())).map(e -> e.data().text());
        }

        // 2. Direct recall from previously learned web knowledge (only if not a dispute or volatile topic)
        if (!isDispute && knowledgeService != null) {
            Optional<LearnedKnowledge> recalled = knowledgeService.recall(normalizedRequest.message());
            if (recalled.isPresent()) {
                boolean isTamil = PromptFactory.isTamil(normalizedRequest.message());
                return Flux.just(knowledgeService.formatRecalledAnswer(recalled.get(), isTamil));
            }
        }

        // 3. Check if the query asks for internet research / real-time data
        if (webSearch != null && isWebSearchQuery(normalizedRequest.message())) {
            return executeWebSearchAndLearn(normalizedRequest);
        }

        // 4. Regular chat with memory and learned facts injected
        return localChat(normalizedRequest);
    }

    private Flux<String> localChat(ChatRequest request) {
        return Mono.fromCallable(() -> {
            String notes = files.state().notes();
            if (database.configured()) {
                var saved = database.list(com.jarvis.database.DatabaseStore.Kind.memories, 10, 0);
                StringBuilder context = new StringBuilder();
                for (var memory : saved) context.append(memory.get("title")).append(": ").append(memory.get("content")).append('\n');
                notes = com.jarvis.files.LocalFiles.clip(context.toString(), 150) + "\n" + com.jarvis.files.LocalFiles.clip(notes, 50);
            }
            if (knowledgeService != null) {
                var learned = knowledgeService.listRecent(2);
                if (!learned.isEmpty()) {
                    StringBuilder kb = new StringBuilder();
                    for (var item : learned) kb.append(item.query()).append(": ").append(item.answer()).append('\n');
                    notes = notes + "\n" + com.jarvis.files.LocalFiles.clip(kb.toString(), 100);
                }
            }
            return notes;
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .flatMapMany(notes -> notes.isBlank() ? chat.stream(request) : chat.stream(request, "User memory notes: " + com.jarvis.files.LocalFiles.clip(notes, 200)));
    }

    public static boolean isWebSearchQuery(String message) {
        if (message == null || message.isBlank()) return false;
        String lower = message.toLowerCase(Locale.ROOT).trim();

        // 1. Explicit search commands
        if (lower.startsWith("/search ") || lower.startsWith("/web ") || lower.startsWith("search ")
                || lower.startsWith("/google ") || lower.startsWith("google ")
                || lower.startsWith("look up ") || lower.startsWith("browse ")
                || lower.contains("search internet") || lower.contains("search the web")
                || lower.contains("search online") || lower.contains("look up online")
                || lower.contains("google this") || lower.contains("find online")
                || lower.contains("இணையத்தில் தேடு") || lower.contains("இணையத்தில் பார்")
                || lower.contains("வலைத்தளத்தில் தேடு") || lower.contains("கூகுளில் தேடு")) {
            return true;
        }

        if (isCorrectionOrDispute(message)) return false;

        // 2. Normalize common shorthand, typos, and abbreviations for query analysis
        String normalized = lower
                .replace("ott releas", "ott release")
                .replace("releas", "release")
                .replace("tam movs", "tamil movies")
                .replace("tam mov", "tamil movie")
                .replace("movs", "movies")
                .replace("mov ", "movie ")
                .replace("goald", "gold")
                .replace("iss ", "is ")
                .replace("wht ", "what ");

        // 3. Standalone real-time domains and keywords
        if (normalized.contains("ott release") || normalized.contains("ott releases")
                || normalized.contains("theatre release") || normalized.contains("theater release")
                || normalized.contains("box office collection") || normalized.contains("box office report")
                || normalized.contains("live score") || normalized.contains("cricket score")
                || normalized.contains("ipl score") || normalized.contains("match score")
                || normalized.contains("breaking news") || normalized.contains("todays news")
                || normalized.contains("today news") || normalized.contains("latest news")
                || normalized.contains("gold rate today") || normalized.contains("today gold rate")
                || normalized.contains("gold price today") || normalized.contains("today gold price")
                || normalized.contains("gold price") || normalized.contains("gold rate")
                || normalized.contains("silver price") || normalized.contains("silver rate")
                || normalized.contains("petrol price today") || normalized.contains("diesel price today")
                || normalized.contains("weather today") || normalized.contains("today weather")
                || normalized.contains("weather in ") || normalized.contains("temperature in ")
                || normalized.contains("வானிலை") || normalized.contains("தங்கம் விலை")
                || normalized.contains("இன்றைய செய்தி") || normalized.contains("ஓடிடி ரிலீஸ்")
                || normalized.contains("வெளியாகும் படங்கள்") || normalized.contains("புதிய படங்கள்")) {
            return true;
        }

        // 4. Current leadership, key figures, and office holders (e.g. "who is prime minister of UK", "who is Google CEO")
        if (normalized.matches(".*\\b(?:who is|who are|tell me who is|name the)\\s+(?:the\\s+)?(?:current|present|new)?\\s*(?:president|prime minister|pm|ceo|governor|chief minister|cm|minister|chancellor|secretary general|director|head|chairman|captain|coach|mayor|leader|pope|king|queen|ambassador|chief justice)\\b.*")
                || normalized.matches(".*\\b(?:who is|who are)\\s+[a-zA-Z0-9_-]+\\s+(?:ceo|president|prime minister|founder|chairman|coach|captain)\\b.*")
                || normalized.matches(".*\\b(?:யார்\\s+(?:தற்போதைய\\s+)?(?:பிரதமர்|முதல்வர்|தலைவர்|சிஇஓ|ஆளுநர்|அமைச்சர்))\\b.*")) {
            return true;
        }

        // 5. Current metrics, statistics, demographics & economic indicators
        if (normalized.matches(".*\\b(?:what is|tell me|what's|how much is)\\s+(?:the\\s+)?(?:current|latest|present|today's)?\\s*(?:population|gdp|inflation|unemployment|interest rate|repo rate|exchange rate|conversion rate|net worth|market cap|valuation|forex|aqi|air quality|pollution|temperature|rainfall|cases|statistics|ranking|standings|score)\\b.*")
                || normalized.matches(".*\\b(?:population|gdp|inflation|unemployment|interest rate|repo rate|exchange rate|conversion rate|net worth|market cap|aqi|air quality)\\s+(?:of|in|for)\\b.*")) {
            return true;
        }

        // 6. Technology versions, product pricing, specs & launch dates
        if (normalized.matches(".*\\b(?:latest|newest|recent|current)\\s+(?:version|release|edition|update|features|specs|specifications|price|cost|model|phone|mobile|laptop|gpu|cpu|processor|chip|car|bike|os|framework|library|software|patch|status)\\b.*")
                || normalized.matches(".*\\b(?:price|cost|release date|launch date)\\s+(?:of|for)\\s+(?:the\\s+)?[a-zA-Z0-9_-]+.*")) {
            return true;
        }

        // 7. Current events, news, developments, crises, space missions
        if (normalized.matches(".*\\b(?:what happened|what is happening|what's happening|happening now|news about|updates? on|situation in|status of|crisis in|war in|conflict in|election in|protests? in|strike in|earthquake in|flood in|cyclone in|tsunami in|accident in|mission of|space mission)\\b.*")
                || normalized.matches(".*\\b(?:என்ன நடந்தது|என்ன நடக்கிறது|செய்தி|செய்திகள்|நிலவரம்)\\b.*")) {
            return true;
        }

        // 8. Sports scores, match results, standings, winners
        if (normalized.matches(".*\\b(?:score of|match score|live score|scorecard|who won|who is winning|points table|standings|winner of|champion of|championship|medal tally|tournament results|fixture|fixtures)\\b.*")) {
            return true;
        }

        // 9. Temporal indicator + Real-Time topic combination
        boolean hasTemporal = normalized.contains("this week") || normalized.contains("this weekend")
                || normalized.contains("today") || normalized.contains("yesterday") || normalized.contains("tomorrow")
                || normalized.contains("this month") || normalized.contains("this year")
                || normalized.contains("latest") || normalized.contains("recent") || normalized.contains("currently")
                || normalized.contains("upcoming") || normalized.contains("newest") || normalized.contains("right now")
                || normalized.contains("intha vaaram") || normalized.contains("indru") || normalized.contains("indraya")
                || normalized.contains("naetru") || normalized.contains("naatru") || normalized.contains("naalaiku")
                || normalized.contains("இந்த வாரம்") || normalized.contains("இன்று") || normalized.contains("இன்றைய")
                || normalized.contains("நேற்று") || normalized.contains("நாளை") || normalized.contains("சமீபத்திய")
                || normalized.contains("தற்போதைய") || normalized.contains("புதிய") || normalized.contains("வரவிருக்கும்");

        if (hasTemporal) {
            boolean hasMedia = normalized.contains("ott") || normalized.contains("movie") || normalized.contains("movies")
                    || normalized.contains("film") || normalized.contains("films") || normalized.contains("cinema")
                    || normalized.contains("theatre") || normalized.contains("theater") || normalized.contains("release")
                    || normalized.contains("releases") || normalized.contains("streaming") || normalized.contains("stream")
                    || normalized.contains("netflix") || normalized.contains("prime") || normalized.contains("hotstar")
                    || normalized.contains("aha") || normalized.contains("trailer") || normalized.contains("teaser")
                    || normalized.contains("episode") || normalized.contains("series")
                    || normalized.contains("padam") || normalized.contains("padangal") || normalized.contains("thiraipadam")
                    || normalized.contains("படம்") || normalized.contains("படங்கள்") || normalized.contains("திரைப்படம்")
                    || normalized.contains("ரிலீஸ்") || normalized.contains("வெளியீடு") || normalized.contains("வெளியான");

            boolean hasNewsOrEvents = normalized.contains("news") || normalized.contains("headline") || normalized.contains("headlines")
                    || normalized.contains("update") || normalized.contains("updates") || normalized.contains("happened")
                    || normalized.contains("happening") || normalized.contains("incident") || normalized.contains("event")
                    || normalized.contains("seithi") || normalized.contains("seithigal") || normalized.contains("செய்தி");

            boolean hasFinance = normalized.contains("price") || normalized.contains("rate") || normalized.contains("gold")
                    || normalized.contains("silver") || normalized.contains("petrol") || normalized.contains("diesel")
                    || normalized.contains("stock") || normalized.contains("stocks") || normalized.contains("crypto")
                    || normalized.contains("bitcoin") || normalized.contains("market") || normalized.contains("sensex")
                    || normalized.contains("nifty") || normalized.contains("vilai") || normalized.contains("விலை");

            boolean hasWeather = normalized.contains("weather") || normalized.contains("temperature") || normalized.contains("forecast")
                    || normalized.contains("rain") || normalized.contains("storm") || normalized.contains("cyclone")
                    || normalized.contains("vanilai") || normalized.contains("mazhai") || normalized.contains("வானிலை");

            boolean hasSports = normalized.contains("score") || normalized.contains("match") || normalized.contains("winner")
                    || normalized.contains("points table") || normalized.contains("ipl") || normalized.contains("cricket")
                    || normalized.contains("football") || normalized.contains("tournament") || normalized.contains("won");

            boolean hasTechOrProduct = normalized.contains("version") || normalized.contains("feature") || normalized.contains("features")
                    || normalized.contains("specs") || normalized.contains("model") || normalized.contains("phone")
                    || normalized.contains("laptop") || normalized.contains("gpu") || normalized.contains("chip")
                    || normalized.contains("software") || normalized.contains("app") || normalized.contains("car")
                    || normalized.contains("bike") || normalized.contains("flight") || normalized.contains("train");

            boolean hasFactOrInfo = normalized.contains("data") || normalized.contains("info") || normalized.contains("information")
                    || normalized.contains("details") || normalized.contains("report") || normalized.contains("stats")
                    || normalized.contains("status") || normalized.contains("situation") || normalized.contains("election")
                    || normalized.contains("result") || normalized.contains("results") || normalized.contains("ranking");

            if (hasMedia || hasNewsOrEvents || hasFinance || hasWeather || hasSports || hasTechOrProduct || hasFactOrInfo) {
                return true;
            }
        }

        // 10. General current state queries: "who is the current...", "who is now...", "what is the current..."
        if (normalized.matches(".*\\b(?:who is|what is|tell me|give me|show me)\\s+(?:the\\s+)?(?:current|new|latest|present)\\b.*")
                || normalized.matches(".*\\b(?:who is|who are)\\s+currently\\b.*")
                || normalized.matches(".*\\bwho won\\b.*")) {
            return true;
        }

        return false;
    }

    public static String normalizeWebSearchQuery(String message) {
        if (message == null || message.isBlank()) return "";
        String clean = message
                .replaceAll("(?i)^/(?:search|web|google)\\s+", "")
                .replaceAll("(?i)^(?:search internet for|search the web for|search online for|look up online for|search for|google for)\\s+", "")
                .trim();

        // Expand chat shorthand, typos, and common abbreviations for clean search queries
        clean = clean
                .replaceAll("(?i)\\bqntm\\b", "quantum")
                .replaceAll("(?i)\\bphys\\b", "physics")
                .replaceAll("(?i)\\bott\\s+releas\\b", "ott release")
                .replaceAll("(?i)\\breleas\\b", "release")
                .replaceAll("(?i)\\btam\\s+movs?\\b", "tamil movies")
                .replaceAll("(?i)\\btam\\b", "tamil")
                .replaceAll("(?i)\\bmovs\\b", "movies")
                .replaceAll("(?i)\\bmov\\b", "movie")
                .replaceAll("(?i)\\btel\\b", "telugu")
                .replaceAll("(?i)\\bmal\\b", "malayalam")
                .replaceAll("(?i)\\btodays\\b", "today")
                .replaceAll("(?i)\\bgoald\\b", "gold")
                .replaceAll("(?i)\\biss\\b", "is")
                .replaceAll("(?i)\\bwht\\b", "what")
                .trim();

        return clean;
    }

    public static boolean isCorrectionOrDispute(String message) {
        if (message == null || message.isBlank()) return false;
        String lower = message.toLowerCase(Locale.ROOT).trim();
        return lower.contains("you are wrong") || lower.contains("you're wrong") || lower.contains("your wrong")
                || lower.contains("its wrong") || lower.contains("it's wrong") || lower.contains("is wrong")
                || lower.contains("are you sure") || lower.contains("incorrect") || lower.contains("not correct")
                || lower.contains("tell me the correct") || lower.contains("wrong ans") || lower.contains("wrong data")
                || lower.contains("fix it") || lower.contains("fix this") || lower.contains("mistake")
                || lower.contains("never asked") || lower.contains("didn't ask") || lower.contains("did not ask")
                || lower.contains("not what i asked") || lower.contains("why this response")
                || lower.contains("why did you say") || lower.contains("why did you give") || lower.contains("why did you mention")
                || lower.contains("why are you talking") || lower.contains("not relevant") || lower.contains("irrelevant")
                || lower.contains("thappu") || lower.contains("தவறு") || lower.contains("பொய்") || lower.contains("சரி இல்லை");
    }

    public static boolean isSystemOrActionCommand(String message) {
        if (message == null || message.isBlank()) return false;
        String lower = message.toLowerCase(Locale.ROOT).trim();
        if (lower.startsWith("/")) return true;
        String stripped = lower.replaceAll("(?i)^(?:hey|hi|hello|ok|okay)?\\s*jarvis\\s*[,:]?\\s*", "")
                .replaceAll("(?i)^(?:(?:please\\s+)?(?:can you|could you|would you|will you|i want you to|i want to|i need you to|help me|can we|i given order to|i gave order to|give order to|order to)\\s+(?:please\\s+)?)+", "")
                .replaceAll("(?i)^please\\s+", "").trim();

        if (stripped.matches("(?i)^(?:open|launch|start|run|access|close|exit|quit|terminate|kill|shut down)\\b.*")) return true;
        if (stripped.matches("(?i)^(?:play|pause|resume|next|previous|prev|stop|skip)\\b.*")) return true;
        if (stripped.matches("(?i)^(?:volume|mute|unmute|silence|louder|quieter)\\b.*")
                || stripped.contains("volume up") || stripped.contains("volume down")) return true;
        if (stripped.contains("biometric") || stripped.contains("face") || stripped.contains("identity")
                || stripped.contains("verify") || stripped.contains("authenticate")) return true;
        if (stripped.contains("hardware") || stripped.contains("diagnostics") || stripped.contains("ram usage")
                || stripped.contains("gpu status") || stripped.contains("system status") || stripped.contains("system health")) return true;
        if (stripped.matches("(?i)^(?:who are you|who am i|what is your name|my name|hello|hi|hey|good morning|good evening|good night)\\b.*")) return true;
        if (stripped.contains("voice speed") || stripped.contains("speak faster") || stripped.contains("speak slower")
                || stripped.contains("change voice") || stripped.contains("switch voice")) return true;
        if (lower.contains("திற") || lower.contains("மூடு") || lower.contains("ஓபன்") || lower.contains("க்ளோஸ்")
                || lower.contains("பாடல்") || lower.contains("பாட்டு") || lower.contains("சத்தம்")
                || lower.contains("வால்யூம்") || lower.contains("முகம்") || lower.contains("குரல்")
                || lower.contains("வணக்கம்") || lower.contains("நிலை")) return true;

        return false;
    }

    public static String resolveContextualQuery(String message, List<ChatRequest.Turn> history) {
        if (isSystemOrActionCommand(message) || isCorrectionOrDispute(message)) {
            return message;
        }
        if (message == null || message.isBlank() || history == null || history.isEmpty()) {
            return message != null ? message : "";
        }

        String lower = message.toLowerCase(Locale.ROOT).trim();
        // Check if query contains deictic / anaphoric referents (that, it, this, him, her, them, etc.)
        boolean hasReferent = lower.matches(".*\\b(?:that|this|it|them|he|she|these|those|there|same|above|mentioned|in that|about that|on that|of that|in this|about this|on this|of this|in it|about it|on it|of it|its|their|his|her)\\b.*")
                || lower.contains("அதில்") || lower.contains("இதில்") || lower.contains("அதை")
                || lower.contains("இதை") || lower.contains("அவர்") || lower.contains("அவர்கள்")
                || lower.contains("அது") || lower.contains("இது");

        if (!hasReferent && message.length() > 35) {
            return message;
        }

        String lastTopic = extractTopicFromHistory(history);
        if (lastTopic == null || lastTopic.isBlank()) {
            return message;
        }

        if (hasReferent) {
            String resolved = message
                    .replaceAll("(?i)\\babout\\s+(?:that|this|it)\\b", "about " + lastTopic)
                    .replaceAll("(?i)\\bin\\s+(?:that|this|it)\\b", "in " + lastTopic)
                    .replaceAll("(?i)\\bon\\s+(?:that|this|it)\\b", "on " + lastTopic)
                    .replaceAll("(?i)\\bof\\s+(?:that|this|it)\\b", "of " + lastTopic)
                    .replaceAll("(?i)\\bfor\\s+(?:that|this|it)\\b", "for " + lastTopic)
                    .replaceAll("(?i)\\bwith\\s+(?:that|this|it)\\b", "with " + lastTopic)
                    .replaceAll("(?i)\\bto\\s+(?:that|this|it)\\b", "to " + lastTopic)
                    .replaceAll("(?i)\\bfrom\\s+(?:that|this|it)\\b", "from " + lastTopic)
                    .replaceAll("(?i)\\b(?:that|this|it)\\b", lastTopic)
                    .replace("அதைப் பற்றி", lastTopic + " பற்றி")
                    .replace("இதைப் பற்றி", lastTopic + " பற்றி")
                    .replace("அதில்", lastTopic + " இல்")
                    .replace("இதில்", lastTopic + " இல்")
                    .replace("அதை", lastTopic)
                    .replace("இதை", lastTopic)
                    .replace("அது", lastTopic)
                    .replace("இது", lastTopic);
            return resolved.trim();
        }

        // If short follow-up like "latest updates", "more details", "give an example", etc.
        String[] words = lower.split("\\s+");
        if (words.length <= 4 && !lower.contains(lastTopic.toLowerCase(Locale.ROOT))) {
            if (lower.matches("(?i)^(?:yes|no|ok|okay|sure|yep|yeah|why|how|what|thanks|thank you|proceed|continue)\\b.*")) {
                return message;
            }
            return message + " regarding " + lastTopic;
        }

        return message;
    }

    public static String extractTopicFromHistory(List<ChatRequest.Turn> history) {
        if (history == null || history.isEmpty()) return null;

        // Traverse history backwards to find the most recent user turn with substantive content
        for (int i = history.size() - 1; i >= 0; i--) {
            var turn = history.get(i);
            if (turn.role() == ChatRequest.Role.user && turn.content() != null && !turn.content().isBlank()) {
                String clean = turn.content().trim();
                // Strip common slash commands
                clean = clean.replaceAll("(?i)^/(?:chat|code|math|search|web|war)\\s+", "");
                // Strip common query preambles
                clean = clean.replaceAll("(?i)^(?:explain(?: a concept)?:?|what is|what are|tell me about|how does|how do|who is|describe|summary of|details of|definition of|give me(?: some)?|பற்றி கூறு|விளக்கு|என்ன)\\s*", "");
                clean = clean.replaceAll("[?!.,;:\"']+$", "").trim();
                clean = clean.replaceAll("(?i)\\s*(?:பற்றி கூறு|பற்றி சொல்|பற்றி விளக்கு|விளக்கு|கூறு|சொல்)$", "").trim();

                // Check if user turn itself was an anaphora or too short
                if (clean.matches("(?i)^(?:yes|no|ok|okay|sure|thanks|thank you|continue|more|tell me|latest|update|latest update|why|how)\\b.*")) {
                    continue;
                }

                if (!clean.isBlank() && clean.length() >= 3) {
                    // Expand abbreviations if present
                    clean = clean
                            .replaceAll("(?i)\\bqntm\\b", "quantum")
                            .replaceAll("(?i)\\bphys\\b", "physics");
                    return clean;
                }
            }
        }

        // Fallback: check first sentence/heading of previous assistant turn
        for (int i = history.size() - 1; i >= 0; i--) {
            var turn = history.get(i);
            if (turn.role() == ChatRequest.Role.assistant && turn.content() != null && !turn.content().isBlank()) {
                String content = turn.content().replaceAll("<think>[\\s\\S]*?</think>", "").trim();
                var boldMatcher = Pattern.compile("(?i)\\*\\*([A-Za-z0-9\\s]{3,35})\\*\\*").matcher(content);
                if (boldMatcher.find()) {
                    return boldMatcher.group(1).trim();
                }
                var phraseMatcher = Pattern.compile("(?i)(?:concept of|theory of|about|explaining|regarding|guide to)\\s+([A-Za-z0-9\\s]{3,35})(?:\\s|\\.|\\,|\\()").matcher(content);
                if (phraseMatcher.find()) {
                    return phraseMatcher.group(1).trim();
                }
            }
        }

        return null;
    }

    public static boolean isRealTimeRefusal(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.replaceAll("<think>[\\s\\S]*?</think>", "").toLowerCase(Locale.ROOT).trim();
        return (lower.contains("do not have access to real-time") || lower.contains("dont have access to real-time")
                || (lower.contains("cannot provide") && lower.contains("real-time"))
                || lower.contains("no access to real-time")
                || lower.contains("cannot provide real-time")
                || lower.contains("do not have real-time")
                || lower.contains("dont have real-time")
                || lower.contains("knowledge cutoff")
                || lower.contains("cannot access real-time")
                || lower.contains("unable to provide real-time")
                || (lower.contains("cannot provide information about") && lower.contains("real-time")));
    }

    private Flux<String> executeWebSearchAndLearn(ChatRequest request) {
        String cleanQuery = normalizeWebSearchQuery(request.message());

        return webSearch.search(cleanQuery)
                .flatMap(sources -> new LocalWebDecision(chat).select(cleanQuery, sources))
                .flatMapMany(sources -> {
                    if (sources.isEmpty()) {
                        log.info("No external web evidence found for '{}'. Reporting retrieval failure explicitly.", cleanQuery);
                        return Flux.just("⚠️ No verified web search results were found for \"" + cleanQuery + "\". No unsupported offline answer was substituted.");
                    }
                    String context = LocalWebDecision.context(sources, 360);
                    StringBuilder answerBuffer = new StringBuilder();

                    StringBuilder sourcesFooter = new StringBuilder("\n\n**Verified Sources:**\n");
                    for (var src : sources) {
                        sourcesFooter.append("- [").append(src.title()).append("](").append(src.url()).append(")\n");
                    }

                    return chat.stream(new ChatRequest(cleanQuery, request.history()), context)
                            .concatWith(Mono.just(sourcesFooter.toString()))
                            .doOnNext(answerBuffer::append)
                            .doFinally(signal -> {
                                if (knowledgeService != null && !answerBuffer.isEmpty()) {
                                    List<String> urls = sources.stream().map(WebSearch.Source::url).filter(Objects::nonNull).toList();
                                    String lang = PromptFactory.isTamil(cleanQuery) ? "ta" : "en";
                                    knowledgeService.learn(cleanQuery, answerBuffer.toString(), urls, lang);
                                }
                            });
                })
                .onErrorResume(error -> {
                    log.warn("Web search failed for '{}': {}. Reporting retrieval failure explicitly.", cleanQuery, error.getMessage());
                    return Flux.just("⚠️ Web search retrieval failed: " + error.getMessage() + ". No unsupported offline answer was substituted.");
                });
    }

    public record ChatReply(String text, String model) {}
    public record StreamData(String text, String code, String message, List<WebSearch.Source> sources, String segment, String model) {
        public StreamData(String text, String code, String message) {
            this(text, code, message, null, null, null);
        }
        public StreamData(String text, String code, String message, List<WebSearch.Source> sources) {
            this(text, code, message, sources, null, null);
        }
    }

    @GetMapping("/segments")
    public List<com.jarvis.ai.SegmentRouter.SegmentInfo> getSegments() {
        if (segmentRouter != null) {
            return segmentRouter.getAvailableSegments();
        }
        return List.of();
    }

    @PostMapping
    public Mono<ChatReply> complete(@Valid @RequestBody ChatRequest request) {
        String normalizedMsg = normalizeQueryTypos(request.message());
        String resolvedQuery = resolveContextualQuery(normalizedMsg, request.history());
        ChatRequest contextualRequest = new ChatRequest(resolvedQuery, request.history());

        var route = segmentRouter != null ? segmentRouter.route(contextualRequest.message()) : null;
        String activeModel = route != null ? route.model() : settings.chatModel();

        if (knowledgeService != null) {
            Optional<LearnedKnowledge> recalled = knowledgeService.recall(contextualRequest.message());
            if (recalled.isPresent()) {
                boolean isTamil = PromptFactory.isTamil(contextualRequest.message());
                return Mono.just(new ChatReply(knowledgeService.formatRecalledAnswer(recalled.get(), isTamil), activeModel));
            }
        }

        // Dedicated Real-Time Research & Fact Checking
        if (realtimeFactService != null && realtimeFactService.isRealtimeQuery(contextualRequest.message())) {
            return completeRealtimeFacts(contextualRequest);
        }

        if (webSearch != null && isWebSearchQuery(contextualRequest.message())) {
            return executeWebSearchAndLearn(contextualRequest)
                    .collectList()
                    .map(parts -> new ChatReply(String.join("", parts), "jarvis-researcher:latest"));
        }

        return localChat(contextualRequest).collectList().flatMap(parts -> {
            String combined = String.join("", parts);
            if (webSearch != null && isRealTimeRefusal(combined)) {
                log.info("Model gave real-time refusal for '{}'; triggering automatic reactive web search fallback", contextualRequest.message());
                return executeWebSearchAndLearn(contextualRequest)
                        .collectList()
                        .map(webParts -> new ChatReply(String.join("", webParts), "jarvis-researcher:latest"));
            }
            return Mono.just(new ChatReply(combined, activeModel));
        });
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<StreamData>> stream(@Valid @RequestBody ChatRequest request) {
        String normalizedMsg = normalizeQueryTypos(request.message());
        String resolvedQuery = resolveContextualQuery(normalizedMsg, request.history());
        ChatRequest contextualRequest = new ChatRequest(resolvedQuery, request.history());

        var route = segmentRouter != null ? segmentRouter.route(contextualRequest.message()) : null;
        String activeSegment = route != null ? route.segment().name() : "GENERAL";
        String activeModel = route != null ? route.model() : settings.chatModel();

        // 1. Direct recall from previously learned web knowledge
        if (knowledgeService != null) {
            Optional<LearnedKnowledge> recalled = knowledgeService.recall(contextualRequest.message());
            if (recalled.isPresent()) {
                boolean isTamil = PromptFactory.isTamil(contextualRequest.message());
                String recalledText = knowledgeService.formatRecalledAnswer(recalled.get(), isTamil);
                return Flux.just(
                        event("delta", new StreamData(recalledText, null, null)),
                        event("done", new StreamData(null, null, null, null, activeSegment, activeModel))
                );
            }
        }

        // 2. Real-time fact gathering & research model stream
        if (realtimeFactService != null && realtimeFactService.isRealtimeQuery(contextualRequest.message())) {
            return streamRealtimeFacts(contextualRequest);
        }

        // 3. Real-time web search stream with sources and deltas
        if (webSearch != null && isWebSearchQuery(contextualRequest.message())) {
            return streamWebSearch(contextualRequest);
        }

        // 4. Regular chat stream with MoSM routing
        return localChat(contextualRequest).map(text -> event("delta", new StreamData(text, null, null)))
                .concatWith(Mono.fromSupplier(() -> event("done", new StreamData(null, null, null, null, activeSegment, activeModel))))
                .onErrorResume(error -> {
                    var safe = ApiErrors.modelError(error);
                    return Mono.just(event("error", new StreamData(null, safe.code(), safe.getMessage())));
                });
    }

    private Mono<ChatReply> completeRealtimeFacts(ChatRequest request) {
        String query = request.message();
        return realtimeFactService.gatherRealtimeFacts(query)
                .flatMap(report -> {
                    StringBuilder answerBuffer = new StringBuilder();
                    StringBuilder footer = new StringBuilder("\n\n**Verified Real-Time Grounding:**\n");
                    for (String src : report.sources()) {
                        footer.append("- ").append(src).append("\n");
                    }

                    return chat.stream(request, report.summaryText())
                            .concatWith(Mono.just(footer.toString()))
                            .doOnNext(answerBuffer::append)
                            .collectList()
                            .map(parts -> {
                                String combined = String.join("", parts);
                                if (realtimeTrainingService != null) {
                                    String lang = PromptFactory.isTamil(query) ? "ta" : "en";
                                    realtimeTrainingService.recordAndAutoTrain(
                                            query,
                                            report.summaryText(),
                                            combined,
                                            report.category(),
                                            report.sources(),
                                            lang
                                    );
                                }
                                return new ChatReply(combined, "jarvis-researcher:latest");
                            });
                })
                .onErrorResume(e -> {
                    log.warn("Real-time fact gathering failed for '{}': {}. Falling back to chat model.", query, e.getMessage());
                    return localChat(request).collectList().map(parts -> new ChatReply(String.join("", parts), settings.chatModel()));
                });
    }

    private Flux<ServerSentEvent<StreamData>> streamRealtimeFacts(ChatRequest request) {
        String query = request.message();
        return realtimeFactService.gatherRealtimeFacts(query)
                .flatMapMany(report -> {
                    var sources = report.sources().stream()
                            .map(s -> new WebSearch.Source(0, s, null, s))
                            .toList();
                    var sourcesEvent = Flux.just(event("sources", new StreamData(null, null, null, sources)));

                    StringBuilder answerBuffer = new StringBuilder();
                    StringBuilder footer = new StringBuilder("\n\n**Verified Real-Time Grounding:**\n");
                    for (String src : report.sources()) {
                        footer.append("- ").append(src).append("\n");
                    }

                    return sourcesEvent.concatWith(
                            chat.stream(request, report.summaryText())
                                    .concatWith(Mono.just(footer.toString()))
                                    .doOnNext(answerBuffer::append)
                                    .map(text -> event("delta", new StreamData(text, null, null)))
                                    .doFinally(signal -> {
                                        if (realtimeTrainingService != null && !answerBuffer.isEmpty()) {
                                            String lang = PromptFactory.isTamil(query) ? "ta" : "en";
                                            realtimeTrainingService.recordAndAutoTrain(
                                                    query,
                                                    report.summaryText(),
                                                    answerBuffer.toString(),
                                                    report.category(),
                                                    report.sources(),
                                                    lang
                                            );
                                        }
                                    })
                    ).concatWithValues(event("done", new StreamData(null, null, null, null, "RESEARCH", "jarvis-researcher:latest")));
                })
                .onErrorResume(error -> {
                    log.warn("Real-time fact streaming failed for '{}': {}. Falling back to chat model with history.", query, error.getMessage());
                    return localChat(request)
                            .map(text -> event("delta", new StreamData(text, null, null)))
                            .concatWith(Mono.fromSupplier(() -> event("done", new StreamData(null, null, null, null, "GENERAL", settings.chatModel()))));
                });
    }

    private Flux<ServerSentEvent<StreamData>> streamWebSearch(ChatRequest request) {
        String cleanQuery = normalizeWebSearchQuery(request.message());

        return webSearch.search(cleanQuery)
                .flatMap(sources -> new LocalWebDecision(chat).select(cleanQuery, sources))
                .flatMapMany(sources -> {
                    if (sources.isEmpty()) {
                        log.info("No external web evidence found for '{}'. Reporting retrieval failure explicitly.", cleanQuery);
                        String failMsg = "⚠️ No verified web search results were found for \"" + cleanQuery + "\". No unsupported offline answer was substituted.";
                        return Flux.just(
                                event("delta", new StreamData(failMsg, null, null, List.of())),
                                event("done", new StreamData(null, null, null, null, "RESEARCH", "jarvis-researcher:latest"))
                        );
                    }

                    var sourcesEvent = Flux.just(event("sources", new StreamData(null, null, null, sources)));
                    String context = LocalWebDecision.context(sources, 360);
                    StringBuilder answerBuffer = new StringBuilder();

                    StringBuilder sourcesFooter = new StringBuilder("\n\n**Verified Sources:**\n");
                    for (var src : sources) {
                        sourcesFooter.append("- [").append(src.title()).append("](").append(src.url()).append(")\n");
                    }

                    return sourcesEvent.concatWith(
                            chat.stream(new ChatRequest(cleanQuery, request.history()), context)
                                    .concatWith(Mono.just(sourcesFooter.toString()))
                                    .doOnNext(answerBuffer::append)
                                    .map(text -> event("delta", new StreamData(text, null, null)))
                                    .doFinally(signal -> {
                                        if (knowledgeService != null && !answerBuffer.isEmpty()) {
                                            List<String> urls = sources.stream().map(WebSearch.Source::url).filter(Objects::nonNull).toList();
                                            String lang = PromptFactory.isTamil(cleanQuery) ? "ta" : "en";
                                            knowledgeService.learn(cleanQuery, answerBuffer.toString(), urls, lang);
                                        }
                                    })
                    ).concatWithValues(event("done", new StreamData(null, null, null, null, "RESEARCH", "jarvis-researcher:latest")));
                })
                .onErrorResume(error -> {
                    log.warn("Web search stream failed for '{}': {}.", cleanQuery, error.getMessage());
                    String failMsg = "⚠️ Web search retrieval failed: " + error.getMessage() + ". No unsupported offline answer was substituted.";
                    return Flux.just(
                            event("delta", new StreamData(failMsg, null, null, List.of())),
                            event("done", new StreamData(null, null, null, null, "RESEARCH", "jarvis-researcher:latest"))
                    );
                });
    }

    private ServerSentEvent<StreamData> event(String type, StreamData data) {
        return ServerSentEvent.<StreamData>builder(data).event(type).build();
    }
}

package com.jarvis.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.jarvis.config.JarvisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * SegmentRouter: Routes user queries to specialized small language model segments.
 *
 * Implements the "Mixture of Small Models" / Segmented Knowledge architecture:
 * - MATH / REASONING: deepseek-r1:1.5b (DeepSeek-R1 Distilled CoT reasoning trace & mathematical proofs)
 * - CODING / TECHNICAL: qwen2.5-coder:1.5b / deepseek-r1:1.5b (Specialized code engineering & debugging)
 * - RESEARCH: jarvis-researcher:latest (Real-time financial markets, OTT releases & verified live facts)
 * - WAR / COMBAT: qwen3:1.7b (Low-latency tactical battlefield persona)
 * - GENERAL / IRON MAN: qwen3:1.7b / default chat model (Conversational voice & system orchestration)
 *
 * If a specialized segment model is not yet installed in Ollama, gracefully falls back to settings.chatModel().
 */
@Component
public class SegmentRouter {

    private static final Logger log = LoggerFactory.getLogger(SegmentRouter.class);

    public enum Segment {
        WAR("qwen3:1.7b", 0.85, "Combat & War Mode Tactical Segment"),
        MATH("deepseek-r1:1.5b", 0.2, "DeepSeek-R1 Cognitive Reasoning & Mathematical Proofs"),
        CODE("qwen2.5-coder:1.5b", 0.2, "Code Engineering Segment"),
        RESEARCH("jarvis-researcher:latest", 0.2, "Real-Time Fact Checking, Stock Markets & OTT Research Segment"),
        VISION("moondream:1.8b", 0.2, "Multimodal Vision & Screen Debugging Segment"),
        GENERAL("qwen3:1.7b", 0.7, "General Assistant & Iron Man Persona");

        private final String defaultModel;
        private final double temperature;
        private final String description;

        Segment(String defaultModel, double temperature, String description) {
            this.defaultModel = defaultModel;
            this.temperature = temperature;
            this.description = description;
        }

        public String defaultModel() { return defaultModel; }
        public double temperature() { return temperature; }
        public String description() { return description; }
    }

    public record RouteDecision(Segment segment, String model, double temperature, String explanation, boolean specializedModelActive) {
        public RouteDecision(Segment segment, String model, double temperature, String explanation) {
            this(segment, model, temperature, explanation, true);
        }
    }

    public record SegmentInfo(
            String id,
            String name,
            String defaultModel,
            String activeModel,
            double temperature,
            String description,
            boolean isSpecializedInstalled
    ) {}

    private final JarvisProperties settings;
    private final WebClient http;
    private final Set<String> installedModels = ConcurrentHashMap.newKeySet();
    private volatile long lastModelCheckTime = 0;

    // Fast heuristics for War & Tactical Combat Mode
    private static final Pattern WAR_PATTERN = Pattern.compile(
            "(?i)(?:\\b(?:war mode|combat mode|red alert|engage war mode|activate war mode|house party protocol|target locked|weapons hot|repulsors|fire repulsor|threat level|battle stations|code red|hulkbuster|veronica protocol|engage hostiles|lock on|full combat|attack protocol|strike authorization|engage weapons|fire missiles|destroy target)\\b|போர் முறை|தாக்குதல் முறை|சிவப்பு எச்சரிக்கை|ஆயுதங்களை இயக்கு)"
    );

    // Fast heuristics for Coding intent
    private static final Pattern CODE_PATTERN = Pattern.compile(
            "(?i)(?:\\b(?:code|coding|script|program|programming|python|java|javascript|typescript|c\\+\\+|cpp|c#|rust|golang|php|ruby|bash|shell|powershell|function|class|def|const|var|let|async|await|import|export|public|private|static|void|int|float|double|return|if|else|for|while|try|catch|exception|sql|select|insert|update|delete|table|database|query|react|vue|angular|component|tsx|jsx|html|css|json|yaml|docker|dockerfile|kubernetes|git|commit|pull|push|branch|repo|repository|regex|regexes|algorithm|leetcode|api|endpoint|controller|service|repository|entity|dto|model|bug|debug|stacktrace|syntax|compile|compiler|runtime)\\b|```|[{}<>;=>]{2,})"
    );

    // Fast heuristics for Deep Cognitive Reasoning & Formal Logic
    private static final Pattern REASONING_PATTERN = Pattern.compile(
            "(?i)(?:\\b(?:deepseek|r1|think through|think deeply|reason|reasoning|step[- ]by[- ]step|logic|logical|deduce|deduction|puzzle|riddle|proof|prove|theorem|paradox|hypothesis|cognitive|truth table|syllogism|first principles|trade[- ]off analysis|optimality|counterfactual|under what conditions|big-o|algorithmic complexity|time complexity|recurrence relation|mathematical induction|discrete math)\\b|யோசி|ஆழமாக யோசி|தர்க்கரீதியாக|காரணம் கூறு|விளக்கு|படி படியாக|நிரூபி)"
    );

    // Fast heuristics for Math & Equations
    private static final Pattern MATH_PATTERN = Pattern.compile(
            "(?i)(?:\\b(?:calculate|solve|equation|equations|integral|integrals|derivative|derivatives|differentiation|calculus|algebra|polynomial|matrix|matrices|vector|vectors|eigenvalue|probability|statistics|mean|median|mode|variance|standard deviation|theorem|proof|arithmetic|formula|formulas|logarithm|factorial|trigonometry|sine|cosine|tangent|cotangent|hypotenuse|triangle|geometry|circle|radius|diameter|area|perimeter|volume)\\b|\\b\\d+\\s*[+\\-*/^%=]\\s*\\d+|\\$\\$?[^$]+\\$\\$?|\\\\[a-zA-Z]+)"
    );

    // Fast heuristics for Realtime Facts, Stock Markets, OTT Releases, Commodities, and Live Grounding
    private static final Pattern RESEARCH_PATTERN = Pattern.compile(
            "(?i)(?:\\b(?:stock|stocks|market|markets|nifty|sensex|share price|shares|nasdaq|s&p|sp500|dow|dow jones|bse|nse|crypto|cryptocurrency|bitcoin|btc|ethereum|eth|trading|ticker|dividend|bull market|bear market|ott|movies|movie|film|films|cinema|releasing this week|released this week|this week release|netflix|prime video|hotstar|disney\\+|zee5|aha|sonyliv|box office|theatre release|theatre releases|gold|goald|silver|bullion|commodity|commodities|forex|usd/inr|dollar rate|rupee rate|crude oil|brent|what time is it|current time|today's date|todays date|what is today's date|what date is it|what day is today|what day is it|current date|date and time|time now|clock|calendar|fact check|fact-check|verify|verify if|is it true that|real-time data|realtime data|live facts|live news|breaking news)\\b|இன்றைய தேதி|தற்போதைய நேரம்|இன்று என்ன கிழமை|பங்குச்சந்தை|திரைப்படம்|வெளியீடு|உண்மை சரிபார்ப்பு|தங்கம்|வெள்ளி|சவரன்|விலை)"
    );

    // Fast heuristics for Multimodal Vision & Screen Debugging
    private static final Pattern VISION_PATTERN = Pattern.compile(
            "(?i)(?:\\b(?:look at (?:my |the )?screen|debug (?:my |the )?screen|check (?:my |the )?screen|what(?:'s| is) on (?:my |the )?screen|screen capture|screenshot|screen error|vision|multimodal|analyze (?:my |the )?screen|view (?:my |the )?screen|help me debug(?: this)?)\\b|திரையைப் பார்|ஸ்க்ரீனைப் பார்|திரையில் என்ன இருக்கிறது|பிழையை சரி செய்)"
    );

    @Autowired
    public SegmentRouter(JarvisProperties settings, WebClient ollamaWebClient) {
        this.settings = settings;
        this.http = ollamaWebClient;
        refreshInstalledModelsAsync();
    }

    public RouteDecision route(String message) {
        if (message == null || message.isBlank()) {
            return fallback(Segment.GENERAL, "Empty query");
        }

        String trimmed = message.trim();
        String lower = trimmed.toLowerCase();

        // 1. Explicit user segment overrides via slash command
        if (lower.startsWith("/war ") || lower.startsWith("/combat ") || lower.equals("/war") || lower.equals("/combat")) {
            return selectModel(Segment.WAR, "Explicit /war combat protocol requested");
        }
        if (lower.startsWith("/code ") || lower.startsWith("/coding ") || lower.startsWith("/dev ")) {
            return selectModel(Segment.CODE, "Explicit /code command requested");
        }
        if (lower.startsWith("/math ") || lower.startsWith("/calc ") || lower.startsWith("/r1 ")
                || lower.startsWith("/deepseek ") || lower.startsWith("/reason ") || lower.startsWith("/think ")
                || lower.startsWith("/logic ") || lower.equals("/r1") || lower.equals("/deepseek")
                || lower.equals("/reason") || lower.equals("/think") || lower.equals("/math")) {
            return selectModel(Segment.MATH, "Explicit DeepSeek-R1 cognitive reasoning command requested");
        }
        if (lower.startsWith("/research ") || lower.startsWith("/fact ") || lower.startsWith("/facts ")
                || lower.startsWith("/stock ") || lower.startsWith("/ott ") || lower.startsWith("/gold ")
                || lower.equals("/research") || lower.equals("/fact") || lower.equals("/facts")
                || lower.equals("/stock") || lower.equals("/ott") || lower.equals("/gold")) {
            return selectModel(Segment.RESEARCH, "Explicit research / fact-checking command requested");
        }
        if (lower.startsWith("/vision ") || lower.startsWith("/screen ") || lower.equals("/vision") || lower.equals("/screen")) {
            return selectModel(Segment.VISION, "Explicit /vision screen analysis command requested");
        }
        if (lower.startsWith("/chat ") || lower.startsWith("/general ")) {
            return selectModel(Segment.GENERAL, "Explicit /chat command requested");
        }

        // 2. Query classification (Priority: War -> Real-Time Research -> Math/Reasoning -> Code -> General)
        if (WAR_PATTERN.matcher(trimmed).find()) {
            return selectModel(Segment.WAR, "Detected war mode / combat directive pattern");
        }

        if (!isDispute(trimmed) && RESEARCH_PATTERN.matcher(trimmed).find()) {
            return selectModel(Segment.RESEARCH, "Detected real-time data / stock market / OTT / fact-checking pattern");
        }

        if (VISION_PATTERN.matcher(trimmed).find()) {
            return selectModel(Segment.VISION, "Detected screen vision / screen debugging directive pattern");
        }

        if (CODE_PATTERN.matcher(trimmed).find()) {
            return selectModel(Segment.CODE, "Detected code/programming pattern in query");
        }

        if (REASONING_PATTERN.matcher(trimmed).find() || MATH_PATTERN.matcher(trimmed).find()) {
            return selectModel(Segment.MATH, "Detected deep cognitive reasoning / mathematical pattern in query");
        }

        return selectModel(Segment.GENERAL, "Default general assistant & voice persona");
    }

    private boolean isDispute(String query) {
        if (query == null || query.isBlank()) return false;
        String lower = query.toLowerCase(Locale.ROOT);
        return lower.contains("never asked") || lower.contains("didn't ask") || lower.contains("did not ask")
                || lower.contains("why this response") || lower.contains("not what i asked")
                || lower.contains("why did you say") || lower.contains("why did you mention")
                || lower.contains("not relevant") || lower.contains("irrelevant") || lower.contains("why are you talking");
    }

    private RouteDecision selectModel(Segment segment, String reason) {
        refreshInstalledModelsIfStale();

        String targetModel = segment.defaultModel();
        // Check if the primary specialized model is installed in Ollama
        boolean isInstalled = isModelInstalled(targetModel);

        if (isInstalled) {
            log.info("SegmentRouter: Routed to specialized {} [{}] - {}", segment, targetModel, reason);
            return new RouteDecision(segment, targetModel, segment.temperature(), reason, true);
        }

        // For MATH/REASONING: Check if jarvis-reasoner:latest is available as local specialized engine
        if (segment == Segment.MATH && isModelInstalled("jarvis-reasoner:latest")) {
            log.info("SegmentRouter: Routed to specialized cognitive engine [jarvis-reasoner:latest] - {}", reason);
            return new RouteDecision(segment, "jarvis-reasoner:latest", segment.temperature(), reason + " (using jarvis-reasoner:latest)", true);
        }

        // For CODE: If qwen2.5-coder is not installed, but deepseek-r1:1.5b is, leverage DeepSeek-R1 for code reasoning
        if (segment == Segment.CODE && isModelInstalled("deepseek-r1:1.5b")) {
            log.info("SegmentRouter: Routed code engineering to DeepSeek-R1 [deepseek-r1:1.5b] - {}", reason);
            return new RouteDecision(segment, "deepseek-r1:1.5b", segment.temperature(), reason + " (using DeepSeek-R1 for code intelligence)", true);
        }

        // Graceful fallback to default configured chat model (e.g. qwen3:1.7b)
        String fallbackModel = settings.chatModel();
        log.info("SegmentRouter: Specialized {} [{}] not installed. Falling back to default [{}]",
                segment, targetModel, fallbackModel);
        return new RouteDecision(segment, fallbackModel, segment.temperature(),
                reason + " (Specialized model " + targetModel + " not yet installed; using " + fallbackModel + ")", false);
    }

    private RouteDecision fallback(Segment segment, String reason) {
        return new RouteDecision(segment, settings.chatModel(), segment.temperature(), reason, false);
    }

    public boolean isModelInstalled(String modelName) {
        if (modelName == null || modelName.isBlank()) return false;
        if (installedModels.isEmpty()) {
            // Include default chat model by default
            installedModels.add(settings.chatModel());
        }
        String cleanTarget = modelName.trim().toLowerCase();
        String targetBase = cleanTarget.contains(":") ? cleanTarget.substring(0, cleanTarget.indexOf(":")) : cleanTarget;
        for (String installed : installedModels) {
            String cleanInst = installed.trim().toLowerCase();
            String instBase = cleanInst.contains(":") ? cleanInst.substring(0, cleanInst.indexOf(":")) : cleanInst;
            if (cleanInst.equals(cleanTarget) || cleanInst.startsWith(cleanTarget + ":") || cleanTarget.startsWith(cleanInst + ":") || instBase.equals(targetBase)) {
                return true;
            }
        }
        return false;
    }

    public List<SegmentInfo> getAvailableSegments() {
        refreshInstalledModelsIfStale();
        List<SegmentInfo> list = new ArrayList<>();
        for (Segment s : Segment.values()) {
            boolean installed = isModelInstalled(s.defaultModel());
            String active = installed ? s.defaultModel() : settings.chatModel();
            if (!installed && s == Segment.MATH && isModelInstalled("jarvis-reasoner:latest")) {
                active = "jarvis-reasoner:latest";
                installed = true;
            }
            list.add(new SegmentInfo(
                    s.name(),
                    s.description(),
                    s.defaultModel(),
                    active,
                    s.temperature(),
                    s.description(),
                    installed
            ));
        }
        return list;
    }

    private void refreshInstalledModelsIfStale() {
        long now = System.currentTimeMillis();
        if (now - lastModelCheckTime > 30_000) { // Check every 30 seconds
            refreshInstalledModelsAsync();
        }
    }

    public void refreshInstalledModelsAsync() {
        lastModelCheckTime = System.currentTimeMillis();
        http.get().uri("/api/tags").retrieve().bodyToMono(JsonNode.class)
                .doOnNext(body -> {
                    if (body.path("models").isArray()) {
                        Set<String> fresh = ConcurrentHashMap.newKeySet();
                        for (JsonNode m : body.path("models")) {
                            String name = m.path("name").asText("");
                            if (!name.isBlank()) fresh.add(name);
                        }
                        installedModels.clear();
                        installedModels.addAll(fresh);
                        log.debug("SegmentRouter: Updated installed models: {}", installedModels);
                    }
                })
                .doOnError(e -> log.debug("SegmentRouter: Failed to refresh installed models from Ollama: {}", e.getMessage()))
                .subscribe();
    }

    public Set<String> getInstalledModels() {
        return Set.copyOf(installedModels);
    }
}

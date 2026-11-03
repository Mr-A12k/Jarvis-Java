package com.jarvis.goal;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Intelligent Multi-Agent Goal Planner that decomposes user objectives into discrete,
 * verifiable action steps executed simultaneously by specialized parallel sub-agents:
 * - Researcher Agent (Web, documentation, market and fact retrieval)
 * - Coder Agent (Code architecture, implementation, refactoring and algorithms)
 * - Validator Agent (Syntax checks, unit test generation, boundary assertions, security audits)
 * - System Analyst Agent (Hardware metrics, workspace diagnostics, runtime environment audits)
 * - Lead Coordinator Agent (Deliverable synthesis and executive summaries)
 *
 * Features native support for both Tamil (தமிழ்) and English.
 */
@Component
public class GoalPlanner {

    private static final Pattern TAMIL_PATTERN = Pattern.compile("[\\u0B80-\\u0BFF]");

    public static boolean isTamil(String text) {
        if (text == null || text.isBlank()) return false;
        if (TAMIL_PATTERN.matcher(text).find()) return true;
        String lower = text.toLowerCase(Locale.ROOT).trim();
        if (lower.matches(".*\\b(?:what|how|why|who|is|are|the|explain|tell|laptop|price|compare|research|plan|in|to|english)\\b.*")) {
            return false;
        }
        return lower.contains("vanakkam") || lower.contains("nandri") || lower.contains("udhavi") || lower.contains("eppadi iruk");
    }

    public List<PlanStep> plan(String objective) {
        if (objective == null || objective.isBlank()) {
            return List.of(new PlanStep(
                    1, 1, "COORDINATOR", "Lead Coordinator Agent",
                    "Execute inquiry", "SYNTHESIZE", Map.of(), "PENDING", null, null, null, List.of(), 1
            ));
        }

        boolean tamil = isTamil(objective);
        String lower = objective.toLowerCase(Locale.ROOT);
        List<PlanStep> steps = new ArrayList<>();
        int index = 1;

        // 1. Coding / Software Engineering / Architecture / Feature Implementation intent
        if (lower.contains("code") || lower.contains("coding") || lower.contains("program")
                || lower.contains("implement") || lower.contains("build") || lower.contains("develop")
                || lower.contains("api") || lower.contains("rest") || lower.contains("python")
                || lower.contains("java") || lower.contains("javascript") || lower.contains("typescript")
                || lower.contains("react") || lower.contains("function") || lower.contains("script")
                || lower.contains("algorithm") || lower.contains("refactor") || lower.contains("debug")
                || lower.contains("test") || lower.contains("class") || lower.contains("controller")
                || lower.contains("service") || lower.contains("நிரல்") || lower.contains("செயலி")
                || lower.contains("மென்பொருள்") || lower.contains("உருவாக்கு")) {

            // Stage 1 (Parallel): Researcher Agent + Coder Agent
            steps.add(new PlanStep(
                    index++,
                    1,
                    "RESEARCHER",
                    tamil ? "ஆராய்ச்சி முகவர்" : "Researcher Agent",
                    tamil ? "தொழில்நுட்ப தேவைகள் மற்றும் சிறந்த கட்டமைப்பு நடைமுறைகளை ஆராய்தல்"
                          : "Research technical specifications and architectural best practices",
                    "RESEARCH_WEB",
                    Map.of("query", objective + " architecture best practices design pattern"),
                    "PENDING", null, null, null,
                    List.of(),
                    1
            ));

            steps.add(new PlanStep(
                    index++,
                    1,
                    "CODER",
                    tamil ? "நிரலாக்க முகவர்" : "Coder Agent",
                    tamil ? "முக்கிய நிரல் தொகுதிகள், வகுப்புகள் மற்றும் அல்காரிதம்களை உருவாக்குதல்"
                          : "Implement core classes, service logic and executable code modules",
                    "CODE_GENERATE",
                    Map.of("objective", objective, "spec", "production-ready implementation"),
                    "PENDING", null, null, null,
                    List.of(),
                    1
            ));

            // Stage 2 (Parallel): Validator Agent + System Analyst Agent
            steps.add(new PlanStep(
                    index++,
                    2,
                    "VALIDATOR",
                    tamil ? "சரிபார்ப்பு முகவர்" : "Validator Agent",
                    tamil ? "நிரல் தொடரியல், எல்லை வழக்குகள் மற்றும் தானியங்கி சோதனைகளை சரிபார்த்தல்"
                          : "Validate code syntax, boundary edge cases and generate unit test assertions",
                    "CODE_VALIDATE",
                    Map.of("target", "code-artifact", "objective", objective),
                    "PENDING", null, null, null,
                    List.of(1, 2),
                    2
            ));

            steps.add(new PlanStep(
                    index++,
                    2,
                    "SYSTEM_ANALYST",
                    tamil ? "கணினி ஆய்வாளர் முகவர்" : "System Analyst Agent",
                    tamil ? "இயக்க சூழல் வளங்கள் மற்றும் கணினி செயல்திறனை தணிக்கை செய்தல்"
                          : "Verify system runtime resource budget and workspace dependencies",
                    "SYSTEM_METRICS",
                    Map.of(),
                    "PENDING", null, null, null,
                    List.of(1, 2),
                    2
            ));

            // Stage 3: Lead Coordinator Agent Synthesis
            steps.add(new PlanStep(
                    index++,
                    3,
                    "COORDINATOR",
                    tamil ? "தலைமை ஒருங்கிணைப்பாளர்" : "Lead Coordinator Agent",
                    tamil ? "நிரல் செயல்படுத்தல், சோதனைகள் மற்றும் ஆவணங்களை ஒருங்கிணைத்து வழங்குதல்"
                          : "Synthesize full multi-agent engineering deliverable with verified code and tests",
                    "SYNTHESIZE",
                    Map.of(),
                    "PENDING", null, null, null,
                    List.of(3, 4),
                    3
            ));

            return steps;
        }

        // 2. Shopping / Product Analysis intent
        if (lower.contains("shopping") || lower.contains("buy") || lower.contains("product")
                || lower.contains("keyboard") || lower.contains("headphone") || lower.contains("mouse")
                || lower.contains("monitor") || lower.contains("laptop") || lower.contains("under $")
                || lower.contains("under ₹") || lower.contains("below ") || lower.contains("budget")
                || lower.contains("வாங்கு") || lower.contains("விலை") || lower.contains("பொருட்கள்")) {

            // Stage 1 (Parallel): Researcher Agent (Product specs + User reviews)
            steps.add(new PlanStep(
                    index++,
                    1,
                    "RESEARCHER",
                    tamil ? "ஆராய்ச்சி முகவர்" : "Researcher Agent",
                    tamil ? "பல்வேறு விற்பனையாளர்களிடம் தயாரிப்புகளை தேடி மதிப்பீடு செய்தல்"
                          : "Search and evaluate product candidates across retailers",
                    "SHOPPING_SEARCH",
                    Map.of("query", objective),
                    "PENDING", null, null, null,
                    List.of(),
                    1
            ));

            steps.add(new PlanStep(
                    index++,
                    1,
                    "RESEARCHER",
                    tamil ? "ஆராய்ச்சி முகவர்" : "Researcher Agent",
                    tamil ? "உண்மை பயனர்களின் மதிப்பீடுகள் மற்றும் நம்பகத்தன்மையை ஆராய்தல்"
                          : "Investigate real user sentiment and reliability reviews",
                    "RESEARCH_WEB",
                    Map.of("query", "user reviews pros and cons " + extractKeywords(objective)),
                    "PENDING", null, null, null,
                    List.of(),
                    1
            ));

            // Stage 2 (Parallel): Validator Agent
            steps.add(new PlanStep(
                    index++,
                    2,
                    "VALIDATOR",
                    tamil ? "சரிபார்ப்பு முகவர்" : "Validator Agent",
                    tamil ? "விலை வரம்புகள் மற்றும் விற்பனையாளர் நம்பகத்தன்மையை சரிபார்த்தல்"
                          : "Validate price authenticity, merchant ratings and budget bounds",
                    "FACT_VERIFY",
                    Map.of("criteria", objective),
                    "PENDING", null, null, null,
                    List.of(1, 2),
                    2
            ));

            // Stage 3: Lead Coordinator Agent Synthesis
            steps.add(new PlanStep(
                    index++,
                    3,
                    "COORDINATOR",
                    tamil ? "தலைமை ஒருங்கிணைப்பாளர்" : "Lead Coordinator Agent",
                    tamil ? "ஒப்பீட்டு அட்டவணை மற்றும் இறுதி பரிந்துரையை தொகுத்து வழங்குதல்"
                          : "Synthesize side-by-side comparison matrix and final recommendation",
                    "SYNTHESIZE",
                    Map.of(),
                    "PENDING", null, null, null,
                    List.of(3),
                    3
            ));

            return steps;
        }

        // 3. Workspace File / Memory Analysis intent
        if (lower.contains("file") || lower.contains("document") || lower.contains("notes")
                || lower.contains("workspace") || lower.contains("codebase") || lower.contains("project")
                || lower.contains("கோப்பு") || lower.contains("ஆவணம்") || lower.contains("திட்டம்")) {

            // Stage 1 (Parallel): Researcher Agent + Coder Agent
            steps.add(new PlanStep(
                    index++,
                    1,
                    "RESEARCHER",
                    tamil ? "ஆராய்ச்சி முகவர்" : "Researcher Agent",
                    tamil ? "பணிச்சூழல் ஆவணங்களிலிருந்து தகவல்களை மீட்டெடுத்தல்"
                          : "Search and retrieve relevant indexed workspace excerpts",
                    "FILE_SEARCH",
                    Map.of("query", objective),
                    "PENDING", null, null, null,
                    List.of(),
                    1
            ));

            steps.add(new PlanStep(
                    index++,
                    1,
                    "CODER",
                    tamil ? "நிரலாக்க முகவர்" : "Coder Agent",
                    tamil ? "பணிச்சூழல் கட்டமைப்பு மற்றும் கோப்பு சார்புகளை பகுப்பாய்வு செய்தல்"
                          : "Analyze codebase architecture, file references and code dependencies",
                    "CODE_GENERATE",
                    Map.of("query", objective),
                    "PENDING", null, null, null,
                    List.of(),
                    1
            ));

            // Stage 2: Validator Agent
            steps.add(new PlanStep(
                    index++,
                    2,
                    "VALIDATOR",
                    tamil ? "சரிபார்ப்பு முகவர்" : "Validator Agent",
                    tamil ? "கோப்பு இணைப்புகள் மற்றும் கட்டமைப்பு ஒருமைப்பாட்டை சரிபார்த்தல்"
                          : "Verify document integrity, cross-references and structure consistency",
                    "CODE_VALIDATE",
                    Map.of(),
                    "PENDING", null, null, null,
                    List.of(1, 2),
                    2
            ));

            // Stage 3: Lead Coordinator Agent Synthesis
            steps.add(new PlanStep(
                    index++,
                    3,
                    "COORDINATOR",
                    tamil ? "தலைமை ஒருங்கிணைப்பாளர்" : "Lead Coordinator Agent",
                    tamil ? "பணிச்சூழல் கண்டுபிடிப்புகள் மற்றும் முக்கிய குறிப்புகளை தொகுத்தல்"
                          : "Synthesize grounded workspace findings and action items",
                    "SYNTHESIZE",
                    Map.of(),
                    "PENDING", null, null, null,
                    List.of(3),
                    3
            ));

            return steps;
        }

        // 4. System Hardware & Telemetry Audit intent
        if (lower.contains("system") || lower.contains("health") || lower.contains("ram")
                || lower.contains("hardware") || lower.contains("metrics") || lower.contains("diagnostics")
                || lower.contains("audit") || lower.contains("கணினி") || lower.contains("வன்பொருள்")
                || lower.contains("நினைவகம்")) {

            // Stage 1 (Parallel): System Analyst Agent + Researcher Agent
            steps.add(new PlanStep(
                    index++,
                    1,
                    "SYSTEM_ANALYST",
                    tamil ? "கணினி ஆய்வாளர் முகவர்" : "System Analyst Agent",
                    tamil ? "கணினியின் வன்பொருள், நினைவகம் மற்றும் செயல்திறன் தரவுகளை சேகரித்தல்"
                          : "Collect real-time hardware, memory and compute snapshots",
                    "SYSTEM_METRICS",
                    Map.of(),
                    "PENDING", null, null, null,
                    List.of(),
                    1
            ));

            steps.add(new PlanStep(
                    index++,
                    1,
                    "RESEARCHER",
                    tamil ? "ஆராய்ச்சி முகவர்" : "Researcher Agent",
                    tamil ? "வன்பொருள் உகப்பாக்கம் மற்றும் செயல்திறன் வழிகாட்டுதல்களை மீட்டெடுத்தல்"
                          : "Retrieve hardware benchmark baselines and tuning guides",
                    "RESEARCH_WEB",
                    Map.of("query", "hardware memory optimization guidelines"),
                    "PENDING", null, null, null,
                    List.of(),
                    1
            ));

            // Stage 2: Validator Agent
            steps.add(new PlanStep(
                    index++,
                    2,
                    "VALIDATOR",
                    tamil ? "சரிபார்ப்பு முகவர்" : "Validator Agent",
                    tamil ? "பாதுகாப்பு வரம்புகள் மற்றும் வெப்பநிலை நிலைத்தன்மையை சரிபார்த்தல்"
                          : "Audit thermal thresholds, memory headroom and safety limits",
                    "SECURITY_AUDIT",
                    Map.of(),
                    "PENDING", null, null, null,
                    List.of(1, 2),
                    2
            ));

            // Stage 3: Lead Coordinator Agent Synthesis
            steps.add(new PlanStep(
                    index++,
                    3,
                    "COORDINATOR",
                    tamil ? "தலைமை ஒருங்கிணைப்பாளர்" : "Lead Coordinator Agent",
                    tamil ? "கணினி ஆரோக்கிய அறிக்கை மற்றும் உகப்பாக்க பரிந்துரைகளை உருவாக்குதல்"
                          : "Generate system health report and performance recommendations",
                    "SYNTHESIZE",
                    Map.of(),
                    "PENDING", null, null, null,
                    List.of(3),
                    3
            ));

            return steps;
        }

        // 5. Default: Multi-Stage Deep Research & Analysis
        // Stage 1 (Parallel): Primary Search + In-depth signals
        steps.add(new PlanStep(
                index++,
                1,
                "RESEARCHER",
                tamil ? "ஆராய்ச்சி முகவர்" : "Researcher Agent",
                tamil ? "முதன்மை தலைப்பு குறித்த விரிவான தகவல்களை இணையத்தில் தேடுதல்"
                      : "Conduct broad informational search on primary topic",
                "RESEARCH_WEB",
                Map.of("query", objective),
                "PENDING", null, null, null,
                List.of(),
                1
        ));

        steps.add(new PlanStep(
                index++,
                1,
                "RESEARCHER",
                tamil ? "ஆராய்ச்சி முகவர்" : "Researcher Agent",
                tamil ? "முக்கிய போக்குகள் மற்றும் ஆதாரங்களை ஆழமாக ஆராய்தல்"
                      : "Perform in-depth analysis on key trends and supporting evidence",
                "RESEARCH_WEB",
                Map.of("query", extractKeywords(objective) + " analysis insights"),
                "PENDING", null, null, null,
                List.of(),
                1
        ));

        // Stage 2: Validator Agent
        steps.add(new PlanStep(
                index++,
                2,
                "VALIDATOR",
                tamil ? "சரிபார்ப்பு முகவர்" : "Validator Agent",
                tamil ? "தகவல் மூலங்களின் நம்பகத்தன்மை மற்றும் சான்றுகளை சரிபார்த்தல்"
                      : "Cross-validate factual claims and verify source authenticity",
                "FACT_VERIFY",
                Map.of("topic", objective),
                "PENDING", null, null, null,
                List.of(1, 2),
                2
        ));

        // Stage 3: Lead Coordinator Agent Synthesis
        steps.add(new PlanStep(
                index++,
                3,
                "COORDINATOR",
                tamil ? "தலைமை ஒருங்கிணைப்பாளர்" : "Lead Coordinator Agent",
                tamil ? "விரிவான இறுதி ஆராய்ச்சி அறிக்கையை கட்டமைப்புடன் தொகுத்தல்"
                      : "Synthesize executive research deliverable with structured findings",
                "SYNTHESIZE",
                Map.of(),
                "PENDING", null, null, null,
                List.of(3),
                3
        ));

        return steps;
    }

    public String generateTitle(String objective) {
        if (objective == null || objective.isBlank()) return "Autonomous Task";
        String clean = objective
                .replaceAll("(?i)^(?:please|can you|i want to|help me|goal:|objective:|தயவுசெய்து|எனக்கு|இலக்கு:)\\s*", "")
                .trim();
        if (clean.length() <= 45) return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
        return Character.toUpperCase(clean.charAt(0)) + clean.substring(1, 42).trim() + "...";
    }

    private String extractKeywords(String text) {
        return text.replaceAll("(?i)\\b(?:recommend|find|analyze|search|look|for|the|best|and|with|under|below|what|is|how|to)\\b", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}

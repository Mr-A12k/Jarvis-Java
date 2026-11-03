package com.jarvis.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.AiProvider;
import com.jarvis.ai.ModelAdmission;
import com.jarvis.common.ApiErrors;
import com.jarvis.common.ApiException;
import com.jarvis.config.JarvisProperties;
import com.jarvis.goal.GoalPlanner;
import com.jarvis.goal.GoalService;
import com.jarvis.mcp.McpApprovalService;
import com.jarvis.shopping.ShoppingService;
import com.jarvis.system.SystemAppLauncher;
import com.jarvis.voice.VoiceCommandDispatcher;
import com.jarvis.voice.agent.VoiceAgentRegistry;
import com.jarvis.voice.tamil.TamilVoiceAgent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ChatService {
    private static final Logger LOG = LoggerFactory.getLogger(ChatService.class);
    private static final Pattern DIRECT_TOOL_PATTERN = Pattern.compile(
            "^(?:/tool|call tool|mcp call)\\s+([a-zA-Z0-9_.-]+)(?:\\s+(.*))?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "\\[TOOL_CALL:\\s*([a-zA-Z0-9_.-]+)\\s+(\\{.*?\\})\\]",
            Pattern.DOTALL
    );

    private final AiProvider provider;
    private final PromptFactory prompts;
    private final JarvisProperties settings;
    private final SystemAppLauncher appLauncher;
    private final ModelAdmission admission;
    private final McpApprovalService mcpService;
    private final ObjectMapper json;
    private final TamilVoiceAgent tamilAgent;
    private final ShoppingService shoppingService;
    private final VoiceAgentRegistry voiceRegistry;
    private final GoalService goalService;
    private final VoiceCommandDispatcher voiceCommandDispatcher;
    private final com.jarvis.ai.SegmentRouter segmentRouter;

    public ChatService(AiProvider provider, PromptFactory prompts, JarvisProperties settings) {
        this(provider, prompts, settings, new SystemAppLauncher(false));
    }

    public ChatService(AiProvider provider, PromptFactory prompts, JarvisProperties settings, SystemAppLauncher appLauncher) {
        this(provider, prompts, settings, appLauncher, new ModelAdmission(settings));
    }

    public ChatService(AiProvider provider, PromptFactory prompts, JarvisProperties settings, SystemAppLauncher appLauncher, ModelAdmission admission) {
        this(provider, prompts, settings, appLauncher, admission, null, new ObjectMapper(), null, null, null, null, null);
    }

    public ChatService(AiProvider provider, PromptFactory prompts, JarvisProperties settings,
                       SystemAppLauncher appLauncher, ModelAdmission admission,
                       McpApprovalService mcpService, ObjectMapper json) {
        this(provider, prompts, settings, appLauncher, admission, mcpService, json, null, null, null, null, null);
    }

    public ChatService(AiProvider provider, PromptFactory prompts, JarvisProperties settings,
                       SystemAppLauncher appLauncher, ModelAdmission admission,
                       McpApprovalService mcpService, ObjectMapper json, TamilVoiceAgent tamilAgent) {
        this(provider, prompts, settings, appLauncher, admission, mcpService, json, tamilAgent, null, null, null, null);
    }

    public ChatService(AiProvider provider, PromptFactory prompts, JarvisProperties settings,
                       SystemAppLauncher appLauncher, ModelAdmission admission,
                       McpApprovalService mcpService, ObjectMapper json,
                       TamilVoiceAgent tamilAgent, ShoppingService shoppingService) {
        this(provider, prompts, settings, appLauncher, admission, mcpService, json, tamilAgent, shoppingService, null, null, null, null);
    }

    public ChatService(AiProvider provider, PromptFactory prompts, JarvisProperties settings,
                       SystemAppLauncher appLauncher, ModelAdmission admission,
                       McpApprovalService mcpService, ObjectMapper json,
                       TamilVoiceAgent tamilAgent, ShoppingService shoppingService,
                       VoiceAgentRegistry voiceRegistry) {
        this(provider, prompts, settings, appLauncher, admission, mcpService, json, tamilAgent, shoppingService, voiceRegistry, null, null, null);
    }

    public ChatService(AiProvider provider, PromptFactory prompts, JarvisProperties settings,
                       SystemAppLauncher appLauncher, ModelAdmission admission,
                       McpApprovalService mcpService, ObjectMapper json,
                       TamilVoiceAgent tamilAgent, ShoppingService shoppingService,
                       VoiceAgentRegistry voiceRegistry, GoalService goalService,
                       VoiceCommandDispatcher voiceCommandDispatcher) {
        this(provider, prompts, settings, appLauncher, admission, mcpService, json, tamilAgent, shoppingService, voiceRegistry, goalService, voiceCommandDispatcher, null);
    }

    @Autowired
    public ChatService(AiProvider provider, PromptFactory prompts, JarvisProperties settings,
                       SystemAppLauncher appLauncher, ModelAdmission admission,
                       @Autowired(required = false) McpApprovalService mcpService,
                       @Autowired(required = false) ObjectMapper json,
                       @Autowired(required = false) TamilVoiceAgent tamilAgent,
                       @Autowired(required = false) ShoppingService shoppingService,
                       @Autowired(required = false) VoiceAgentRegistry voiceRegistry,
                       @Autowired(required = false) GoalService goalService,
                       @Autowired(required = false) VoiceCommandDispatcher voiceCommandDispatcher,
                       @Autowired(required = false) com.jarvis.ai.SegmentRouter segmentRouter) {
        this.provider = provider;
        this.prompts = prompts;
        this.settings = settings;
        this.appLauncher = appLauncher != null ? appLauncher : new SystemAppLauncher();
        this.admission = admission != null ? admission : new ModelAdmission(settings);
        this.mcpService = mcpService;
        this.json = json != null ? json : new ObjectMapper();
        this.tamilAgent = tamilAgent;
        this.shoppingService = shoppingService;
        this.voiceRegistry = voiceRegistry;
        this.goalService = goalService;
        this.voiceCommandDispatcher = voiceCommandDispatcher;
        this.segmentRouter = segmentRouter;
    }

    public Flux<String> stream(ChatRequest request) {
        return stream(request, "");
    }

    public Flux<String> stream(ChatRequest request, String webContext) {
        return stream(request, webContext, false);
    }

    public Flux<String> streamFiles(ChatRequest request, String evidence) {
        return stream(request, evidence, true);
    }

    private Flux<String> stream(ChatRequest request, String webContext, boolean files) {
        return Flux.defer(() -> {
            if (!files) {
                if (voiceCommandDispatcher != null) {
                    var cmdResult = voiceCommandDispatcher.dispatch(request.message());
                    if (cmdResult.isPresent()) return Flux.just(cmdResult.get());
                }
                if (tamilAgent != null) {
                    var directTamil = tamilAgent.answerDirectly(request.message());
                    if (directTamil.isPresent()) return Flux.just(directTamil.get());
                }
                var launchResult = appLauncher.launch(request.message());
                if (launchResult.isPresent()) return Flux.just(launchResult.get());
                var closeResult = appLauncher.close(request.message());
                if (closeResult.isPresent()) return Flux.just(closeResult.get());
                var fact = new LocalFacts().answer(request.message());
                if (fact.isPresent()) return Flux.just(fact.get());
                var calculated = new Arithmetic().answer(request.message());
                if (calculated.isPresent()) return Flux.just(calculated.get());
                var directTool = handleDirectToolCall(request.message());
                if (directTool.isPresent()) return Flux.just(directTool.get());
                var shoppingResult = handleShoppingRequest(request.message());
                if (shoppingResult.isPresent()) return Flux.just(shoppingResult.get());
                var goalResult = handleGoalRequest(request.message());
                if (goalResult.isPresent()) return Flux.just(goalResult.get());
            }
            var route = segmentRouter != null ? segmentRouter.route(request.message()) : null;
            if (route != null) {
                LOG.info("Segment routing: query='{}' -> segment={} model='{}'", 
                        request.message().length() > 60 ? request.message().substring(0, 57) + "..." : request.message(),
                        route.segment(), route.model());
            }
            var tools = (!files && mcpService != null) ? mcpService.discoverActiveTools() : List.<McpApprovalService.ToolInfo>of();
            var prompt = files ? prompts.buildFiles(request, webContext, route) : prompts.build(request, webContext, tools, route);
            var requestId = UUID.randomUUID();
            long start = System.nanoTime();
            var characters = new AtomicInteger();
            return Flux.using(admission::chat, permit -> provider.stream(prompt)
                .takeUntilOther(Mono.delay(settings.responseTimeout()).flatMap(t -> Mono.error(new TimeoutException())))
                .handle((String delta, reactor.core.publisher.SynchronousSink<String> sink) -> {
                    if (characters.addAndGet(delta.length()) > settings.outputTokens() * 32)
                        sink.error(new ApiException(HttpStatus.BAD_GATEWAY, "OUTPUT_LIMIT", "The model exceeded its output budget."));
                    else sink.next(delta);
                })
                .switchIfEmpty(Flux.error(new ApiException(HttpStatus.BAD_GATEWAY, "EMPTY_RESPONSE", "The model returned no text. Retry with a shorter request."))),
                ModelAdmission.Lease::close, true)
                .onErrorMap(ApiErrors::modelError)
                .doFinally(signal -> LOG.info("inference request={} outcome={} durationMs={} outputChars={}", requestId, signal, (System.nanoTime()-start)/1_000_000, characters.get()));
        });
    }

    public Mono<String> complete(ChatRequest request) {
        if (mcpService == null) {
            return stream(request).collect(StringBuilder::new, StringBuilder::append).map(StringBuilder::toString);
        }
        return executeToolLoop(request, 0);
    }

    private Mono<String> executeToolLoop(ChatRequest request, int iteration) {
        return stream(request).collect(StringBuilder::new, StringBuilder::append).map(StringBuilder::toString)
                .flatMap(text -> {
                    if (iteration >= 2) {
                        return Mono.just(text);
                    }
                    Matcher matcher = TOOL_CALL_PATTERN.matcher(text);
                    if (!matcher.find()) {
                        return Mono.just(text);
                    }

                    String toolName = matcher.group(1);
                    String rawArgs = matcher.group(2);
                    Map<String, Object> arguments = Map.of();
                    try {
                        arguments = json.readValue(rawArgs, new TypeReference<Map<String, Object>>() {});
                    } catch (Exception e) {
                        return Mono.just("Tool call syntax error: invalid arguments JSON: " + e.getMessage());
                    }

                    try {
                        var execution = mcpService.requestToolExecution(toolName, arguments);
                        if (execution.approvalRequired()) {
                            return Mono.just("Action requires human approval [challengeId: " + execution.challengeId() + "] for tool '"
                                    + toolName + "'. Please review and approve or reject the action.");
                        }

                        String observation = "Tool '" + toolName + "' observation: " + formatToolResult(execution.result());
                        var updatedHistory = new ArrayList<>(request.history());
                        updatedHistory.add(new ChatRequest.Turn(ChatRequest.Role.assistant, text));
                        updatedHistory.add(new ChatRequest.Turn(ChatRequest.Role.user, observation));

                        ChatRequest followUp = new ChatRequest(
                                "Please answer the user's question using the tool observation above.",
                                updatedHistory
                        );
                        return executeToolLoop(followUp, iteration + 1);
                    } catch (ApiException e) {
                        return Mono.just("Tool '" + toolName + "' execution error [" + e.code() + "]: " + e.getMessage());
                    } catch (Exception e) {
                        return Mono.just("Tool execution failed: " + e.getMessage());
                    }
                });
    }

    Optional<String> handleDirectToolCall(String message) {
        if (mcpService == null || message == null || message.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = DIRECT_TOOL_PATTERN.matcher(message.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }

        String toolName = matcher.group(1);
        String argsRaw = matcher.group(2);
        Map<String, Object> arguments = Map.of();
        if (argsRaw != null && !argsRaw.isBlank()) {
            try {
                arguments = json.readValue(argsRaw.trim(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                return Optional.of("Invalid JSON arguments for tool '" + toolName + "': " + e.getMessage());
            }
        }

        try {
            var execution = mcpService.requestToolExecution(toolName, arguments);
            if (execution.approvalRequired()) {
                return Optional.of("Action requires human approval [challengeId: " + execution.challengeId() + "] for tool '"
                        + toolName + "'. Please review and approve or reject the action.");
            }
            return Optional.of("Tool '" + toolName + "' executed:\n" + formatToolResult(execution.result()));
        } catch (ApiException e) {
            return Optional.of("Tool '" + toolName + "' error [" + e.code() + "]: " + e.getMessage());
        } catch (Exception e) {
            return Optional.of("Tool execution failed: " + e.getMessage());
        }
    }

    private String formatToolResult(Object result) {
        if (result == null) return "Success (no output)";
        if (result instanceof CharSequence) return result.toString();
        try {
            return json.writeValueAsString(result);
        } catch (Exception e) {
            return result.toString();
        }
    }

    private Optional<String> handleShoppingRequest(String message) {
        if (message == null || shoppingService == null) return Optional.empty();
        String trimmed = message.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        boolean isExplicit = lower.startsWith("/shop") || lower.startsWith("shop ") || lower.startsWith("shopping ");
        boolean isProductComparison = (lower.startsWith("compare ") || lower.contains("recommend "))
                && (lower.contains("budget") || lower.contains("$") || lower.contains("usd") || lower.contains("inr") || lower.contains("price") || lower.contains("under "));

        if (isExplicit || isProductComparison) {
            var criteria = shoppingService.parseCriteria(trimmed);
            var items = shoppingService.searchProducts(criteria);
            var matrix = shoppingService.compare(items, criteria);
            return Optional.of(shoppingService.formatMarkdownTable(matrix));
        }
        return Optional.empty();
    }

    private Optional<String> handleGoalRequest(String message) {
        if (goalService == null || message == null) return Optional.empty();
        String trimmed = message.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String objective = null;

        if (lower.startsWith("/goal ") || lower.startsWith("/plan ")) {
            objective = trimmed.substring(6).trim();
        } else if (lower.startsWith("goal:") || lower.startsWith("objective:")) {
            objective = trimmed.substring(trimmed.indexOf(':') + 1).trim();
        } else if (lower.startsWith("இலக்கு:")) {
            objective = trimmed.substring(trimmed.indexOf(':') + 1).trim();
        }

        if (objective != null && !objective.isBlank()) {
            var run = goalService.startGoal(objective);
            boolean tamil = GoalPlanner.isTamil(objective);
            StringBuilder sb = new StringBuilder();
            if (tamil) {
                sb.append("<think>தானியங்கி பல-படி இலக்கு திட்டத்தை தொடங்கியுள்ளேன். படிகள் வரிசையாக இயக்கப்படுகின்றன.</think>\n");
                sb.append("🎯 **தானியங்கி இலக்கு தொடங்கப்பட்டது (Autonomous Goal Initiated)**\n\n");
                sb.append("- **இலக்கு எண் (ID):** `").append(run.id()).append("`\n");
                sb.append("- **தலைப்பு:** ").append(run.title()).append("\n");
                sb.append("- **திட்டமிடப்பட்ட நிலைகள்:** ").append(run.steps().size()).append(" படிகள்\n\n");
                sb.append("### 📋 செயல்படுத்தப்படும் நிலைகள்:\n");
                for (var s : run.steps()) {
                    sb.append(s.stepIndex()).append(". **").append(s.title()).append("** (`").append(s.actionType()).append("`)\n");
                }
                sb.append("\n*இலக்கின் நேரலை முன்னேற்றத்தை இடது மெனுவில் உள்ள **Goal planner** திரையில் கண்காணிக்கலாம்.*");
            } else {
                sb.append("<think>Initiated multi-step autonomous goal planner. Decomposed objective into verified milestones.</think>\n");
                sb.append("🎯 **Autonomous Goal Initiated:** `").append(run.id()).append("`\n\n");
                sb.append("**Title:** ").append(run.title()).append("\n");
                sb.append("**Phases Scheduled:** ").append(run.steps().size()).append(" milestones\n\n");
                sb.append("### 📋 Action Plan:\n");
                for (var s : run.steps()) {
                    sb.append(s.stepIndex()).append(". **").append(s.title()).append("** (`").append(s.actionType()).append("`)\n");
                }
                sb.append("\n*You can track live execution, step outputs, and executive reports in the **Goal Planner** panel.*");
            }
            return Optional.of(sb.toString());
        }
        return Optional.empty();
    }
}

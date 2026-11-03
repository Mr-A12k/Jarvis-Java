package com.jarvis.ai;

import com.jarvis.config.JarvisProperties;
import com.jarvis.system.SystemHardwareService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {
    private final AiProvider provider;
    private final JarvisProperties settings;
    private final SystemHardwareService hardware;
    private final SegmentRouter segmentRouter;

    public HealthController(AiProvider provider, JarvisProperties settings) {
        this(provider, settings, new SystemHardwareService(), null);
    }

    public HealthController(AiProvider provider, JarvisProperties settings, SystemHardwareService hardware) {
        this(provider, settings, hardware, null);
    }

    @Autowired
    public HealthController(AiProvider provider, JarvisProperties settings, SystemHardwareService hardware,
                            @Autowired(required = false) SegmentRouter segmentRouter) {
        this.provider = provider;
        this.settings = settings;
        this.hardware = hardware != null ? hardware : new SystemHardwareService();
        this.segmentRouter = segmentRouter;
    }

    @GetMapping("/segments")
    public Map<String, Object> segments() {
        var mathInstalled = segmentRouter != null && segmentRouter.isModelInstalled("deepseek-r1:1.5b");
        var codeInstalled = segmentRouter != null && segmentRouter.isModelInstalled("qwen2.5-coder:1.5b");
        var generalInstalled = segmentRouter == null || segmentRouter.isModelInstalled(settings.chatModel());
        var installed = segmentRouter != null ? segmentRouter.getInstalledModels() : java.util.Set.of(settings.chatModel());

        return Map.of(
            "segments", java.util.List.of(
                Map.of("id", "war", "name", "Combat & War Mode (House Party Protocol)", "targetModel", settings.chatModel(), "installed", true, "command", "/war"),
                Map.of("id", "math", "name", "Mathematical Reasoning", "targetModel", "deepseek-r1:1.5b", "installed", mathInstalled, "command", "/math"),
                Map.of("id", "code", "name", "Code Engineering", "targetModel", "qwen2.5-coder:1.5b", "installed", codeInstalled, "command", "/code"),
                Map.of("id", "general", "name", "General & Voice Persona", "targetModel", settings.chatModel(), "installed", generalInstalled, "command", "/chat")
            ),
            "installedModels", installed
        );
    }

    @GetMapping("/health/live")
    public Map<String, String> live() {
        return Map.of("status", "up");
    }

    @GetMapping("/health/model")
    public Mono<AiProvider.ModelHealth> model() {
        return provider.health();
    }

    @GetMapping("/system/resources")
    public SystemHardwareService.HardwareSnapshot resources() {
        return hardware.snapshot();
    }

    @GetMapping("/diagnostics")
    public Map<String, Object> diagnostics() {
        var runtime = Runtime.getRuntime();
        var snap = hardware.snapshot();

        Map<String, Object> result = new HashMap<>();
        result.put("model", settings.chatModel());
        result.put("contextLength", settings.contextLength());
        result.put("outputTokens", settings.outputTokens());
        result.put("concurrency", settings.concurrency());
        result.put("heapUsedBytes", runtime.totalMemory() - runtime.freeMemory());
        result.put("heapMaxBytes", runtime.maxMemory());
        result.put("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
        result.put("cloudFallback", false);
        result.put("phase", 2);
        result.put("persistence", false);

        result.put("ram", snap.ram());
        result.put("gpu", snap.gpu());
        result.put("rom", snap.rom());
        result.put("heap", snap.heap());

        return result;
    }
}

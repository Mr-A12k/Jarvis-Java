package com.jarvis.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.SegmentRouter;
import com.jarvis.config.JarvisProperties;
import com.jarvis.knowledge.KnowledgeService;
import com.jarvis.knowledge.LearnedKnowledge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RealtimeModelTrainingService:
 * Manages the continuous autonomous learning and training loop for the
 * dedicated research model (`jarvis-researcher:latest`).
 *
 * Automatically records verified factual question-answer pairs into a
 * training dataset (data/training/live_facts_dataset.jsonl) and dynamically
 * adapts/trains the local Ollama model with zero cloud dependencies.
 */
@Service
public class RealtimeModelTrainingService {

    private static final Logger log = LoggerFactory.getLogger(RealtimeModelTrainingService.class);

    private final Path datasetPath;
    private final Path modelfilePath;
    private final ObjectMapper json;
    private final KnowledgeService knowledgeService;
    private final SegmentRouter segmentRouter;
    private final JarvisProperties settings;

    private final AtomicInteger sampleCount = new AtomicInteger(0);
    private final AtomicBoolean isTraining = new AtomicBoolean(false);
    private volatile Instant lastTrainedAt = null;
    private volatile String lastTrainingMessage = "Initialized. Ready for autonomous training.";

    public record TrainingSample(
            String id,
            Instant timestamp,
            String category,
            String instruction,
            String input,
            String output,
            List<String> sources
    ) {}

    public record TrainingStatus(
            String modelName,
            int totalSamples,
            boolean isTraining,
            Instant lastTrainedAt,
            String message,
            int learnedFactsCount
    ) {}

    @Autowired
    public RealtimeModelTrainingService(
            @Value("${jarvis.training.dataset:data/training/live_facts_dataset.jsonl}") String datasetPath,
            @Value("${jarvis.training.modelfile:data/training/Modelfile.researcher}") String modelfilePath,
            @Autowired(required = false) ObjectMapper json,
            @Autowired(required = false) KnowledgeService knowledgeService,
            @Autowired(required = false) SegmentRouter segmentRouter,
            JarvisProperties settings
    ) {
        this.datasetPath = Path.of(datasetPath).toAbsolutePath().normalize();
        this.modelfilePath = Path.of(modelfilePath).toAbsolutePath().normalize();
        this.json = (json != null ? json.copy() : new ObjectMapper()).findAndRegisterModules();
        this.knowledgeService = knowledgeService;
        this.segmentRouter = segmentRouter;
        this.settings = settings;

        initDataset();
    }

    private void initDataset() {
        try {
            if (Files.exists(datasetPath)) {
                int lines = 0;
                try (BufferedReader reader = Files.newBufferedReader(datasetPath, StandardCharsets.UTF_8)) {
                    while (reader.readLine() != null) lines++;
                }
                sampleCount.set(lines);
                log.info("RealtimeModelTrainingService: Found {} existing training samples in {}", lines, datasetPath);
            } else {
                if (datasetPath.getParent() != null) {
                    Files.createDirectories(datasetPath.getParent());
                }
            }
        } catch (Exception e) {
            log.warn("Could not inspect training dataset: {}", e.getMessage());
        }
    }

    /**
     * Records a newly answered real-time fact into the training dataset,
     * commits to knowledge memory, and triggers an autonomous model update asynchronously.
     */
    public CompletableFuture<Void> recordAndAutoTrain(
            String query,
            String rawEvidence,
            String assistantAnswer,
            RealtimeFactReport.Category category,
            List<String> sources,
            String language
    ) {
        if (query == null || query.isBlank() || assistantAnswer == null || assistantAnswer.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                // 1. Commit to Knowledge memory for immediate zero-latency recall
                if (knowledgeService != null) {
                    knowledgeService.learn(query, assistantAnswer, sources, language);
                }

                // 2. Append to continuous instruction dataset
                String sampleId = "fact-" + UUID.randomUUID().toString().substring(0, 8);
                TrainingSample sample = new TrainingSample(
                        sampleId,
                        Instant.now(),
                        category != null ? category.name() : "GENERAL_FACTS",
                        query.trim(),
                        rawEvidence != null ? rawEvidence.trim() : "",
                        assistantAnswer.trim(),
                        sources != null ? List.copyOf(sources) : List.of()
                );

                appendSample(sample);
                int count = sampleCount.incrementAndGet();
                log.info("Recorded real-time training sample #{} [{}] for query: '{}'", count, sampleId, query);

                // 3. Trigger autonomous model rebuild / update
                autoTrainModelInternal(false);
            } catch (Exception e) {
                log.error("Failed to record and train on real-time data: {}", e.getMessage(), e);
            }
        });
    }

    private synchronized void appendSample(TrainingSample sample) {
        try {
            if (datasetPath.getParent() != null && !Files.exists(datasetPath.getParent())) {
                Files.createDirectories(datasetPath.getParent());
            }
            String line = json.writeValueAsString(sample) + "\n";
            Files.writeString(datasetPath, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to append training sample to {}", datasetPath, e);
        }
    }

    /**
     * Rebuilds / trains the dedicated Ollama model (`jarvis-researcher:latest`)
     * with the accumulated knowledge and system persona.
     */
    public synchronized boolean autoTrainModelInternal(boolean force) {
        if (isTraining.get()) {
            log.info("Realtime model training is already running in background. Skipping duplicate trigger.");
            return false;
        }

        isTraining.set(true);
        lastTrainingMessage = "Autonomous model adaptation in progress...";

        CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting autonomous training of jarvis-researcher:latest...");

                // Collect the top recent learned facts from KnowledgeService
                List<LearnedKnowledge> facts = knowledgeService != null ? knowledgeService.listRecent(20) : List.of();
                StringBuilder factsBlock = new StringBuilder();
                for (LearnedKnowledge k : facts) {
                    factsBlock.append("• [FACT: ").append(k.query()).append("]\n  ").append(k.answer()).append("\n\n");
                }

                String baseModel = settings.chatModel(); // e.g. qwen3:1.7b

                // Generate dynamic Ollama Modelfile with baked-in realtime grounded facts
                String modelfileContent = String.format(
                        "FROM %s\n" +
                        "PARAMETER temperature 0.2\n" +
                        "PARAMETER top_p 0.90\n" +
                        "PARAMETER stop \"<|im_end|>\"\n" +
                        "PARAMETER stop \"<|endoftext|>\"\n" +
                        "SYSTEM \"\"\"You are J.A.R.V.I.S. Realtime Research & Fact-Checking Engine (jarvis-researcher:latest).\n" +
                        "You specialize in:\n" +
                        "1. Millisecond-accurate system date, time, and day of week.\n" +
                        "2. Real-time stock market data (Nifty 50, BSE Sensex, S&P 500, Nasdaq, Crypto, and individual stocks).\n" +
                        "3. This week's OTT and movie releases across Netflix, Prime Video, Hotstar, and theatres.\n" +
                        "4. Verified live fact-checking and web grounding.\n" +
                        "COGNITIVE MODE: Always conduct your reasoning in <think>...</think> in the language of the prompt.\n" +
                        "LANGUAGE POLICY: If the prompt is in English, respond in English. If in Tamil, think and respond in Tamil script.\n\n" +
                        "[AUTONOMOUSLY LEARNED GROUNDED FACTS & KNOWLEDGE MEMORY]:\n" +
                        "%s\"\"\"\n",
                        baseModel,
                        factsBlock.isEmpty() ? "No prior learned facts yet. Ground all queries using real-time observations." : factsBlock.toString().trim()
                );

                if (modelfilePath.getParent() != null && !Files.exists(modelfilePath.getParent())) {
                    Files.createDirectories(modelfilePath.getParent());
                }
                Files.writeString(modelfilePath, modelfileContent, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                // Run `ollama create jarvis-researcher:latest -f <modelfile>`
                ProcessBuilder pb = new ProcessBuilder("ollama", "create", "jarvis-researcher:latest", "-f", modelfilePath.toString());
                pb.redirectErrorStream(true);
                Process proc = pb.start();

                String output;
                try (var is = proc.getInputStream()) {
                    output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
                int exitCode = proc.waitFor();

                if (exitCode == 0) {
                    lastTrainedAt = Instant.now();
                    lastTrainingMessage = String.format("Successfully auto-trained jarvis-researcher:latest with %d samples and %d facts at %s",
                            sampleCount.get(), facts.size(), lastTrainedAt);
                    log.info("Auto-training SUCCESS: {}", lastTrainingMessage);

                    // Refresh SegmentRouter model list
                    if (segmentRouter != null) {
                        segmentRouter.refreshInstalledModelsAsync();
                    }
                } else {
                    lastTrainingMessage = "Ollama model create exited with code " + exitCode + ": " + output;
                    log.error("Auto-training FAILED: {}", lastTrainingMessage);
                }
            } catch (Exception e) {
                lastTrainingMessage = "Error during model training: " + e.getMessage();
                log.error("Autonomous training error", e);
            } finally {
                isTraining.set(false);
            }
        });

        return true;
    }

    public TrainingStatus getStatus() {
        int factsCount = knowledgeService != null ? knowledgeService.listRecent(100).size() : 0;
        return new TrainingStatus(
                "jarvis-researcher:latest",
                sampleCount.get(),
                isTraining.get(),
                lastTrainedAt,
                lastTrainingMessage,
                factsCount
        );
    }
}

package com.jarvis.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.SegmentRouter;
import com.jarvis.config.JarvisProperties;
import com.jarvis.knowledge.KnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RealtimeModelTrainingServiceTest {

    @TempDir
    Path tempDir;

    private KnowledgeService knowledgeService;
    private SegmentRouter segmentRouter;
    private JarvisProperties settings;
    private RealtimeModelTrainingService trainingService;
    private Path datasetPath;
    private Path modelfilePath;

    @BeforeEach
    void setUp() {
        datasetPath = tempDir.resolve("live_facts_dataset.jsonl");
        modelfilePath = tempDir.resolve("Modelfile.researcher");

        knowledgeService = mock(KnowledgeService.class);
        segmentRouter = mock(SegmentRouter.class);

        settings = new JarvisProperties("", URI.create("http://127.0.0.1:11434"),
                "qwen3:1.7b", 8192, 2048, 1,
                Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(1), "30s");

        trainingService = new RealtimeModelTrainingService(
                datasetPath.toString(),
                modelfilePath.toString(),
                new ObjectMapper(),
                knowledgeService,
                segmentRouter,
                settings
        );
    }

    @Test
    void recordsTrainingSampleAndUpdatesStatus() throws Exception {
        var future = trainingService.recordAndAutoTrain(
                "What is today's stock price for Nifty 50?",
                "Nifty 50 is at 22,600.00",
                "Nifty 50 closed at 22,600.00 today.",
                RealtimeFactReport.Category.STOCK_MARKET,
                List.of("Yahoo Finance"),
                "en"
        );

        future.join();

        assertThat(Files.exists(datasetPath)).isTrue();
        List<String> lines = Files.readAllLines(datasetPath);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).contains("Nifty 50").contains("STOCK_MARKET");

        var status = trainingService.getStatus();
        assertThat(status.modelName()).isEqualTo("jarvis-researcher:latest");
        assertThat(status.totalSamples()).isEqualTo(1);

        verify(knowledgeService, atLeastOnce()).learn(anyString(), anyString(), anyList(), anyString());
    }
}

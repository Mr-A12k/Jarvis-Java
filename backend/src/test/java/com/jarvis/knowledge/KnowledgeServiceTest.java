package com.jarvis.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeServiceTest {

    @TempDir
    Path tempDir;

    private Path storageFile;
    private KnowledgeService knowledgeService;

    @BeforeEach
    void setUp() {
        storageFile = tempDir.resolve("learned-knowledge.jsonl");
        knowledgeService = new KnowledgeService(storageFile.toString(), new ObjectMapper(), null);
    }

    @Test
    void detectsDisputeOrCorrectionQueries() {
        assertThat(KnowledgeService.isDisputeOrCorrection("you are wrong")).isTrue();
        assertThat(KnowledgeService.isDisputeOrCorrection("you're wrong search it in web and tell me the correct ans")).isTrue();
        assertThat(KnowledgeService.isDisputeOrCorrection("are you sure is this the goald price in india?? its wrong...")).isTrue();
        assertThat(KnowledgeService.isDisputeOrCorrection("this is incorrect")).isTrue();
        assertThat(KnowledgeService.isDisputeOrCorrection("wrong ans fix it")).isTrue();
        assertThat(KnowledgeService.isDisputeOrCorrection("இது தவறு")).isTrue();
        assertThat(KnowledgeService.isDisputeOrCorrection("பொய்")).isTrue();

        // Normal queries are not disputes
        assertThat(KnowledgeService.isDisputeOrCorrection("what is photosynthesis")).isFalse();
        assertThat(KnowledgeService.isDisputeOrCorrection("capital of France")).isFalse();
    }

    @Test
    void detectsVolatileLiveTopics() {
        assertThat(KnowledgeService.isVolatileLiveTopic("gold price today")).isTrue();
        assertThat(KnowledgeService.isVolatileLiveTopic("what iss the goald price today")).isTrue();
        assertThat(KnowledgeService.isVolatileLiveTopic("silver rate in india")).isTrue();
        assertThat(KnowledgeService.isVolatileLiveTopic("crude oil price")).isTrue();
        assertThat(KnowledgeService.isVolatileLiveTopic("nifty 50 share price")).isTrue();
        assertThat(KnowledgeService.isVolatileLiveTopic("weather in Chennai")).isTrue();
        assertThat(KnowledgeService.isVolatileLiveTopic("தங்கம் விலை நிலவரம்")).isTrue();

        // Static topics are not volatile
        assertThat(KnowledgeService.isVolatileLiveTopic("who is Albert Einstein")).isFalse();
        assertThat(KnowledgeService.isVolatileLiveTopic("how does JVM garbage collection work")).isFalse();
        assertThat(KnowledgeService.isVolatileLiveTopic("formula for water")).isFalse();
    }

    @Test
    void skipsLearningDisputesAndVolatileTopics() {
        // Disputed query must not be learned
        LearnedKnowledge disputeResult = knowledgeService.learn(
                "you are wrong tell me the correct answer",
                "Here is the corrected info",
                List.of("https://example.com"),
                "en"
        );
        assertThat(disputeResult).isNull();

        // Volatile topic must not be committed to static memory
        LearnedKnowledge goldResult = knowledgeService.learn(
                "what is the gold price today",
                "Gold price is 13,000 INR per gram",
                List.of("https://example.com"),
                "en"
        );
        assertThat(goldResult).isNull();

        // Legitimate static fact should be learned
        LearnedKnowledge fact = knowledgeService.learn(
                "who developed Java",
                "Java was originally developed by James Gosling at Sun Microsystems.",
                List.of("https://oracle.com"),
                "en"
        );
        assertThat(fact).isNotNull();
        assertThat(fact.query()).isEqualTo("who developed Java");
    }

    @Test
    void recallsLegitimateFactsAndBypassesVolatileOrDispute() {
        knowledgeService.learn(
                "capital of Japan",
                "The capital of Japan is Tokyo.",
                List.of("https://wikipedia.org"),
                "en"
        );

        // Recall should find the match
        Optional<LearnedKnowledge> recalled = knowledgeService.recall("what is the capital of Japan");
        assertThat(recalled).isPresent();
        assertThat(recalled.get().answer()).contains("Tokyo");

        // Volatile query recall should always be empty even if somehow asked
        Optional<LearnedKnowledge> volatileRecall = knowledgeService.recall("what is the gold price today");
        assertThat(volatileRecall).isEmpty();

        // Dispute query should bypass recall and invalidate related items
        Optional<LearnedKnowledge> disputeRecall = knowledgeService.recall("you are wrong about capital of Japan");
        assertThat(disputeRecall).isEmpty();
    }

    @Test
    void invalidatesKnowledgeProperly() {
        knowledgeService.learn(
                "distance from Earth to Moon",
                "The average distance from Earth to Moon is about 384,400 km.",
                List.of("https://nasa.gov"),
                "en"
        );

        assertThat(knowledgeService.recall("distance from Earth to Moon")).isPresent();

        // Invalidate
        boolean removed = knowledgeService.invalidate("Earth to Moon distance is wrong");
        assertThat(removed).isTrue();

        assertThat(knowledgeService.recall("distance from Earth to Moon")).isEmpty();
    }
}

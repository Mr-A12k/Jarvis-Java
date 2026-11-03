package com.jarvis.shopping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ShoppingControllerTest {

    private ShoppingController controller;

    @BeforeEach
    void setUp() {
        controller = new ShoppingController(new ShoppingService(null));
    }

    @Test
    void parseCriteriaEndpoint() {
        StepVerifier.create(controller.parseCriteria(new ShoppingController.CriteriaRequest("Logitech wireless mouse below $50")))
                .assertNext(criteria -> {
                    assertThat(criteria.query()).containsIgnoringCase("mouse");
                    assertThat(criteria.maxPrice()).isEqualByComparingTo(new BigDecimal("50"));
                    assertThat(criteria.preferredBrands()).contains("Logitech");
                })
                .verifyComplete();
    }

    @Test
    void searchAndCompareEndpoints() {
        var criteria = new ShoppingCriteria("headphones", BigDecimal.valueOf(150), "USD", List.of("anc"), List.of(), "best_value");

        StepVerifier.create(controller.search(criteria))
                .assertNext(items -> {
                    assertThat(items).isNotEmpty();
                    assertThat(items.getFirst().price()).isLessThanOrEqualTo(BigDecimal.valueOf(175));
                })
                .verifyComplete();

        StepVerifier.create(controller.compare(new ShoppingController.CompareRequest(criteria, null)))
                .assertNext(matrix -> {
                    assertThat(matrix.items()).isNotEmpty();
                    assertThat(matrix.bestValueId()).isNotNull();
                    assertThat(matrix.recommendationSummary()).isNotEmpty();
                })
                .verifyComplete();
    }
}

package com.jarvis.shopping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ShoppingServiceTest {

    private ShoppingService shoppingService;

    @BeforeEach
    void setUp() {
        shoppingService = new ShoppingService(null);
    }

    @Test
    void parsesNaturalLanguageShoppingCriteria() {
        var criteria = shoppingService.parseCriteria("Recommend a wireless mechanical keyboard under $100 with bluetooth");

        assertThat(criteria.query()).containsIgnoringCase("keyboard");
        assertThat(criteria.maxPrice()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(criteria.currency()).isEqualTo("USD");
        assertThat(criteria.requiredFeatures()).contains("wireless", "bluetooth", "mechanical");
    }

    @Test
    void parsesInrCurrencyAndBudget() {
        var criteria = shoppingService.parseCriteria("Find noise cancelling headphones below 15000 inr with Sony brand");

        assertThat(criteria.maxPrice()).isEqualByComparingTo(new BigDecimal("15000"));
        assertThat(criteria.currency()).isEqualTo("INR");
        assertThat(criteria.preferredBrands()).contains("Sony");
    }

    @Test
    void searchesProductsWithinBudgetAndRanksBestValue() {
        var criteria = new ShoppingCriteria("mechanical keyboard", BigDecimal.valueOf(100.00), "USD", List.of("wireless"), List.of(), "best_value");
        var items = shoppingService.searchProducts(criteria);

        assertThat(items).isNotEmpty();
        assertThat(items).allMatch(item -> item.price().compareTo(BigDecimal.valueOf(115.00)) <= 0);
        // Best value candidate exists
        assertThat(items.stream().anyMatch(i -> i.title().contains("Keychron") || i.title().contains("Redragon"))).isTrue();
    }

    @Test
    void generatesComparisonMatrixWithAccurateBadgesAndHandoffLinks() {
        var item1 = new ProductItem("kb-1", "Keychron K2", BigDecimal.valueOf(79.99), "USD", 4.7, 4000, "Amazon", "https://amazon.com/k2", Map.of("Switch", "Brown", "Battery", "240h"), List.of("Wireless"), true);
        var item2 = new ProductItem("kb-2", "Redragon K552", BigDecimal.valueOf(39.99), "USD", 4.4, 30000, "Amazon", "https://amazon.com/k552", Map.of("Switch", "Blue"), List.of("Wired"), true);

        var criteria = new ShoppingCriteria("keyboard", BigDecimal.valueOf(100.00), "USD", List.of(), List.of(), "best_value");
        var matrix = shoppingService.compare(List.of(item1, item2), criteria);

        assertThat(matrix.items()).hasSize(2);
        assertThat(matrix.comparedSpecs()).contains("Switch", "Battery");
        assertThat(matrix.topRatedId()).isEqualTo("kb-1"); // 4.7 > 4.4
        assertThat(matrix.bestValueId()).isIn("kb-1", "kb-2");
        assertThat(matrix.recommendationSummary()).contains("Keychron K2");

        String markdown = shoppingService.formatMarkdownTable(matrix);
        assertThat(markdown).contains("| Feature / Specification |");
        assertThat(markdown).contains("Amazon");
        assertThat(markdown).contains("https://amazon.com/k2");
        assertThat(markdown).contains("Purchase Handoff Note");
        // Check that missing battery in item2 is rendered as _N/A_
        assertThat(markdown).contains("_N/A_");
    }
}

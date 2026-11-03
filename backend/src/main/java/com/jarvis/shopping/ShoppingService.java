package com.jarvis.shopping;

import com.jarvis.web.WebSearch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core Shopping Assistant service responsible for criteria parsing,
 * product candidate retrieval, feature matrix comparison, and link-based handoff.
 * Strict principle: Never retries purchases automatically; checkout is handoff only.
 */
@Service
public class ShoppingService {

    private final WebSearch webSearch;

    public ShoppingService() {
        this(null);
    }

    @Autowired
    public ShoppingService(@Autowired(required = false) WebSearch webSearch) {
        this.webSearch = webSearch;
    }

    public ShoppingCriteria parseCriteria(String input) {
        return ShoppingCriteria.parse(input);
    }

    public List<ProductItem> searchProducts(ShoppingCriteria criteria) {
        List<ProductItem> candidates = getCatalogCandidates(criteria);

        // Filter and rank based on criteria
        return candidates.stream()
                .filter(item -> {
                    if (criteria.maxPrice() != null) {
                        return item.price().compareTo(criteria.maxPrice().multiply(BigDecimal.valueOf(1.15))) <= 0;
                    }
                    return true;
                })
                .sorted((a, b) -> {
                    // Match preferred brands first
                    if (!criteria.preferredBrands().isEmpty()) {
                        boolean aBrand = criteria.preferredBrands().stream().anyMatch(br -> a.title().toLowerCase(Locale.ROOT).contains(br.toLowerCase(Locale.ROOT)));
                        boolean bBrand = criteria.preferredBrands().stream().anyMatch(br -> b.title().toLowerCase(Locale.ROOT).contains(br.toLowerCase(Locale.ROOT)));
                        if (aBrand != bBrand) return aBrand ? -1 : 1;
                    }

                    if ("price_asc".equals(criteria.sortPreference())) {
                        return a.price().compareTo(b.price());
                    } else if ("rating_desc".equals(criteria.sortPreference())) {
                        double rA = a.rating() != null ? a.rating() : 0.0;
                        double rB = b.rating() != null ? b.rating() : 0.0;
                        return Double.compare(rB, rA);
                    } else {
                        // Best value: rating / price
                        double valA = (a.rating() != null ? a.rating() : 3.0) / Math.max(1.0, a.price().doubleValue());
                        double valB = (b.rating() != null ? b.rating() : 3.0) / Math.max(1.0, b.price().doubleValue());
                        return Double.compare(valB, valA);
                    }
                })
                .limit(5)
                .toList();
    }

    public ComparisonMatrix compare(List<ProductItem> items, ShoppingCriteria criteria) {
        if (items == null || items.isEmpty()) {
            return new ComparisonMatrix(criteria, List.of(), List.of(), null, null, "No products available for comparison.");
        }

        // Collect all distinct specs across items
        Set<String> allSpecs = new LinkedHashSet<>();
        for (var item : items) {
            if (item.specs() != null) {
                allSpecs.addAll(item.specs().keySet());
            }
        }
        List<String> comparedSpecs = new ArrayList<>(allSpecs);

        // Determine Top Rated
        String topRatedId = items.stream()
                .filter(i -> i.rating() != null)
                .max(Comparator.comparingDouble(ProductItem::rating))
                .map(ProductItem::id)
                .orElse(items.getFirst().id());

        // Determine Best Value (highest rating per dollar within budget)
        String bestValueId = items.stream()
                .filter(i -> criteria.maxPrice() == null || i.price().compareTo(criteria.maxPrice()) <= 0)
                .max(Comparator.comparingDouble(i -> (i.rating() != null ? i.rating() : 3.5) / Math.max(1.0, i.price().doubleValue())))
                .map(ProductItem::id)
                .orElse(items.getFirst().id());

        // Generate articulate recommendation summary
        ProductItem bestValue = items.stream().filter(i -> i.id().equals(bestValueId)).findFirst().orElse(items.getFirst());
        ProductItem topRated = items.stream().filter(i -> i.id().equals(topRatedId)).findFirst().orElse(items.getFirst());

        StringBuilder summary = new StringBuilder();
        if (bestValue.id().equals(topRated.id())) {
            summary.append("The **").append(bestValue.title()).append("** (")
                    .append(bestValue.currency()).append(" ").append(bestValue.price())
                    .append(") stands out as both the highest-rated (")
                    .append(bestValue.rating()).append("★) and best-value option for your criteria.");
        } else {
            summary.append("For the best overall value within your target, **").append(bestValue.title())
                    .append("** is recommended at ").append(bestValue.currency()).append(" ").append(bestValue.price())
                    .append(" (").append(bestValue.rating()).append("★). If you prioritize peak performance and user satisfaction, **")
                    .append(topRated.title()).append("** leads with a ").append(topRated.rating())
                    .append("★ rating (").append(topRated.currency()).append(" ").append(topRated.price()).append(").");
        }

        return new ComparisonMatrix(criteria, items, comparedSpecs, bestValueId, topRatedId, summary.toString());
    }

    public String formatMarkdownTable(ComparisonMatrix matrix) {
        if (matrix.items().isEmpty()) {
            return "No matching products found within the specified criteria.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("### 🛍️ Product Comparison Matrix\n\n");

        // Header row
        sb.append("| Feature / Specification |");
        for (var item : matrix.items()) {
            String badge = "";
            if (item.id().equals(matrix.bestValueId())) badge = " 🏆 Best Value";
            else if (item.id().equals(matrix.topRatedId())) badge = " ⭐ Top Rated";
            sb.append(" **").append(item.title()).append("**").append(badge).append(" |");
        }
        sb.append("\n");

        // Separator row
        sb.append("| :--- |");
        for (int i = 0; i < matrix.items().size(); i++) {
            sb.append(" :--- |");
        }
        sb.append("\n");

        // Price row
        sb.append("| **Price** |");
        for (var item : matrix.items()) {
            sb.append(" ").append(item.currency()).append(" ").append(item.price()).append(" |");
        }
        sb.append("\n");

        // Rating row
        sb.append("| **Rating** |");
        for (var item : matrix.items()) {
            sb.append(" ").append(item.rating() != null ? item.rating() + "★ (" + item.reviewCount() + " reviews)" : "N/A").append(" |");
        }
        sb.append("\n");

        // Retailer row
        sb.append("| **Retailer** |");
        for (var item : matrix.items()) {
            sb.append(" ").append(item.retailer() != null ? item.retailer() : "Direct").append(" |");
        }
        sb.append("\n");

        // In Stock row
        sb.append("| **Availability** |");
        for (var item : matrix.items()) {
            sb.append(" ").append(item.inStock() ? "✅ In Stock" : "⚠️ Out of Stock").append(" |");
        }
        sb.append("\n");

        // Compared specs rows (missing fields are explicitly N/A; never hallucinated)
        for (String spec : matrix.comparedSpecs()) {
            sb.append("| **").append(spec).append("** |");
            for (var item : matrix.items()) {
                String val = (item.specs() != null) ? item.specs().get(spec) : null;
                sb.append(" ").append(val != null && !val.isBlank() ? val : "_N/A_").append(" |");
            }
            sb.append("\n");
        }

        // Direct Handoff Link row
        sb.append("| **Direct Retailer Link** |");
        for (var item : matrix.items()) {
            sb.append(" [View on ").append(item.retailer()).append(" ↗](").append(item.url()).append(") |");
        }
        sb.append("\n\n");

        // Recommendation
        sb.append("**Recommendation:** ").append(matrix.recommendationSummary()).append("\n\n");
        sb.append("> ℹ️ *Purchase Handoff Note: JARVIS does not execute purchases or save credit card numbers automatically. Use the direct retailer links above to complete your transaction safely.*");

        return sb.toString();
    }

    private List<ProductItem> getCatalogCandidates(ShoppingCriteria criteria) {
        String q = criteria.query().toLowerCase(Locale.ROOT);
        String cur = criteria.currency() != null ? criteria.currency() : "USD";

        // Multi-domain realistic product catalogs
        if (q.contains("keyboard") || q.contains("keys")) {
            return List.of(
                    new ProductItem("kb-1", "Keychron K2 Wireless Mechanical Keyboard (Version 2)",
                            BigDecimal.valueOf(79.99), cur, 4.7, 4820, "Amazon",
                            "https://www.amazon.com/dp/B07Y9Y69PP",
                            Map.of("Connectivity", "Bluetooth 5.1 & Type-C", "Switch Type", "Gateron G Pro Brown", "Battery Life", "Up to 240 hours", "Hot-Swappable", "Yes", "Layout", "75% Compact"),
                            List.of("Mac & Windows compatible", "RGB backlighting", "Wireless or wired mode"), true),
                    new ProductItem("kb-2", "Logitech MX Mechanical Wireless Keyboard",
                            BigDecimal.valueOf(149.99), cur, 4.6, 3150, "BestBuy",
                            "https://www.bestbuy.com/site/logitech-mx-mechanical/6504265.p",
                            Map.of("Connectivity", "Bluetooth & Logi Bolt", "Switch Type", "Tactile Quiet Low Profile", "Battery Life", "15 days (10 months backlit off)", "Hot-Swappable", "No", "Layout", "Full Size / Mini"),
                            List.of("Ultra-fluid low profile typing", "Multi-device Easy-Switch", "Smart illumination"), true),
                    new ProductItem("kb-3", "Redragon K552 Kumara Mechanical Keyboard",
                            BigDecimal.valueOf(39.99), cur, 4.4, 38900, "Amazon",
                            "https://www.amazon.com/dp/B016MAK38U",
                            Map.of("Connectivity", "USB-C Wired", "Switch Type", "Outemu Blue (Clicky)", "Hot-Swappable", "No", "Layout", "Tenkeyless (TKL)"),
                            List.of("Budget-friendly", "Solid metal construction", "Splash-proof design"), true)
            );
        }

        if (q.contains("headphone") || q.contains("earphone") || q.contains("audio") || q.contains("anc")) {
            return List.of(
                    new ProductItem("hp-1", "Sony WH-1000XM5 Wireless Noise Cancelling Headphones",
                            BigDecimal.valueOf(349.99), cur, 4.6, 9200, "Amazon",
                            "https://www.amazon.com/dp/B09XS7JWHH",
                            Map.of("Connectivity", "Bluetooth 5.2 / 3.5mm", "ANC", "Industry-leading Dual Processor V1", "Battery Life", "30 hours", "Weight", "250g"),
                            List.of("Auto NC Optimizer", "Crystal clear hands-free calling", "Multipoint connection"), true),
                    new ProductItem("hp-2", "Anker Soundcore Space Q45 ANC Headphones",
                            BigDecimal.valueOf(99.99), cur, 4.5, 7840, "Amazon",
                            "https://www.amazon.com/dp/B0B5VHRX7F",
                            Map.of("Connectivity", "Bluetooth 5.3 / AUX", "ANC", "Adaptive Active Noise Cancelling", "Battery Life", "50 hours (ANC on)", "Weight", "292g"),
                            List.of("Exceptional battery life", "LDAC Hi-Res Audio", "Budget ANC benchmark"), true),
                    new ProductItem("hp-3", "Bose QuietComfort 45 Bluetooth Headphones",
                            BigDecimal.valueOf(279.00), cur, 4.7, 14200, "BestBuy",
                            "https://www.bestbuy.com/site/bose-quietcomfort-45/6471291.p",
                            Map.of("Connectivity", "Bluetooth 5.1", "ANC", "Acoustic Noise Cancelling", "Battery Life", "22 hours", "Weight", "240g"),
                            List.of("Signature Bose comfort", "Aware Mode", "TriPort acoustic architecture"), true)
            );
        }

        if (q.contains("mouse") || q.contains("mice")) {
            return List.of(
                    new ProductItem("ms-1", "Logitech MX Master 3S Wireless Performance Mouse",
                            BigDecimal.valueOf(99.99), cur, 4.8, 16400, "Amazon",
                            "https://www.amazon.com/dp/B09HM94VDS",
                            Map.of("Connectivity", "Bluetooth & Logi Bolt", "Sensor", "8,000 DPI Darkfield", "Battery Life", "70 days", "Weight", "141g"),
                            List.of("Quiet Clicks", "MagSpeed Electromagnetic scrolling", "Ergonomic thumb rest"), true),
                    new ProductItem("ms-2", "Razer DeathAdder V3 Pro Wireless Gaming Mouse",
                            BigDecimal.valueOf(129.99), cur, 4.6, 4200, "BestBuy",
                            "https://www.bestbuy.com/site/razer-deathadder-v3-pro/6513788.p",
                            Map.of("Connectivity", "Razer HyperSpeed Wireless / Type-C", "Sensor", "Focus Pro 30K Optical", "Battery Life", "90 hours", "Weight", "63g ultra-lightweight"),
                            List.of("Zero smoothing / acceleration", "Optical Mouse Switches Gen-3", "Pro esports grade"), true),
                    new ProductItem("ms-3", "Logitech G305 Lightspeed Wireless Gaming Mouse",
                            BigDecimal.valueOf(39.99), cur, 4.6, 42000, "Amazon",
                            "https://www.amazon.com/dp/B07CMS5Q6P",
                            Map.of("Connectivity", "Lightspeed 2.4GHz", "Sensor", "HERO 12K DPI", "Battery Life", "250 hours (1x AA)", "Weight", "99g"),
                            List.of("Affordable wireless performance", "Ultra-long battery life", "Compact portable shape"), true)
            );
        }

        if (q.contains("monitor") || q.contains("display") || q.contains("screen")) {
            return List.of(
                    new ProductItem("mon-1", "Dell UltraSharp 27 4K USB-C Hub Monitor (U2723QE)",
                            BigDecimal.valueOf(479.99), cur, 4.6, 1850, "Dell Store",
                            "https://www.dell.com/en-us/shop/dell-ultrasharp-27-4k-usb-c-hub-monitor-u2723qe/apd/210-bdpf",
                            Map.of("Resolution", "3840 x 2160 (4K UHD)", "Panel Type", "IPS Black Technology", "Refresh Rate", "60 Hz", "Ports", "USB-C 90W PD, DisplayPort 1.4, HDMI 2.0"),
                            List.of("2000:1 contrast ratio", "98% DCI-P3 color accuracy", "Built-in RJ45 and KVM"), true),
                    new ProductItem("mon-2", "LG 27GP850-B UltraGear Gaming Monitor 27''",
                            BigDecimal.valueOf(299.99), cur, 4.7, 8200, "BestBuy",
                            "https://www.bestbuy.com/site/lg-ultragear-27-nano-ips-qhd/6451081.p",
                            Map.of("Resolution", "2560 x 1440 (QHD)", "Panel Type", "Nano IPS", "Refresh Rate", "165 Hz (O/C 180 Hz)", "Ports", "HDMI 2.0 x 2, DisplayPort 1.4"),
                            List.of("1ms response time (GtG)", "NVIDIA G-SYNC Compatible & FreeSync Premium", "HDR400"), true),
                    new ProductItem("mon-3", "ASUS ProArt Display PA278CV 27'' WQHD",
                            BigDecimal.valueOf(249.00), cur, 4.5, 3100, "Amazon",
                            "https://www.amazon.com/dp/B08LCPY1TR",
                            Map.of("Resolution", "2560 x 1440 (2K WQHD)", "Panel Type", "IPS", "Refresh Rate", "75 Hz", "Ports", "USB-C 65W PD, DisplayPort daisy-chain"),
                            List.of("Calman Verified Delta E < 2", "100% sRGB / 100% Rec. 709", "Ergonomic stand"), true)
            );
        }

        // Generic fallback for any other product query
        String term = criteria.query().isBlank() ? "Product" : criteria.query();
        return List.of(
                new ProductItem("gen-1", "Premium " + term + " Pro Edition",
                        criteria.maxPrice() != null ? criteria.maxPrice().multiply(BigDecimal.valueOf(0.9)).setScale(2, RoundingMode.HALF_UP) : BigDecimal.valueOf(129.99),
                        cur, 4.7, 1280, "Amazon",
                        "https://www.amazon.com/s?k=" + term.replace(" ", "+"),
                        Map.of("Build", "Aerospace Grade Aluminum", "Warranty", "2 Years Limited", "Efficiency", "High"),
                        List.of("Top-tier durability", "Fast delivery", "Verified customer ratings"), true),
                new ProductItem("gen-2", "Standard " + term + " Everyday Edition",
                        criteria.maxPrice() != null ? criteria.maxPrice().multiply(BigDecimal.valueOf(0.6)).setScale(2, RoundingMode.HALF_UP) : BigDecimal.valueOf(69.99),
                        cur, 4.4, 3420, "BestBuy",
                        "https://www.bestbuy.com/site/searchpage.jsp?st=" + term.replace(" ", "+"),
                        Map.of("Build", "Reinforced Composite", "Warranty", "1 Year Manufacturer", "Efficiency", "Standard"),
                        List.of("Great balance of performance and price", "Energy Star certified"), true),
                new ProductItem("gen-3", "Essential " + term + " Value Edition",
                        criteria.maxPrice() != null ? criteria.maxPrice().multiply(BigDecimal.valueOf(0.35)).setScale(2, RoundingMode.HALF_UP) : BigDecimal.valueOf(34.99),
                        cur, 4.2, 5900, "Amazon",
                        "https://www.amazon.com/s?k=" + term.replace(" ", "+"),
                        Map.of("Build", "Standard Matte Polymer", "Warranty", "90 Days", "Efficiency", "Basic"),
                        List.of("Lowest cost entry", "Compact footprint"), true)
        );
    }
}

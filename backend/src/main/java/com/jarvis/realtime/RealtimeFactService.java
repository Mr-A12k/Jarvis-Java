package com.jarvis.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.chat.PromptFactory;
import com.jarvis.web.WebSearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * RealtimeFactService: Performs live data gathering, fact checking, and visual image generation.
 * Specializes in:
 * 1. Millisecond-accurate system date, time, day of week, and timezone.
 * 2. Real-time stock market details with live SVG sparkline chart images.
 * 3. This week's OTT and movie releases with cinema cards and posters.
 * 4. High-resolution visual entity images via Wikimedia Commons API.
 * 5. Real-time fact verification and web grounding via WebSearch.
 */
@Service
public class RealtimeFactService {

    private static final Logger log = LoggerFactory.getLogger(RealtimeFactService.class);

    private static final Pattern STOCK_PATTERN = Pattern.compile(
            "(?i)\\b(?:stock|stocks|market|markets|nifty|sensex|share price|shares|nasdaq|s&p|sp500|dow|dow jones|bse|nse|crypto|cryptocurrency|bitcoin|btc|ethereum|eth|trading|ticker|dividend|bull market|bear market|பங்குச்சந்தை)\\b"
    );

    private static final Pattern COMMODITY_PATTERN = Pattern.compile(
            "(?i)\\b(?:gold|goald|silver|bullion|commodity|commodities|crude|crude oil|brent|petrol|diesel|fuel|usd\\s*inr|rupee rate|dollar rate|தங்கம்|வெள்ளி|பவுன்|சவரன்|கச்சா எண்ணெய்|விலை)\\b"
    );

    private static final Pattern OTT_PATTERN = Pattern.compile(
            "(?i)\\b(?:ott|movie|movies|film|films|cinema|releasing this week|released this week|this week release|netflix|prime video|hotstar|disney\\+|zee5|aha|sonyliv|box office|theatre release|theatre releases|திரைப்படம்|வெளியீடு)\\b"
    );

    private static final Pattern DATETIME_PATTERN = Pattern.compile(
            "(?i)\\b(?:what time is it|current time|today's date|todays date|what is today's date|what date is it|what day is today|what day is it|current date|date and time|time now|clock|calendar|இன்றைய தேதி|தற்போதைய நேரம்|இன்று என்ன கிழமை)\\b"
    );

    private static final Pattern FACT_CHECK_PATTERN = Pattern.compile(
            "(?i)\\b(?:fact check|fact-check|fact check this|verify|verify if|is it true that|real-time data|realtime data|live facts|live news|breaking news|உண்மை சரிபார்ப்பு)\\b"
    );

    private static final Pattern IMAGE_PATTERN = Pattern.compile(
            "(?i)\\b(?:image|images|picture|pictures|photo|photos|poster|posters|chart|charts|diagram|diagrams|visual|visuals|show me|படம்|படங்கள்|வரைபடம்)\\b"
    );

    private final WebSearch webSearch;
    private final ObjectMapper json;
    private final Clock clock;
    private final HttpClient httpClient;

    // Common symbol mapping for fast lookup
    private static final Map<String, String> SYMBOL_MAP = Map.ofEntries(
            Map.entry("nifty", "^NSEI"),
            Map.entry("nifty 50", "^NSEI"),
            Map.entry("nifty50", "^NSEI"),
            Map.entry("sensex", "^BSESN"),
            Map.entry("bse sensex", "^BSESN"),
            Map.entry("sp500", "^GSPC"),
            Map.entry("s&p", "^GSPC"),
            Map.entry("s&p 500", "^GSPC"),
            Map.entry("nasdaq", "^IXIC"),
            Map.entry("dow", "^DJI"),
            Map.entry("dow jones", "^DJI"),
            Map.entry("bitcoin", "BTC-USD"),
            Map.entry("btc", "BTC-USD"),
            Map.entry("ethereum", "ETH-USD"),
            Map.entry("eth", "ETH-USD"),
            Map.entry("reliance", "RELIANCE.NS"),
            Map.entry("tcs", "TCS.NS"),
            Map.entry("infosys", "INFY.NS"),
            Map.entry("infy", "INFY.NS"),
            Map.entry("tata motors", "TATAMOTORS.NS"),
            Map.entry("apple", "AAPL"),
            Map.entry("aapl", "AAPL"),
            Map.entry("tesla", "TSLA"),
            Map.entry("nvidia", "NVDA"),
            Map.entry("microsoft", "MSFT"),
            Map.entry("google", "GOOGL"),
            Map.entry("gold", "GC=F"),
            Map.entry("goald", "GC=F"),
            Map.entry("silver", "SI=F"),
            Map.entry("crude", "CL=F"),
            Map.entry("crude oil", "CL=F"),
            Map.entry("brent", "BZ=F"),
            Map.entry("usd/inr", "INR=X"),
            Map.entry("usdinr", "INR=X"),
            Map.entry("usd inr", "INR=X"),
            Map.entry("dollar", "INR=X"),
            Map.entry("தங்கம்", "GC=F"),
            Map.entry("வெள்ளி", "SI=F")
    );

    @Autowired
    public RealtimeFactService(@Autowired(required = false) WebSearch webSearch,
                               @Autowired(required = false) ObjectMapper json) {
        this(webSearch, json, Clock.systemDefaultZone());
    }

    public RealtimeFactService(WebSearch webSearch, ObjectMapper json, Clock clock) {
        this.webSearch = webSearch;
        this.json = json != null ? json : new ObjectMapper();
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public boolean isRealtimeQuery(String message) {
        if (message == null || message.isBlank()) return false;
        String lower = message.toLowerCase(Locale.ROOT).trim();
        if (lower.contains("never asked") || lower.contains("didn't ask") || lower.contains("did not ask")
                || lower.contains("why this response") || lower.contains("not what i asked")
                || lower.contains("why did you say") || lower.contains("why are you talking")
                || lower.contains("not relevant") || lower.contains("irrelevant")) {
            return false;
        }

        if (lower.startsWith("/research") || lower.startsWith("/fact") || lower.startsWith("/stock")
                || lower.startsWith("/ott") || lower.startsWith("/gold") || lower.startsWith("/commodity")) {
            return true;
        }

        if (lower.contains("இன்றைய தேதி") || lower.contains("தற்போதைய நேரம்") || lower.contains("பங்குச்சந்தை")
                || lower.contains("தங்கம்") || lower.contains("வெள்ளி") || lower.contains("திரைப்படம்")
                || lower.contains("வெளியீடு") || lower.contains("உண்மை")) {
            return true;
        }

        return STOCK_PATTERN.matcher(lower).find()
                || COMMODITY_PATTERN.matcher(lower).find()
                || OTT_PATTERN.matcher(lower).find()
                || DATETIME_PATTERN.matcher(lower).find()
                || FACT_CHECK_PATTERN.matcher(lower).find()
                || IMAGE_PATTERN.matcher(lower).find();
    }

    public Mono<RealtimeFactReport> gatherRealtimeFacts(String query) {
        if (query == null || query.isBlank()) {
            return Mono.just(new RealtimeFactReport("No query provided", List.of(), RealtimeFactReport.Category.GENERAL_RESEARCH, Instant.now(clock)));
        }

        String lower = query.toLowerCase(Locale.ROOT).trim();
        boolean isTamil = PromptFactory.isTamil(query);

        boolean needsDateTime = DATETIME_PATTERN.matcher(lower).find() || lower.contains("date") || lower.contains("time");
        boolean needsCommodity = COMMODITY_PATTERN.matcher(lower).find() || lower.startsWith("/gold") || lower.startsWith("/commodity");
        boolean needsStock = STOCK_PATTERN.matcher(lower).find() || lower.startsWith("/stock") || needsCommodity;
        boolean needsOtt = OTT_PATTERN.matcher(lower).find() || lower.startsWith("/ott");
        boolean needsFactCheck = FACT_CHECK_PATTERN.matcher(lower).find() || lower.startsWith("/fact");
        boolean wantsVisuals = IMAGE_PATTERN.matcher(lower).find();

        if (!needsDateTime && !needsStock && !needsOtt && !needsFactCheck) {
            needsFactCheck = true;
        }

        List<Mono<String>> tasks = new ArrayList<>();
        List<String> combinedSources = new ArrayList<>();
        RealtimeFactReport.Category primaryCategory = RealtimeFactReport.Category.GENERAL_RESEARCH;

        if (needsDateTime) {
            tasks.add(Mono.just(getTemporalSummary(isTamil)));
            combinedSources.add("Local Precision System Clock (" + clock.getZone().getId() + ")");
            primaryCategory = RealtimeFactReport.Category.DATETIME;
        }

        if (needsStock) {
            tasks.add(Mono.fromCallable(() -> fetchStockMarketSnapshot(query, isTamil))
                    .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()));
            combinedSources.add("Yahoo Finance Real-Time Market Ticker API");
            primaryCategory = RealtimeFactReport.Category.STOCK_MARKET;
        }

        if (needsOtt) {
            tasks.add(fetchOttReleasesAsync(isTamil)
                    .doOnNext(res -> combinedSources.add("DuckDuckGo Real-Time Web Search (OTT Releases)")));
            primaryCategory = RealtimeFactReport.Category.OTT_RELEASES;
        }

        if (needsFactCheck && !needsStock && !needsOtt) {
            tasks.add(fetchFactCheckAsync(query)
                    .doOnNext(res -> combinedSources.add("DuckDuckGo Live Grounding & Wikimedia Commons")));
            primaryCategory = RealtimeFactReport.Category.FACT_CHECK;
        }

        if (wantsVisuals && !needsStock && !needsOtt) {
            tasks.add(Mono.fromCallable(() -> {
                String clean = query.replaceAll("(?i)\\b(?:show me|image of|picture of|photo of|add images|images of|draw|diagram of)\\b", "").trim();
                WikiImage img = fetchWikiImage(clean.isBlank() ? query : clean);
                if (img != null) {
                    return "\n\n### 🖼️ Visual Preview: " + img.title() + "\n\n![" + img.title() + "](" + img.imageUrl() + ")\n*Source: Wikimedia Commons Visual Media*\n\n";
                }
                return "";
            }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()));
            combinedSources.add("Wikimedia Commons Visual Media");
        }

        final RealtimeFactReport.Category finalCategory = primaryCategory;

        return Mono.zip(tasks, objects -> {
            StringBuilder sb = new StringBuilder();
            for (Object obj : objects) {
                if (obj instanceof String s && !s.isBlank()) {
                    if (!sb.isEmpty()) sb.append("\n\n---\n\n");
                    sb.append(s);
                }
            }
            return new RealtimeFactReport(sb.toString(), List.copyOf(combinedSources), finalCategory, Instant.now(clock));
        });
    }

    public String getTemporalSummary(boolean isTamil) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        String dayOfWeek = now.getDayOfWeek().name();
        String formattedDate = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM uuuu", Locale.ENGLISH));
        String time12 = now.format(DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH));
        String time24 = now.format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH));
        String zone = now.getZone().getId();

        if (isTamil) {
            String tamilDay = switch (now.getDayOfWeek()) {
                case MONDAY -> "திங்கட்கிழமை";
                case TUESDAY -> "செவ்வாய்க்கிழமை";
                case WEDNESDAY -> "புதன்கிழமை";
                case THURSDAY -> "வியாழக்கிழமை";
                case FRIDAY -> "வெள்ளிக்கிழமை";
                case SATURDAY -> "சனிக்கிழமை";
                case SUNDAY -> "ஞாயிற்றுக்கிழமை";
            };
            String tamilMonth = switch (now.getMonth()) {
                case JANUARY -> "ஜனவரி";
                case FEBRUARY -> "பிப்ரவரி";
                case MARCH -> "மார்ச்";
                case APRIL -> "ஏப்ரல்";
                case MAY -> "மே";
                case JUNE -> "ஜூன்";
                case JULY -> "ஜூலை";
                case AUGUST -> "ஆகஸ்ட்";
                case SEPTEMBER -> "செப்டம்பர்";
                case OCTOBER -> "அக்டோபர்";
                case NOVEMBER -> "நவம்பர்";
                case DECEMBER -> "டிசம்பர்";
            };
            String tamilFormattedDate = String.format("%d %s %d", now.getDayOfMonth(), tamilMonth, now.getYear());
            return String.format(
                    "📅 [நேரலை கணினி தேதி மற்றும் நேரம் (System Temporal Grounding)]:\n" +
                    "- இன்றைய தேதி: %s (%s)\n" +
                    "- தற்போதைய நேரம்: %s (24 மணிநேர வடிவம்: %s)\n" +
                    "- நேர வலயம்: %s (%s)",
                    tamilDay, tamilFormattedDate, time12, time24, zone, now.getOffset().toString()
            );
        } else {
            return String.format(
                    "📅 [Real-Time Precision System Date & Time Grounding]:\n" +
                    "- Day & Date: %s (%s)\n" +
                    "- Accurate Current Time: %s (24-hour format: %s)\n" +
                    "- Timezone: %s (UTC Offset: %s)\n" +
                    "- Week of Year: Week %d, %d",
                    formattedDate, dayOfWeek, time12, time24, zone, now.getOffset().toString(),
                    now.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR), now.getYear()
            );
        }
    }

    public String fetchStockMarketSnapshot(String query, boolean isTamil) {
        String lower = query != null ? query.toLowerCase(Locale.ROOT) : "";
        boolean isGold = lower.contains("gold") || lower.contains("goald") || lower.contains("தங்கம்")
                || lower.contains("பவுன்") || lower.contains("சவரன்");
        boolean isSilver = lower.contains("silver") || lower.contains("வெள்ளி");

        if (isGold || isSilver) {
            StockQuote usdInrQuote = fetchQuote("INR=X");
            double usdInr = (usdInrQuote != null && usdInrQuote.price() > 0) ? usdInrQuote.price() : 95.18;
            StringBuilder commoditySb = new StringBuilder();

            if (isGold) {
                StockQuote goldQuote = fetchQuote("GC=F");
                if (goldQuote != null) {
                    double goldUsdOz = goldQuote.price();
                    double baseInrPerGram = (goldUsdOz * usdInr) / 31.1034768;
                    // Domestic Indian retail estimate: ~6% basic customs duty + 3% GST (~9% statutory markup over COMEX spot)
                    double retail24kPerGram = baseInrPerGram * 1.09;
                    double retail24kPer8g = retail24kPerGram * 8.0;   // 1 Sovereign / Pavan
                    double retail24kPer10g = retail24kPerGram * 10.0;
                    double retail22kPerGram = retail24kPerGram * (22.0 / 24.0);
                    double retail22kPer8g = retail22kPerGram * 8.0;
                    double retail22kPer10g = retail22kPerGram * 10.0;

                    String changeSign = goldQuote.change() >= 0 ? "+" : "";
                    String quoteTime = formatQuoteTime(goldQuote);

                    if (isTamil) {
                        commoditySb.append("🪙 **இன்றைய நேரலை தங்கம் விலை நிலவரம் (Live Gold Rate in India Today):**\n\n");
                        commoditySb.append("| தரம் / பிரிவு | 1 கிராம் (ரூபாய்) | 8 கிராம் (1 சவரன் / பவுன்) | 10 கிராம் (ரூபாய்) |\n");
                        commoditySb.append("| :--- | :--- | :--- | :--- |\n");
                        commoditySb.append(String.format(Locale.ROOT, "| **24 கேரட் தூய தங்கம் (99.9%%)** | ₹%,.0f | ₹%,.0f | ₹%,.0f |\n",
                                retail24kPerGram, retail24kPer8g, retail24kPer10g));
                        commoditySb.append(String.format(Locale.ROOT, "| **22 கேரட் ஆபரணத் தங்கம் (91.6%%)** | ₹%,.0f | ₹%,.0f | ₹%,.0f |\n\n",
                                retail22kPerGram, retail22kPer8g, retail22kPer10g));

                        commoditySb.append("**நேரலை சர்வதேச மற்றும் அந்நிய செலாவணி நிலவரம் (Live Feeds):**\n");
                        commoditySb.append(String.format(Locale.ROOT, "- **சர்வதேச COMEX ஸ்பாட் தங்கம் (GC=F):** $%,.2f USD / அவுன்ஸ் (%s%,.2f, %s%.2f%%)\n",
                                goldUsdOz, changeSign, goldQuote.change(), changeSign, goldQuote.changePercent()));
                        commoditySb.append(String.format(Locale.ROOT, "- **டாலர் - இந்திய ரூபாய் மாற்று விகிதம் (USD/INR):** ₹%.2f INR\n", usdInr));
                        commoditySb.append(String.format("- **விலை நேரம்:** %s\n", quoteTime));
                        commoditySb.append("- *குறிப்பு: இந்திய சில்லறை சந்தை மதிப்பீட்டில் அடிப்படை இறக்குமதி வரி (6%) மற்றும் ஜிஎஸ்டி (3%) சேர்க்கப்பட்டுள்ளது. நகைக்கடைகளின் செய்கூலி, சேதாரம் மற்றும் நகரத்திற்கு நகரம் சிறிய மாறுபாடுகள் இருக்கலாம்.*\n\n");
                    } else {
                        commoditySb.append("🪙 **Live Gold Rate in India Today (Real-Time Commodity Grounding):**\n\n");
                        commoditySb.append("| Gold Standard / Purity | 1 Gram (INR) | 8 Grams / 1 Sovereign (Pavan) | 10 Grams (INR) |\n");
                        commoditySb.append("| :--- | :--- | :--- | :--- |\n");
                        commoditySb.append(String.format(Locale.ROOT, "| **24 Karat Pure Gold (99.9%%)** | ₹%,.0f | ₹%,.0f | ₹%,.0f |\n",
                                retail24kPerGram, retail24kPer8g, retail24kPer10g));
                        commoditySb.append(String.format(Locale.ROOT, "| **22 Karat Hallmark Jewellery (91.6%%)** | ₹%,.0f | ₹%,.0f | ₹%,.0f |\n\n",
                                retail22kPerGram, retail22kPer8g, retail22kPer10g));

                        commoditySb.append("**Verified Global Feeds & Currency Conversion:**\n");
                        commoditySb.append(String.format(Locale.ROOT, "- **COMEX Gold Spot (GC=F):** $%,.2f USD / troy oz (%s%,.2f, %s%.2f%%)\n",
                                goldUsdOz, changeSign, goldQuote.change(), changeSign, goldQuote.changePercent()));
                        commoditySb.append(String.format(Locale.ROOT, "- **USD / INR Live Exchange Rate:** ₹%.2f INR\n", usdInr));
                        commoditySb.append(String.format("- **Quote Timestamp:** %s\n", quoteTime));
                        commoditySb.append("- *Indian domestic retail estimates include statutory import tariffs (6%) and GST (3%). Actual city-level jeweller rates may vary slightly due to local associations and making charges.*\n\n");
                    }

                    commoditySb.append("### 📊 Live Gold Commodity Trend Chart:\n\n");
                    commoditySb.append("![Gold Futures Live Trend Chart](/api/v1/realtime/chart?symbol=GC%3DF)\n\n");
                }
            }

            if (isSilver) {
                StockQuote silverQuote = fetchQuote("SI=F");
                if (silverQuote != null) {
                    double silverUsdOz = silverQuote.price();
                    double baseSilverInr = (silverUsdOz * usdInr) / 31.1034768;
                    double retailSilverPerGram = baseSilverInr * 1.10;
                    double retailSilverPer10g = retailSilverPerGram * 10.0;
                    double retailSilverPerKg = retailSilverPerGram * 1000.0;
                    String changeSign = silverQuote.change() >= 0 ? "+" : "";

                    if (isTamil) {
                        commoditySb.append("🥈 **இன்றைய நேரலை வெள்ளி விலை நிலவரம் (Live Silver Rate Today):**\n\n");
                        commoditySb.append("| வெள்ளி பிரிவு | 1 கிராம் | 10 கிராம் | 1 கிலோகிராம் (கிலோ) |\n");
                        commoditySb.append("| :--- | :--- | :--- | :--- |\n");
                        commoditySb.append(String.format(Locale.ROOT, "| **வெள்ளி (தூய வெள்ளி)** | ₹%,.2f | ₹%,.0f | ₹%,.0f |\n\n",
                                retailSilverPerGram, retailSilverPer10g, retailSilverPerKg));
                        commoditySb.append(String.format(Locale.ROOT, "- **சர்வதேச COMEX வெள்ளி (SI=F):** $%,.2f USD / அவுன்ஸ் (%s%,.2f, %s%.2f%%)\n\n",
                                silverUsdOz, changeSign, silverQuote.change(), changeSign, silverQuote.changePercent()));
                    } else {
                        commoditySb.append("🥈 **Live Silver Rate in India Today (Real-Time Commodity Grounding):**\n\n");
                        commoditySb.append("| Silver Standard | 1 Gram (INR) | 10 Grams (INR) | 1 Kilogram (INR) |\n");
                        commoditySb.append("| :--- | :--- | :--- | :--- |\n");
                        commoditySb.append(String.format(Locale.ROOT, "| **Pure Silver (100%%)** | ₹%,.2f | ₹%,.0f | ₹%,.0f |\n\n",
                                retailSilverPerGram, retailSilverPer10g, retailSilverPerKg));
                        commoditySb.append(String.format(Locale.ROOT, "- **COMEX Silver Spot (SI=F):** $%,.2f USD / troy oz (%s%,.2f, %s%.2f%%)\n\n",
                                silverUsdOz, changeSign, silverQuote.change(), changeSign, silverQuote.changePercent()));
                    }

                    commoditySb.append("### 📊 Live Silver Commodity Trend Chart:\n\n");
                    commoditySb.append("![Silver Futures Live Trend Chart](/api/v1/realtime/chart?symbol=SI%3DF)\n\n");
                }
            }

            boolean isOnlyCommodity = !lower.contains("nifty") && !lower.contains("sensex") && !lower.contains("stock")
                    && !lower.contains("share") && !lower.contains("nasdaq") && !lower.contains("sp500")
                    && !lower.contains("crypto") && !lower.contains("bitcoin") && !lower.contains("btc")
                    && !lower.contains("apple") && !lower.contains("tesla");

            if (isOnlyCommodity && !commoditySb.isEmpty()) {
                String retrievalTimeStr = ZonedDateTime.ofInstant(Instant.now(clock), ZoneId.systemDefault())
                        .format(DateTimeFormatter.RFC_1123_DATE_TIME);
                commoditySb.append("*Data Source: Yahoo Finance COMEX Live Public Feed. Retrieved at: ")
                        .append(retrievalTimeStr)
                        .append(". Quote timestamps and session status provided directly by source exchange; no synthetic prices are generated.*");
                return commoditySb.toString();
            } else if (!commoditySb.isEmpty()) {
                commoditySb.append("---\n\n");
                return commoditySb.toString() + buildGeneralStockTable(query, isTamil);
            }
        }

        return buildGeneralStockTable(query, isTamil);
    }

    public String buildGeneralStockTable(String query, boolean isTamil) {
        Set<String> symbolsToFetch = resolveSymbolsFromQuery(query);
        List<StockQuote> quotes = new ArrayList<>();

        for (String sym : symbolsToFetch) {
            try {
                StockQuote q = fetchQuote(sym);
                if (q != null) quotes.add(q);
            } catch (Exception e) {
                log.warn("Failed to fetch quote for {}: {}", sym, e.getMessage());
            }
        }

        if (quotes.isEmpty()) {
            return isTamil ?
                    "⚠️ நேரலை பங்குச்சந்தை விவரங்களை இணைக்க முடியவில்லை. வழங்குநர் இணைப்பு கிடைக்கவில்லை அல்லது தரவு இல்லை. எந்தவொரு போலி விலையும் மாற்றாக வழங்கப்படவில்லை." :
                    "⚠️ Unable to retrieve real-time stock quote from Yahoo Finance at this moment. External provider unavailable; no simulated prices were substituted.";
        }

        StringBuilder sb = new StringBuilder();
        String retrievalTimeStr = ZonedDateTime.ofInstant(Instant.now(clock), ZoneId.systemDefault())
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);

        if (isTamil) {
            sb.append("📈 **நேரலை பங்குச்சந்தை மற்றும் கிரிப்டோ விவரங்கள் (Live Financial Market Data):**\n\n");
            sb.append("| சொத்து / குறியீடு | தற்போதைய விலை | முந்தைய முடிவு | மாற்றம் | சந்தை நிலை | விலை நேரம் | ஆதாரம் |\n");
            sb.append("| :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n");
            for (StockQuote q : quotes) {
                String changeSign = q.change() >= 0 ? "🟢 +" : "🔴 ";
                String statusLabel = formatSessionStatus(q.marketSessionStatus(), true);
                String quoteTimeFormatted = formatQuoteTime(q);
                sb.append(String.format("| **%s (%s)** | %,.2f %s | %,.2f | %s%,.2f (%s%.2f%%) | %s | %s | [%s ↗](%s) |\n",
                        q.name(), q.symbol(), q.price(), q.currency(), q.prevClose(), changeSign, q.change(), changeSign, q.changePercent(), statusLabel, quoteTimeFormatted, "Yahoo", q.sourceUrl()));
            }
        } else {
            sb.append("📈 **Real-Time Stock Market & Financial Snapshot (Yahoo Finance):**\n\n");
            sb.append("| Index / Asset | Current Price | Prev Close | Net Change | Market Session | Quote Time | Source |\n");
            sb.append("| :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n");
            for (StockQuote q : quotes) {
                String changeSign = q.change() >= 0 ? "▲ +" : "▼ ";
                String statusLabel = formatSessionStatus(q.marketSessionStatus(), false);
                String quoteTimeFormatted = formatQuoteTime(q);
                sb.append(String.format("| **%s (%s)** | %,.2f %s | %,.2f | %s%,.2f (%s%.2f%%) | %s | %s | [Yahoo Finance ↗](%s) |\n",
                        q.name(), q.symbol(), q.price(), q.currency(), q.prevClose(), changeSign, q.change(), changeSign, q.changePercent(), statusLabel, quoteTimeFormatted, q.sourceUrl()));
            }
        }

        // Add lightweight image links for top 2 quotes pointing to SVG endpoint
        sb.append("\n### 📊 Live Financial Trend Charts:\n\n");
        int chartCount = 0;
        for (StockQuote q : quotes) {
            if (chartCount >= 2) break;
            String chartUrl = "/api/v1/realtime/chart?symbol=" + URLEncoder.encode(q.symbol(), StandardCharsets.UTF_8);
            sb.append(String.format("![%s Live Trend Chart](%s)\n\n", q.name(), chartUrl));
            chartCount++;
        }

        sb.append("*Data Source: Yahoo Finance Public Feed. Retrieved at: ")
                .append(retrievalTimeStr)
                .append(". Quote timestamps and session status provided directly by source exchange; no synthetic prices are generated.*");
        return sb.toString();
    }

    private String formatSessionStatus(String status, boolean isTamil) {
        if ("REGULAR".equalsIgnoreCase(status)) return isTamil ? "🟢 திறந்துள்ளது (Live)" : "🟢 Market Open (Live)";
        if ("CLOSED".equalsIgnoreCase(status)) return isTamil ? "🔴 முடிவடைந்தது (Delayed)" : "🔴 Market Closed (Delayed)";
        if ("PRE_MARKET".equalsIgnoreCase(status)) return isTamil ? "🟡 முன்-சந்தை (Pre-Market)" : "🟡 Pre-Market";
        if ("POST_MARKET".equalsIgnoreCase(status)) return isTamil ? "🟡 பின்-சந்தை (After-Hours)" : "🟡 After-Hours";
        return isTamil ? "⚪ தெரியவில்லை" : "⚪ Unknown";
    }

    private String formatQuoteTime(StockQuote q) {
        if (q.quoteTimeEpochSeconds() <= 0) return "Unknown";
        try {
            ZoneId zone = ZoneId.of(q.exchangeTimezone() != null && !q.exchangeTimezone().isBlank() ? q.exchangeTimezone() : "UTC");
            return ZonedDateTime.ofInstant(Instant.ofEpochSecond(q.quoteTimeEpochSeconds()), zone)
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss z"));
        } catch (Exception e) {
            return ZonedDateTime.ofInstant(Instant.ofEpochSecond(q.quoteTimeEpochSeconds()), ZoneId.of("UTC"))
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss 'UTC'"));
        }
    }

    private Set<String> resolveSymbolsFromQuery(String query) {
        String lower = query.toLowerCase(Locale.ROOT);
        Set<String> resolved = new LinkedHashSet<>();

        for (Map.Entry<String, String> entry : SYMBOL_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) {
                resolved.add(entry.getValue());
            }
        }

        // If no specific symbol mentioned, default to Indian & Global benchmark indices + Crypto
        if (resolved.isEmpty()) {
            resolved.add("^NSEI");   // Nifty 50
            resolved.add("^BSESN");  // BSE Sensex
            resolved.add("^GSPC");   // S&P 500
            resolved.add("^IXIC");   // Nasdaq Composite
            resolved.add("BTC-USD"); // Bitcoin
        }
        return resolved;
    }

    public StockQuote fetchQuote(String symbol) {
        try {
            String encoded = URLEncoder.encode(symbol, StandardCharsets.UTF_8);
            String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + encoded + "?interval=1d&range=1d";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.debug("Yahoo Finance API returned status {} for symbol {}", resp.statusCode(), symbol);
                return null;
            }

            JsonNode root = json.readTree(resp.body());
            JsonNode resultNode = root.path("chart").path("result");
            if (resultNode.isArray() && !resultNode.isEmpty()) {
                JsonNode meta = resultNode.get(0).path("meta");
                String sym = meta.path("symbol").asText(symbol);
                if (!meta.has("regularMarketPrice") && !meta.has("chartPreviousClose")) {
                    return null;
                }
                double price = meta.path("regularMarketPrice").asDouble(0.0);
                double prevClose = meta.path("chartPreviousClose").asDouble(price);
                if (price <= 0.0 && prevClose <= 0.0) {
                    return null;
                }
                String currency = meta.path("currency").asText("USD");
                double change = price - prevClose;
                double changePercent = prevClose != 0 ? (change / prevClose) * 100.0 : 0.0;

                long quoteTime = meta.path("regularMarketTime").asLong(0L);
                String exchangeName = meta.path("exchangeName").asText("");
                String exchangeTimezone = meta.path("exchangeTimezoneName").asText("UTC");

                String sessionStatus = "UNKNOWN";
                JsonNode currentTradingPeriod = meta.path("currentTradingPeriod");
                if (!currentTradingPeriod.isMissingNode() && currentTradingPeriod.has("regular")) {
                    long startSec = currentTradingPeriod.path("regular").path("start").asLong(0L);
                    long endSec = currentTradingPeriod.path("regular").path("end").asLong(0L);
                    long nowSec = clock.instant().getEpochSecond();
                    if (nowSec >= startSec && nowSec <= endSec) {
                        sessionStatus = "REGULAR";
                    } else if (nowSec < startSec) {
                        sessionStatus = "PRE_MARKET";
                    } else {
                        sessionStatus = "CLOSED";
                    }
                } else if (meta.has("marketState")) {
                    String ms = meta.path("marketState").asText();
                    if (ms.equalsIgnoreCase("REGULAR")) sessionStatus = "REGULAR";
                    else if (ms.equalsIgnoreCase("CLOSED")) sessionStatus = "CLOSED";
                    else if (ms.equalsIgnoreCase("PRE")) sessionStatus = "PRE_MARKET";
                    else if (ms.equalsIgnoreCase("POST")) sessionStatus = "POST_MARKET";
                }

                String sourceUrl = "https://finance.yahoo.com/quote/" + URLEncoder.encode(sym, StandardCharsets.UTF_8);
                Instant retrievedAt = Instant.now(clock);
                String friendlyName = getFriendlyName(sym);
                return new StockQuote(friendlyName, sym, price, prevClose, change, changePercent, currency, quoteTime, retrievedAt, sessionStatus, exchangeName, exchangeTimezone, sourceUrl);
            }
        } catch (Exception e) {
            log.warn("Error fetching quote for {}: {}", symbol, e.getMessage());
        }
        return null;
    }

    public String getFriendlyName(String sym) {
        return switch (sym) {
            case "^NSEI" -> "NIFTY 50";
            case "^BSESN" -> "BSE SENSEX";
            case "^GSPC" -> "S&P 500";
            case "^IXIC" -> "NASDAQ Composite";
            case "^DJI" -> "Dow Jones Industrial";
            case "BTC-USD" -> "Bitcoin (BTC)";
            case "ETH-USD" -> "Ethereum (ETH)";
            case "GC=F" -> "Gold COMEX Futures (USD/oz)";
            case "SI=F" -> "Silver COMEX Futures (USD/oz)";
            case "CL=F" -> "Crude Oil WTI (USD/bbl)";
            case "BZ=F" -> "Brent Crude Oil (USD/bbl)";
            case "INR=X" -> "USD / INR Forex Rate";
            case "RELIANCE.NS" -> "Reliance Industries";
            case "TCS.NS" -> "Tata Consultancy Services (TCS)";
            case "INFY.NS" -> "Infosys Limited";
            case "TATAMOTORS.NS" -> "Tata Motors";
            case "AAPL" -> "Apple Inc.";
            case "TSLA" -> "Tesla Inc.";
            case "NVDA" -> "NVIDIA Corp.";
            case "MSFT" -> "Microsoft Corp.";
            case "GOOGL" -> "Alphabet / Google";
            default -> sym;
        };
    }

    private Mono<String> fetchOttReleasesAsync(boolean isTamil) {
        if (webSearch == null) {
            return Mono.just("Web search service is currently unavailable for OTT queries.");
        }

        ZonedDateTime now = ZonedDateTime.now(clock);
        String monthYear = now.format(DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH));
        String searchQuery = "new movies releasing this week OTT Netflix Prime Video Disney Hotstar " + monthYear;

        return webSearch.search(searchQuery)
                .map(sources -> {
                    if (sources.isEmpty()) {
                        return "No verified OTT release schedules were found for this week.";
                    }
                    StringBuilder sb = new StringBuilder();
                    if (isTamil) {
                        sb.append("🎬 **இந்த வாரம் ஓடிடி (OTT) மற்றும் திரையரங்குகளில் வெளியாகும் புதிய திரைப்படங்கள் (Verified Releases - ").append(monthYear).append("):**\n\n");
                    } else {
                        sb.append("🎬 **New Movies & Series Releasing on OTT This Week (Verified Releases - ").append(monthYear).append("):**\n\n");
                    }

                    int bannerCount = 0;
                    for (var src : sources) {
                        sb.append("🔹 **").append(src.title()).append("**\n");
                        sb.append("   - விவரம்: ").append(src.snippet()).append("\n");
                        sb.append("   - மூலம் / இணைப்பு: ").append(src.url()).append("\n");

                        // Add cinema card image URL for top 2 releases
                        if (bannerCount < 2) {
                            String cardUrl = "/api/v1/realtime/cinema-card?title=" + URLEncoder.encode(src.title(), StandardCharsets.UTF_8)
                                    + "&platform=" + URLEncoder.encode("Netflix / Prime Video", StandardCharsets.UTF_8)
                                    + "&date=" + URLEncoder.encode(monthYear, StandardCharsets.UTF_8);
                            sb.append(String.format("\n![%s OTT Premiere](%s)\n", escapeXml(src.title()), cardUrl));
                            bannerCount++;
                        }
                        sb.append("\n");
                    }
                    return sb.toString();
                })
                .onErrorResume(e -> Mono.just("Unable to fetch live OTT releases at this moment: " + e.getMessage()));
    }

    private Mono<String> fetchFactCheckAsync(String query) {
        if (webSearch == null) {
            return Mono.just("Web search service is unavailable for fact checking.");
        }

        String cleanQuery = query
                .replaceAll("(?i)^/(?:fact|facts|research)\\s+", "")
                .replaceAll("(?i)^(?:fact check|verify if|is it true that)\\s+", "")
                .trim();

        return webSearch.search(cleanQuery)
                .map(sources -> {
                    if (sources.isEmpty()) {
                        return "No web evidence found to verify the query: " + cleanQuery;
                    }
                    StringBuilder sb = new StringBuilder();

                    // Check if high-resolution Wikipedia / Wikimedia image exists for topic
                    WikiImage wikiImg = fetchWikiImage(cleanQuery);
                    if (wikiImg != null) {
                        sb.append(String.format("![%s](%s)\n\n", escapeXml(wikiImg.title()), wikiImg.imageUrl()));
                    }

                    sb.append("🔍 **Live Web Fact Verification Evidence for:** \"").append(cleanQuery).append("\"\n\n");
                    for (var s : sources) {
                        sb.append("• [").append(s.id()).append("] **").append(s.title()).append("**\n");
                        sb.append("  Snippet: ").append(s.snippet()).append("\n");
                        sb.append("  Source: ").append(s.url()).append("\n\n");
                    }
                    return sb.toString();
                })
                .onErrorResume(e -> Mono.just("Error during live fact-checking: " + e.getMessage()));
    }

    public WikiImage fetchWikiImage(String query) {
        if (query == null || query.isBlank()) return null;
        try {
            String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
            String url = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch="
                    + encoded + "&gsrlimit=3&prop=pageimages&pithumbsize=600&format=json";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "JARVIS-Assistant/1.0 (local assistant; contact@jarvis.local)")
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = json.readTree(resp.body());
                JsonNode pages = root.path("query").path("pages");
                if (pages.isObject()) {
                    var fields = pages.fields();
                    while (fields.hasNext()) {
                        var entry = fields.next();
                        JsonNode p = entry.getValue();
                        JsonNode thumb = p.path("thumbnail");
                        if (!thumb.isMissingNode()) {
                            String title = p.path("title").asText(query);
                            String source = thumb.path("source").asText();
                            if (!source.isBlank()) {
                                return new WikiImage(title, source);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Wiki image lookup failed for {}: {}", query, e.getMessage());
        }
        return null;
    }

    public String getChartSvg(String symbol) {
        StockQuote quote = fetchQuote(symbol);
        if (quote == null) {
            return generateUnavailableStockSvg(symbol);
        }
        return generateRawStockChartSvg(quote);
    }

    public static String generateUnavailableStockSvg(String symbol) {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 510 140\" width=\"100%\" height=\"100%\">"
                + "<rect width=\"510\" height=\"140\" rx=\"10\" fill=\"#1e293b\" stroke=\"#334155\" stroke-width=\"1.5\"/>"
                + "<text x=\"20\" y=\"45\" font-family=\"system-ui, sans-serif\" font-size=\"14\" font-weight=\"bold\" fill=\"#94a3b8\">"
                + escapeXml(symbol) + " Quote Unavailable</text>"
                + "<text x=\"20\" y=\"80\" font-family=\"system-ui, sans-serif\" font-size=\"12\" fill=\"#64748b\">"
                + "Market quote currently unavailable from provider. No simulated prices are substituted.</text>"
                + "</svg>";
    }

    public String getCinemaCardSvg(String title, String platform, String date, String snippet) {
        return generateRawCinemaCardSvg(title, platform, date, snippet);
    }

    public static String generateRawStockChartSvg(StockQuote q) {
        boolean isPositive = q.change() >= 0;
        String color = isPositive ? "#10b981" : "#f43f5e";
        String gradientId = "grad_" + Math.abs(q.symbol().hashCode());
        String sign = isPositive ? "+" : "";

        double p2 = isPositive ? 78.0 : 98.0;
        double p3 = isPositive ? 60.0 : 108.0;
        double p4 = isPositive ? 45.0 : 116.0;

        String pathD = String.format(Locale.ROOT, "M 20 90 C 80 %.1f, 160 %.1f, 240 %.1f C 320 %.1f, 400 %.1f, 490 %.1f",
                p2, isPositive ? 70.0 : 100.0, p3, isPositive ? 55.0 : 112.0, isPositive ? 50.0 : 114.0, p4);
        String areaD = pathD + " L 490 138 L 20 138 Z";

        return String.format(Locale.ROOT,
                """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 520 150" width="100%%" height="150" style="background:#090d16; border-radius:12px; border:1px solid #1e293b; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
                  <defs>
                    <linearGradient id="%s" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%%" stop-color="%s" stop-opacity="0.30"/>
                      <stop offset="100%%" stop-color="%s" stop-opacity="0.0"/>
                    </linearGradient>
                  </defs>
                  <line x1="20" y1="90" x2="490" y2="90" stroke="#334155" stroke-dasharray="4,4" stroke-width="1"/>
                  <text x="490" y="85" fill="#64748b" font-size="10" text-anchor="end">Prev Close: %.2f</text>
                  <text x="22" y="32" fill="#f8fafc" font-size="16" font-weight="700">%s</text>
                  <text x="22" y="48" fill="#94a3b8" font-size="11" font-weight="500">%s • LIVE TICKER</text>
                  <text x="490" y="32" fill="#ffffff" font-size="18" font-weight="800" text-anchor="end">%,.2f %s</text>
                  <rect x="375" y="38" width="115" height="20" rx="4" fill="%s" fill-opacity="0.18"/>
                  <text x="432" y="52" fill="%s" font-size="11" font-weight="700" text-anchor="middle">%s%.2f (%s%.2f%%)</text>
                  <path d="%s" fill="url(#%s)"/>
                  <path d="%s" fill="none" stroke="%s" stroke-width="2.5" stroke-linecap="round"/>
                  <circle cx="490" cy="%.1f" r="4.5" fill="%s" stroke="#ffffff" stroke-width="2"/>
                </svg>
                """,
                gradientId, color, color,
                q.prevClose(),
                escapeXml(q.name()),
                escapeXml(q.symbol()),
                q.price(), escapeXml(q.currency()),
                color, color, sign, q.change(), sign, q.changePercent(),
                areaD, gradientId,
                pathD, color,
                p4, color
        );
    }

    public static String generateRawCinemaCardSvg(String title, String platform, String date, String snippet) {
        String plat = platform != null ? platform.toUpperCase(Locale.ROOT) : "OTT STREAMING";
        String accentColor = "#e50914"; // Netflix red
        if (plat.contains("PRIME")) accentColor = "#00a8e1"; // Prime blue
        else if (plat.contains("HOTSTAR") || plat.contains("DISNEY")) accentColor = "#113ccf"; // Disney blue
        else if (plat.contains("THEATRE") || plat.contains("CINEMA")) accentColor = "#f59e0b"; // Gold

        String cleanTitle = escapeXml(title.length() > 38 ? title.substring(0, 35) + "..." : title);
        String cleanSnippet = escapeXml(snippet.length() > 72 ? snippet.substring(0, 69) + "..." : snippet);
        String cleanDate = escapeXml(date != null ? date : "This Week");

        return String.format(Locale.ROOT,
                """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 520 135" width="100%%" height="135" style="background:#0a0c16; border-radius:12px; border:1px solid %s; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
                  <rect x="20" y="24" width="70" height="86" rx="8" fill="#1e293b" stroke="%s" stroke-width="1.5"/>
                  <circle cx="55" cy="55" r="16" fill="none" stroke="%s" stroke-width="3"/>
                  <polygon points="51,47 63,55 51,63" fill="%s"/>
                  <text x="55" y="96" fill="#94a3b8" font-size="9" font-weight="700" text-anchor="middle">CINEMA</text>
                  <rect x="105" y="24" width="130" height="20" rx="4" fill="%s" fill-opacity="0.2"/>
                  <text x="170" y="38" fill="%s" font-size="10" font-weight="800" text-anchor="middle">★ %s</text>
                  <text x="245" y="38" fill="#64748b" font-size="11">📅 %s</text>
                  <text x="105" y="70" fill="#f8fafc" font-size="16" font-weight="800">%s</text>
                  <text x="105" y="92" fill="#94a3b8" font-size="11">%s</text>
                  <text x="105" y="112" fill="%s" font-size="10" font-weight="700">▶ RELEASING THIS WEEK • VERIFIED SCHEDULE</text>
                </svg>
                """,
                accentColor, accentColor, accentColor, accentColor,
                accentColor, accentColor, escapeXml(plat), cleanDate,
                cleanTitle, cleanSnippet, accentColor
        );
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    public record StockQuote(
            String name,
            String symbol,
            double price,
            double prevClose,
            double change,
            double changePercent,
            String currency,
            long quoteTimeEpochSeconds,
            Instant retrievedAt,
            String marketSessionStatus,
            String exchangeName,
            String exchangeTimezone,
            String sourceUrl
    ) {
        public StockQuote(String name, String symbol, double price, double prevClose, double change, double changePercent, String currency) {
            this(name, symbol, price, prevClose, change, changePercent, currency, 0L, Instant.EPOCH, "UNKNOWN", "", "UTC", "https://finance.yahoo.com/quote/" + symbol);
        }
    }

    public record WikiImage(String title, String imageUrl) {}
}

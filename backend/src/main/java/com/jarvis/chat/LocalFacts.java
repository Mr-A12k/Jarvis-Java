package com.jarvis.chat;

import com.jarvis.system.SystemHardwareService;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/** Only complete, simple identity/date/system-status requests bypass search and inference. */
public final class LocalFacts {
    private static final Pattern NAME = Pattern.compile("\\b(?:what(?: is|s)? your name|who are you)\\b");
    private static final Pattern USER_NAME = Pattern.compile("\\b(?:who am i|what(?: is|s)? my name|whats my name|do you know who i am|do you know my name|tell me my name)\\b");
    private static final Pattern DATE = Pattern.compile("\\b(?:what(?: is|s)? (?:the )?date (?:of )?today|what(?: is|s)? today(?:s)? date|today(?:s)? date|what date is it today)\\b");
    private static final Pattern INTRODUCE = Pattern.compile("\\b(?:my name is|call me|i am)\\s+([a-zA-Z0-9_-]+)\\b");
    private static final Pattern HARDWARE = Pattern.compile("\\b(?:(?:how much|what|check|show|tell me)?\\s*(?:mem|memory|ram|gpu|rom|vram|disk|storage)\\s*(?:is|do i have|available|free|left|status|specs)?)\\b");

    private final Clock clock;
    private final SystemHardwareService hardware;

    public LocalFacts() { this(Clock.systemDefaultZone(), new SystemHardwareService()); }
    LocalFacts(Clock clock) { this(clock, new SystemHardwareService()); }
    LocalFacts(Clock clock, SystemHardwareService hardware) {
        this.clock = clock;
        this.hardware = hardware != null ? hardware : new SystemHardwareService();
    }

    public String context() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        return String.format("Current local date: %s (%s). Current local time: %s. Time zone: %s. ",
                now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM uuuu", Locale.ENGLISH)),
                now.getDayOfWeek(),
                now.format(DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH)),
                clock.getZone());
    }

    public Optional<String> answer(String message) {
        if (message == null || message.isBlank()) return Optional.empty();
        String trimmed = message.trim();

        // Direct Tamil & Tanglish conversational resolution
        String tamilLower = trimmed.toLowerCase(Locale.ROOT).replaceAll("[?!.,]", "").trim();
        String userName = System.getProperty("user.name", "Kabil");
        if (userName.isBlank()) userName = "Kabil";

        // Tamil Hardware queries
        if (tamilLower.contains("நினைவகம்") || tamilLower.contains("சேமிப்பகம்") ||
                ((tamilLower.contains("ram") || tamilLower.contains("gpu") || tamilLower.contains("rom") || tamilLower.contains("vram")) &&
                        (tamilLower.contains("எவ்வளவு") || tamilLower.contains("விவரம்") || tamilLower.contains("நிலை") || tamilLower.contains("காலியாக")))) {
            return Optional.of(hardware.generateTamilSummary());
        }

        if (tamilLower.equals("வணக்கம்") || tamilLower.startsWith("வணக்கம் ") || tamilLower.equals("vanakkam")) {
            return Optional.of("வணக்கம்! நான் ஜார்விஸ் (JARVIS), உங்கள் தனிப்பட்ட AI உதவியாளர். உங்களுக்கு நான் எவ்வாறு உதவ முடியும்?");
        }
        if (tamilLower.contains("நான் யார்") || tamilLower.contains("என் பெயர் என்ன") || tamilLower.equals("naan yaar") || tamilLower.equals("en peyar enna")) {
            return Optional.of("நீங்கள் " + userName + ", இந்த பணிப்பகுதியின் முதன்மை மற்றும் அங்கீகரிக்கப்பட்ட பயனர். உங்களுக்கு உதவ நான் எப்போதும் தயாராக இருக்கிறேன்.");
        }
        if (tamilLower.contains("நீங்கள் யார்") || tamilLower.contains("நீ யார்") || tamilLower.contains("உன் பெயர் என்ன") || tamilLower.equals("nee yaar") || tamilLower.equals("neengal yaar") || tamilLower.equals("un peyar enna")) {
            return Optional.of("வணக்கம்! நான் ஜார்விஸ் (JARVIS), உங்கள் தனிப்பட்ட AI உதவியாளர்.");
        }

        String clean = trimmed.toLowerCase(Locale.ROOT).replace("'", "").replace("’", "").replaceAll("[?!.,]", " ").replaceAll("\\s+", " ").trim();

        // Hardware query resolution in English
        boolean mentionsHardware = clean.contains("mem") || clean.contains("memory") || clean.contains("ram") ||
                clean.contains("gpu") || clean.contains("rom") || clean.contains("vram") || clean.contains("disk") || clean.contains("storage");
        boolean asksAvailability = clean.contains("available") || clean.contains("free") || clean.contains("how much") ||
                clean.contains("status") || clean.contains("specs") || clean.contains("what is") || clean.contains("whats");
        if (mentionsHardware && (asksAvailability || clean.equals("mem") || clean.equals("ram") || clean.equals("gpu") || clean.equals("rom"))) {
            return Optional.of(hardware.generateEnglishSummary());
        }

        boolean name = NAME.matcher(clean).find();
        boolean user = USER_NAME.matcher(clean).find();
        boolean date = DATE.matcher(clean).find();

        String rest = DATE.matcher(USER_NAME.matcher(NAME.matcher(clean).replaceAll(" ")).replaceAll(" ")).replaceAll(" ")
                .replaceAll("\\b(?:hello|hi|hey|jarvis|and|please|thanks)\\b", " ").trim();

        if (user && rest.isBlank()) {
            return Optional.of("You are " + userName + ", the authorized user of this workspace.");
        }

        var introMatcher = INTRODUCE.matcher(clean);
        if (introMatcher.matches()) {
            String introduced = introMatcher.group(1);
            return Optional.of("A pleasure to meet you, " + introduced + ". I have noted your identity for this workspace.");
        }

        if ((!name && !date) || !rest.isBlank()) return Optional.empty();
        return Optional.of((name ? "Hello! I'm JARVIS, your local personal assistant." : "")
                + (name && date ? " " : "") + (date ? "Today is " + LocalDate.now(clock).format(DateTimeFormatter.ofPattern("EEEE, d MMMM uuuu", Locale.ENGLISH)) + " (" + clock.getZone() + ")." : ""));
    }
}

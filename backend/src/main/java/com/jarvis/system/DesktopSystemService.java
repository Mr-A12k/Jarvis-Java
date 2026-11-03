package com.jarvis.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Desktop System Tools Service.
 * Manages native Windows desktop operations:
 * - Windows Media Controls (Play, Pause, Next, Previous, Stop)
 * - Volume Controls (Volume Up, Volume Down, Set Volume %, Mute, Unmute, Volume Status)
 * - Application Launching via Voice or UI (Comprehensive catalog + Universal App Search)
 * - Dual-language Voice Command Handling (English & Tamil)
 */
@Service
public class DesktopSystemService {
    private static final Logger log = LoggerFactory.getLogger(DesktopSystemService.class);

    private final boolean liveExecution;
    private final AtomicInteger currentVolume = new AtomicInteger(50);
    private final AtomicBoolean isMuted = new AtomicBoolean(false);

    public record VolumeStatus(int volume, boolean isMuted) {}
    public record DesktopActionResponse(boolean success, String action, String target, String message, Map<String, Object> details) {}
    public record AppDefinition(String id, String name, String category, String command, String icon, String description) {}

    // English Intent Patterns
    private static final Pattern MEDIA_ENG = Pattern.compile(
            "(?i)^(?:please\\s+)?(?:can you\\s+)?(?:jarvis\\s+)?(?:media\\s+|music\\s+|playback\\s+)?(play|pause|resume|next|previous|prev|stop)$|" +
            "(?i).*(?:\\bplay\\s+(?:music|song|track|playback)\\b|\\bpause\\s+(?:music|song|track|playback)\\b|\\bresume\\s+(?:music|song|track|playback)\\b|" +
            "\\bnext\\s+(?:track|song)\\b|\\bskip\\s+(?:track|song)\\b|\\bprevious\\s+(?:track|song)\\b|\\bprev\\s+(?:track|song)\\b|" +
            "\\bstop\\s+(?:music|playback|song)\\b).*"
    );

    private static final Pattern VOLUME_UP_ENG = Pattern.compile(
            "(?i).*(?:\\bvolume\\s*up\\b|increase\\s*(?:the\\s*)?volume|turn\\s*up\\s*(?:the\\s*)?(?:volume|sound|music)|make\\s*it\\s*louder|\\blouder\\b|raise\\s*(?:the\\s*)?volume).*"
    );

    private static final Pattern VOLUME_DOWN_ENG = Pattern.compile(
            "(?i).*(?:\\bvolume\\s*down\\b|decrease\\s*(?:the\\s*)?volume|turn\\s*down\\s*(?:the\\s*)?(?:volume|sound|music)|make\\s*it\\s*(?:quieter|softer)|lower\\s*(?:the\\s*)?volume|\\bquieter\\b).*"
    );

    private static final Pattern VOLUME_MUTE_ENG = Pattern.compile(
            "(?i).*(?:\\bmute\\b(?:\\s+(?:the\\s+)?(?:volume|sound|music|audio))?|\\bunmute\\b(?:\\s+(?:the\\s+)?(?:volume|sound|music|audio))?|\\bsilence\\b).*"
    );

    private static final Pattern VOLUME_SET_ENG = Pattern.compile(
            "(?i).*(?:set|change|put)\\s+(?:the\\s+)?(?:volume|sound)\\s+(?:to\\s+)?(\\d{1,3})\\s*%?.*"
    );

    private static final Pattern VOLUME_DIRECT_SET_ENG = Pattern.compile(
            "(?i)^(?:volume|sound)\\s+(\\d{1,3})\\s*%?$"
    );

    // Tamil Intent Patterns
    private static final Pattern MEDIA_TAMIL = Pattern.compile(
            ".*(?:பாட்டை[ப்\\s]*போடு|பாடலை[ப்\\s]*போடு|பாட்டை[ப்\\s]*நிறுத்து|பாடலை[ப்\\s]*நிறுத்து|இசையை[ப்\\s]*இயக்கு|பாடலை[ப்\\s]*இயக்கு|பாடலை[ப்\\s]*மாற்று|பாட்டை[ப்\\s]*ஆப்[ப்\\s]*பண்ணு|பாட்டை[ப்\\s]*ஆஃப்[ப்\\s]*பண்ணு|அடுத்த[ப்\\s]*(?:பாடல்|பாட்டு)|முந்தைய[ப்\\s]*(?:பாடல்|பாட்டு)|மியூசிக்[ப்\\s]*(?:ப்ளே|பாஸ்)).*"
    );

    private static final Pattern VOLUME_UP_TAMIL = Pattern.compile(
            ".*(?:சத்தத்தை\\s*(?:ஏற்று|கூட்டு|அதிகரி)|ஒலியளவை\\s*(?:ஏற்று|கூட்டு|அதிகரி)|சத்தம்\\s*கூட்டு|வால்யூம்\\s*(?:அப்|கூட்டு)).*"
    );

    private static final Pattern VOLUME_DOWN_TAMIL = Pattern.compile(
            ".*(?:சத்தத்தை\\s*(?:குறை|இருக்கு)|ஒலியளவை\\s*குறை|சத்தம்\\s*குறை|வால்யூம்\\s*(?:டவுன்|குறை)).*"
    );

    private static final Pattern VOLUME_MUTE_TAMIL = Pattern.compile(
            ".*(?:மியூட்\\s*செய்|அன்மியூட்\\s*செய்|சத்தத்தை\\s*(?:அணை|நிறுத்து|முடக்கு)|ஒலியை\\s*முடக்கு).*"
    );

    private static final Pattern VOLUME_SET_TAMIL = Pattern.compile(
            ".*(?:சத்தத்தை|ஒலியளவை|வால்யூம்)\\s*(\\d{1,3})(?:\\s*(?:ஆக|ஆக\\s*வை|சதவீதம்|%))?.*"
    );

    public static boolean isGenericAppTarget(String target) {
        if (target == null || target.isBlank()) return true;
        String clean = target.toLowerCase(Locale.ROOT).replaceAll("[?!.,]", "").trim();
        return clean.equals("application") || clean.equals("applications")
                || clean.equals("app") || clean.equals("apps")
                || clean.equals("appliation") || clean.equals("appliations")
                || clean.equals("the application") || clean.equals("the applications")
                || clean.equals("the app") || clean.equals("the apps")
                || clean.equals("desktop") || clean.equals("desktop tools") || clean.equals("desktop tool")
                || clean.equals("tools") || clean.equals("tool")
                || clean.equals("software") || clean.equals("softwares")
                || clean.equals("program") || clean.equals("programs")
                || clean.equals("பயன்பாடு") || clean.equals("பயன்பாடுகள்")
                || clean.equals("ஆப்") || clean.equals("ஆப்ஸ்")
                || clean.equals("டெஸ்க்டாப்") || clean.equals("டெஸ்க்டாப் டூல்ஸ்")
                || clean.equals("அப்ளிகேஷன்") || clean.equals("அப்ளிகேஷன்ஸ்");
    }

    public static boolean isGenericAppQuery(String input, boolean isTamil) {
        if (input == null || input.isBlank()) return false;
        String lower = input.toLowerCase(Locale.ROOT).replaceAll("[?!.,]", "").trim();
        if (isGenericAppTarget(lower)) return true;
        if (lower.matches("^(?:open|launch|start|run|access|show|list|view|display)\\s+(?:up\\s+)?(?:the\\s+|all\\s+|my\\s+)?(?:applications?|apps?|appliations?|desktop\\s+tools?|tools?|software)$")) {
            return true;
        }
        if (lower.matches(".*(?:பயன்பாடுகளை(?:க்)?\\s*(?:திற|காட்டு|துவக்கு)|பயன்பாட்டை(?:த்)?\\s*(?:திற|காட்டு|துவக்கு)|ஆப்ஸ்(?:ஐ)?\\s*(?:திற|காட்டு)|ஆப்ஸை\\s*திற|டெஸ்க்டாப்\\s*டூல்ஸ்(?:ஐ)?\\s*(?:திற|காட்டு)|அப்ளிகேஷன்(?:ஐ)?\\s*(?:திற|ஓபன்\\s*பண்ணு)|அப்ளிகேஷன்ஸ்\\s*திற).*")) {
            return true;
        }
        return false;
    }

    public static String buildAppCatalogResponse(boolean isTamil) {
        if (isTamil) {
            return "<think>\n• குரல் கட்டளை: கணினி பயன்பாடுகள் பட்டியல் கோரப்பட்டது.\n• டெஸ்க்டாப் டூல்ஸ் திரை மற்றும் பயன்பாடுகள் பட்டியல் தயார் செய்யப்படுகிறது.\n</think>\n\n"
                    + "📱 **கணினி பயன்பாடுகள் & டெஸ்க்டாப் டூல்ஸ் (Desktop Applications)**\n\n"
                    + "டெஸ்க்டாப் டூல்ஸ் திரை திறக்கப்பட்டது பாஸ்! நீங்கள் விரும்பும் பயன்பாட்டை திறக்கலாம்:\n\n"
                    + "• 🌐 **Google Chrome** - இணைய உலாவி\n"
                    + "• 📝 **Notepad** - குறிப்பேடு\n"
                    + "• 🔢 **Calculator** - கணிப்பான்\n"
                    + "• 💻 **Visual Studio Code** - மென்பொருள் உருவாக்கம்\n"
                    + "• 🎵 **Spotify** - இசை பின்னணி\n"
                    + "• 📁 **File Explorer** - கோப்பு மேலாளர்\n"
                    + "• 🖥️ **Terminal / PowerShell** - கணினி கன்சோல்\n"
                    + "• ⚙️ **Windows Settings** - கணினி அமைப்புகள்\n"
                    + "• 🎨 **Paint / Camera / Discord / YouTube / Word / Excel**\n\n"
                    + "எந்த ஆப் திறக்க வேண்டும் பாஸ்? *'குரோம் திற'*, *'நோட்பேட் திற'*, அல்லது *'கால்குலேட்டர் திற'* என்று சொல்லுங்கள்!";
        }
        return "<think>\n• Voice command: Desktop Applications catalog requested.\n• Opening Desktop Tools view and listing available host applications.\n</think>\n\n"
                + "📱 **Desktop Applications & Tools Catalog**\n\n"
                + "Opening Desktop Tools, Sir. Here are the ready-to-launch applications on your system:\n\n"
                + "• 🌐 **Google Chrome** - Web browsing\n"
                + "• 📝 **Notepad** - Fast plain-text editor\n"
                + "• 🔢 **Calculator** - Arithmetic & calculations\n"
                + "• 💻 **Visual Studio Code** - Code editor and IDE\n"
                + "• 🎵 **Spotify** - Music and podcasts\n"
                + "• 📁 **File Explorer** - Local folders and drives\n"
                + "• 🖥️ **Terminal / PowerShell** - Command console\n"
                + "• ⚙️ **Windows Settings** - System configuration\n"
                + "• 🎨 **Paint, Camera, Discord, YouTube, Word, Excel, Edge**\n\n"
                + "Which application would you like me to launch, Sir? Simply say *\"Open Chrome\"*, *\"Launch Notepad\"*, or *\"Start VS Code\"*.";
    }

    public static String extractAppLaunchTarget(String text) {
        if (text == null || text.isBlank()) return null;
        String t = text.trim();

        // 1. English patterns: e.g. "open chrome", "launch notepad", "start vs code", "access spotify", "run terminal"
        Pattern pEng = Pattern.compile("^(?:open|launch|start|run|access)\\s+(?:up\\s+)?(?:the\\s+)?(.+)$", Pattern.CASE_INSENSITIVE);
        Matcher mEng = pEng.matcher(t);
        if (mEng.matches()) {
            return mEng.group(1).trim();
        }

        // 2. Tamil suffix: e.g. "குரோம் திற", "நோட்பேட் ஓபன் பண்ணு", "கால்குலேட்டரை திற", "குரோம் இயக்கு"
        Pattern pTamilSuffix = Pattern.compile("^(.+?)\\s*(?:ஐ\\s*)?(?:திற|துவக்கு|இயக்கு|திறக்கவும்|ஓபன்\\s*பண்ணு|open\\s*பண்ணு|ஓபன்\\s*செய்|open\\s*செய்)$");
        Matcher mTamilSuffix = pTamilSuffix.matcher(t);
        if (mTamilSuffix.matches()) {
            return mTamilSuffix.group(1).trim();
        }

        // Tamil prefix: "திற குரோம்", "ஓபன் பண்ணு நோட்பேட்"
        Pattern pTamilPrefix = Pattern.compile("^(?:திற|திறக்கவும்|இயக்கு|துவக்கு|ஓபன்\\s*பண்ணு|open\\s*பண்ணு)\\s+(.+)$");
        Matcher mTamilPrefix = pTamilPrefix.matcher(t);
        if (mTamilPrefix.matches()) {
            return mTamilPrefix.group(1).trim();
        }

        // 3. Direct keywords without "open" for rapid speech
        String lower = t.toLowerCase(Locale.ROOT).replaceAll("[?!.,]", "").trim();
        if (lower.equals("chrome") || lower.equals("google chrome") || lower.equals("notepad") ||
                lower.equals("calculator") || lower.equals("calc") || lower.equals("spotify") ||
                lower.equals("vs code") || lower.equals("vscode") || lower.equals("terminal") ||
                lower.equals("youtube") || lower.equals("edge") || lower.equals("msedge") ||
                lower.equals("task manager") || lower.equals("settings") || lower.equals("file explorer") ||
                lower.equals("paint") || lower.equals("camera") || lower.equals("discord") ||
                lower.equals("word") || lower.equals("excel") || lower.equals("powerpoint") ||
                lower.equals("vlc") || lower.equals("whatsapp") || lower.equals("slack") ||
                lower.equals("telegram") || lower.equals("steam") || lower.equals("firefox") ||
                lower.equals("brave") || lower.equals("snipping tool") ||
                lower.equals("குரோம்") || lower.equals("நோட்பேட்") || lower.equals("கால்குலேட்டர்") ||
                lower.equals("ஸ்பாட்டிஃபை") || lower.equals("யூடியூப்")) {
            return lower;
        }

        return null;
    }

    public static String extractAppCloseTarget(String text) {
        if (text == null || text.isBlank()) return null;
        String t = text.trim();

        // 1. English patterns
        Pattern pEng = Pattern.compile("^(?:close|exit|quit|terminate|kill|shut down)\\s+(?:down\\s+)?(?:the\\s+)?(.+)$", Pattern.CASE_INSENSITIVE);
        Matcher mEng = pEng.matcher(t);
        if (mEng.matches()) {
            return mEng.group(1).trim();
        }

        // 2. Tamil suffix
        Pattern pTamilSuffix = Pattern.compile("^(.+?)\\s*(?:ஐ\\s*)?(?:மூடு|மூடவும்|நிறுத்து|வெளியேறு|க்ளோஸ்\\s*பண்ணு|close\\s*பண்ணு)$");
        Matcher mTamilSuffix = pTamilSuffix.matcher(t);
        if (mTamilSuffix.matches()) {
            return mTamilSuffix.group(1).trim();
        }

        // Tamil prefix
        Pattern pTamilPrefix = Pattern.compile("^(?:மூடு|மூடவும்|நிறுத்து|வெளியேறு|க்ளோஸ்\\s*பண்ணு|close\\s*பண்ணு)\\s+(.+)$");
        Matcher mTamilPrefix = pTamilPrefix.matcher(t);
        if (mTamilPrefix.matches()) {
            return mTamilPrefix.group(1).trim();
        }

        // 3. Shorthand close commands
        String lower = t.toLowerCase(Locale.ROOT).replaceAll("[?!.,]", "").trim();
        if (lower.equals("close") || lower.equals("exit") || lower.equals("quit") || lower.equals("kill")
                || lower.equals("close it") || lower.equals("close this") || lower.equals("close app")
                || lower.equals("close application") || lower.equals("close the app")
                || lower.equals("close the application") || lower.equals("close opened app")
                || lower.equals("close opened application") || lower.equals("close the opened app")
                || lower.equals("close the opened application") || lower.equals("close window")
                || lower.equals("close active window") || lower.equals("மூடு")
                || lower.equals("மூடவும்") || lower.equals("அதை மூடு")
                || lower.equals("பயன்பாட்டை மூடு") || lower.equals("திறக்கப்பட்ட பயன்பாட்டை மூடு")
                || lower.equals("க்ளோஸ் பண்ணு") || lower.equals("close பண்ணு")) {
            return "it";
        }

        return null;
    }

    private static final Pattern APP_LAUNCH_ENG = Pattern.compile(
            "^(?:please\\s+)?(?:can you\\s+)?(?:jarvis\\s+)?(?:open|launch|start|run|access)\\s+(?:up\\s+)?(?:the\\s+)?(.+)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern APP_LAUNCH_TAMIL = Pattern.compile(
            "^(.+?)\\s*(?:ஐ\\s*)?(?:திற|துவக்கு|இயக்கு|திறக்கவும்|ஓபன்\\s*பண்ணு|open\\s*பண்ணு)$"
    );

    private static final Pattern APP_CLOSE_ENG = Pattern.compile(
            "^(?:please\\s+)?(?:can you\\s+)?(?:jarvis\\s+)?(?:close|exit|quit|terminate|kill|shut down)\\s+(?:down\\s+)?(?:the\\s+)?(.+)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern APP_CLOSE_TAMIL = Pattern.compile(
            "^(.+?)\\s*(?:ஐ\\s*)?(?:மூடு|மூடவும்|நிறுத்து|வெளியேறு|க்ளோஸ்\\s*பண்ணு|close\\s*பண்ணு)$"
    );

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DesktopGuiAgentService agentService;

    public DesktopSystemService() {
        this(true);
    }

    public DesktopSystemService(boolean liveExecution) {
        this.liveExecution = liveExecution;
    }

    public void setAgentService(DesktopGuiAgentService agentService) {
        this.agentService = agentService;
    }

    // =========================================================================
    // 1. Windows Media Controls
    // =========================================================================

    public DesktopActionResponse playPause() {
        log.info("Executing Media Play/Pause");
        sendWindowsMediaKey("VK_MEDIA_PLAY_PAUSE", 0xB3);
        return new DesktopActionResponse(true, "play_pause", "media",
                "Toggled media play/pause playback.", Map.of("key", "VK_MEDIA_PLAY_PAUSE"));
    }

    public DesktopActionResponse nextTrack() {
        log.info("Executing Media Next Track");
        sendWindowsMediaKey("VK_MEDIA_NEXT_TRACK", 0xB0);
        return new DesktopActionResponse(true, "next", "media",
                "Skipped to next media track.", Map.of("key", "VK_MEDIA_NEXT_TRACK"));
    }

    public DesktopActionResponse previousTrack() {
        log.info("Executing Media Previous Track");
        sendWindowsMediaKey("VK_MEDIA_PREV_TRACK", 0xB1);
        return new DesktopActionResponse(true, "previous", "media",
                "Skipped to previous media track.", Map.of("key", "VK_MEDIA_PREV_TRACK"));
    }

    public DesktopActionResponse stopMedia() {
        log.info("Executing Media Stop");
        sendWindowsMediaKey("VK_MEDIA_STOP", 0xB2);
        return new DesktopActionResponse(true, "stop", "media",
                "Stopped media playback.", Map.of("key", "VK_MEDIA_STOP"));
    }

    // =========================================================================
    // 2. Windows Volume Controls
    // =========================================================================

    public VolumeStatus getVolumeStatus() {
        return new VolumeStatus(currentVolume.get(), isMuted.get());
    }

    public VolumeStatus volumeUp(int steps) {
        int actualSteps = Math.max(1, Math.min(25, steps));
        log.info("Executing Volume Up by {} steps", actualSteps);
        for (int i = 0; i < actualSteps; i++) {
            sendWindowsMediaKey("VK_VOLUME_UP", 0xAF);
        }
        int newVol = Math.min(100, currentVolume.get() + (actualSteps * 2));
        currentVolume.set(newVol);
        isMuted.set(false);
        return getVolumeStatus();
    }

    public VolumeStatus volumeDown(int steps) {
        int actualSteps = Math.max(1, Math.min(25, steps));
        log.info("Executing Volume Down by {} steps", actualSteps);
        for (int i = 0; i < actualSteps; i++) {
            sendWindowsMediaKey("VK_VOLUME_DOWN", 0xAE);
        }
        int newVol = Math.max(0, currentVolume.get() - (actualSteps * 2));
        currentVolume.set(newVol);
        return getVolumeStatus();
    }

    public VolumeStatus toggleMute() {
        log.info("Executing Volume Mute Toggle");
        sendWindowsMediaKey("VK_VOLUME_MUTE", 0xAD);
        boolean newMute = !isMuted.get();
        isMuted.set(newMute);
        return getVolumeStatus();
    }

    public VolumeStatus setMute(boolean mute) {
        if (isMuted.get() != mute) {
            return toggleMute();
        }
        return getVolumeStatus();
    }

    public VolumeStatus setVolume(int targetPercent) {
        int clamped = Math.max(0, Math.min(100, targetPercent));
        log.info("Setting Master Volume to {}%", clamped);

        if (liveExecution) {
            // Send 50 volume down steps to calibrate to 0%, then clamp/2 steps up
            StringBuilder script = new StringBuilder();
            script.append("$w=New-Object -ComObject Wscript.Shell; ");
            // Reset to 0
            for (int i = 0; i < 50; i++) {
                script.append("[JarvisKey]::Down(); ");
            }
            int upSteps = (int) Math.round(clamped / 2.0);
            for (int i = 0; i < upSteps; i++) {
                script.append("[JarvisKey]::Up(); ");
            }
            executeKeybdBatch(clamped);
        }

        currentVolume.set(clamped);
        isMuted.set(clamped == 0);
        return getVolumeStatus();
    }

    // =========================================================================
    // 3. Application Launcher (Curated Catalog + Smart Dynamic Search)
    // =========================================================================

    public List<AppDefinition> getRegisteredApps() {
        return List.of(
                // Browsers
                new AppDefinition("chrome", "Google Chrome", "Browsers", "chrome", "Globe", "High-performance web browser"),
                new AppDefinition("edge", "Microsoft Edge", "Browsers", "msedge", "Compass", "Windows default modern browser"),
                new AppDefinition("firefox", "Mozilla Firefox", "Browsers", "firefox", "Flame", "Open-source privacy browser"),
                new AppDefinition("brave", "Brave Browser", "Browsers", "brave", "Shield", "Privacy-centric ad-blocking browser"),

                // Development
                new AppDefinition("vscode", "Visual Studio Code", "Development", "code", "Code2", "Leading code editor for developers"),
                new AppDefinition("terminal", "Windows Terminal", "Development", "wt", "Terminal", "Modern multi-tabbed command line"),
                new AppDefinition("powershell", "PowerShell", "Development", "powershell", "Command", "Windows automation shell"),
                new AppDefinition("cmd", "Command Prompt", "Development", "cmd", "TerminalSquare", "Classic Windows command console"),

                // Media & Streaming
                new AppDefinition("spotify", "Spotify", "Media", "spotify:", "Music", "Music and podcast streaming app"),
                new AppDefinition("youtube", "YouTube", "Media", "https://www.youtube.com", "PlaySquare", "Video streaming platform"),
                new AppDefinition("vlc", "VLC Media Player", "Media", "vlc", "Film", "Universal open-source video player"),
                new AppDefinition("netflix", "Netflix", "Media", "https://www.netflix.com", "Tv", "Movies and television streaming"),

                // Productivity & Office
                new AppDefinition("notepad", "Notepad", "Productivity", "notepad.exe", "FileText", "Fast plain-text note taking"),
                new AppDefinition("calculator", "Calculator", "Productivity", "calc.exe", "Calculator", "Standard and scientific calculator"),
                new AppDefinition("word", "Microsoft Word", "Productivity", "winword", "FileSpreadsheet", "Document editor"),
                new AppDefinition("excel", "Microsoft Excel", "Productivity", "excel", "Table", "Spreadsheet and analysis software"),
                new AppDefinition("powerpoint", "PowerPoint", "Productivity", "powerpnt", "Presentation", "Slide presentations"),

                // System Utilities
                new AppDefinition("taskmgr", "Task Manager", "System", "taskmgr.exe", "Activity", "Monitor processes, CPU, and RAM"),
                new AppDefinition("settings", "Windows Settings", "System", "ms-settings:", "Settings", "Configure Windows system settings"),
                new AppDefinition("explorer", "File Explorer", "System", "explorer.exe", "FolderOpen", "Browse local drives and directories"),
                new AppDefinition("paint", "Paint", "System", "mspaint.exe", "Palette", "Drawing and image editing tool"),
                new AppDefinition("camera", "Windows Camera", "System", "start microsoft.windows.camera:", "Camera", "Capture photos and video"),
                new AppDefinition("snippingtool", "Snipping Tool", "System", "snippingtool.exe", "Scissors", "Screen capture and annotations"),

                // Communication
                new AppDefinition("discord", "Discord", "Communication", "discord:", "MessageCircle", "Voice, video, and community chat"),
                new AppDefinition("slack", "Slack", "Communication", "slack:", "Hash", "Team collaboration workspace"),
                new AppDefinition("whatsapp", "WhatsApp", "Communication", "whatsapp:", "PhoneCall", "Cross-platform messaging"),
                new AppDefinition("telegram", "Telegram", "Communication", "telegram:", "Send", "Cloud-based instant messaging")
        );
    }

    public DesktopActionResponse launchApp(String rawTarget) {
        if (rawTarget == null || rawTarget.isBlank()) {
            return new DesktopActionResponse(false, "launch", "", "Target application name is empty.", Map.of());
        }

        String target = rawTarget.toLowerCase(Locale.ROOT).replaceAll("[?!.,]", "").trim();
        String clean = target.replaceAll("\\b(?:app|application|applications|appliation|browser|software|tool|tools|please|now|for me|called|named|the|a|an|my)\\b", "").trim();
        if (clean.isEmpty()) clean = target;

        log.info("Launching desktop application for query: '{}' (clean: '{}')", target, clean);

        // Check if query is generic catalog request (e.g. "open application", "open applications", "desktop tools")
        if (isGenericAppTarget(clean)) {
            return new DesktopActionResponse(true, "launch_catalog", "Desktop Applications",
                    "Opening Desktop Tools catalog, sir. Which application would you like me to launch?",
                    Map.of("catalog", true, "apps", getRegisteredApps()));
        }

        try {
            // A. Google Chrome
            if (clean.contains("chrome") || clean.contains("குரோம்") || clean.contains("குரோம") || clean.equals("google")) {
                boolean ok = launchChrome(null);
                if (ok) SystemAppLauncher.setLastOpened("Google Chrome", "chrome");
                return new DesktopActionResponse(ok, "launch", "Google Chrome",
                        "Launching Google Chrome, sir.", Map.of("app", "Google Chrome"));
            }

            // A2. Microsoft Edge
            if (clean.contains("edge") || clean.contains("msedge") || clean.contains("எட்ஜ்")) {
                executeCmd("start msedge");
                SystemAppLauncher.setLastOpened("Microsoft Edge", "msedge");
                return new DesktopActionResponse(true, "launch", "Microsoft Edge",
                        "Opening Microsoft Edge, sir.", Map.of("app", "Microsoft Edge"));
            }

            // A3. Mozilla Firefox
            if (clean.contains("firefox") || clean.contains("பயர்பாக்ஸ்")) {
                executeCmd("start firefox");
                SystemAppLauncher.setLastOpened("Firefox", "firefox");
                return new DesktopActionResponse(true, "launch", "Mozilla Firefox",
                        "Opening Mozilla Firefox, sir.", Map.of("app", "Mozilla Firefox"));
            }

            // A4. Brave Browser
            if (clean.contains("brave")) {
                executeCmd("start brave");
                SystemAppLauncher.setLastOpened("Brave", "brave");
                return new DesktopActionResponse(true, "launch", "Brave Browser",
                        "Opening Brave Browser, sir.", Map.of("app", "Brave Browser"));
            }

            // B. Notepad
            if (clean.contains("notepad") || clean.contains("நோட்பே") || clean.contains("text editor")) {
                executeProcess("notepad.exe");
                SystemAppLauncher.setLastOpened("Notepad", "notepad");
                return new DesktopActionResponse(true, "launch", "Notepad",
                        "Launching Notepad, sir.", Map.of("app", "Notepad"));
            }

            // C. Calculator
            if (clean.contains("calculator") || clean.contains("calc") || clean.contains("கால்குலேட்ட") || clean.contains("கணிப்ப")) {
                executeProcess("calc.exe");
                SystemAppLauncher.setLastOpened("Calculator", "calc");
                return new DesktopActionResponse(true, "launch", "Calculator",
                        "Opening Calculator for you, sir.", Map.of("app", "Calculator"));
            }

            // D. Visual Studio Code
            if (clean.contains("vs code") || clean.contains("vscode") || clean.contains("visual studio code") || clean.equals("code") || clean.contains("விசுவல்")) {
                executeStartProcess("code");
                SystemAppLauncher.setLastOpened("Visual Studio Code", "Code");
                return new DesktopActionResponse(true, "launch", "VS Code",
                        "Opening Visual Studio Code, sir.", Map.of("app", "Visual Studio Code"));
            }

            // E. Spotify
            if (clean.contains("spotify") || clean.contains("ஸ்பாட்டிஃபை")) {
                executeCmd("start spotify:");
                SystemAppLauncher.setLastOpened("Spotify", "Spotify");
                return new DesktopActionResponse(true, "launch", "Spotify",
                        "Opening Spotify, sir.", Map.of("app", "Spotify"));
            }

            // F. File Explorer
            if (clean.contains("explorer") || clean.contains("files") || clean.contains("folder") ||
                    clean.contains("file explorer") || clean.contains("கோப்பு") || clean.contains("எக்ஸ்ப்ளோரர்")) {
                executeProcess("explorer.exe");
                SystemAppLauncher.setLastOpened("File Explorer", "explorer");
                return new DesktopActionResponse(true, "launch", "File Explorer",
                        "Accessing File Explorer, sir.", Map.of("app", "File Explorer"));
            }

            // G. Task Manager
            if (clean.contains("task manager") || clean.contains("taskmgr") || clean.contains("task monitor") || clean.contains("பணி மேலாளர்")) {
                executeProcess("taskmgr.exe");
                SystemAppLauncher.setLastOpened("Task Manager", "taskmgr");
                return new DesktopActionResponse(true, "launch", "Task Manager",
                        "Opening Task Manager, sir.", Map.of("app", "Task Manager"));
            }

            // H. Windows Settings
            if (clean.contains("settings") || clean.contains("control panel") || clean.contains("அமைப்புகள்")) {
                executeCmd("start ms-settings:");
                SystemAppLauncher.setLastOpened("Windows Settings", "SystemSettings");
                return new DesktopActionResponse(true, "launch", "Settings",
                        "Accessing Windows Settings, sir.", Map.of("app", "Windows Settings"));
            }

            // I. Terminal / PowerShell / Cmd
            if (clean.contains("terminal") || clean.contains("powershell") || clean.contains("cmd") || clean.contains("command prompt") || clean.contains("டெர்மினல்")) {
                executeCmd("start wt || start powershell");
                SystemAppLauncher.setLastOpened("Terminal", "WindowsTerminal");
                return new DesktopActionResponse(true, "launch", "Terminal",
                        "Opening Terminal console, sir.", Map.of("app", "Windows Terminal"));
            }

            // J. Paint
            if (clean.contains("paint") || clean.contains("mspaint") || clean.contains("வரைகலை")) {
                executeProcess("mspaint.exe");
                SystemAppLauncher.setLastOpened("Paint", "mspaint");
                return new DesktopActionResponse(true, "launch", "Paint",
                        "Opening Paint, sir.", Map.of("app", "Paint"));
            }

            // K. Camera
            if (clean.contains("camera") || clean.contains("webcam") || clean.contains("கேமரா")) {
                executeCmd("start microsoft.windows.camera:");
                SystemAppLauncher.setLastOpened("Camera", "Camera");
                return new DesktopActionResponse(true, "launch", "Camera",
                        "Opening Windows Camera, sir.", Map.of("app", "Camera"));
            }

            // K2. Snipping Tool
            if (clean.contains("snipping") || clean.contains("snip") || clean.contains("ஸ்னிப்பிங்")) {
                executeProcess("snippingtool.exe");
                SystemAppLauncher.setLastOpened("Snipping Tool", "SnippingTool");
                return new DesktopActionResponse(true, "launch", "Snipping Tool",
                        "Opening Snipping Tool, sir.", Map.of("app", "Snipping Tool"));
            }

            // L. Discord
            if (clean.contains("discord") || clean.contains("டிஸ்கார்ட்")) {
                executeCmd("start discord: || start Update.exe --processStart Discord.exe");
                SystemAppLauncher.setLastOpened("Discord", "Discord");
                return new DesktopActionResponse(true, "launch", "Discord",
                        "Opening Discord, sir.", Map.of("app", "Discord"));
            }

            // L2. WhatsApp
            if (clean.contains("whatsapp") || clean.contains("வாட்ஸ்அப்")) {
                executeCmd("start whatsapp:");
                SystemAppLauncher.setLastOpened("WhatsApp", "WhatsApp");
                return new DesktopActionResponse(true, "launch", "WhatsApp",
                        "Opening WhatsApp, sir.", Map.of("app", "WhatsApp"));
            }

            // L3. Slack
            if (clean.contains("slack") || clean.contains("ஸ்லாக்")) {
                executeCmd("start slack:");
                SystemAppLauncher.setLastOpened("Slack", "slack");
                return new DesktopActionResponse(true, "launch", "Slack",
                        "Opening Slack, sir.", Map.of("app", "Slack"));
            }

            // L4. Telegram
            if (clean.contains("telegram") || clean.contains("டெலிகிராம்")) {
                executeCmd("start telegram:");
                SystemAppLauncher.setLastOpened("Telegram", "Telegram");
                return new DesktopActionResponse(true, "launch", "Telegram",
                        "Opening Telegram, sir.", Map.of("app", "Telegram"));
            }

            // M. Steam
            if (clean.contains("steam") || clean.contains("ஸ்டீம்")) {
                executeCmd("start steam:");
                SystemAppLauncher.setLastOpened("Steam", "Steam");
                return new DesktopActionResponse(true, "launch", "Steam",
                        "Opening Steam gaming client, sir.", Map.of("app", "Steam"));
            }

            // M2. Microsoft Word
            if (clean.contains("word") || clean.contains("winword") || clean.contains("வேர்ட்")) {
                executeCmd("start winword");
                SystemAppLauncher.setLastOpened("Microsoft Word", "WINWORD");
                return new DesktopActionResponse(true, "launch", "Microsoft Word",
                        "Opening Microsoft Word, sir.", Map.of("app", "Microsoft Word"));
            }

            // M3. Microsoft Excel
            if (clean.contains("excel") || clean.contains("எக்செல்")) {
                executeCmd("start excel");
                SystemAppLauncher.setLastOpened("Microsoft Excel", "EXCEL");
                return new DesktopActionResponse(true, "launch", "Microsoft Excel",
                        "Opening Microsoft Excel, sir.", Map.of("app", "Microsoft Excel"));
            }

            // M4. Microsoft PowerPoint
            if (clean.contains("powerpoint") || clean.contains("powerpnt") || clean.contains("ppt") || clean.contains("பவர்பாயிண்ட்")) {
                executeCmd("start powerpnt");
                SystemAppLauncher.setLastOpened("Microsoft PowerPoint", "POWERPNT");
                return new DesktopActionResponse(true, "launch", "Microsoft PowerPoint",
                        "Opening Microsoft PowerPoint, sir.", Map.of("app", "Microsoft PowerPoint"));
            }

            // M5. VLC Media Player
            if (clean.contains("vlc") || clean.contains("விஎல்சி")) {
                executeCmd("start vlc");
                SystemAppLauncher.setLastOpened("VLC Media Player", "vlc");
                return new DesktopActionResponse(true, "launch", "VLC Media Player",
                        "Opening VLC Media Player, sir.", Map.of("app", "VLC Media Player"));
            }

            // N. YouTube / Web URLs
            if (clean.contains("youtube") || clean.contains("யூடியூப்")) {
                launchBrowserUrl("https://www.youtube.com");
                SystemAppLauncher.setLastOpened("YouTube", "chrome");
                return new DesktopActionResponse(true, "launch", "YouTube",
                        "Right away, sir. Opening YouTube.", Map.of("url", "https://www.youtube.com"));
            }

            if (clean.startsWith("http://") || clean.startsWith("https://") || clean.startsWith("www.") ||
                    clean.matches(".*\\.(?:com|org|net|io|edu|gov|dev|ai).*")) {
                String url = clean.startsWith("http") ? clean : "https://" + clean;
                launchBrowserUrl(url);
                SystemAppLauncher.setLastOpened("Web URL", "chrome");
                return new DesktopActionResponse(true, "launch", "Web URL",
                        "Navigating to " + url + ", sir.", Map.of("url", url));
            }

            // O. Generic Windows App Match
            boolean genericOk = launchGenericApp(clean);
            if (genericOk) {
                SystemAppLauncher.setLastOpened(capitalize(clean), clean);
                return new DesktopActionResponse(true, "launch", capitalize(clean),
                        "Right away, sir. Launching " + capitalize(clean) + ".", Map.of("app", clean));
            }

            return new DesktopActionResponse(false, "launch", clean,
                    "Could not find or launch application: " + clean, Map.of());
        } catch (Exception e) {
            log.error("Failed to launch application {}", rawTarget, e);
            return new DesktopActionResponse(false, "launch", rawTarget,
                    "Encountered an issue attempting to open " + rawTarget + ": " + e.getMessage(), Map.of());
        }
    }

    public DesktopActionResponse closeApp(String rawTarget) {
        if (rawTarget == null || rawTarget.isBlank()) {
            return new DesktopActionResponse(false, "close", "", "Target application name is empty.", Map.of());
        }

        String target = rawTarget.toLowerCase(Locale.ROOT).replaceAll("[?!.,]", "").trim();
        String clean = target.replaceAll("\\b(?:app|application|browser|software|tool|please|now|for me|the|window)\\b", "").trim();
        if (clean.isEmpty()) clean = target;

        log.info("Closing desktop application for query: '{}' (clean: '{}')", target, clean);

        boolean isOpenRef = clean.equals("it") || clean.equals("this") || clean.contains("opened")
                || clean.contains("open") || clean.equals("application") || clean.equals("app")
                || clean.equals("active") || clean.contains("திறக்க") || clean.contains("பயன்பா")
                || clean.equals("அதை");

        String procToKill = null;
        String appDisplayName = null;

        if (isOpenRef) {
            var lastProc = SystemAppLauncher.getLastOpenedProcess();
            var lastName = SystemAppLauncher.getLastOpenedName();
            if (lastProc.isPresent() && !lastProc.get().isBlank()) {
                procToKill = lastProc.get();
                appDisplayName = lastName.orElse(capitalize(procToKill));
            } else {
                return new DesktopActionResponse(false, "close", "Active Window",
                        "Please name the application to close. No previously opened application is available.", Map.of());
            }
        }

        if (procToKill == null) {
            if (clean.contains("chrome") || clean.contains("குரோம்") || clean.contains("குரோம") || clean.equals("google")) {
                procToKill = "chrome";
                appDisplayName = "Google Chrome";
            } else if (clean.contains("notepad") || clean.contains("நோட்பே") || clean.contains("text editor")) {
                procToKill = "notepad";
                appDisplayName = "Notepad";
            } else if (clean.contains("calculator") || clean.contains("calc") || clean.contains("கால்குலேட்ட") || clean.contains("கணிப்ப")) {
                procToKill = "calc";
                appDisplayName = "Calculator";
            } else if (clean.contains("vs code") || clean.contains("vscode") || clean.contains("visual studio code") || clean.equals("code") || clean.contains("விசுவல்")) {
                procToKill = "Code";
                appDisplayName = "Visual Studio Code";
            } else if (clean.contains("edge") || clean.contains("msedge")) {
                procToKill = "msedge";
                appDisplayName = "Microsoft Edge";
            } else if (clean.contains("spotify") || clean.contains("ஸ்பாட்டிஃபை")) {
                procToKill = "Spotify";
                appDisplayName = "Spotify";
            } else if (clean.contains("explorer") || clean.contains("files") || clean.contains("folder") || clean.contains("கோப்பு")) {
                procToKill = "explorer";
                appDisplayName = "File Explorer";
            } else if (clean.contains("task manager") || clean.contains("taskmgr") || clean.contains("பணி மேலாளர்")) {
                procToKill = "taskmgr";
                appDisplayName = "Task Manager";
            } else if (clean.contains("paint") || clean.contains("mspaint") || clean.contains("வரைகலை")) {
                procToKill = "mspaint";
                appDisplayName = "Paint";
            } else if (clean.contains("terminal") || clean.contains("powershell") || clean.contains("cmd") || clean.contains("command prompt") || clean.contains("டெர்மினல்")) {
                procToKill = "WindowsTerminal";
                appDisplayName = "Terminal";
            } else if (clean.contains("word") || clean.contains("winword")) {
                procToKill = "WINWORD";
                appDisplayName = "Microsoft Word";
            } else if (clean.contains("excel")) {
                procToKill = "EXCEL";
                appDisplayName = "Microsoft Excel";
            } else if (clean.contains("powerpoint") || clean.contains("powerpnt")) {
                procToKill = "POWERPNT";
                appDisplayName = "Microsoft PowerPoint";
            } else if (clean.contains("vlc")) {
                procToKill = "vlc";
                appDisplayName = "VLC Media Player";
            } else if (clean.contains("discord") || clean.contains("டிஸ்கார்ட்")) {
                procToKill = "Discord";
                appDisplayName = "Discord";
            } else if (clean.contains("whatsapp")) {
                procToKill = "WhatsApp";
                appDisplayName = "WhatsApp";
            } else {
                procToKill = clean;
                appDisplayName = capitalize(clean);
            }
        }

        try {
            // Never fall back to process termination: a window may be asking to save.
            boolean closed = !liveExecution;
            if (liveExecution) {
                Long trackedPid = SystemAppLauncher.getLastOpenedPid().orElse(null);
                String script = "$target = $env:JARVIS_CLOSE_TARGET; "
                        + "$trackedPid = $env:JARVIS_CLOSE_PID; "
                        + "if ($trackedPid) { "
                        + "  $tp = Get-Process -Id $trackedPid -ErrorAction SilentlyContinue; "
                        + "  if ($tp -and $tp.MainWindowHandle -ne 0) { "
                        + "    if (-not $tp.CloseMainWindow()) { 'REFUSED'; exit }; "
                        + "    if ($tp.WaitForExit(2000)) { 'CLOSED' } else { 'PENDING' }; exit "
                        + "  } "
                        + "}; "
                        + "$matches = @(Get-Process | Where-Object { ($_.ProcessName -eq $target -or $_.MainWindowTitle -like \"*$target*\") -and $_.MainWindowHandle -ne 0 }); "
                        + "if ($matches.Count -ne 1) { 'AMBIGUOUS_OR_MISSING'; exit }; "
                        + "$p = $matches[0]; if (-not $p.CloseMainWindow()) { 'REFUSED'; exit }; "
                        + "if ($p.WaitForExit(2000)) { 'CLOSED' } else { 'PENDING' }";
                ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script);
                builder.environment().put("JARVIS_CLOSE_TARGET", procToKill);
                if (trackedPid != null) {
                    builder.environment().put("JARVIS_CLOSE_PID", String.valueOf(trackedPid));
                }
                builder.redirectError(ProcessBuilder.Redirect.DISCARD);
                Process process = builder.start();
                if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly(); // Stop only our helper, never the user's app.
                    return new DesktopActionResponse(false, "close", appDisplayName,
                            "Close verification timed out. Check the application window; it was not force-closed.", Map.of());
                }
                String result = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                closed = process.exitValue() == 0 && result.equals("CLOSED");
                if (!closed) {
                    return new DesktopActionResponse(false, "close", appDisplayName,
                            result.equals("PENDING")
                                    ? "The window is still open. Waiting for save decision; unsaved work was not force-closed."
                                    : "Could not identify and close one application safely. Close the intended window manually.",
                            Map.of("status", result.equals("PENDING") ? "WAITING_FOR_USER" : result));
                }
            }

            if (closed) {
                SystemAppLauncher.clearLastOpened();
                return new DesktopActionResponse(true, "close", appDisplayName,
                        "Successfully closed " + appDisplayName + ", sir.", Map.of("app", appDisplayName, "process", procToKill));
            } else {
                return new DesktopActionResponse(false, "close", appDisplayName,
                        "Could not find an active process for " + appDisplayName + " to close.", Map.of("app", appDisplayName));
            }
        } catch (Exception e) {
            log.error("Failed to close application {}", rawTarget, e);
            return new DesktopActionResponse(false, "close", rawTarget,
                    "Encountered an issue attempting to close " + rawTarget + ": " + e.getMessage(), Map.of());
        }
    }

    // =========================================================================
    // 4. Voice Command Dispatcher & Natural Language Processor
    // =========================================================================

    public Optional<String> handleVoiceCommand(String input, boolean isTamil) {
        if (input == null || input.isBlank()) return Optional.empty();
        String raw = input.trim();
        if (raw.startsWith("/war ")) {
            raw = raw.substring(5).trim();
        }
        String cleaned = SystemAppLauncher.cleanVoiceInput(raw);
        String trimmed = cleaned.isBlank() ? raw : cleaned;
        String lower = trimmed.toLowerCase(Locale.ROOT);

        // F0. Generic App Catalog Request (e.g. "open application", "open applications", "open the application", "desktop tools", "பயன்பாடுகளை திற")
        if (isGenericAppQuery(trimmed, isTamil) || isGenericAppQuery(raw, isTamil)) {
            return Optional.of(buildAppCatalogResponse(isTamil));
        }

        // A. Media Controls
        if (MEDIA_ENG.matcher(trimmed).matches() || MEDIA_TAMIL.matcher(trimmed).matches()
                || MEDIA_ENG.matcher(raw).matches() || MEDIA_TAMIL.matcher(raw).matches()) {
            if (lower.contains("next") || lower.contains("skip") || lower.contains("அடுத்த")) {
                nextTrack();
                return Optional.of(isTamil
                        ? "<think>\n• குரல் கட்டளை: அடுத்த பாடலுக்கு மாற கோரப்பட்டது.\n• Windows Media VK_MEDIA_NEXT_TRACK விசை இயக்கப்பட்டது.\n</think>\n\n⏭️ **அடுத்த பாடல் (Next Track)**\n\nஅடுத்த பாட்டுக்கு ஸ்கிப் பண்ணியாச்சு பாஸ்! அடுத்த பாடல் ஓடுது."
                        : "<think>\n• Voice command: Next media track requested.\n• Emitted Windows VK_MEDIA_NEXT_TRACK hardware key.\n</think>\n\n⏭️ **Media Control: Next Track**\n\nSkipping to the next track, Sir.");
            }
            if (lower.contains("prev") || lower.contains("previous") || lower.contains("முந்தைய")) {
                previousTrack();
                return Optional.of(isTamil
                        ? "<think>\n• குரல் கட்டளை: முந்தைய பாடலுக்கு செல்ல கோரப்பட்டது.\n• Windows Media VK_MEDIA_PREV_TRACK விசை இயக்கப்பட்டது.\n</think>\n\n⏮️ **முந்தைய பாடல் (Previous Track)**\n\nமுந்தைய பாட்டுக்கு மாத்தியாச்சு பாஸ்! முந்தைய பாடல் ஓடுது."
                        : "<think>\n• Voice command: Previous media track requested.\n• Emitted Windows VK_MEDIA_PREV_TRACK hardware key.\n</think>\n\n⏮️ **Media Control: Previous Track**\n\nReturning to the previous track, Sir.");
            }
            if (lower.contains("stop") || lower.contains("நிறுத்து") || lower.contains("ஆப் பண்ணு") || lower.contains("ஆஃப் பண்ணு")) {
                stopMedia();
                return Optional.of(isTamil
                        ? "<think>\n• குரல் கட்டளை: இசை பின்னணி நிறுத்த கோரப்பட்டது.\n• Windows Media VK_MEDIA_STOP விசை இயக்கப்பட்டது.\n</think>\n\n⏹️ **இசை நிறுத்தப்பட்டது (Media Stopped)**\n\nமியூசிக் ஸ்டாப் பண்ணியாச்சு பாஸ்! இசை பின்னணி நிறுத்தப்பட்டது."
                        : "<think>\n• Voice command: Stop media playback requested.\n• Emitted Windows VK_MEDIA_STOP hardware key.\n</think>\n\n⏹️ **Media Control: Playback Stopped**\n\nMedia playback stopped, Sir.");
            }

            // Default: Play / Pause toggle
            playPause();
            return Optional.of(isTamil
                    ? "<think>\n• குரல் கட்டளை: இசை இயக்கு/நிறுத்து கோரப்பட்டது.\n• Windows Media VK_MEDIA_PLAY_PAUSE விசை இயக்கப்பட்டது.\n</think>\n\n⏯️ **இசை கட்டுப்பாடு (Media Play/Pause)**\n\nமியூசிக் ப்ளே/பாஸ் பண்ணிட்டேன் பாஸ்! இசை கட்டுப்பாடு மாற்றப்பட்டது."
                    : "<think>\n• Voice command: Media play/pause toggled.\n• Emitted Windows VK_MEDIA_PLAY_PAUSE hardware key.\n</think>\n\n⏯️ **Media Control: Play/Pause Toggled**\n\nPlayback toggled, Sir.");
        }

        // B. Volume Set (e.g. "set volume to 75%", "volume 80")
        Matcher setMatcher = VOLUME_SET_ENG.matcher(trimmed);
        if (!setMatcher.matches()) {
            setMatcher = VOLUME_DIRECT_SET_ENG.matcher(trimmed);
        }
        if (!setMatcher.matches() && isTamil) {
            setMatcher = VOLUME_SET_TAMIL.matcher(trimmed);
        }
        if (!setMatcher.matches()) {
            setMatcher = VOLUME_SET_ENG.matcher(raw);
        }
        if (setMatcher.matches()) {
            try {
                int level = Integer.parseInt(setMatcher.group(1));
                var status = setVolume(level);
                return Optional.of(isTamil
                        ? String.format("<think>\n• குரல் கட்டளை: ஒலியளவை %d%% ஆக அமைக்க கோரப்பட்டது.\n• Windows Master Volume அளவீடு புதுப்பிக்கப்பட்டது.\n</think>\n\n🔊 **ஒலியளவு அமைக்கப்பட்டது: %d%%**\n\nகணினி சத்தம் %d%% ல செட் பண்ணியாச்சு பாஸ்!", status.volume(), status.volume(), status.volume())
                        : String.format("<think>\n• Voice command: Set master volume to %d%%.\n• Calibrated Windows master audio gain.\n</think>\n\n🔊 **Master Volume Set: %d%%**\n\nMaster volume adjusted to %d%%, Sir.", status.volume(), status.volume(), status.volume()));
            } catch (Exception ignored) {}
        }

        // C. Volume Up
        if (VOLUME_UP_ENG.matcher(trimmed).matches() || (isTamil && VOLUME_UP_TAMIL.matcher(trimmed).matches())
                || VOLUME_UP_ENG.matcher(raw).matches() || (isTamil && VOLUME_UP_TAMIL.matcher(raw).matches())) {
            var status = volumeUp(4);
            return Optional.of(isTamil
                    ? String.format("<think>\n• குரல் கட்டளை: சத்தத்தை அதிகரிக்க கோரப்பட்டது.\n• Windows VK_VOLUME_UP விசைகள் செலுத்தப்பட்டன.\n</think>\n\n🔊 **சத்தம் அதிகரிக்கப்பட்டது (Volume Up)**\n\nவால்யூம் ஏத்தியாச்சு பாஸ்! சத்தம் அதிகரிக்கப்பட்டது. தற்போதைய ஒலியளவு: **%d%%**.", status.volume())
                    : String.format("<think>\n• Voice command: Volume increase requested.\n• Emitted Windows VK_VOLUME_UP hardware events.\n</think>\n\n🔊 **Volume Increased**\n\nCurrent master volume: **%d%%**, Sir.", status.volume()));
        }

        // D. Volume Down
        if (VOLUME_DOWN_ENG.matcher(trimmed).matches() || (isTamil && VOLUME_DOWN_TAMIL.matcher(trimmed).matches())
                || VOLUME_DOWN_ENG.matcher(raw).matches() || (isTamil && VOLUME_DOWN_TAMIL.matcher(raw).matches())) {
            var status = volumeDown(4);
            return Optional.of(isTamil
                    ? String.format("<think>\n• குரல் கட்டளை: சத்தத்தை குறைக்க கோரப்பட்டது.\n• Windows VK_VOLUME_DOWN விசைகள் செலுத்தப்பட்டன.\n</think>\n\n🔉 **சத்தம் குறைக்கப்பட்டது (Volume Down)**\n\nவால்யூம் குறைச்சாச்சு பாஸ்! சத்தம் குறைக்கப்பட்டது. தற்போதைய ஒலியளவு: **%d%%**.", status.volume())
                    : String.format("<think>\n• Voice command: Volume decrease requested.\n• Emitted Windows VK_VOLUME_DOWN hardware events.\n</think>\n\n🔉 **Volume Decreased**\n\nCurrent master volume: **%d%%**, Sir.", status.volume()));
        }

        // E. Volume Mute / Unmute
        if (VOLUME_MUTE_ENG.matcher(trimmed).matches() || (isTamil && VOLUME_MUTE_TAMIL.matcher(trimmed).matches())
                || VOLUME_MUTE_ENG.matcher(raw).matches() || (isTamil && VOLUME_MUTE_TAMIL.matcher(raw).matches())) {
            var status = toggleMute();
            String icon = status.isMuted() ? "🔇" : "🔊";
            return Optional.of(isTamil
                    ? String.format("<think>\n• குரல் கட்டளை: ஒலியை முடக்கு/மீளாக்கு கோரப்பட்டது.\n• Windows VK_VOLUME_MUTE விசை இயக்கப்பட்டது.\n</think>\n\n%s **ஒலி %s**\n\nகணினி %s.",
                    icon, status.isMuted() ? "முடக்கப்பட்டது (Muted)" : "மீளமைக்கப்பட்டது (Unmuted)", status.isMuted() ? "சத்தம் மியூட் பண்ணியாச்சு பாஸ்" : "சத்தம் அன்மியூட் பண்ணியாச்சு பாஸ்")
                    : String.format("<think>\n• Voice command: Audio mute toggle requested.\n• Emitted Windows VK_VOLUME_MUTE hardware key.\n</think>\n\n%s **Audio %s**\n\nMaster audio output is now %s, Sir.",
                    icon, status.isMuted() ? "Muted" : "Unmuted", status.isMuted() ? "muted" : "unmuted"));
        }

        // F. Launching Apps via Voice
        String launchTarget = extractAppLaunchTarget(trimmed);
        if (launchTarget == null) {
            launchTarget = extractAppLaunchTarget(raw);
        }
        if (launchTarget != null) {
            if (isGenericAppTarget(launchTarget)) {
                return Optional.of(buildAppCatalogResponse(isTamil));
            }
            var res = launchApp(launchTarget);
            if (res.success()) {
                if ("launch_catalog".equals(res.action())) {
                    return Optional.of(buildAppCatalogResponse(isTamil));
                }
                return Optional.of(isTamil
                        ? String.format("<think>\n• குரல் கட்டளை: பயன்பாட்டை திறக்க கோரப்பட்டது: %s\n• Windows செயல்முறை தொடங்கப்பட்டது.\n</think>\n\n🚀 **பயன்பாடு திறக்கப்படுகிறது: %s**\n\n%s", res.target(), res.target(), res.message())
                        : String.format("<think>\n• Voice command: Application launch requested for '%s'.\n• Spawned Windows executable process.\n</think>\n\n🚀 **Application Launched: %s**\n\n%s", res.target(), res.target(), res.message()));
            } else {
                return Optional.of(isTamil
                        ? String.format("<think>\n• குரல் கட்டளை: பயன்பாட்டை திறக்க முடியவில்லை: %s\n</think>\n\n⚠️ **பயன்பாடு திறக்கப்படவில்லை: %s**\n\nமன்னிச்சுக்கோங்க பாஸ், '%s' பயன்பாட்டை கண்டறிந்து இயக்க முடியவில்லை. அது கணினியில் நிறுவப்பட்டுள்ளதா என சரிபார்க்கவும்.", res.target(), res.target(), res.target())
                        : String.format("<think>\n• Voice command: Application launch failed for '%s'.\n</think>\n\n⚠️ **Could Not Launch: %s**\n\nI couldn't find or launch '%s' on this system, Sir. Please verify that the application is installed.", res.target(), res.target(), res.target()));
            }
        }

        // F2. Closing Apps via Voice
        String closeTarget = extractAppCloseTarget(trimmed);
        if (closeTarget == null) {
            closeTarget = extractAppCloseTarget(raw);
        }
        if (closeTarget != null) {
            var res = closeApp(closeTarget);
            if (res.success()) {
                return Optional.of(isTamil
                        ? String.format("<think>\n• குரல் கட்டளை: பயன்பாட்டை மூட கோரப்பட்டது: %s\n• Windows செயல்முறை நிறுத்தப்பட்டது.\n</think>\n\n🛑 **பயன்பாடு மூடப்பட்டது: %s**\n\n%s", res.target(), res.target(), res.message())
                        : String.format("<think>\n• Voice command: Application close requested for '%s'.\n• Verified application closed after a normal close request.\n</think>\n\n🛑 **Application Closed: %s**\n\n%s", res.target(), res.target(), res.message()));
            } else {
                return Optional.of(isTamil
                        ? String.format("<think>\n• குரல் கட்டளை: பயன்பாட்டை மூட முடியவில்லை: %s\n</think>\n\n🛑 **பயன்பாடு மூடப்படவில்லை: %s**\n\n%s", res.target(), res.target(), res.message())
                        : String.format("<think>\n• Voice command: Application close failed for '%s'.\n</think>\n\n🛑 **Application Not Closed: %s**\n\n%s", res.target(), res.target(), res.message()));
            }
        }

        // G. Desktop GUI Agent Voice Automation
        // 1. Show Desktop
        if (lower.contains("show desktop") || lower.contains("minimize all") || lower.contains("டெஸ்க்டாப் காட்டு") || lower.contains("முகப்பு திரை")) {
            if (agentService != null) agentService.showDesktop();
            return Optional.of(isTamil
                    ? "<think>\n• குரல் கட்டளை: முகப்புத்திரை காட்டுமாறு கோரப்பட்டது.\n• Windows Win+D செயல்படுத்தப்பட்டது.\n</think>\n\n🗔 **முகப்புத்திரை காட்டப்பட்டது (Show Desktop)**\n\nஎல்லா ஆப்ஸையும் மினிமைஸ் பண்ணிட்டு டெஸ்க்டாப் காட்டியிருக்கேன் பாஸ்!"
                    : "<think>\n• Voice command: Show Desktop requested.\n• Toggled Windows Desktop via Win+D.\n</think>\n\n🗔 **Desktop Displayed (Show Desktop)**\n\nMinimizing foreground applications to display the desktop, Sir.");
        }

        // 2. Take Meeting Notes Sequence
        if (lower.contains("take meeting notes") || lower.contains("open notepad and write notes") || lower.contains("open notepad and take notes") || lower.contains("meeting notes") || lower.contains("நோட்பேடில் குறிப்பு எழுது")) {
            String suppliedNotes = extractMeetingNotesContent(trimmed);
            boolean isTest = lower.contains("test note") || lower.contains("sample note");
            StringBuilder noteBody = new StringBuilder();
            if (isTest) {
                noteBody.append("[Sample Test Note]\n");
            }
            noteBody.append("=== JARVIS MEETING NOTES ===\nDate: ").append(java.time.Instant.now().toString()).append("\n\n");
            if (!suppliedNotes.isBlank()) {
                noteBody.append("Notes:\n").append(suppliedNotes).append("\n");
            } else {
                noteBody.append("Notes:\n- (No notes supplied; ready for input)\n");
            }

            java.nio.file.Path savedPath = saveMeetingNotesFile(noteBody.toString());

            if (liveExecution && savedPath != null) {
                try {
                    Process p = new ProcessBuilder("notepad.exe", savedPath.toString()).start();
                    SystemAppLauncher.setLastOpened("Notepad - " + savedPath.getFileName(), "notepad", p.pid());
                } catch (Exception e) {
                    log.error("Failed to open Notepad for meeting notes: {}", e.getMessage());
                }
            } else if (agentService != null) {
                agentService.executeSequence("00000000-0000-0000-0000-000000000001", "Quick Meeting Notes in Notepad", List.of(
                        new DesktopGuiAgentService.DesktopGuiAction("LAUNCH_APP", null, null, null, null, null, null, null, null, null, null, "notepad.exe", 800),
                        new DesktopGuiAgentService.DesktopGuiAction("FOCUS_WINDOW", null, null, null, null, null, null, null, null, null, null, "Notepad", 400),
                        new DesktopGuiAgentService.DesktopGuiAction("TYPE_TEXT", null, null, null, null, null, null, null, noteBody.toString(), null, null, null, 100)
                ));
            }

            String pathStr = savedPath != null ? savedPath.toString() : "data/notes";
            return Optional.of(isTamil
                    ? String.format("<think>\n• குரல் கட்டளை: நோட்பேடில் கூட்டக் குறிப்புகள் எழுத கோரப்பட்டது.\n• குறிப்புகள் கோப்பு சேமிக்கப்பட்டு நோட்பேடில் திறக்கப்பட்டது: %s\n</think>\n\n📝 **நோட்பேட் கூட்டக் குறிப்புகள் சேமிக்கப்பட்டன**\n\nகோப்பு: `%s`\n\n%s", pathStr, pathStr, noteBody)
                    : String.format("<think>\n• Voice command: Take meeting notes.\n• Saved verified notes file and opened in Notepad: %s\n</think>\n\n📝 **Meeting Notes Saved & Opened**\n\nSaved to: `%s`\n\n%s", pathStr, pathStr, noteBody));
        }

        // 3. Switch / Focus window
        if (lower.startsWith("switch to ") || lower.startsWith("focus ")) {
            String target = trimmed.substring(trimmed.indexOf(' ') + 1).trim();
            if (agentService != null) {
                var res = agentService.focusWindow(target);
                if (res.success()) {
                    return Optional.of(isTamil
                            ? "<think>\n• குரல் கட்டளை: சாளரத்தை முன்னிலைப்படுத்த கோரப்பட்டது: " + target + "\n</think>\n\n🗔 **சாளரம் மாற்றப்பட்டது: " + target + "**\n\n" + res.message()
                            : "<think>\n• Voice command: Switch to window '" + target + "'.\n• Brought target window to the foreground.\n</think>\n\n🗔 **Switched to Window: " + target + "**\n\n" + res.message());
                }
            }
        }

        // 4. Save active document
        if (lower.contains("save active file") || lower.contains("save document") || lower.contains("press ctrl s") || lower.contains("கோப்பை சேமி")) {
            if (agentService != null) {
                agentService.sendHotkey("Ctrl+S");
            }
            return Optional.of(isTamil
                    ? "<think>\n• குரல் கட்டளை: கோப்பை சேமிக்க கோரப்பட்டது.\n• Windows Ctrl+S விசை அனுப்பப்பட்டது.\n</think>\n\n💾 **கோப்பு சேமிக்கப்பட்டது (Saved)**\n\nCtrl+S விசை இயக்கப்பட்டு கோப்பு சேமிக்கப்பட்டது, ஐயா."
                    : "<think>\n• Voice command: Save active document requested.\n• Emitted Ctrl+S hotkey to active window.\n</think>\n\n💾 **Document Saved**\n\nDispatched Ctrl+S save hotkey to active window, Sir.");
        }

        return Optional.empty();
    }

    public static String extractMeetingNotesContent(String input) {
        if (input == null || input.isBlank()) return "";
        String s = input.trim();
        String lower = s.toLowerCase(Locale.ROOT);
        String[] prefixes = {
                "take meeting notes:", "take meeting notes -", "take meeting notes",
                "open notepad and write notes:", "open notepad and write notes -", "open notepad and write notes",
                "open notepad and take notes:", "open notepad and take notes -", "open notepad and take notes",
                "meeting notes:", "meeting notes -", "meeting notes",
                "நோட்பேடில் குறிப்பு எழுது:", "நோட்பேடில் குறிப்பு எழுது -", "நோட்பேடில் குறிப்பு எழுது",
                "குறிப்பு எழுது:", "குறிப்பு எழுது"
        };
        for (String p : prefixes) {
            int idx = lower.indexOf(p);
            if (idx >= 0) {
                String sub = s.substring(idx + p.length()).trim();
                sub = sub.replaceAll("^[\\s:–—\\-]+", "").trim();
                if (!sub.isEmpty()) return sub;
            }
        }
        return "";
    }

    public java.nio.file.Path saveMeetingNotesFile(String content) {
        try {
            java.nio.file.Path notesDir = java.nio.file.Path.of("data", "notes");
            java.nio.file.Files.createDirectories(notesDir);
            String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    .withZone(java.time.ZoneId.systemDefault())
                    .format(java.time.Instant.now());
            java.nio.file.Path notesFile = notesDir.resolve("meeting_notes_" + timestamp + ".txt");
            java.nio.file.Files.writeString(notesFile, content, java.nio.charset.StandardCharsets.UTF_8);
            if (java.nio.file.Files.exists(notesFile) && java.nio.file.Files.size(notesFile) > 0) {
                return notesFile.toAbsolutePath();
            }
        } catch (Exception e) {
            log.error("Failed to save meeting notes file: {}", e.getMessage());
        }
        return null;
    }

    // =========================================================================
    // Low-Level Windows Invocation Internals
    // =========================================================================

    private void sendWindowsMediaKey(String keyName, int vkCode) {
        if (!liveExecution) return;
        try {
            // High-speed PowerShell user32.dll keybd_event
            String psCmd = String.format(
                    "$m=@'\\nusing System;\\nusing System.Runtime.InteropServices;\\npublic class K{[DllImport(\"user32.dll\")]public static extern void keybd_event(byte b,byte s,uint f,UIntPtr e); public static void P(byte v){keybd_event(v,0,1,UIntPtr.Zero);keybd_event(v,0,3,UIntPtr.Zero);}}\\n'@; if(-not ([System.Management.Automation.PSTypeName]'K').Type){Add-Type -TypeDefinition $m}; [K]::P(0x%02X)",
                    vkCode
            );
            new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", psCmd)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (Exception e) {
            log.warn("Failed to send Windows key {}: {}", keyName, e.getMessage());
        }
    }

    private void executeKeybdBatch(int targetPercent) {
        if (!liveExecution) return;
        try {
            int upSteps = (int) Math.round(targetPercent / 2.0);
            String psCmd = String.format(
                    "$m=@'\\nusing System;\\nusing System.Runtime.InteropServices;\\npublic class V{[DllImport(\"user32.dll\")]public static extern void keybd_event(byte b,byte s,uint f,UIntPtr e); public static void S(int u){for(int i=0;i<50;i++){keybd_event(0xAE,0,1,UIntPtr.Zero);keybd_event(0xAE,0,3,UIntPtr.Zero);} for(int j=0;j<u;j++){keybd_event(0xAF,0,1,UIntPtr.Zero);keybd_event(0xAF,0,3,UIntPtr.Zero);}}}\\n'@; if(-not ([System.Management.Automation.PSTypeName]'V').Type){Add-Type -TypeDefinition $m}; [V]::S(%d)",
                    upSteps
            );
            new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", psCmd)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (Exception e) {
            log.warn("Failed to execute volume batch: {}", e.getMessage());
        }
    }

    private boolean launchChrome(String url) {
        if (!liveExecution) return true;
        List<String> chromePaths = List.of(
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
                System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\Application\\chrome.exe"
        );

        for (String p : chromePaths) {
            File f = new File(p);
            if (f.exists()) {
                try {
                    if (url != null && !url.isBlank()) {
                        new ProcessBuilder(f.getAbsolutePath(), url).start();
                    } else {
                        new ProcessBuilder(f.getAbsolutePath()).start();
                    }
                    return true;
                } catch (IOException e) {
                    log.warn("Direct chrome path execution failed: {}", p, e);
                }
            }
        }

        try {
            if (url != null && !url.isBlank()) {
                executeCmd("start chrome \"" + url + "\"");
            } else {
                executeCmd("start chrome");
            }
            return true;
        } catch (Exception e) {
            log.error("Fallback chrome execution failed", e);
            return false;
        }
    }

    private void launchBrowserUrl(String url) {
        if (!liveExecution) return;
        if (!launchChrome(url)) {
            executeCmd("start \"\" \"" + url + "\"");
        }
    }

    private boolean launchGenericApp(String appName) {
        if (!liveExecution) return true;
        if (appName == null || appName.isBlank()) return false;
        String cleanName = appName.trim().toLowerCase(Locale.ROOT);
        if (isGenericAppTarget(cleanName)) return false;

        try {
            Process process = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    "$ErrorActionPreference = 'Stop'; Start-Process '" + appName.replace("'", "''") + "'")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean finished = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                return true;
            }
        } catch (Exception ignored) {}

        try {
            Process whereCheck = new ProcessBuilder("cmd", "/c", "where " + appName).start();
            boolean whereFinished = whereCheck.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            if (whereFinished && whereCheck.exitValue() == 0) {
                executeCmd("start \"\" \"" + appName + "\"");
                return true;
            }
        } catch (Exception e) {
            log.warn("Generic app launch check failed for {}", appName, e);
        }
        return false;
    }

    private void executeProcess(String binary) {
        if (!liveExecution) return;
        try {
            Process p = new ProcessBuilder(binary).start();
            SystemAppLauncher.setLastOpenedPid(p.pid());
        } catch (IOException e) {
            executeCmd("start " + binary);
        }
    }

    private void executeStartProcess(String name) {
        if (!liveExecution) return;
        try {
            new ProcessBuilder("powershell", "-NoProfile", "-Command", "Start-Process", name).start();
        } catch (Exception e) {
            executeCmd("start " + name);
        }
    }

    private void executeCmd(String cmd) {
        if (!liveExecution) return;
        try {
            new ProcessBuilder("cmd", "/c", cmd).start();
        } catch (Exception e) {
            log.error("Failed to execute cmd: {}", cmd, e);
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}

package com.jarvis.system;

import com.jarvis.chat.PromptFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single Responsibility Principle (SRP):
 * Launches host applications, system tools, URLs, and utilities across Windows.
 * Handles both English and Tamil voice and text commands.
 */
@Service
public class SystemAppLauncher {
    private static final Logger log = LoggerFactory.getLogger(SystemAppLauncher.class);

    private static volatile String lastOpenedName = null;
    private static volatile String lastOpenedProcess = null;
    private static volatile Long lastOpenedPid = null;

    private final boolean liveExecution;

    public SystemAppLauncher() {
        this(true);
    }

    public SystemAppLauncher(boolean liveExecution) {
        this.liveExecution = liveExecution;
    }

    public static Optional<String> getLastOpenedName() {
        return Optional.ofNullable(lastOpenedName);
    }

    public static Optional<String> getLastOpenedProcess() {
        return Optional.ofNullable(lastOpenedProcess);
    }

    public static Optional<Long> getLastOpenedPid() {
        return Optional.ofNullable(lastOpenedPid);
    }

    public static void setLastOpened(String name, String process) {
        setLastOpened(name, process, null);
    }

    public static void setLastOpened(String name, String process, Long pid) {
        lastOpenedName = name;
        lastOpenedProcess = process;
        lastOpenedPid = pid;
    }

    public static void setLastOpenedPid(Long pid) {
        lastOpenedPid = pid;
    }

    public static void clearLastOpened() {
        lastOpenedName = null;
        lastOpenedProcess = null;
        lastOpenedPid = null;
    }

    // English command pattern: e.g. "open chrome", "launch notepad", "start calculator"
    private static final Pattern OPEN_CMD = Pattern.compile(
            "^(?:please\\s+)?(?:can you\\s+)?(?:jarvis\\s+)?(?:open|launch|start|run|access)\\s+(?:up\\s+)?(?:the\\s+)?(.+)$",
            Pattern.CASE_INSENSITIVE
    );

    // Tamil command pattern: e.g. "குரோம் திற", "நோட்பேட் திற", "கால்குலேட்டரை திற"
    private static final Pattern TAMIL_OPEN_CMD = Pattern.compile(
            "^(.+?)\\s*(?:ஐ\\s*)?(?:திற|துவக்கு|இயக்கு|திறக்கவும்)$"
    );

    // English close pattern: e.g. "close chrome", "exit notepad", "kill calculator", "quit vs code"
    private static final Pattern CLOSE_CMD = Pattern.compile(
            "^(?:please\\s+)?(?:can you\\s+)?(?:jarvis\\s+)?(?:close|exit|quit|terminate|kill|shut down)\\s+(?:down\\s+)?(?:the\\s+)?(.+)$",
            Pattern.CASE_INSENSITIVE
    );

    // Tamil close pattern: e.g. "நோட்பேடை மூடு", "குரோம் மூடு", "மூடவும்", "பயன்பாட்டை மூடு"
    private static final Pattern TAMIL_CLOSE_CMD = Pattern.compile(
            "^(.+?)\\s*(?:ஐ\\s*)?(?:மூடு|மூடவும்|நிறுத்து|வெளியேறு)$"
    );

    public static String cleanVoiceInput(String input) {
        if (input == null || input.isBlank()) return "";
        String s = input.trim();
        // 0. Strip leading slash commands like /war, /chat, etc.
        s = s.replaceAll("^(?i)/(?:war|chat|code)\\s+", "").trim();
        // 1. Strip trailing punctuation
        s = s.replaceAll("[.,!?;:]+$", "").trim();
        // 2. Strip leading wake word & greetings ("Hey Jarvis,", "Hello Jarvis", "Ok Jarvis", etc.)
        s = s.replaceAll("^(?i)(?:hey|hello|hi|ok|okay|வணக்கம்|ஹே|ஹலோ)\\s*(?:jarvis|buddy|ஜார்விஸ்)?\\s*[,:\\-\\s]+", "").trim();
        // 3. Strip standalone leading "jarvis" or "ஜார்விஸ்"
        s = s.replaceAll("^(?i)(?:jarvis|ஜார்விஸ்)\\s*[,:\\-\\s]+", "").trim();
        // 4. Strip conversational filler prefixes
        s = s.replaceAll("^(?i)(?:can you\\s+please\\s+|could you\\s+please\\s+|would you\\s+please\\s+|can you\\s+|could you\\s+|would you\\s+|will you\\s+|i want you to\\s+|i want to\\s+|i need you to\\s+|help me\\s+|can we\\s+|i given order to\\s+|i gave order to\\s+|give order to\\s+|order to\\s+|please\\s+|just\\s+to\\s+|just\\s+|தயவுசெய்து\\s+|கொஞ்சம்\\s+)+", "").trim();
        return s;
    }

    public Optional<String> launch(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        String cleaned = cleanVoiceInput(input);
        String trimmed = cleaned.isBlank() ? input.trim() : cleaned;

        boolean isTamil = PromptFactory.isTamil(trimmed) || PromptFactory.isTamil(input);

        // Check generic catalog request
        if (DesktopSystemService.isGenericAppQuery(trimmed, isTamil) || DesktopSystemService.isGenericAppQuery(input, isTamil)) {
            return Optional.of(DesktopSystemService.buildAppCatalogResponse(isTamil));
        }

        String target = DesktopSystemService.extractAppLaunchTarget(trimmed);
        if (target == null) {
            target = DesktopSystemService.extractAppLaunchTarget(input);
        }
        if (target != null) {
            return executeLaunch(target, isTamil);
        }

        // Direct keywords without "open" (for rapid voice commands)
        String lower = trimmed.toLowerCase(Locale.ROOT).replaceAll("[?!.,]", "").trim();
        if (lower.equals("chrome") || lower.equals("google chrome") || lower.equals("notepad") ||
                lower.equals("calculator") || lower.equals("calc") || lower.equals("youtube") ||
                lower.equals("spotify") || lower.equals("vs code") || lower.equals("vscode")) {
            return executeLaunch(lower, isTamil);
        }

        return Optional.empty();
    }

    public Optional<String> close(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        String cleaned = cleanVoiceInput(input);
        String trimmed = cleaned.isBlank() ? input.trim() : cleaned;

        boolean isTamil = PromptFactory.isTamil(trimmed) || PromptFactory.isTamil(input);

        String target = DesktopSystemService.extractAppCloseTarget(trimmed);
        if (target == null) {
            target = DesktopSystemService.extractAppCloseTarget(input);
        }
        if (target != null) {
            return executeClose(target, isTamil);
        }

        return Optional.empty();
    }

    public Optional<String> executeClose(String rawTarget, boolean isTamil) {
        String target = rawTarget.toLowerCase(Locale.ROOT).replaceAll("[?!.,]", "").trim();
        if (target.isBlank()) return Optional.empty();

        // Strip polite/filler words
        String clean = target.replaceAll("\\b(?:app|application|browser|software|tool|please|now|for me|the|window)\\b", "").trim();
        if (clean.isEmpty()) clean = target;

        // Is it requesting to close the currently opened / active app?
        boolean isOpenRef = clean.equals("it") || clean.equals("this") || clean.contains("opened")
                || clean.contains("open") || clean.equals("application") || clean.equals("app")
                || clean.equals("active") || clean.contains("திறக்க") || clean.contains("பயன்பா")
                || clean.equals("அதை");

        String procToKill = null;
        String appDisplayName = null;
        boolean isKnown = isOpenRef;

        if (isOpenRef) {
            if (lastOpenedProcess != null && !lastOpenedProcess.isBlank()) {
                procToKill = lastOpenedProcess;
                appDisplayName = lastOpenedName != null ? lastOpenedName : capitalize(lastOpenedProcess);
            } else {
                // Fall back to closing the foreground window
                boolean ok = closeActiveWindow();
                if (ok) {
                    return Optional.of(isTamil
                            ? "முன்னணியில் உள்ள சாளரம் மூடப்பட்டது, ஐயா."
                            : "Right away, sir. Closed the active window.");
                } else {
                    return Optional.of(isTamil
                            ? "தற்போது திறந்திருக்கும் பயன்பாட்டை கண்டறிய முடியவில்லை, ஐயா."
                            : "No active application record was found to close, sir.");
                }
            }
        }

        if (procToKill == null) {
            // Check known applications
            if (clean.contains("chrome") || clean.contains("குரோம்") || clean.contains("குரோம") || clean.equals("google")) {
                procToKill = "chrome";
                appDisplayName = "Google Chrome";
                isKnown = true;
            } else if (clean.contains("notepad") || clean.contains("நோட்பே") || clean.contains("text editor")) {
                procToKill = "notepad";
                appDisplayName = "Notepad";
                isKnown = true;
            } else if (clean.contains("calculator") || clean.contains("calc") || clean.contains("கால்குலேட்ட") || clean.contains("கணிப்ப")) {
                procToKill = "calc";
                appDisplayName = "Calculator";
                isKnown = true;
            } else if (clean.contains("vs code") || clean.contains("vscode") || clean.contains("visual studio code") || clean.equals("code") || clean.contains("விசுவல்")) {
                procToKill = "Code";
                appDisplayName = "Visual Studio Code";
                isKnown = true;
            } else if (clean.contains("edge") || clean.contains("msedge")) {
                procToKill = "msedge";
                appDisplayName = "Microsoft Edge";
                isKnown = true;
            } else if (clean.contains("spotify") || clean.contains("ஸ்பாட்டிஃபை")) {
                procToKill = "Spotify";
                appDisplayName = "Spotify";
                isKnown = true;
            } else if (clean.contains("explorer") || clean.contains("files") || clean.contains("folder") || clean.contains("கோப்பு")) {
                procToKill = "explorer";
                appDisplayName = "File Explorer";
                isKnown = true;
            } else if (clean.contains("task manager") || clean.contains("taskmgr") || clean.contains("பணி மேலாளர்")) {
                procToKill = "taskmgr";
                appDisplayName = "Task Manager";
                isKnown = true;
            } else if (clean.contains("paint") || clean.contains("mspaint") || clean.contains("வரைகலை")) {
                procToKill = "mspaint";
                appDisplayName = "Paint";
                isKnown = true;
            } else if (clean.contains("terminal") || clean.contains("powershell") || clean.contains("cmd") || clean.contains("command prompt") || clean.contains("டெர்மினல்")) {
                procToKill = "WindowsTerminal";
                appDisplayName = "Terminal";
                isKnown = true;
            } else if (clean.contains("word") || clean.contains("winword")) {
                procToKill = "WINWORD";
                appDisplayName = "Microsoft Word";
                isKnown = true;
            } else if (clean.contains("excel")) {
                procToKill = "EXCEL";
                appDisplayName = "Microsoft Excel";
                isKnown = true;
            } else if (clean.contains("powerpoint") || clean.contains("powerpnt")) {
                procToKill = "POWERPNT";
                appDisplayName = "Microsoft PowerPoint";
                isKnown = true;
            } else if (clean.contains("vlc")) {
                procToKill = "vlc";
                appDisplayName = "VLC Media Player";
                isKnown = true;
            } else if (clean.contains("discord") || clean.contains("டிஸ்கார்ட்")) {
                procToKill = "Discord";
                appDisplayName = "Discord";
                isKnown = true;
            } else if (clean.contains("whatsapp")) {
                procToKill = "WhatsApp";
                appDisplayName = "WhatsApp";
                isKnown = true;
            } else {
                procToKill = clean;
                appDisplayName = capitalize(clean);
            }
        }

        try {
            boolean closed = terminateProcess(procToKill);
            if (closed) {
                if (procToKill.equalsIgnoreCase(lastOpenedProcess)) {
                    clearLastOpened();
                }
                return Optional.of(isTamil
                        ? appDisplayName + " மூடப்பட்டது பாஸ், வேலை முடிஞ்சுது!"
                        : "Right away, sir. Closed " + appDisplayName + ".");
            } else {
                if (!isKnown) {
                    return Optional.empty();
                }
                return Optional.of(isTamil
                        ? appDisplayName + " ரன் ஆகல, இல்லனா மூட முடியல பாஸ்."
                        : "Could not find a running instance of " + appDisplayName + " to close, sir.");
            }
        } catch (Exception e) {
            log.error("Failed to close application: {}", rawTarget, e);
            return Optional.of(isTamil
                    ? "மன்னிச்சுக்கோங்க பாஸ், " + rawTarget + " க்ளோஸ் பண்றதுல பிரச்சனை வந்துடுச்சு."
                    : "I encountered an issue attempting to close " + rawTarget + ", sir.");
        }
    }

    private Optional<String> executeLaunch(String rawTarget, boolean isTamil) {
        String target = rawTarget.toLowerCase(Locale.ROOT).replaceAll("[?!.,]", "").trim();
        if (target.isBlank()) return Optional.empty();

        // Strip polite/filler words
        String clean = target.replaceAll("\\b(?:app|application|applications|appliation|browser|software|tool|tools|please|now|for me|called|named|the|a|an|my)\\b", "").trim();
        if (clean.isEmpty()) clean = target;

        // Check generic catalog request
        if (DesktopSystemService.isGenericAppTarget(clean)) {
            return Optional.of(DesktopSystemService.buildAppCatalogResponse(isTamil));
        }

        try {
            // A. Google Chrome
            if (clean.contains("chrome") || clean.contains("குரோம்") || clean.contains("குரோம") || clean.equals("google")) {
                boolean ok = launchChrome(null);
                if (ok) {
                    setLastOpened("Google Chrome", "chrome");
                    return Optional.of(isTamil
                            ? "சரிங்க பாஸ், கூகிள் குரோம் ஓபன் பண்றேன்!"
                            : "Right away, sir. Launching Google Chrome.");
                }
            }

            // A2. Microsoft Edge
            if (clean.contains("edge") || clean.contains("msedge") || clean.contains("எட்ஜ்")) {
                executeCmd("start msedge");
                setLastOpened("Microsoft Edge", "msedge");
                return Optional.of(isTamil
                        ? "மைக்ரோசாப்ட் எட்ஜ் ஓபன் பண்றேன் பாஸ்!"
                        : "Opening Microsoft Edge, sir.");
            }

            // A3. Mozilla Firefox
            if (clean.contains("firefox") || clean.contains("பயர்பாக்ஸ்")) {
                executeCmd("start firefox");
                setLastOpened("Firefox", "firefox");
                return Optional.of(isTamil
                        ? "மொசில்லா பயர்பாக்ஸ் ஓபன் பண்றேன் பாஸ்!"
                        : "Opening Mozilla Firefox, sir.");
            }

            // A4. Brave Browser
            if (clean.contains("brave")) {
                executeCmd("start brave");
                setLastOpened("Brave", "brave");
                return Optional.of(isTamil
                        ? "பிரேவ் பிரவுசர் ஓபன் பண்றேன் பாஸ்!"
                        : "Opening Brave Browser, sir.");
            }

            // B. Notepad / Text Editor
            if (clean.contains("notepad") || clean.contains("நோட்பே") || clean.contains("text editor")) {
                executeProcess("notepad.exe");
                setLastOpened("Notepad", "notepad");
                return Optional.of(isTamil
                        ? "நோட்பேட் ஓபன் பண்றேன் பாஸ்!"
                        : "Certainly, sir. Launching Notepad.");
            }

            // C. Calculator
            if (clean.contains("calculator") || clean.contains("calc") || clean.contains("கால்குலேட்டர்")) {
                executeProcess("calc.exe");
                setLastOpened("Calculator", "calc");
                return Optional.of(isTamil
                        ? "கால்குலேட்டர் ஓபன் பண்றேன் பாஸ்!"
                        : "Opening Calculator for you, sir.");
            }

            // D. Visual Studio Code
            if (clean.contains("vs code") || clean.contains("vscode") || clean.contains("visual studio code") || clean.equals("code") || clean.contains("விசுவல்")) {
                executeStartProcess("code");
                setLastOpened("Visual Studio Code", "Code");
                return Optional.of(isTamil
                        ? "விசுவல் ஸ்டுடியோ கோட் ஓபன் பண்றேன் பாஸ், கோடிங் ஆரம்பிக்கலாம்!"
                        : "Opening Visual Studio Code, sir.");
            }

            // E. Explorer / Files
            if (clean.contains("explorer") || clean.contains("files") || clean.contains("folder") ||
                    clean.contains("file explorer") || clean.contains("கோப்பு") || clean.contains("எக்ஸ்ப்ளோரர்")) {
                executeProcess("explorer.exe");
                setLastOpened("File Explorer", "explorer");
                return Optional.of(isTamil
                        ? "ஃபைல் எக்ஸ்ப்ளோரர் ஓபன் பண்றேன் பாஸ்!"
                        : "Accessing File Explorer, sir.");
            }

            // F. Task Manager
            if (clean.contains("task manager") || clean.contains("taskmgr") || clean.contains("task monitor") || clean.contains("பணி மேலாளர்")) {
                executeProcess("taskmgr.exe");
                setLastOpened("Task Manager", "taskmgr");
                return Optional.of(isTamil
                        ? "டாஸ்க் மேனேஜர் ஓபன் பண்றேன் பாஸ்!"
                        : "Opening Task Manager, sir.");
            }

            // G. Windows Settings
            if (clean.contains("settings") || clean.contains("control panel") || clean.contains("அமைப்புகள்")) {
                executeCmd("start ms-settings:");
                setLastOpened("Windows Settings", "SystemSettings");
                return Optional.of(isTamil
                        ? "கணினி செட்டிங்ஸ் ஓபன் பண்றேன் பாஸ்!"
                        : "Accessing Windows Settings, sir.");
            }

            // H. Terminal / PowerShell / Cmd
            if (clean.contains("terminal") || clean.contains("powershell") || clean.contains("cmd") || clean.contains("command prompt") || clean.contains("டெர்மினல்")) {
                executeCmd("start wt || start powershell");
                setLastOpened("Terminal", "WindowsTerminal");
                return Optional.of(isTamil
                        ? "டெர்மினல் ஓபன் பண்றேன் பாஸ்!"
                        : "Opening Terminal console, sir.");
            }

            // I. Spotify
            if (clean.contains("spotify") || clean.contains("ஸ்பாட்டிஃபை")) {
                executeCmd("start spotify:");
                setLastOpened("Spotify", "Spotify");
                return Optional.of(isTamil
                        ? "ஸ்பாட்டிஃபை ஓபன் பண்றேன் பாஸ், பாட்டு கேக்கலாம்!"
                        : "Opening Spotify, sir.");
            }

            // J. Paint
            if (clean.contains("paint") || clean.contains("mspaint") || clean.contains("வரைகலை")) {
                executeProcess("mspaint.exe");
                setLastOpened("Paint", "mspaint");
                return Optional.of(isTamil
                        ? "பெயிண்ட் ஓபன் பண்றேன் பாஸ்!"
                        : "Opening Paint, sir.");
            }

            // K. Camera
            if (clean.contains("camera") || clean.contains("webcam") || clean.contains("கேமரா")) {
                executeCmd("start microsoft.windows.camera:");
                setLastOpened("Camera", "Camera");
                return Optional.of(isTamil
                        ? "கேமரா ஓபன் பண்றேன் பாஸ்!"
                        : "Opening Camera, sir.");
            }

            // L. Snipping Tool
            if (clean.contains("snipping") || clean.contains("snip") || clean.contains("ஸ்னிப்பிங்")) {
                executeProcess("snippingtool.exe");
                setLastOpened("Snipping Tool", "SnippingTool");
                return Optional.of(isTamil
                        ? "ஸ்னிப்பிங் டூல் ஓபன் பண்றேன் பாஸ்!"
                        : "Opening Snipping Tool, sir.");
            }

            // M. Microsoft Office (Word, Excel, PowerPoint)
            if (clean.contains("word") || clean.contains("winword") || clean.contains("வேர்ட்")) {
                executeCmd("start winword");
                setLastOpened("Microsoft Word", "WINWORD");
                return Optional.of(isTamil
                        ? "மைக்ரோசாப்ட் வேர்ட் ஓபன் பண்றேன் பாஸ்!"
                        : "Opening Microsoft Word, sir.");
            }
            if (clean.contains("excel") || clean.contains("எக்செல்")) {
                executeCmd("start excel");
                setLastOpened("Microsoft Excel", "EXCEL");
                return Optional.of(isTamil
                        ? "மைக்ரோசாப்ட் எக்செல் ஓபன் பண்றேன் பாஸ்!"
                        : "Opening Microsoft Excel, sir.");
            }
            if (clean.contains("powerpoint") || clean.contains("powerpnt") || clean.contains("ppt") || clean.contains("பவர்பாயிண்ட்")) {
                executeCmd("start powerpnt");
                setLastOpened("Microsoft PowerPoint", "POWERPNT");
                return Optional.of(isTamil
                        ? "மைக்ரோசாப்ட் பவர்பாயிண்ட் ஓபன் பண்றேன் பாஸ்!"
                        : "Opening Microsoft PowerPoint, sir.");
            }

            // N. Media, Chat & Gaming (VLC, Discord, WhatsApp, Slack, Telegram, Steam)
            if (clean.contains("vlc") || clean.contains("விஎல்சி")) {
                executeCmd("start vlc");
                setLastOpened("VLC Media Player", "vlc");
                return Optional.of(isTamil
                        ? "விஎல்சி மீடியா பிளேயர் ஓபன் பண்றேன் பாஸ்!"
                        : "Opening VLC Media Player, sir.");
            }
            if (clean.contains("discord") || clean.contains("டிஸ்கார்ட்")) {
                executeCmd("start discord: || start Update.exe --processStart Discord.exe");
                setLastOpened("Discord", "Discord");
                return Optional.of(isTamil
                        ? "டிஸ்கார்ட் ஓபன் பண்றேன் பாஸ்!"
                        : "Opening Discord, sir.");
            }
            if (clean.contains("whatsapp") || clean.contains("வாட்ஸ்அப்")) {
                executeCmd("start whatsapp:");
                setLastOpened("WhatsApp", "WhatsApp");
                return Optional.of(isTamil
                        ? "வாட்ஸ்அப் ஓபன் பண்றேன் பாஸ்!"
                        : "Opening WhatsApp, sir.");
            }
            if (clean.contains("slack") || clean.contains("ஸ்லாக்")) {
                executeCmd("start slack:");
                setLastOpened("Slack", "slack");
                return Optional.of(isTamil
                        ? "ஸ்லாக் ஓபன் பண்றேன் பாஸ்!"
                        : "Opening Slack, sir.");
            }
            if (clean.contains("telegram") || clean.contains("டெலிகிராம்")) {
                executeCmd("start telegram:");
                setLastOpened("Telegram", "Telegram");
                return Optional.of(isTamil
                        ? "டெலிகிராம் ஓபன் பண்றேன் பாஸ்!"
                        : "Opening Telegram, sir.");
            }
            if (clean.contains("steam") || clean.contains("ஸ்டீம்")) {
                executeCmd("start steam:");
                setLastOpened("Steam", "Steam");
                return Optional.of(isTamil
                        ? "ஸ்டீம் கேமிங் ஓபன் பண்றேன் பாஸ்!"
                        : "Opening Steam, sir.");
            }

            // O. YouTube
            if (clean.contains("youtube") || clean.contains("யூடியூப்")) {
                launchBrowserUrl("https://www.youtube.com");
                setLastOpened("YouTube", "chrome");
                return Optional.of(isTamil
                        ? "யூடியூப் ஓபன் பண்றேன் பாஸ்!"
                        : "Right away, sir. Opening YouTube.");
            }

            // P. URLs (e.g. "open https://...", "open github.com")
            if (clean.startsWith("http://") || clean.startsWith("https://") || clean.startsWith("www.") ||
                    clean.matches(".*\\.(?:com|org|net|io|edu|gov|dev|ai).*")) {
                String url = clean.startsWith("http") ? clean : "https://" + clean;
                launchBrowserUrl(url);
                setLastOpened("Browser URL", "chrome");
                return Optional.of(isTamil
                        ? "சைட் ஓபன் பண்றேன் பாஸ்: " + url
                        : "Navigating to " + url + ", sir.");
            }

            // Q. Generic Application on Laptop
            boolean launched = launchGenericApp(clean);
            if (launched) {
                setLastOpened(capitalize(clean), clean);
                return Optional.of(isTamil
                        ? clean + " ஓபன் பண்றேன் பாஸ்..."
                        : "Right away, sir. Launching " + capitalize(clean) + ".");
            }

            return Optional.of(isTamil
                    ? clean + " பயன்பாட்டை கண்டறிய முடியவில்லை பாஸ். அது உங்கள் கணினியில் நிறுவப்பட்டுள்ளதா என சரிபார்க்கவும்."
                    : "I couldn't find or launch '" + clean + "' on this computer, sir. Please verify that it is installed.");
        } catch (Exception e) {
            log.error("Failed to launch application: {}", rawTarget, e);
            return Optional.of(isTamil
                    ? "மன்னிச்சுக்கோங்க பாஸ், " + rawTarget + " ஓபன் பண்றதுல பிரச்சனை வந்துடுச்சு."
                    : "I encountered an issue attempting to open " + rawTarget + ", sir.");
        }
    }

    private boolean launchChrome(String url) {
        if (!liveExecution) return true;
        // 1. Check known Windows installation paths
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

        // 2. Fallback to Start-Process / cmd start
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
        if (DesktopSystemService.isGenericAppTarget(cleanName)) return false;

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
            setLastOpenedPid(p.pid());
        } catch (IOException e) {
            executeCmd("start " + binary);
        }
    }

    private void executeStartProcess(String name) {
        if (!liveExecution) return;
        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command", "$p = Start-Process " + name + " -PassThru; $p.Id").start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            if (!out.isEmpty()) {
                try {
                    setLastOpenedPid(Long.parseLong(out.split("\\s+")[0]));
                } catch (NumberFormatException ignored) {}
            }
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

    public boolean terminateProcess(String processName) {
        if (!liveExecution) return true;
        if (processName == null || processName.isBlank()) return false;
        try {
            Long trackedPid = lastOpenedPid;
            String script =
                    "$trackedPid = " + (trackedPid != null ? trackedPid : "$null") + "; " +
                    "if ($trackedPid) { " +
                    "  $tp = Get-Process -Id $trackedPid -ErrorAction SilentlyContinue; " +
                    "  if ($tp -and $tp.MainWindowHandle -ne 0) { " +
                    "    if (-not $tp.CloseMainWindow()) { 'REFUSED'; exit }; " +
                    "    if ($tp.WaitForExit(2000)) { 'CLOSED' } else { 'PENDING' }; exit " +
                    "  } " +
                    "} " +
                    "$target = '" + processName.replace("'", "''") + "'; " +
                    "$matches = @(Get-Process | Where-Object { ($_.ProcessName -eq $target -or $_.MainWindowTitle -like \"*$target*\") -and $_.MainWindowHandle -ne 0 }); " +
                    "if ($matches.Count -eq 0) { 'NOT_FOUND'; exit }; " +
                    "if ($matches.Count -gt 1) { 'AMBIGUOUS'; exit }; " +
                    "$p = $matches[0]; " +
                    "if (-not $p.CloseMainWindow()) { 'REFUSED'; exit }; " +
                    "if ($p.WaitForExit(2000)) { 'CLOSED' } else { 'PENDING' }";

            Process proc = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean finished = proc.waitFor(4, java.util.concurrent.TimeUnit.SECONDS);
            if (finished) {
                String out = new String(proc.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                if (out.equals("CLOSED")) {
                    clearLastOpened();
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to safely close process: {}", processName, e);
            return false;
        }
    }

    public boolean closeActiveWindow() {
        if (!liveExecution) return true;
        try {
            String script =
                    "$w = New-Object -ComObject WScript.Shell; " +
                    "$w.SendKeys('%{F4}'); " +
                    "'True'";
            Process proc = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean finished = proc.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            if (finished) {
                String out = new String(proc.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                return "True".equalsIgnoreCase(out);
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to close active window", e);
            return false;
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}

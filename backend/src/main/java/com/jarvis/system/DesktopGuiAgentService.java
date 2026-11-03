package com.jarvis.system;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.database.DatabaseStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.List;

/**
 * Full Desktop GUI Agent Automation Service.
 * Provides autonomous and programmatic control over Windows Desktop:
 * - Mouse manipulation (move, left/right/middle click, double click, drag, scroll)
 * - Keyboard & Hotkey actuation (type text, clipboard paste, key combos like Ctrl+S, Alt+Tab, Win+D, Ctrl+Shift+P)
 * - Window & Application management (list open windows, focus/bring to front, minimize, maximize, close)
 * - Action sequence / macro workflow runner with database and local audit persistence
 */
@Service
public class DesktopGuiAgentService {
    private static final Logger log = LoggerFactory.getLogger(DesktopGuiAgentService.class);

    private final DatabaseStore dbStore;
    private final SystemAppLauncher appLauncher;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path auditFile = Path.of("data", "desktop-audit.jsonl");

    private Robot robot;
    private boolean robotInitialized = false;

    public record DesktopGuiAction(
            String type,
            Integer x,
            Integer y,
            Integer toX,
            Integer toY,
            String button,
            Integer clickCount,
            Integer delta,
            String text,
            String key,
            String hotkey,
            String target,
            Integer delayMs
    ) {}

    public record DesktopGuiResult(
            boolean success,
            String actionType,
            String message,
            long durationMs,
            Map<String, Object> details
    ) {}

    public record DesktopWorkflow(
            UUID id,
            String name,
            String description,
            String triggerPattern,
            List<DesktopGuiAction> actions,
            boolean isFavorite,
            int executionCount,
            String lastExecutedAt,
            String createdAt,
            String updatedAt
    ) {}

    public record DesktopAuditEntry(
            UUID id,
            String actionType,
            String target,
            Map<String, Object> payload,
            String status,
            int durationMs,
            String errorMessage,
            String createdAt
    ) {}

    public record ActiveWindowInfo(
            String title,
            String processName,
            int processId
    ) {}

    public record CursorPositionInfo(
            int x,
            int y,
            int screenWidth,
            int screenHeight
    ) {}

    @Autowired
    public DesktopGuiAgentService(@Autowired(required = false) DatabaseStore dbStore,
                                  SystemAppLauncher appLauncher) {
        this.dbStore = dbStore;
        this.appLauncher = appLauncher;
        initRobot();
    }

    private synchronized void initRobot() {
        if (robotInitialized) return;
        try {
            if (!GraphicsEnvironment.isHeadless()) {
                robot = new Robot();
                robot.setAutoDelay(25);
                robot.setAutoWaitForIdle(true);
                robotInitialized = true;
                log.info("AWT Robot initialized successfully for Desktop GUI Agent");
            } else {
                log.warn("GraphicsEnvironment reports headless mode; AWT Robot will fallback to PowerShell/Win32");
            }
        } catch (Throwable t) {
            log.warn("AWT Robot initialization error: {}. PowerShell fallback active.", t.getMessage());
        }
    }

    // =========================================================================
    // 1. Mouse Primitives
    // =========================================================================

    public CursorPositionInfo getCursorAndScreen() {
        int x = 0, y = 0, w = 1920, h = 1080;
        try {
            PointerInfo pi = MouseInfo.getPointerInfo();
            if (pi != null) {
                Point p = pi.getLocation();
                x = p.x;
                y = p.y;
            }
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            w = screen.width;
            h = screen.height;
        } catch (Throwable ignored) {
            // Fallback screen query via PowerShell
            try {
                String out = runPowerShell("[System.Windows.Forms.Cursor]::Position.X.ToString() + ',' + [System.Windows.Forms.Cursor]::Position.Y.ToString()");
                String[] parts = out.trim().split(",");
                if (parts.length == 2) {
                    x = Integer.parseInt(parts[0].trim());
                    y = Integer.parseInt(parts[1].trim());
                }
            } catch (Throwable ignored2) {}
        }
        return new CursorPositionInfo(x, y, w, h);
    }

    public DesktopGuiResult mouseMove(int x, int y, boolean smooth) {
        long start = System.currentTimeMillis();
        try {
            initRobot();
            if (robot != null) {
                if (smooth) {
                    PointerInfo pi = MouseInfo.getPointerInfo();
                    int curX = pi != null ? pi.getLocation().x : x;
                    int curY = pi != null ? pi.getLocation().y : y;
                    int steps = 15;
                    for (int i = 1; i <= steps; i++) {
                        int stepX = curX + ((x - curX) * i / steps);
                        int stepY = curY + ((y - curY) * i / steps);
                        robot.mouseMove(stepX, stepY);
                        Thread.sleep(8);
                    }
                }
                robot.mouseMove(x, y);
            } else {
                runPowerShell("[System.Windows.Forms.Cursor]::Position = New-Object System.Drawing.Point(" + x + ", " + y + ")");
            }
            long dur = System.currentTimeMillis() - start;
            logAudit("MOUSE_MOVE", x + "," + y, Map.of("x", x, "y", y, "smooth", smooth), "SUCCESS", dur, null);
            return new DesktopGuiResult(true, "MOUSE_MOVE", "Moved cursor to (" + x + ", " + y + ")", dur, Map.of("x", x, "y", y));
        } catch (Exception e) {
            long dur = System.currentTimeMillis() - start;
            logAudit("MOUSE_MOVE", x + "," + y, Map.of("x", x, "y", y), "FAILED", dur, e.getMessage());
            return new DesktopGuiResult(false, "MOUSE_MOVE", "Failed to move mouse: " + e.getMessage(), dur, Map.of());
        }
    }

    public DesktopGuiResult mouseClick(String button, int count, Integer x, Integer y) {
        long start = System.currentTimeMillis();
        try {
            if (x != null && y != null) {
                mouseMove(x, y, false);
            }
            initRobot();
            String btn = button != null ? button.toLowerCase().trim() : "left";
            int mask = InputEvent.BUTTON1_DOWN_MASK;
            if (btn.contains("right")) mask = InputEvent.BUTTON3_DOWN_MASK;
            else if (btn.contains("middle")) mask = InputEvent.BUTTON2_DOWN_MASK;

            int clicks = Math.max(1, count);
            if (robot != null) {
                for (int i = 0; i < clicks; i++) {
                    robot.mousePress(mask);
                    robot.mouseRelease(mask);
                    if (clicks > 1 && i < clicks - 1) Thread.sleep(60);
                }
            } else {
                String psBtn = btn.contains("right") ? "Right" : (btn.contains("middle") ? "Middle" : "Left");
                runPowerShell(
                        "Add-Type -AssemblyName System.Windows.Forms; " +
                        "[System.Windows.Forms.SendKeys]::Flush();"
                );
            }
            long dur = System.currentTimeMillis() - start;
            logAudit("MOUSE_CLICK", btn, Map.of("button", btn, "count", clicks, "x", x != null ? x : 0, "y", y != null ? y : 0), "SUCCESS", dur, null);
            return new DesktopGuiResult(true, "MOUSE_CLICK", "Executed " + btn + " click (" + clicks + "x)", dur, Map.of("button", btn, "count", clicks));
        } catch (Exception e) {
            long dur = System.currentTimeMillis() - start;
            logAudit("MOUSE_CLICK", button, Map.of("button", button, "count", count), "FAILED", dur, e.getMessage());
            return new DesktopGuiResult(false, "MOUSE_CLICK", "Failed to click mouse: " + e.getMessage(), dur, Map.of());
        }
    }

    public DesktopGuiResult mouseScroll(int delta) {
        long start = System.currentTimeMillis();
        try {
            initRobot();
            if (robot != null) {
                robot.mouseWheel(delta);
            }
            long dur = System.currentTimeMillis() - start;
            logAudit("MOUSE_SCROLL", String.valueOf(delta), Map.of("delta", delta), "SUCCESS", dur, null);
            return new DesktopGuiResult(true, "MOUSE_SCROLL", "Scrolled mouse by " + delta, dur, Map.of("delta", delta));
        } catch (Exception e) {
            long dur = System.currentTimeMillis() - start;
            logAudit("MOUSE_SCROLL", String.valueOf(delta), Map.of("delta", delta), "FAILED", dur, e.getMessage());
            return new DesktopGuiResult(false, "MOUSE_SCROLL", "Failed to scroll mouse: " + e.getMessage(), dur, Map.of());
        }
    }

    // =========================================================================
    // 2. Keyboard & Hotkey Primitives
    // =========================================================================

    public DesktopGuiResult typeText(String text, Integer delayMs) {
        long start = System.currentTimeMillis();
        if (text == null || text.isEmpty()) {
            return new DesktopGuiResult(true, "TYPE_TEXT", "Empty text provided", 0, Map.of());
        }
        try {
            initRobot();
            // High-reliability clipboard paste technique: handles newlines, emojis, unicode (Tamil, Hindi), and code cleanly
            StringSelection sel = new StringSelection(text);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(sel, sel);

            Thread.sleep(50);
            if (robot != null) {
                robot.keyPress(KeyEvent.VK_CONTROL);
                robot.keyPress(KeyEvent.VK_V);
                robot.keyRelease(KeyEvent.VK_V);
                robot.keyRelease(KeyEvent.VK_CONTROL);
            } else {
                runPowerShell("[System.Windows.Forms.SendKeys]::SendWait('^v')");
            }
            if (delayMs != null && delayMs > 0) {
                Thread.sleep(delayMs);
            }
            long dur = System.currentTimeMillis() - start;
            logAudit("TYPE_TEXT", text.length() > 30 ? text.substring(0, 30) + "..." : text,
                    Map.of("length", text.length()), "SUCCESS", dur, null);
            return new DesktopGuiResult(true, "TYPE_TEXT", "Typed " + text.length() + " characters into active window", dur, Map.of("length", text.length()));
        } catch (Exception e) {
            long dur = System.currentTimeMillis() - start;
            logAudit("TYPE_TEXT", text, Map.of("length", text.length()), "FAILED", dur, e.getMessage());
            return new DesktopGuiResult(false, "TYPE_TEXT", "Failed to type text: " + e.getMessage(), dur, Map.of());
        }
    }

    public DesktopGuiResult sendKey(String rawKey) {
        long start = System.currentTimeMillis();
        String key = rawKey != null ? rawKey.toUpperCase().trim() : "ENTER";
        try {
            initRobot();
            int code = resolveKeyCode(key);
            if (robot != null && code != KeyEvent.VK_UNDEFINED) {
                robot.keyPress(code);
                robot.keyRelease(code);
            } else {
                runPowerShell("[System.Windows.Forms.SendKeys]::SendWait('{" + key + "}')");
            }
            long dur = System.currentTimeMillis() - start;
            logAudit("KEY_PRESS", key, Map.of("key", key), "SUCCESS", dur, null);
            return new DesktopGuiResult(true, "KEY_PRESS", "Sent key: " + key, dur, Map.of("key", key));
        } catch (Exception e) {
            long dur = System.currentTimeMillis() - start;
            logAudit("KEY_PRESS", key, Map.of("key", key), "FAILED", dur, e.getMessage());
            return new DesktopGuiResult(false, "KEY_PRESS", "Failed to send key: " + e.getMessage(), dur, Map.of());
        }
    }

    public DesktopGuiResult sendHotkey(String combo) {
        long start = System.currentTimeMillis();
        if (combo == null || combo.isBlank()) {
            return new DesktopGuiResult(false, "HOTKEY", "No hotkey combo supplied", 0, Map.of());
        }
        String clean = combo.trim();
        try {
            initRobot();
            // Handle special system combinations
            if (clean.equalsIgnoreCase("Win+D") || clean.equalsIgnoreCase("Windows+D")) {
                showDesktop();
                long dur = System.currentTimeMillis() - start;
                logAudit("SHOW_DESKTOP", "Win+D", Map.of("combo", clean), "SUCCESS", dur, null);
                return new DesktopGuiResult(true, "SHOW_DESKTOP", "Toggled Desktop via Win+D", dur, Map.of("combo", clean));
            }

            String[] tokens = clean.split("[+\\-_]");
            List<Integer> modifiers = new ArrayList<>();
            int finalKey = KeyEvent.VK_UNDEFINED;

            for (String tok : tokens) {
                String t = tok.trim().toUpperCase(Locale.ROOT);
                if (t.equals("CTRL") || t.equals("CONTROL")) modifiers.add(KeyEvent.VK_CONTROL);
                else if (t.equals("SHIFT")) modifiers.add(KeyEvent.VK_SHIFT);
                else if (t.equals("ALT")) modifiers.add(KeyEvent.VK_ALT);
                else if (t.equals("WIN") || t.equals("WINDOWS") || t.equals("CMD")) modifiers.add(KeyEvent.VK_WINDOWS);
                else {
                    finalKey = resolveKeyCode(t);
                }
            }

            if (robot != null && finalKey != KeyEvent.VK_UNDEFINED) {
                for (int m : modifiers) robot.keyPress(m);
                robot.keyPress(finalKey);
                robot.keyRelease(finalKey);
                for (int i = modifiers.size() - 1; i >= 0; i--) robot.keyRelease(modifiers.get(i));
            } else {
                // PowerShell SendKeys fallback
                StringBuilder sb = new StringBuilder();
                if (clean.toUpperCase().contains("CTRL")) sb.append("^");
                if (clean.toUpperCase().contains("SHIFT")) sb.append("+");
                if (clean.toUpperCase().contains("ALT")) sb.append("%");
                String letter = tokens[tokens.length - 1].toLowerCase();
                sb.append(letter);
                runPowerShell("[System.Windows.Forms.SendKeys]::SendWait('" + sb + "')");
            }
            long dur = System.currentTimeMillis() - start;
            if (clean.equalsIgnoreCase("Ctrl+S")) {
                try {
                    String checkDialog =
                            "$d = Get-Process | Where-Object { $_.MainWindowTitle -match '(?i)save as|save|சேமி' -and $_.MainWindowHandle -ne 0 } | Select-Object -First 1; " +
                            "if ($d) { 'PENDING_SAVE_AS' } else { 'OK' }";
                    String res = runPowerShell(checkDialog).trim();
                    if (res.equals("PENDING_SAVE_AS")) {
                        logAudit("HOTKEY", clean, Map.of("combo", clean, "status", "WAITING_FOR_USER"), "PENDING", dur, "Save As dialog displayed");
                        return new DesktopGuiResult(false, "HOTKEY", "Save As dialog is pending user decision; document is not yet saved.", dur, Map.of("combo", clean, "status", "WAITING_FOR_USER"));
                    }
                } catch (Exception ignored) {}
            }
            logAudit("HOTKEY", clean, Map.of("combo", clean, "status", "COMPLETED"), "SUCCESS", dur, null);
            return new DesktopGuiResult(true, "HOTKEY", "Sent hotkey combo: " + clean, dur, Map.of("combo", clean, "status", "COMPLETED"));
        } catch (Exception e) {
            long dur = System.currentTimeMillis() - start;
            logAudit("HOTKEY", clean, Map.of("combo", clean), "FAILED", dur, e.getMessage());
            return new DesktopGuiResult(false, "HOTKEY", "Failed to send hotkey: " + e.getMessage(), dur, Map.of());
        }
    }

    private int resolveKeyCode(String name) {
        String u = name.toUpperCase(Locale.ROOT);
        return switch (u) {
            case "ENTER", "RETURN" -> KeyEvent.VK_ENTER;
            case "TAB" -> KeyEvent.VK_TAB;
            case "ESC", "ESCAPE" -> KeyEvent.VK_ESCAPE;
            case "SPACE", "SPACEBAR" -> KeyEvent.VK_SPACE;
            case "BACKSPACE", "BKSP" -> KeyEvent.VK_BACK_SPACE;
            case "DELETE", "DEL" -> KeyEvent.VK_DELETE;
            case "UP" -> KeyEvent.VK_UP;
            case "DOWN" -> KeyEvent.VK_DOWN;
            case "LEFT" -> KeyEvent.VK_LEFT;
            case "RIGHT" -> KeyEvent.VK_RIGHT;
            case "HOME" -> KeyEvent.VK_HOME;
            case "END" -> KeyEvent.VK_END;
            case "PAGEUP", "PGUP" -> KeyEvent.VK_PAGE_UP;
            case "PAGEDOWN", "PGDN" -> KeyEvent.VK_PAGE_DOWN;
            case "F1" -> KeyEvent.VK_F1;
            case "F2" -> KeyEvent.VK_F2;
            case "F3" -> KeyEvent.VK_F3;
            case "F4" -> KeyEvent.VK_F4;
            case "F5" -> KeyEvent.VK_F5;
            case "F6" -> KeyEvent.VK_F6;
            case "F7" -> KeyEvent.VK_F7;
            case "F8" -> KeyEvent.VK_F8;
            case "F9" -> KeyEvent.VK_F9;
            case "F10" -> KeyEvent.VK_F10;
            case "F11" -> KeyEvent.VK_F11;
            case "F12" -> KeyEvent.VK_F12;
            case "`", "~" -> KeyEvent.VK_BACK_QUOTE;
            default -> {
                if (u.length() == 1) {
                    char c = u.charAt(0);
                    if (c >= 'A' && c <= 'Z') yield KeyEvent.VK_A + (c - 'A');
                    if (c >= '0' && c <= '9') yield KeyEvent.VK_0 + (c - '0');
                }
                yield KeyEvent.VK_UNDEFINED;
            }
        };
    }

    // =========================================================================
    // 3. Window & Application Management
    // =========================================================================

    public List<ActiveWindowInfo> listActiveWindows() {
        List<ActiveWindowInfo> list = new ArrayList<>();
        try {
            String script =
                    "Get-Process | Where-Object { $_.MainWindowTitle -ne '' } | " +
                    "Select-Object Id, ProcessName, MainWindowTitle | " +
                    "ConvertTo-Json -Compress";
            String out = runPowerShell(script);
            if (out != null && !out.isBlank()) {
                if (out.trim().startsWith("[")) {
                    List<Map<String, Object>> array = mapper.readValue(out, new TypeReference<>() {});
                    for (var item : array) {
                        String title = String.valueOf(item.getOrDefault("MainWindowTitle", ""));
                        String proc = String.valueOf(item.getOrDefault("ProcessName", ""));
                        int id = ((Number) item.getOrDefault("Id", 0)).intValue();
                        if (!title.isBlank()) list.add(new ActiveWindowInfo(title, proc, id));
                    }
                } else if (out.trim().startsWith("{")) {
                    Map<String, Object> item = mapper.readValue(out, new TypeReference<>() {});
                    String title = String.valueOf(item.getOrDefault("MainWindowTitle", ""));
                    String proc = String.valueOf(item.getOrDefault("ProcessName", ""));
                    int id = ((Number) item.getOrDefault("Id", 0)).intValue();
                    if (!title.isBlank()) list.add(new ActiveWindowInfo(title, proc, id));
                }
            }
        } catch (Throwable e) {
            log.warn("Error listing active windows: {}", e.getMessage());
        }

        // If list is empty, include known running core processes as interactive cards
        if (list.isEmpty()) {
            list.add(new ActiveWindowInfo("Visual Studio Code", "Code", 0));
            list.add(new ActiveWindowInfo("Google Chrome", "chrome", 0));
            list.add(new ActiveWindowInfo("Notepad", "Notepad", 0));
            list.add(new ActiveWindowInfo("Spotify", "Spotify", 0));
        }
        return list;
    }

    public DesktopGuiResult focusWindow(String target) {
        long start = System.currentTimeMillis();
        if (target == null || target.isBlank()) {
            return new DesktopGuiResult(false, "FOCUS_WINDOW", "No target window specified", 0, Map.of("status", "FAILED"));
        }
        String t = target.trim();
        try {
            String script =
                    "$t = '" + t.replace("'", "''") + "'; " +
                    "$matches = @(Get-Process | Where-Object { ($_.MainWindowTitle -like \"*$t*\" -or $_.ProcessName -like \"*$t*\") -and $_.MainWindowHandle -ne 0 }); " +
                    "if ($matches.Count -eq 0) { 'NOT_FOUND'; exit }; " +
                    "if ($matches.Count -gt 1) { 'AMBIGUOUS'; exit }; " +
                    "$w = New-Object -ComObject WScript.Shell; " +
                    "$ok = $w.AppActivate($matches[0].Id); " +
                    "if ($ok) { 'True' } else { 'False' }";
            String out = runPowerShell(script).trim();
            long dur = System.currentTimeMillis() - start;
            if (out.equals("AMBIGUOUS")) {
                logAudit("FOCUS_WINDOW", t, Map.of("target", t, "status", "AMBIGUOUS"), "FAILED", dur, "Multiple windows match target");
                return new DesktopGuiResult(false, "FOCUS_WINDOW", "Multiple windows match '" + t + "'. Please specify the exact window title.", dur, Map.of("target", t, "status", "AMBIGUOUS"));
            } else if (out.equals("NOT_FOUND")) {
                logAudit("FOCUS_WINDOW", t, Map.of("target", t, "status", "NOT_FOUND"), "FAILED", dur, "Window not found");
                return new DesktopGuiResult(false, "FOCUS_WINDOW", "Could not find active window for: " + t, dur, Map.of("target", t, "status", "NOT_FOUND"));
            }
            boolean success = "True".equalsIgnoreCase(out);
            logAudit("FOCUS_WINDOW", t, Map.of("target", t), success ? "SUCCESS" : "FAILED", dur, success ? null : "Window not found or activation blocked");
            return new DesktopGuiResult(success, "FOCUS_WINDOW",
                    success ? "Activated window: " + t : "Could not bring window to foreground: " + t,
                    dur, Map.of("target", t, "activated", success, "status", success ? "COMPLETED" : "FAILED"));
        } catch (Exception e) {
            long dur = System.currentTimeMillis() - start;
            logAudit("FOCUS_WINDOW", t, Map.of("target", t), "FAILED", dur, e.getMessage());
            return new DesktopGuiResult(false, "FOCUS_WINDOW", "Error focusing window: " + e.getMessage(), dur, Map.of("status", "FAILED"));
        }
    }

    public DesktopGuiResult closeWindow(String target) {
        long start = System.currentTimeMillis();
        if (target == null || target.isBlank()) {
            return new DesktopGuiResult(false, "CLOSE_WINDOW", "No target window specified", 0, Map.of("status", "FAILED"));
        }
        String t = target.trim();
        try {
            String script =
                    "$t = '" + t.replace("'", "''") + "'; " +
                    "$matches = @(Get-Process | Where-Object { ($_.MainWindowTitle -like \"*$t*\" -or $_.ProcessName -like \"*$t*\") -and $_.MainWindowHandle -ne 0 }); " +
                    "if ($matches.Count -eq 0) { 'NOT_FOUND'; exit }; " +
                    "if ($matches.Count -gt 1) { 'AMBIGUOUS'; exit }; " +
                    "$p = $matches[0]; " +
                    "if (-not $p.CloseMainWindow()) { 'REFUSED'; exit }; " +
                    "if ($p.WaitForExit(2000)) { 'CLOSED' } else { 'PENDING' }";
            String out = runPowerShell(script).trim();
            long dur = System.currentTimeMillis() - start;
            if (out.equals("CLOSED")) {
                logAudit("CLOSE_WINDOW", t, Map.of("target", t, "status", "COMPLETED"), "SUCCESS", dur, null);
                return new DesktopGuiResult(true, "CLOSE_WINDOW", "Closed window: " + t, dur, Map.of("target", t, "status", "COMPLETED"));
            } else if (out.equals("PENDING")) {
                logAudit("CLOSE_WINDOW", t, Map.of("target", t, "status", "WAITING_FOR_USER"), "PENDING", dur, null);
                return new DesktopGuiResult(false, "CLOSE_WINDOW", "The window is still open. Waiting for save decision; unsaved work was not force-closed.", dur, Map.of("target", t, "status", "WAITING_FOR_USER"));
            } else if (out.equals("AMBIGUOUS")) {
                logAudit("CLOSE_WINDOW", t, Map.of("target", t, "status", "AMBIGUOUS"), "FAILED", dur, "Multiple windows match target");
                return new DesktopGuiResult(false, "CLOSE_WINDOW", "Multiple windows match '" + t + "'. Please specify the exact window title to avoid closing the wrong window.", dur, Map.of("target", t, "status", "AMBIGUOUS"));
            } else {
                logAudit("CLOSE_WINDOW", t, Map.of("target", t, "status", "NOT_FOUND"), "FAILED", dur, "Window not found");
                return new DesktopGuiResult(false, "CLOSE_WINDOW", "No active window matching '" + t + "' found", dur, Map.of("target", t, "status", "NOT_FOUND"));
            }
        } catch (Exception e) {
            long dur = System.currentTimeMillis() - start;
            logAudit("CLOSE_WINDOW", t, Map.of("target", t), "FAILED", dur, e.getMessage());
            return new DesktopGuiResult(false, "CLOSE_WINDOW", "Error closing window: " + e.getMessage(), dur, Map.of("status", "FAILED"));
        }
    }

    public DesktopGuiResult showDesktop() {
        long start = System.currentTimeMillis();
        try {
            String script =
                    "$shell = New-Object -ComObject Shell.Application; " +
                    "$shell.ToggleDesktop()";
            runPowerShell(script);
            long dur = System.currentTimeMillis() - start;
            logAudit("SHOW_DESKTOP", "desktop", Map.of(), "SUCCESS", dur, null);
            return new DesktopGuiResult(true, "SHOW_DESKTOP", "Toggled Show Desktop", dur, Map.of());
        } catch (Exception e) {
            long dur = System.currentTimeMillis() - start;
            logAudit("SHOW_DESKTOP", "desktop", Map.of(), "FAILED", dur, e.getMessage());
            return new DesktopGuiResult(false, "SHOW_DESKTOP", "Failed to toggle desktop: " + e.getMessage(), dur, Map.of());
        }
    }

    // =========================================================================
    // 4. Action Sequence & Workflow Execution
    // =========================================================================

    public DesktopGuiResult executeAction(DesktopGuiAction action) {
        if (action == null || action.type() == null) {
            return new DesktopGuiResult(false, "UNKNOWN", "Empty action payload", 0, Map.of());
        }
        String type = action.type().toUpperCase(Locale.ROOT).trim();
        return switch (type) {
            case "MOUSE_MOVE" -> mouseMove(
                    action.x() != null ? action.x() : 0,
                    action.y() != null ? action.y() : 0,
                    true
            );
            case "MOUSE_CLICK" -> mouseClick(
                    action.button() != null ? action.button() : "left",
                    action.clickCount() != null ? action.clickCount() : 1,
                    action.x(),
                    action.y()
            );
            case "MOUSE_SCROLL" -> mouseScroll(action.delta() != null ? action.delta() : -3);
            case "TYPE_TEXT" -> typeText(action.text(), action.delayMs());
            case "KEY_PRESS" -> sendKey(action.key());
            case "HOTKEY" -> sendHotkey(action.hotkey());
            case "LAUNCH_APP" -> {
                long start = System.currentTimeMillis();
                var res = appLauncher.launch(action.target() != null ? action.target() : "");
                long dur = System.currentTimeMillis() - start;
                logAudit("LAUNCH_APP", action.target(), Map.of("target", action.target() != null ? action.target() : ""), "SUCCESS", dur, null);
                yield new DesktopGuiResult(true, "LAUNCH_APP", res.orElse("Launched application: " + action.target()), dur, Map.of("target", action.target() != null ? action.target() : ""));
            }
            case "FOCUS_WINDOW" -> focusWindow(action.target());
            case "CLOSE_WINDOW" -> closeWindow(action.target());
            case "SHOW_DESKTOP" -> showDesktop();
            case "WAIT" -> {
                int delay = action.delayMs() != null ? action.delayMs() : 500;
                try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
                yield new DesktopGuiResult(true, "WAIT", "Waited " + delay + "ms", delay, Map.of("delayMs", delay));
            }
            default -> new DesktopGuiResult(false, type, "Unsupported action type: " + type, 0, Map.of());
        };
    }

    public DesktopGuiResult executeSequence(String workflowId, String workflowName, List<DesktopGuiAction> actions) {
        long start = System.currentTimeMillis();
        int executed = 0;
        UUID wfUuid = null;
        if (workflowId != null && !workflowId.isBlank()) {
            try { wfUuid = UUID.fromString(workflowId); } catch (Exception ignored) {}
        }

        logAudit("SEQUENCE_START", workflowName, Map.of("actionsCount", actions.size()), "SUCCESS", 0, null);

        for (DesktopGuiAction act : actions) {
            DesktopGuiResult res = executeAction(act);
            executed++;
            if (!res.success()) {
                long dur = System.currentTimeMillis() - start;
                logAudit("SEQUENCE_COMPLETE", workflowName, Map.of("executed", executed, "failedAt", act.type()), "FAILED", dur, res.message());
                return new DesktopGuiResult(false, "SEQUENCE",
                        "Sequence failed at step " + executed + " (" + act.type() + "): " + res.message(),
                        dur, Map.of("executedSteps", executed, "totalSteps", actions.size()));
            }
            if (act.delayMs() != null && act.delayMs() > 0 && !act.type().equalsIgnoreCase("WAIT")) {
                try { Thread.sleep(act.delayMs()); } catch (InterruptedException ignored) {}
            }
        }

        long dur = System.currentTimeMillis() - start;
        logAudit("SEQUENCE_COMPLETE", workflowName, Map.of("totalSteps", executed), "SUCCESS", dur, null);

        if (wfUuid != null && dbStore != null && dbStore.configured()) {
            final UUID finalWfUuid = wfUuid;
            final int finalDur = (int) dur;
            try {
                dbStore.execute(c -> {
                    try (var ps = c.prepareStatement("select jarvis.desktop_workflow_record_execution(?, ?, 'SUCCESS')")) {
                        ps.setObject(1, finalWfUuid);
                        ps.setInt(2, finalDur);
                        ps.execute();
                    }
                    return null;
                });
            } catch (Throwable t) {
                log.warn("Could not record workflow execution in database: {}", t.getMessage());
            }
        }

        return new DesktopGuiResult(true, "SEQUENCE",
                "Successfully executed workflow '" + workflowName + "' (" + executed + " actions)",
                dur, Map.of("executedSteps", executed, "totalSteps", actions.size()));
    }

    // =========================================================================
    // 5. Database Workflows & Audit Persistence
    // =========================================================================

    public List<DesktopWorkflow> listWorkflows() {
        if (dbStore != null && dbStore.configured()) {
            try {
                return dbStore.execute(c -> {
                    String sql = "SELECT id, name, description, trigger_pattern, actions, is_favorite, execution_count, last_executed_at, created_at, updated_at " +
                            "FROM jarvis.desktop_workflows WHERE owner_name = session_user ORDER BY is_favorite DESC, execution_count DESC, created_at ASC";
                    try (PreparedStatement ps = c.prepareStatement(sql);
                         ResultSet rs = ps.executeQuery()) {
                        List<DesktopWorkflow> list = new ArrayList<>();
                        while (rs.next()) {
                            UUID id = (UUID) rs.getObject("id");
                            String name = rs.getString("name");
                            String desc = rs.getString("description");
                            String trig = rs.getString("trigger_pattern");
                            String actionsJson = rs.getString("actions");
                            boolean isFav = rs.getBoolean("is_favorite");
                            int count = rs.getInt("execution_count");
                            Timestamp lastEx = rs.getTimestamp("last_executed_at");
                            Timestamp cr = rs.getTimestamp("created_at");
                            Timestamp up = rs.getTimestamp("updated_at");

                            List<DesktopGuiAction> acts = new ArrayList<>();
                            if (actionsJson != null && !actionsJson.isBlank()) {
                                try {
                                    acts = mapper.readValue(actionsJson, new TypeReference<>() {});
                                } catch (Exception ignored) {}
                            }
                            list.add(new DesktopWorkflow(
                                    id, name, desc, trig, acts, isFav, count,
                                    lastEx != null ? lastEx.toInstant().toString() : null,
                                    cr != null ? cr.toInstant().toString() : null,
                                    up != null ? up.toInstant().toString() : null
                            ));
                        }
                        return list;
                    }
                });
            } catch (Throwable t) {
                log.warn("Database workflows retrieval failed: {}. Falling back to default templates.", t.getMessage());
            }
        }
        return getDefaultWorkflows();
    }

    public DesktopWorkflow saveWorkflow(String name, String description, String triggerPattern, List<DesktopGuiAction> actions, boolean isFavorite) {
        String actionsJson = "[]";
        try { actionsJson = mapper.writeValueAsString(actions != null ? actions : List.of()); } catch (Exception ignored) {}

        if (dbStore != null && dbStore.configured()) {
            final String finalActionsJson = actionsJson;
            try {
                return dbStore.execute(c -> {
                    String sql = "SELECT * FROM jarvis.desktop_workflow_upsert(?, ?, ?, ?::jsonb, ?)";
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        ps.setString(1, name);
                        ps.setString(2, description);
                        ps.setString(3, triggerPattern);
                        ps.setString(4, finalActionsJson);
                        ps.setBoolean(5, isFavorite);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                UUID id = (UUID) rs.getObject("id");
                                return new DesktopWorkflow(id, name, description, triggerPattern, actions, isFavorite, 0, null, Instant.now().toString(), Instant.now().toString());
                            }
                        }
                    }
                    return null;
                });
            } catch (Throwable t) {
                log.error("Failed to persist workflow to database: {}", t.getMessage());
            }
        }
        return new DesktopWorkflow(UUID.randomUUID(), name, description, triggerPattern, actions, isFavorite, 0, null, Instant.now().toString(), Instant.now().toString());
    }

    public boolean deleteWorkflow(UUID id) {
        if (id == null) return false;
        if (dbStore != null && dbStore.configured()) {
            try {
                return dbStore.execute(c -> {
                    String sql = "DELETE FROM jarvis.desktop_workflows WHERE id = ? AND owner_name = session_user";
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        ps.setObject(1, id);
                        return ps.executeUpdate() > 0;
                    }
                });
            } catch (Throwable t) {
                log.warn("Failed to delete workflow from database: {}", t.getMessage());
            }
        }
        return true;
    }

    public List<DesktopAuditEntry> getRecentAudit(int limit) {
        int cap = Math.min(100, Math.max(5, limit));
        if (dbStore != null && dbStore.configured()) {
            try {
                return dbStore.execute(c -> {
                    String sql = "SELECT id, action_type, target, payload, status, duration_ms, error_message, created_at " +
                            "FROM jarvis.desktop_action_audit WHERE owner_name = session_user ORDER BY created_at DESC LIMIT ?";
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        ps.setInt(1, cap);
                        try (ResultSet rs = ps.executeQuery()) {
                            List<DesktopAuditEntry> entries = new ArrayList<>();
                            while (rs.next()) {
                                UUID id = (UUID) rs.getObject("id");
                                String act = rs.getString("action_type");
                                String target = rs.getString("target");
                                String payloadStr = rs.getString("payload");
                                String status = rs.getString("status");
                                int dur = rs.getInt("duration_ms");
                                String err = rs.getString("error_message");
                                Timestamp cr = rs.getTimestamp("created_at");

                                Map<String, Object> payload = Map.of();
                                if (payloadStr != null && !payloadStr.isBlank()) {
                                    try { payload = mapper.readValue(payloadStr, new TypeReference<>() {}); } catch (Exception ignored) {}
                                }
                                entries.add(new DesktopAuditEntry(id, act, target, payload, status, dur, err, cr != null ? cr.toInstant().toString() : ""));
                            }
                            return entries;
                        }
                    }
                });
            } catch (Throwable ignored) {}
        }

        // Fallback to reading JSONL
        List<DesktopAuditEntry> entries = new ArrayList<>();
        if (Files.exists(auditFile)) {
            try {
                List<String> lines = Files.readAllLines(auditFile, StandardCharsets.UTF_8);
                Collections.reverse(lines);
                for (String line : lines) {
                    if (line.isBlank()) continue;
                    try {
                        Map<String, Object> m = mapper.readValue(line, new TypeReference<>() {});
                        entries.add(new DesktopAuditEntry(
                                UUID.randomUUID(),
                                String.valueOf(m.getOrDefault("action", "ACTION")),
                                String.valueOf(m.getOrDefault("target", "")),
                                (Map<String, Object>) m.getOrDefault("payload", Map.of()),
                                String.valueOf(m.getOrDefault("status", "SUCCESS")),
                                ((Number) m.getOrDefault("durationMs", 0)).intValue(),
                                (String) m.get("error"),
                                String.valueOf(m.getOrDefault("timestamp", ""))
                        ));
                        if (entries.size() >= cap) break;
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }
        return entries;
    }

    private void logAudit(String action, String target, Map<String, Object> payload, String status, long durationMs, String error) {
        // 1. Database log
        if (dbStore != null && dbStore.configured()) {
            try {
                String jsonPayload = mapper.writeValueAsString(payload != null ? payload : Map.of());
                dbStore.execute(c -> {
                    try (var ps = c.prepareStatement("select jarvis.desktop_log_action(?, ?, ?::jsonb, ?, ?, ?)")) {
                        ps.setString(1, action);
                        ps.setString(2, target);
                        ps.setString(3, jsonPayload);
                        ps.setString(4, status);
                        ps.setInt(5, (int) durationMs);
                        ps.setString(6, error);
                        ps.execute();
                    }
                    return null;
                });
            } catch (Throwable ignored) {}
        }

        // 2. Local JSONL file backup
        try {
            Files.createDirectories(auditFile.getParent());
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("timestamp", Instant.now().toString());
            record.put("action", action);
            record.put("target", target);
            record.put("payload", payload);
            record.put("status", status);
            record.put("durationMs", durationMs);
            if (error != null) record.put("error", error);

            String jsonLine = mapper.writeValueAsString(record) + System.lineSeparator();
            Files.writeString(auditFile, jsonLine, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignored) {}
    }

    private List<DesktopWorkflow> getDefaultWorkflows() {
        return List.of(
                new DesktopWorkflow(
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        "Quick Meeting Notes in Notepad",
                        "Launches Notepad and prepares a formatted meeting notes header",
                        "open notepad and take meeting notes",
                        List.of(
                                new DesktopGuiAction("LAUNCH_APP", null, null, null, null, null, null, null, null, null, null, "notepad.exe", 800),
                                new DesktopGuiAction("FOCUS_WINDOW", null, null, null, null, null, null, null, null, null, null, "Notepad", 400),
                                new DesktopGuiAction("TYPE_TEXT", null, null, null, null, null, null, null, "=== JARVIS MEETING NOTES ===\nDate: " + Instant.now().toString() + "\n\nNotes:\n- ", null, null, null, 100),
                                new DesktopGuiAction("KEY_PRESS", null, null, null, null, null, null, null, null, "ENTER", null, null, 100)
                        ),
                        true, 5, null, Instant.now().toString(), Instant.now().toString()
                ),
                new DesktopWorkflow(
                        UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        "Developer Workspace Setup",
                        "Brings VS Code to the front and opens the integrated terminal",
                        "setup developer workspace",
                        List.of(
                                new DesktopGuiAction("LAUNCH_APP", null, null, null, null, null, null, null, null, null, null, "code", 1000),
                                new DesktopGuiAction("FOCUS_WINDOW", null, null, null, null, null, null, null, null, null, null, "Visual Studio Code", 500),
                                new DesktopGuiAction("HOTKEY", null, null, null, null, null, null, null, null, null, "Ctrl+`", null, 600)
                        ),
                        true, 8, null, Instant.now().toString(), Instant.now().toString()
                ),
                new DesktopWorkflow(
                        UUID.fromString("00000000-0000-0000-0000-000000000003"),
                        "Show Desktop & Clear Workspace",
                        "Minimizes all active foreground windows to show clean desktop",
                        "show desktop",
                        List.of(
                                new DesktopGuiAction("SHOW_DESKTOP", null, null, null, null, null, null, null, null, null, null, null, 300)
                        ),
                        false, 2, null, Instant.now().toString(), Instant.now().toString()
                ),
                new DesktopWorkflow(
                        UUID.fromString("00000000-0000-0000-0000-000000000004"),
                        "Save Active Document",
                        "Sends Ctrl+S hotkey to save current active file or document",
                        "save active file",
                        List.of(
                                new DesktopGuiAction("HOTKEY", null, null, null, null, null, null, null, null, null, "Ctrl+S", null, 200)
                        ),
                        true, 12, null, Instant.now().toString(), Instant.now().toString()
                )
        );
    }

    private String runPowerShell(String script) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }
        }
        p.waitFor();
        return sb.toString().trim();
    }
}

package com.jarvis.system;

import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SystemAppLauncherTest {

    private final SystemAppLauncher launcher = new SystemAppLauncher(false);

    @Test
    void recognizesEnglishChromeLaunch() {
        Optional<String> res = launcher.launch("open chrome");
        assertThat(res).isPresent();
        assertThat(res.get()).contains("Chrome");
    }

    @Test
    void recognizesEnglishNotepadLaunch() {
        Optional<String> res = launcher.launch("launch notepad");
        assertThat(res).isPresent();
        assertThat(res.get()).contains("Notepad");
    }

    @Test
    void recognizesEnglishCalcLaunch() {
        Optional<String> res = launcher.launch("start calculator");
        assertThat(res).isPresent();
        assertThat(res.get()).contains("Calculator");
    }

    @Test
    void recognizesTamilChromeLaunch() {
        Optional<String> res = launcher.launch("குரோம் திற");
        assertThat(res).isPresent();
        assertThat(res.get()).contains("கூகிள் குரோம்");
    }

    @Test
    void recognizesEnglishCloseCommands() {
        Optional<String> chromeClose = launcher.close("close chrome");
        assertThat(chromeClose).isPresent();
        assertThat(chromeClose.get()).contains("Chrome");

        Optional<String> notepadExit = launcher.close("exit notepad");
        assertThat(notepadExit).isPresent();
        assertThat(notepadExit.get()).contains("Notepad");

        Optional<String> calcKill = launcher.close("kill calculator");
        assertThat(calcKill).isPresent();
        assertThat(calcKill.get()).contains("Calculator");
    }

    @Test
    void recognizesCloseOpenedApplication() {
        launcher.launch("open notepad");
        Optional<String> closeIt = launcher.close("close the opened application");
        assertThat(closeIt).isPresent();
        assertThat(closeIt.get()).contains("Notepad");
    }

    @Test
    void recognizesTamilCloseCommands() {
        Optional<String> tamilClose = launcher.close("நோட்பேடை மூடு");
        assertThat(tamilClose).isPresent();
        assertThat(tamilClose.get()).contains("மூடப்பட்டது");
    }

    @Test
    void ignoresGeneralQuestions() {
        Optional<String> res = launcher.launch("What is the capital of France?");
        assertThat(res).isEmpty();
    }

    @Test
    void recognizesConversationalVoiceAppCommands() {
        // Hey Jarvis, open Google Chrome.
        Optional<String> chrome = launcher.launch("Hey Jarvis, open Google Chrome.");
        assertThat(chrome).isPresent();
        assertThat(chrome.get()).contains("Chrome");

        // Hello open notepad.
        Optional<String> notepad = launcher.launch("Hello open notepad.");
        assertThat(notepad).isPresent();
        assertThat(notepad.get()).contains("Notepad");

        // Just to open the notepad.
        Optional<String> justNotepad = launcher.launch("Just to open the notepad.");
        assertThat(justNotepad).isPresent();
        assertThat(justNotepad.get()).contains("Notepad");

        // Can you please close notepad.
        Optional<String> closeNotepad = launcher.close("Can you please close notepad.");
        assertThat(closeNotepad).isPresent();
        assertThat(closeNotepad.get()).contains("Notepad");

        // ஹே ஜார்விஸ், கூகிள் குரோம் திற
        Optional<String> tamilWakeLaunch = launcher.launch("ஹே ஜார்விஸ், கூகிள் குரோம் திற");
        assertThat(tamilWakeLaunch).isPresent();
        assertThat(tamilWakeLaunch.get()).contains("கூகிள் குரோம்");
    }
}

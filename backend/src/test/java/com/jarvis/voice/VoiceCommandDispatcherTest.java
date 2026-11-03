package com.jarvis.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.biometrics.BiometricProfile;
import com.jarvis.biometrics.BiometricService;
import com.jarvis.biometrics.BiometricStore;
import com.jarvis.goal.GoalPlanner;
import com.jarvis.goal.GoalService;
import com.jarvis.system.SystemHardwareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceCommandDispatcherTest {

    @TempDir
    Path tempDir;

    private BiometricService biometricService;
    private SystemHardwareService hardwareService;
    private GoalService goalService;
    private VoiceCommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        Path bioPath = tempDir.resolve("profiles.json");
        BiometricStore bioStore = new BiometricStore(bioPath.toString(), null, null);
        biometricService = new BiometricService(bioStore);

        hardwareService = new SystemHardwareService();

        GoalPlanner planner = new GoalPlanner();
        Path goalPath = tempDir.resolve("goals.json");
        goalService = new GoalService(planner, null, null, null, hardwareService, null, null, new ObjectMapper(), goalPath.toString());

        Path smarthomeDir = tempDir.resolve("smarthome");
        var smarthomeStore = new com.jarvis.smarthome.SmartHomeStore(smarthomeDir.toString(), new ObjectMapper(), null);
        var smarthomeService = new com.jarvis.smarthome.SmartHomeService(
                smarthomeStore,
                new com.jarvis.smarthome.HomeAssistantClient(new ObjectMapper()),
                new com.jarvis.smarthome.MqttGatewayClient()
        );

        dispatcher = new VoiceCommandDispatcher(
                biometricService,
                hardwareService,
                null,
                null,
                null,
                goalService,
                new LanguagePreferenceService(),
                new com.jarvis.system.DesktopSystemService(false),
                null,
                smarthomeService
        );
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        if (goalService != null) {
            for (var g : goalService.listGoals()) {
                goalService.cancelGoal(g.id());
            }
        }
        Thread.sleep(150);
    }

    @Test
    void testBiometricVoiceCommandEnglish() {
        Optional<String> res = dispatcher.dispatch("Verify my face and identity");
        assertThat(res).isPresent();
        assertThat(res.get()).contains("Biometric Verification Confirmed");
        assertThat(res.get()).contains("Master Kabil");
        assertThat(res.get()).contains("<think>");
    }

    @Test
    void testBiometricVoiceCommandTamil() {
        Optional<String> res = dispatcher.dispatch("என் முகத்தை சரிபார்");
        assertThat(res).isPresent();
        assertThat(res.get()).contains("பயோமெட்ரிக் அங்கீகாரம்");
        assertThat(res.get()).contains("கபில் ஐயா");
        assertThat(res.get()).contains("<think>");
    }

    @Test
    void testHardwareDiagnosticsEnglish() {
        Optional<String> res = dispatcher.dispatch("Check system hardware diagnostics");
        assertThat(res).isPresent();
        assertThat(res.get()).contains("System Hardware Diagnostics Snapshot");
        assertThat(res.get()).contains("RAM Memory");
    }

    @Test
    void testHardwareDiagnosticsTamil() {
        Optional<String> res = dispatcher.dispatch("கணினி நிலை என்ன?");
        assertThat(res).isPresent();
        assertThat(res.get()).contains("கணினி வன்பொருள் & ஆரோக்கிய நிலை");
        assertThat(res.get()).contains("நினைவகம் (RAM)");
    }

    @Test
    void testAutonomousGoalVoiceCommandTamil() {
        Optional<String> res = dispatcher.dispatch("இலக்கு: புதிய விசைப்பலகை வாங்க சிறந்த பரிந்துரை");
        assertThat(res).isPresent();
        assertThat(res.get()).contains("தானியங்கி இலக்கு தொடங்கப்பட்டது");
        assertThat(res.get()).contains("goal-");
    }

    @Test
    void testVoiceSpeedCommandEnglish() {
        Optional<String> res = dispatcher.dispatch("Jarvis, speak faster please");
        assertThat(res).isPresent();
        assertThat(res.get()).contains("Speech Speed & Cadence Calibrated");
        assertThat(res.get()).contains("0.50x");
        assertThat(res.get()).contains("2.00x");
    }

    @Test
    void testVoiceSpeedCommandTamil() {
        Optional<String> res = dispatcher.dispatch("வேகமாக பேசு ஜார்விஸ்");
        assertThat(res).isPresent();
        assertThat(res.get()).contains("குரல் வேகம் கட்டுப்பாடு");
        assertThat(res.get()).contains("0.5x");
    }

    @Test
    void testDesktopMediaVoiceCommandEnglish() {
        Optional<String> res = dispatcher.dispatch("Jarvis play music");
        assertThat(res).isPresent();
        assertThat(res.get()).contains("Media Control: Play/Pause Toggled");
        assertThat(res.get()).contains("<think>");
    }

    @Test
    void testDesktopVolumeVoiceCommandEnglish() {
        Optional<String> res = dispatcher.dispatch("Jarvis set volume to 70%");
        assertThat(res).isPresent();
        assertThat(res.get()).contains("Master Volume Set: 70%");
    }

    @Test
    void testDesktopAppLaunchVoiceCommandEnglish() {
        Optional<String> res = dispatcher.dispatch("Jarvis open spotify");
        assertThat(res).isPresent();
        assertThat(res.get()).contains("Application Launched: Spotify");
    }

    @Test
    void testDesktopMediaVoiceCommandTamil() {
        Optional<String> res = dispatcher.dispatch("பாட்டை நிறுத்து");
        assertThat(res).isPresent();
        assertThat(res.get()).contains("இசை நிறுத்தப்பட்டது");
    }

    @Test
    void testDesktopVolumeVoiceCommandTamil() {
        Optional<String> res = dispatcher.dispatch("சத்தத்தை கூட்டு");
        assertThat(res).isPresent();
        assertThat(res.get()).contains("சத்தம் அதிகரிக்கப்பட்டது");
    }

    @Test
    void testSmartHomeMovieModeEnglish() {
        Optional<String> res = dispatcher.dispatch("Jarvis, set room lighting to Movie mode");
        assertThat(res).isPresent();
        assertThat(res.get()).contains("Movie Mode Activated");
        assertThat(res.get()).contains("15%");
        assertThat(res.get()).contains("Desk Power Strip");
    }

    @Test
    void testSmartHomeTurnOffDeskPowerStrip() {
        Optional<String> res = dispatcher.dispatch("Jarvis, turn off the desk power strip");
        assertThat(res).isPresent();
        assertThat(res.get()).contains("Desk Power Strip Powered OFF");
        assertThat(res.get()).contains("0.0 W");
    }

    @Test
    void testSmartHomeMovieModeTamil() {
        Optional<String> res = dispatcher.dispatch("அறை விளக்குகளை மூவி மோடுக்கு மாற்று");
        assertThat(res).isPresent();
        assertThat(res.get()).contains("மூவி மோடு செயல்படுத்தப்பட்டது");
    }

    @Test
    void testSmartHomeTurnOffDeskPowerStripTamil() {
        Optional<String> res = dispatcher.dispatch("டெஸ்க் பவர் ஸ்ட்ரிப்பை ஆஃப் செய்");
        assertThat(res).isPresent();
        assertThat(res.get()).contains("டெஸ்க் பவர் ஸ்ட்ரிப் அணைக்கப்பட்டது");
    }

    @Test
    void testLowConfidenceBlocksConsequentialCommands() {
        // Low confidence (0.22 < 0.35 threshold) attempting to close application
        Optional<String> resEng = dispatcher.dispatch("close notepad", 0.22);
        assertThat(resEng).isPresent();
        assertThat(resEng.get()).contains("I'm not confident I heard you correctly");
        assertThat(resEng.get()).contains("22%");

        // Low confidence attempting action in Tamil
        Optional<String> resTam = dispatcher.dispatch("நோட்பேடை மூடு", 0.30);
        assertThat(resTam).isPresent();
        assertThat(resTam.get()).contains("உங்கள் பேச்சு தெளிவாகக் கேட்கவில்லை");
        assertThat(resTam.get()).contains("30%");
    }

    @Test
    void testUnrelatedQueryReturnsEmpty() {
        Optional<String> res = dispatcher.dispatch("Tell me about quantum physics");
        assertThat(res).isEmpty();
    }
}

package com.jarvis.system;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DesktopSystemServiceTest {

    private DesktopSystemService service;

    @BeforeEach
    void setUp() {
        service = new DesktopSystemService(false); // mock execution for testing
    }

    @Test
    void testMediaControlsDirect() {
        var pp = service.playPause();
        assertThat(pp.success()).isTrue();
        assertThat(pp.action()).isEqualTo("play_pause");

        var next = service.nextTrack();
        assertThat(next.success()).isTrue();
        assertThat(next.action()).isEqualTo("next");

        var prev = service.previousTrack();
        assertThat(prev.success()).isTrue();
        assertThat(prev.action()).isEqualTo("previous");

        var stop = service.stopMedia();
        assertThat(stop.success()).isTrue();
        assertThat(stop.action()).isEqualTo("stop");
    }

    @Test
    void testVolumeControlsDirect() {
        var volUp = service.volumeUp(2);
        assertThat(volUp.volume()).isGreaterThanOrEqualTo(50);

        var volDown = service.volumeDown(2);
        assertThat(volDown.volume()).isLessThanOrEqualTo(volUp.volume());

        var setVol = service.setVolume(75);
        assertThat(setVol.volume()).isEqualTo(75);
        assertThat(setVol.isMuted()).isFalse();

        var mute = service.toggleMute();
        assertThat(mute.isMuted()).isTrue();

        var unmute = service.setMute(false);
        assertThat(unmute.isMuted()).isFalse();
    }

    @Test
    void testVoiceMediaCommandsEnglish() {
        Optional<String> playRes = service.handleVoiceCommand("Jarvis, play music", false);
        assertThat(playRes).isPresent();
        assertThat(playRes.get()).contains("Play/Pause");
        assertThat(playRes.get()).contains("<think>");

        Optional<String> nextRes = service.handleVoiceCommand("skip track", false);
        assertThat(nextRes).isPresent();
        assertThat(nextRes.get()).contains("Next Track");

        Optional<String> prevRes = service.handleVoiceCommand("previous song", false);
        assertThat(prevRes).isPresent();
        assertThat(prevRes.get()).contains("Previous Track");

        Optional<String> stopRes = service.handleVoiceCommand("stop music", false);
        assertThat(stopRes).isPresent();
        assertThat(stopRes.get()).contains("Playback Stopped");
    }

    @Test
    void testVoiceMediaCommandsTamil() {
        Optional<String> playTamil = service.handleVoiceCommand("பாட்டைப் போடு", true);
        assertThat(playTamil).isPresent();
        assertThat(playTamil.get()).contains("இசை கட்டுப்பாடு");

        Optional<String> nextTamil = service.handleVoiceCommand("அடுத்த பாடல்", true);
        assertThat(nextTamil).isPresent();
        assertThat(nextTamil.get()).contains("அடுத்த பாடல்");

        Optional<String> stopTamil = service.handleVoiceCommand("பாட்டை நிறுத்து", true);
        assertThat(stopTamil).isPresent();
        assertThat(stopTamil.get()).contains("இசை நிறுத்தப்பட்டது");
    }

    @Test
    void testVoiceVolumeCommandsEnglish() {
        Optional<String> upRes = service.handleVoiceCommand("Jarvis, volume up", false);
        assertThat(upRes).isPresent();
        assertThat(upRes.get()).contains("Volume Increased");

        Optional<String> downRes = service.handleVoiceCommand("make it quieter", false);
        assertThat(downRes).isPresent();
        assertThat(downRes.get()).contains("Volume Decreased");

        Optional<String> setRes = service.handleVoiceCommand("set volume to 80%", false);
        assertThat(setRes).isPresent();
        assertThat(setRes.get()).contains("Master Volume Set: 80%");

        Optional<String> muteRes = service.handleVoiceCommand("mute volume", false);
        assertThat(muteRes).isPresent();
        assertThat(muteRes.get()).contains("Audio Muted");
    }

    @Test
    void testVoiceVolumeCommandsTamil() {
        Optional<String> upTamil = service.handleVoiceCommand("சத்தத்தை கூட்டு", true);
        assertThat(upTamil).isPresent();
        assertThat(upTamil.get()).contains("சத்தம் அதிகரிக்கப்பட்டது");

        Optional<String> downTamil = service.handleVoiceCommand("சத்தத்தை குறை", true);
        assertThat(downTamil).isPresent();
        assertThat(downTamil.get()).contains("சத்தம் குறைக்கப்பட்டது");

        Optional<String> muteTamil = service.handleVoiceCommand("மியூட் செய்", true);
        assertThat(muteTamil).isPresent();
        assertThat(muteTamil.get()).contains("முடக்கப்பட்டது");
    }

    @Test
    void testVoiceAppLaunchEnglish() {
        Optional<String> chromeRes = service.handleVoiceCommand("open chrome", false);
        assertThat(chromeRes).isPresent();
        assertThat(chromeRes.get()).contains("Google Chrome");

        Optional<String> calcRes = service.handleVoiceCommand("launch calculator", false);
        assertThat(calcRes).isPresent();
        assertThat(calcRes.get()).contains("Calculator");

        Optional<String> codeRes = service.handleVoiceCommand("start vs code", false);
        assertThat(codeRes).isPresent();
        assertThat(codeRes.get()).contains("VS Code");

        Optional<String> spotRes = service.handleVoiceCommand("open spotify", false);
        assertThat(spotRes).isPresent();
        assertThat(spotRes.get()).contains("Spotify");
    }

    @Test
    void testVoiceAppLaunchTamil() {
        Optional<String> chromeTamil = service.handleVoiceCommand("குரோம் திற", true);
        assertThat(chromeTamil).isPresent();
        assertThat(chromeTamil.get()).contains("Google Chrome");

        Optional<String> calcTamil = service.handleVoiceCommand("கால்குலேட்டரை திற", true);
        assertThat(calcTamil).isPresent();
        assertThat(calcTamil.get()).contains("Calculator");
    }

    @Test
    void testVoiceAppCloseEnglish() {
        Optional<String> notepadClose = service.handleVoiceCommand("close notepad", false);
        assertThat(notepadClose).isPresent();
        assertThat(notepadClose.get()).contains("Notepad");
        assertThat(notepadClose.get()).contains("Application Closed");

        Optional<String> chromeExit = service.handleVoiceCommand("exit chrome", false);
        assertThat(chromeExit).isPresent();
        assertThat(chromeExit.get()).contains("Chrome");

        Optional<String> closeIt = service.handleVoiceCommand("close the opened application", false);
        assertThat(closeIt).isPresent();
    }

    @Test
    void testVoiceAppCloseTamil() {
        Optional<String> notepadTamil = service.handleVoiceCommand("நோட்பேடை மூடு", true);
        assertThat(notepadTamil).isPresent();
        assertThat(notepadTamil.get()).contains("மூடப்பட்டது");
    }

    @Test
    void testRegisteredAppsCatalog() {
        var apps = service.getRegisteredApps();
        assertThat(apps).isNotEmpty();
        assertThat(apps).anyMatch(a -> a.id().equals("chrome"));
        assertThat(apps).anyMatch(a -> a.id().equals("vscode"));
        assertThat(apps).anyMatch(a -> a.id().equals("spotify"));
        assertThat(apps).anyMatch(a -> a.id().equals("terminal"));
        assertThat(apps).anyMatch(a -> a.id().equals("settings"));
    }

    @Test
    void testMeetingNotesExtractionAndSaving() throws Exception {
        String input = "take meeting notes: discussed Q3 product launch and allocated $50k cloud budget";
        String extracted = DesktopSystemService.extractMeetingNotesContent(input);
        assertThat(extracted).isEqualTo("discussed Q3 product launch and allocated $50k cloud budget");

        // Verify file saving and survival on disk
        java.nio.file.Path saved = service.saveMeetingNotesFile("=== JARVIS MEETING NOTES ===\nNotes:\n" + extracted);
        assertThat(saved).isNotNull();
        assertThat(java.nio.file.Files.exists(saved)).isTrue();
        assertThat(java.nio.file.Files.size(saved)).isGreaterThan(0);

        String readBack = java.nio.file.Files.readString(saved);
        assertThat(readBack).contains("discussed Q3 product launch and allocated $50k cloud budget");
        assertThat(readBack).doesNotContain("Attendees: Team"); // No invented attendees!
        assertThat(readBack).doesNotContain("Project Review"); // No invented topic!

        // Clean up test file
        java.nio.file.Files.deleteIfExists(saved);
    }

    @Test
    void testTakeMeetingNotesVoiceCommand() {
        Optional<String> res = service.handleVoiceCommand("take meeting notes: team agreed on Rust backend migration", false);
        assertThat(res).isPresent();
        assertThat(res.get()).contains("Meeting Notes Saved");
        assertThat(res.get()).contains("team agreed on Rust backend migration");
        assertThat(res.get()).doesNotContain("Attendees: Team");
    }
}

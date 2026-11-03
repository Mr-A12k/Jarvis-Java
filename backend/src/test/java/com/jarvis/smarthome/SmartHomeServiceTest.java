package com.jarvis.smarthome;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SmartHomeServiceTest {

    @TempDir
    Path tempDir;

    private SmartHomeStore store;
    private SmartHomeService service;

    @BeforeEach
    void setUp() {
        store = new SmartHomeStore(tempDir.toString(), new ObjectMapper(), null);
        HomeAssistantClient haClient = new HomeAssistantClient(new ObjectMapper());
        MqttGatewayClient mqttClient = new MqttGatewayClient();
        service = new SmartHomeService(store, haClient, mqttClient);
    }

    @Test
    void testSetRoomLightingToMovieModeEnglish() {
        Optional<String> result = service.handleVoiceCommand("Jarvis, set room lighting to Movie mode", false);
        assertThat(result).isPresent();
        String output = result.get();
        assertThat(output).contains("<think>");
        assertThat(output).contains("Movie Mode Activated");
        assertThat(output).contains("15%");
        assertThat(output).contains("Desk Power Strip");
        assertThat(output).contains("OFF");

        // Verify device state updates
        SmartDevice roomLight = store.findDevice("light.room_lighting").orElseThrow();
        assertThat(roomLight.state()).isEqualTo("on");
        assertThat(roomLight.attributes().get("brightness")).isEqualTo(15);
        assertThat(roomLight.attributes().get("color")).isEqualTo("#3b82f6");

        SmartDevice deskStrip = store.findDevice("switch.desk_power_strip").orElseThrow();
        assertThat(deskStrip.state()).isEqualTo("off");
        assertThat(deskStrip.attributes().get("power_watts")).isEqualTo(0.0);
    }

    @Test
    void testTurnOffDeskPowerStripEnglish() {
        // Ensure starting state is ON
        service.executeDeviceAction("switch.desk_power_strip", "turn_on", Map.of());

        Optional<String> result = service.handleVoiceCommand("Jarvis, turn off the desk power strip", false);
        assertThat(result).isPresent();
        String output = result.get();
        assertThat(output).contains("<think>");
        assertThat(output).contains("Desk Power Strip Powered OFF");
        assertThat(output).contains("0.0 W");

        SmartDevice deskStrip = store.findDevice("switch.desk_power_strip").orElseThrow();
        assertThat(deskStrip.state()).isEqualTo("off");
        assertThat(deskStrip.attributes().get("power_watts")).isEqualTo(0.0);
    }

    @Test
    void testTurnOnDeskPowerStripEnglish() {
        // First turn off
        service.executeDeviceAction("switch.desk_power_strip", "turn_off", Map.of());

        Optional<String> result = service.handleVoiceCommand("Jarvis, turn on the desk power strip", false);
        assertThat(result).isPresent();
        String output = result.get();
        assertThat(output).contains("<think>");
        assertThat(output).contains("Desk Power Strip Powered ON");
        assertThat(output).contains("42.5 W");

        SmartDevice deskStrip = store.findDevice("switch.desk_power_strip").orElseThrow();
        assertThat(deskStrip.state()).isEqualTo("on");
        assertThat(deskStrip.attributes().get("power_watts")).isEqualTo(42.5);
    }

    @Test
    void testMovieModeTamil() {
        Optional<String> result = service.handleVoiceCommand("அறை விளக்குகளை மூவி மோடுக்கு மாற்று", true);
        assertThat(result).isPresent();
        String output = result.get();
        assertThat(output).contains("<think>");
        assertThat(output).contains("மூவி மோடு செயல்படுத்தப்பட்டது");
        assertThat(output).contains("15%");
    }

    @Test
    void testDeskPowerStripTamil() {
        Optional<String> result = service.handleVoiceCommand("டெஸ்க் பவர் ஸ்ட்ரிப்பை ஆஃப் செய்", true);
        assertThat(result).isPresent();
        String output = result.get();
        assertThat(output).contains("<think>");
        assertThat(output).contains("டெஸ்க் பவர் ஸ்ட்ரிப் அணைக்கப்பட்டது");
        assertThat(output).contains("0.0 W");
    }

    @Test
    void testDimmingCommand() {
        Optional<String> result = service.handleVoiceCommand("Jarvis, dim room lighting to 25%", false);
        assertThat(result).isPresent();
        assertThat(result.get()).contains("25%");

        SmartDevice light = store.findDevice("light.room_lighting").orElseThrow();
        assertThat(light.attributes().get("brightness")).isEqualTo(25);
    }

    @Test
    void testSmartHomeStatusQuery() {
        Optional<String> result = service.handleVoiceCommand("Jarvis, what is the smart home status?", false);
        assertThat(result).isPresent();
        String output = result.get();
        assertThat(output).contains("Smart Home & IoT System Telemetry");
        assertThat(output).contains("Room Lighting");
        assertThat(output).contains("Desk Power Strip");
    }

    @Test
    void testExecuteDeviceActionDirectly() {
        SmartDevice dev = service.executeDeviceAction("light.ceiling_light", "turn_on", Map.of());
        assertThat(dev.state()).isEqualTo("on");

        SmartDevice toggled = service.executeDeviceAction("light.ceiling_light", "toggle", Map.of());
        assertThat(toggled.state()).isEqualTo("off");
    }

    @Test
    void testWorkModeScene() {
        Map<String, Object> res = service.activateScene("scene.work_mode");
        assertThat(res).containsKey("scene");

        SmartDevice light = store.findDevice("light.room_lighting").orElseThrow();
        assertThat(light.state()).isEqualTo("on");
        assertThat(light.attributes().get("brightness")).isEqualTo(100);

        SmartDevice deskStrip = store.findDevice("switch.desk_power_strip").orElseThrow();
        assertThat(deskStrip.state()).isEqualTo("on");
    }
}

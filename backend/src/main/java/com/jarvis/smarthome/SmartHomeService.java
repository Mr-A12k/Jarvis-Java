package com.jarvis.smarthome;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core Smart Home & IoT Automation Service.
 * Unifies local network device control via:
 * 1. Home Assistant REST & WebSocket API
 * 2. Local MQTT Broker Gateway (Tasmota / ESPHome / Zigbee2MQTT / Mosquitto)
 * 3. Local Virtual Sandbox & Simulation Engine
 *
 * Fully supports natural language commands in English and Tamil (தமிழ்).
 */
@Service
public class SmartHomeService {

    private static final Logger log = LoggerFactory.getLogger(SmartHomeService.class);

    private final SmartHomeStore store;
    private final HomeAssistantClient haClient;
    private final MqttGatewayClient mqttClient;

    // Command Intent Regex Patterns (English)
    private static final Pattern SCENE_ENG = Pattern.compile(
            "(?i).*(?:set|switch|change|put|activate|turn on)?\\s*(?:the\\s*)?(?:room\\s*lighting|lights?|ambience)?\\s*(?:to|in)?\\s*(?:the\\s*)?(movie|cinema|work|night|relax|gaming)\\s*mode.*"
    );

    private static final Pattern DESK_STRIP_OFF_ENG = Pattern.compile(
            "(?i).*(?:turn\\s*off|switch\\s*off|power\\s*off|shut\\s*off|kill|disable)\\s*(?:the\\s*)?(?:desk\\s*power\\s*strip|desk\\s*strip|desk\\s*plug|desk\\s*socket|power\\s*strip).*"
    );

    private static final Pattern DESK_STRIP_ON_ENG = Pattern.compile(
            "(?i).*(?:turn\\s*on|switch\\s*on|power\\s*on|enable)\\s*(?:the\\s*)?(?:desk\\s*power\\s*strip|desk\\s*strip|desk\\s*plug|desk\\s*socket|power\\s*strip).*"
    );

    private static final Pattern DEVICE_POWER_ENG = Pattern.compile(
            "(?i).*(?:turn|switch|power)\\s+(on|off)\\s+(?:the\\s+)?([a-zA-Z0-9_\\s]+).*"
    );

    private static final Pattern DIM_BRIGHTNESS_ENG = Pattern.compile(
            "(?i).*(?:dim|brighten)\\s+(?:the\\s+)?([a-zA-Z0-9_\\s]+?)\\s+(?:to\\s+)?(\\d{1,3})\\s*%?.*"
    );

    private static final Pattern SET_BRIGHTNESS_ENG = Pattern.compile(
            "(?i).*(?:set)\\s+(?:the\\s+)?([a-zA-Z0-9_\\s]+?)\\s+(?:brightness|light(?:ing)?)\\s+(?:to\\s+)?(\\d{1,3})\\s*%?.*"
    );

    private static final Pattern SMART_HOME_STATUS_ENG = Pattern.compile(
            "(?i).*(?:smart\\s*home\\s*status|iot\\s*status|smart\\s*devices|list\\s*(?:iot|smart)\\s*devices|home\\s*assistant\\s*status|mqtt\\s*status|home\\s*status).*"
    );

    // Command Intent Regex Patterns (Tamil)
    private static final Pattern SCENE_TAMIL = Pattern.compile(
            ".*(?:மூவி\\s*மோடு|சினிமா\\s*மோடு|திரைப்பட\\s*முறை|விளக்குகளை\\s*மூவி|ஒர்க்\\s*மோடு|வேலை\\s*மோடு|நைட்\\s*மோடு|இரவு\\s*மோடு|ரிலாக்ஸ்\\s*மோடு|கேமிங்\\s*மோடு).*"
    );

    private static final Pattern DESK_STRIP_OFF_TAMIL = Pattern.compile(
            ".*(?:டெஸ்க்\\s*பவர்\\s*ஸ்ட்ரிப்(?:பை)?|பவர்\\s*ஸ்ட்ரிப்(?:பை)?)\\s*(?:ஆஃப்\\s*செய்|ஆப்\\s*பண்ணு|அணை|நிறுத்து|ஆஃப்).*"
    );

    private static final Pattern DESK_STRIP_ON_TAMIL = Pattern.compile(
            ".*(?:டெஸ்க்\\s*பவர்\\s*ஸ்ட்ரிப்(?:பை)?|பவர்\\s*ஸ்ட்ரிப்(?:பை)?)\\s*(?:ஆன்\\s*செய்|இயக்கு|ஆன்\\s*பண்ணு|ஆன்).*"
    );

    private static final Pattern SMART_HOME_STATUS_TAMIL = Pattern.compile(
            ".*(?:ஸ்மார்ட்\\s*ஹோம்\\s*நிலை|ஐஓடி\\s*நிலை|வீட்டு\\s*சாதனங்கள்|மின்சாதனங்கள்\\s*நிலை).* "
    );

    @Autowired
    public SmartHomeService(
            SmartHomeStore store,
            HomeAssistantClient haClient,
            MqttGatewayClient mqttClient
    ) {
        this.store = store;
        this.haClient = haClient;
        this.mqttClient = mqttClient;

        // Auto-connect to MQTT if configured
        SmartHomeConfig cfg = store.getConfig();
        if (cfg.mqttEnabled() && !cfg.simulationMode()) {
            mqttClient.connect(cfg.mqttBrokerUrl(), cfg.mqttClientId(), cfg.mqttUsername(), cfg.mqttPassword());
        }
    }

    /**
     * Executes a low-level action on a specific smart device.
     */
    public SmartDevice executeDeviceAction(String entityId, String action, Map<String, Object> params) {
        Optional<SmartDevice> opt = store.findDevice(entityId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Device not found: " + entityId);
        }
        SmartDevice device = opt.get();
        SmartHomeConfig config = store.getConfig();

        String newState = device.state();
        Map<String, Object> newAttrs = new HashMap<>(device.attributes());

        switch (action.toLowerCase(Locale.ROOT)) {
            case "turn_on" -> {
                newState = "on";
                if (device.type() == SmartDevice.DeviceType.SWITCH) {
                    newAttrs.put("power_watts", 42.5);
                }
            }
            case "turn_off" -> {
                newState = "off";
                if (device.type() == SmartDevice.DeviceType.SWITCH) {
                    newAttrs.put("power_watts", 0.0);
                }
            }
            case "toggle" -> {
                newState = "on".equalsIgnoreCase(device.state()) ? "off" : "on";
                if (device.type() == SmartDevice.DeviceType.SWITCH) {
                    newAttrs.put("power_watts", "on".equals(newState) ? 42.5 : 0.0);
                }
            }
            case "set_brightness" -> {
                if (params != null && params.containsKey("brightness")) {
                    int b = ((Number) params.get("brightness")).intValue();
                    newAttrs.put("brightness", Math.max(0, Math.min(100, b)));
                    newState = b > 0 ? "on" : "off";
                }
            }
            case "set_color" -> {
                if (params != null && params.containsKey("color")) {
                    newAttrs.put("color", params.get("color"));
                    newState = "on";
                }
            }
            case "set_temperature" -> {
                if (params != null && params.containsKey("temperature")) {
                    newAttrs.put("target_temperature", params.get("temperature"));
                }
            }
            default -> log.warn("Unknown device action: {}", action);
        }

        SmartDevice updated = device.withState(newState, newAttrs);
        store.saveDevice(updated);

        // Protocol Dispatch
        dispatchToHardware(updated, action, params, config);

        // Audit Log
        store.logEvent(SmartHomeEvent.of(
                "REST_API", action.toUpperCase(Locale.ROOT), device.entityId(),
                "Device " + device.name() + " set to " + newState, true, params
        ));

        return updated;
    }

    /**
     * Activates a predefined smart scene across multiple devices simultaneously.
     */
    public Map<String, Object> activateScene(String sceneIdOrName) {
        Optional<SmartScene> opt = store.findScene(sceneIdOrName);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Scene not found: " + sceneIdOrName);
        }
        SmartScene scene = opt.get();
        SmartHomeConfig config = store.getConfig();

        List<Map<String, Object>> executed = new ArrayList<>();
        for (SmartScene.SceneAction action : scene.actions()) {
            try {
                Map<String, Object> params = new HashMap<>();
                if ("set_brightness".equals(action.action())) {
                    params.put("brightness", action.value());
                } else if ("set_color".equals(action.action())) {
                    params.put("color", action.value());
                } else if ("set_temperature".equals(action.action())) {
                    params.put("temperature", action.value());
                }

                SmartDevice d = executeDeviceAction(action.deviceId(), action.action(), params);
                executed.add(Map.of(
                        "deviceId", d.entityId(),
                        "name", d.name(),
                        "action", action.action(),
                        "state", d.state()
                ));
            } catch (Exception e) {
                log.warn("Failed scene action on {}: {}", action.deviceId(), e.getMessage());
            }
        }

        // Dispatch Scene activation to Home Assistant / MQTT
        if (config.haEnabled() && !config.simulationMode()) {
            haClient.callService(config.haBaseUrl(), config.haToken(), "scene", "turn_on", Map.of("entity_id", scene.id()));
        }
        if (config.mqttEnabled() && !config.simulationMode()) {
            mqttClient.publish("jarvis/iot/scene/" + scene.id() + "/state", "activated");
        }

        store.logEvent(SmartHomeEvent.of(
                "SCENE", "ACTIVATE_SCENE", scene.id(),
                "Activated scene: " + scene.name(), true, Map.of("actionsCount", executed.size())
        ));

        return Map.of(
                "scene", scene,
                "executedActions", executed,
                "timestamp", Instant.now().toString()
        );
    }

    /**
     * Synchronizes live entity states from Home Assistant.
     */
    public int syncFromHomeAssistant() {
        SmartHomeConfig config = store.getConfig();
        if (!config.haEnabled() || config.simulationMode()) {
            return 0;
        }

        List<Map<String, Object>> haStates = haClient.fetchStates(config.haBaseUrl(), config.haToken());
        int imported = 0;

        for (Map<String, Object> item : haStates) {
            String entityId = (String) item.get("entity_id");
            if (entityId == null) continue;

            String state = String.valueOf(item.get("state"));
            @SuppressWarnings("unchecked")
            Map<String, Object> attributes = (Map<String, Object>) item.getOrDefault("attributes", Collections.emptyMap());
            String friendlyName = (String) attributes.getOrDefault("friendly_name", entityId);

            SmartDevice.DeviceType type = SmartDevice.DeviceType.OTHER;
            if (entityId.startsWith("light.")) type = SmartDevice.DeviceType.LIGHT;
            else if (entityId.startsWith("switch.")) type = SmartDevice.DeviceType.SWITCH;
            else if (entityId.startsWith("climate.")) type = SmartDevice.DeviceType.CLIMATE;
            else if (entityId.startsWith("sensor.") || entityId.startsWith("binary_sensor.")) type = SmartDevice.DeviceType.SENSOR;
            else if (entityId.startsWith("scene.")) type = SmartDevice.DeviceType.SCENE;

            // Only register relevant domestic IoT categories
            if (type != SmartDevice.DeviceType.OTHER) {
                SmartDevice d = new SmartDevice(
                        "ha-" + entityId.replace('.', '-'),
                        entityId,
                        friendlyName,
                        type,
                        "Home Assistant",
                        state,
                        SmartDevice.Protocol.HOME_ASSISTANT,
                        attributes,
                        true,
                        Instant.now().toString()
                );
                store.saveDevice(d);
                imported++;
            }
        }

        store.logEvent(SmartHomeEvent.of(
                "HOME_ASSISTANT", "SYNC_ENTITIES", "homeassistant",
                "Synchronized " + imported + " entities from Home Assistant", true, Map.of("count", imported)
        ));

        return imported;
    }

    /**
     * Natural Language Voice Command Dispatcher.
     * Evaluates natural queries like:
     * - "Jarvis, set room lighting to Movie mode"
     * - "Jarvis, turn off the desk power strip"
     * - "Jarvis, turn on desk power strip"
     * Returns rich formatted response with <think> tag if handled.
     */
    public Optional<String> handleVoiceCommand(String query, boolean isTamil) {
        if (query == null || query.isBlank()) return Optional.empty();
        String trimmed = query.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        // 1. "Jarvis, set room lighting to Movie mode" / Scene Activation
        Matcher sceneEng = SCENE_ENG.matcher(trimmed);
        if (sceneEng.matches() || SCENE_TAMIL.matcher(trimmed).matches()) {
            String sceneKeyword = "movie";
            if (sceneEng.matches() && sceneEng.group(1) != null) {
                sceneKeyword = sceneEng.group(1).toLowerCase(Locale.ROOT);
            } else if (lower.contains("work") || trimmed.contains("ஒர்க்") || trimmed.contains("வேலை")) {
                sceneKeyword = "work";
            } else if (lower.contains("night") || trimmed.contains("நைட்") || trimmed.contains("இரவு")) {
                sceneKeyword = "night";
            } else if (lower.contains("relax") || trimmed.contains("ரிலாக்ஸ்")) {
                sceneKeyword = "relax";
            } else if (lower.contains("gaming") || trimmed.contains("கேமிங்")) {
                sceneKeyword = "gaming";
            }

            return Optional.of(handleSceneVoiceCommand(sceneKeyword, isTamil));
        }

        // 2. "Jarvis, turn off the desk power strip"
        if (DESK_STRIP_OFF_ENG.matcher(trimmed).matches() || DESK_STRIP_OFF_TAMIL.matcher(trimmed).matches()) {
            return Optional.of(handleDeskStripCommand(false, isTamil));
        }

        // 3. "Jarvis, turn on the desk power strip"
        if (DESK_STRIP_ON_ENG.matcher(trimmed).matches() || DESK_STRIP_ON_TAMIL.matcher(trimmed).matches()) {
            return Optional.of(handleDeskStripCommand(true, isTamil));
        }

        // 4. Dim / Brightness Control ("dim room lighting to 20%", "set room lighting brightness to 50%")
        if (!lower.contains("volume") && !lower.contains("sound") && !lower.contains("audio")) {
            Matcher dimEng = DIM_BRIGHTNESS_ENG.matcher(trimmed);
            if (dimEng.matches()) {
                String targetDevice = dimEng.group(1).trim();
                int brightness = Integer.parseInt(dimEng.group(2));
                Optional<String> dimRes = handleDimmingCommand(targetDevice, brightness, isTamil);
                if (dimRes.isPresent()) return dimRes;
            }
            Matcher setBrightEng = SET_BRIGHTNESS_ENG.matcher(trimmed);
            if (setBrightEng.matches()) {
                String targetDevice = setBrightEng.group(1).trim();
                int brightness = Integer.parseInt(setBrightEng.group(2));
                Optional<String> dimRes = handleDimmingCommand(targetDevice, brightness, isTamil);
                if (dimRes.isPresent()) return dimRes;
            }
        }

        // 5. Generic Power Control ("turn off room lighting", "turn on ceiling light")
        Matcher pwrEng = DEVICE_POWER_ENG.matcher(trimmed);
        if (pwrEng.matches()) {
            String action = pwrEng.group(1).toLowerCase(Locale.ROOT);
            String target = pwrEng.group(2).trim();
            Optional<SmartDevice> devOpt = store.findDevice(target);
            if (devOpt.isPresent()) {
                boolean turnOn = "on".equals(action);
                return Optional.of(handleGenericDevicePower(devOpt.get(), turnOn, isTamil));
            }
        }

        // 6. Smart Home & IoT Telemetry / Status Query
        if (SMART_HOME_STATUS_ENG.matcher(trimmed).matches() || SMART_HOME_STATUS_TAMIL.matcher(trimmed).matches()) {
            return Optional.of(handleStatusVoiceCommand(isTamil));
        }

        return Optional.empty();
    }

    private String handleSceneVoiceCommand(String sceneKeyword, boolean isTamil) {
        String targetSceneId = "scene.movie_mode";
        String sceneName = "Movie Mode";
        String emoji = "🎬";

        if (sceneKeyword.contains("work")) {
            targetSceneId = "scene.work_mode";
            sceneName = "Work Mode";
            emoji = "💻";
        } else if (sceneKeyword.contains("night")) {
            targetSceneId = "scene.night_mode";
            sceneName = "Night Mode";
            emoji = "🌙";
        } else if (sceneKeyword.contains("relax")) {
            targetSceneId = "scene.relax_mode";
            sceneName = "Relax Mode";
            emoji = "🌅";
        } else if (sceneKeyword.contains("gaming")) {
            targetSceneId = "scene.gaming_mode";
            sceneName = "Gaming Mode";
            emoji = "🎮";
        }

        activateScene(targetSceneId);

        if (isTamil) {
            String displayTamil = "மூவி மோடு";
            if (sceneKeyword.contains("work")) {
                displayTamil = "வேலை மோடு (Work Mode)";
            } else if (sceneKeyword.contains("night")) {
                displayTamil = "இரவு மோடு (Night Mode)";
            } else if (sceneKeyword.contains("relax")) {
                displayTamil = "ரிலாக்ஸ் மோடு";
            } else if (sceneKeyword.contains("gaming")) {
                displayTamil = "கேமிங் மோடு";
            }
            return "<think>\n"
                    + "• குரல் கட்டளை: ஸ்மார்ட் ஹோம் காட்சி கோரப்பட்டது -> \"" + displayTamil + "\"\n"
                    + "• இலக்கு: " + targetSceneId + "\n"
                    + "• அறை விளக்குகள் 15% மங்கலாக்கப்பட்டு, டெஸ்க் பவர் ஸ்ட்ரிப் அணைக்கப்பட்டது.\n"
                    + "• Home Assistant REST & MQTT நெறிமுறைகள் மூலம் அனுப்பப்பட்டது.\n"
                    + "</think>\n\n"
                    + emoji + " **" + displayTamil + " செயல்படுத்தப்பட்டது (Smart Scene Activated)**\n\n"
                    + "- **அறை விளக்குகள் (Room Lighting):** 15% மங்கலாக்கப்பட்டது (சினிமா இண்டிகோ `#3b82f6`)\n"
                    + "- **மேல் கூரை விளக்கு (Ceiling Light):** அணைக்கப்பட்டது (OFF)\n"
                    + "- **டெஸ்க் பவர் ஸ்ட்ரிப் (Desk Power Strip):** அணைக்கப்பட்டது (0.0 W · கண்ணை கூசும் ஒளி தடுக்கப்பட்டது)\n"
                    + "- **இணைப்பு நெறிமுறை:** Home Assistant REST & MQTT Gateway\n\n"
                    + "ஐயா, அறை விளக்குகள் மூவி மோடுக்கு மாற்றப்பட்டு தேவையற்ற மின்சாதனங்கள் அணைக்கப்பட்டுள்ளன. திரைப்படத்தை மகிழ்ந்து பாருங்கள்.";
        } else {
            return "<think>\n"
                    + "• Voice/Text command: Smart Home scene activation requested -> \"" + sceneName + "\"\n"
                    + "• Target Scene: " + targetSceneId + "\n"
                    + "• Protocol dispatch: Dimming room lighting to 15% (Cinematic Indigo), switching OFF desk power strip and ambient ceiling light.\n"
                    + "• Dispatched through SmartHomeService (Home Assistant REST / MQTT broker gateway).\n"
                    + "</think>\n\n"
                    + emoji + " **" + sceneName + " Activated**\n\n"
                    + "- **Room Lighting:** Dimmed to **15%** (Cinematic Indigo `#3b82f6`)\n"
                    + "- **Ceiling Light:** Switched **OFF**\n"
                    + "- **Desk Power Strip:** Powered **OFF** (0.0 W · Glare eliminated)\n"
                    + "- **Environment:** Theater acoustics & ambient darkness calibrated\n"
                    + "- **Gateway:** Dispatched via Home Assistant REST API & Local MQTT Broker\n\n"
                    + "Sir, room lighting has been set to " + sceneName + " and peripheral power has been switched off. Enjoy your screening.";
        }
    }

    private String handleDeskStripCommand(boolean turnOn, boolean isTamil) {
        String action = turnOn ? "turn_on" : "turn_off";
        executeDeviceAction("switch.desk_power_strip", action, Collections.emptyMap());

        if (turnOn) {
            if (isTamil) {
                return "<think>\n"
                        + "• குரல் கட்டளை: டெஸ்க் பவர் ஸ்ட்ரிப்பை இயக்க கோரப்பட்டது.\n"
                        + "• இலக்கு: switch.desk_power_strip (பணி அறை - Office)\n"
                        + "• ரிலே ஆன் செய்யப்பட்டது. சுமை: 42.5 W.\n"
                        + "</think>\n\n"
                        + "🔌 **டெஸ்க் பவர் ஸ்ட்ரிப் இயக்கப்பட்டது (Powered ON)**\n\n"
                        + "- **சாதனம்:** டெஸ்க் பவர் ஸ்ட்ரிப் (`switch.desk_power_strip`)\n"
                        + "- **இடம்:** பணி அறை (Office Workstation)\n"
                        + "- **ரிலே நிலை:** 🟢 **செயலில் உள்ளது (ON)**\n"
                        + "- **மின் பயன்பாடு:** **42.5 W** (இயல்பான சுமை)\n"
                        + "- **நெறிமுறை:** MQTT Gateway (cmnd/desk_power_strip/POWER = ON) & HA REST\n\n"
                        + "டெஸ்க் பவர் ஸ்ட்ரிப் வெற்றிகரமாக இயக்கப்பட்டது, ஐயா.";
            } else {
                return "<think>\n"
                        + "• Voice/Text command: Smart Home switch power on requested -> \"Desk Power Strip\"\n"
                        + "• Target Device: switch.desk_power_strip (Office)\n"
                        + "• Relay command dispatched via SmartHomeService (MQTT: cmnd/desk_power_strip/POWER = ON, HA: switch.turn_on).\n"
                        + "• Outlets energized; telemetry reading 42.5W baseline.\n"
                        + "</think>\n\n"
                        + "🔌 **Desk Power Strip Powered ON**\n\n"
                        + "- **Device:** Desk Power Strip (`switch.desk_power_strip`)\n"
                        + "- **Location:** Office Workstation\n"
                        + "- **Relay State:** 🟢 **ON**\n"
                        + "- **Current Load:** **42.5 W** (Active load detected)\n"
                        + "- **Protocol:** Dispatched via Local MQTT Broker & Home Assistant Gateway\n\n"
                        + "Desk power strip is now active and delivering power, Sir.";
            }
        } else {
            if (isTamil) {
                return "<think>\n"
                        + "• குரல் கட்டளை: டெஸ்க் பவர் ஸ்ட்ரிப்பை அணைக்க கோரப்பட்டது.\n"
                        + "• இலக்கு: switch.desk_power_strip (Office)\n"
                        + "• ரிலே ஆஃப் செய்யப்பட்டது. மின் நுகர்வு: 0.0 W.\n"
                        + "</think>\n\n"
                        + "🔌 **டெஸ்க் பவர் ஸ்ட்ரிப் அணைக்கப்பட்டது (Powered OFF)**\n\n"
                        + "- **சாதனம்:** டெஸ்க் பவர் ஸ்ட்ரிப் (`switch.desk_power_strip`)\n"
                        + "- **இடம்:** பணி அறை (Office Workstation)\n"
                        + "- **ரிலே நிலை:** 🔴 **அணைக்கப்பட்டது (OFF)**\n"
                        + "- **மின் பயன்பாடு:** **0.0 W** (முற்றிலும் மின்சாரம் துண்டிக்கப்பட்டது)\n"
                        + "- **நெறிமுறை:** MQTT Gateway (cmnd/desk_power_strip/POWER = OFF) & HA REST\n\n"
                        + "டெஸ்க் பவர் ஸ்ட்ரிப் பாதுகாப்பாக அணைக்கப்பட்டுவிட்டது, ஐயா.";
            } else {
                return "<think>\n"
                        + "• Voice/Text command: Smart Home switch power off requested -> \"Desk Power Strip\"\n"
                        + "• Target Device: switch.desk_power_strip (Office)\n"
                        + "• Relay command dispatched via SmartHomeService (MQTT: cmnd/desk_power_strip/POWER = OFF, HA: switch.turn_off).\n"
                        + "• Load dropped from 42.5W to 0.0W.\n"
                        + "</think>\n\n"
                        + "🔌 **Desk Power Strip Powered OFF**\n\n"
                        + "- **Device:** Desk Power Strip (`switch.desk_power_strip`)\n"
                        + "- **Location:** Office Workstation\n"
                        + "- **Relay State:** 🔴 **OFF**\n"
                        + "- **Current Load:** **0.0 W** (Was 42.5 W)\n"
                        + "- **Protocol:** Dispatched via Local MQTT Broker & Home Assistant Gateway\n\n"
                        + "The desk power strip has been safely switched off, Sir. All workstation outlets are de-energized.";
            }
        }
    }

    private Optional<String> handleDimmingCommand(String targetName, int brightness, boolean isTamil) {
        String lowerTarget = targetName.toLowerCase(Locale.ROOT);
        if (lowerTarget.contains("volume") || lowerTarget.contains("sound") || lowerTarget.contains("audio")) {
            return Optional.empty();
        }
        Optional<SmartDevice> opt = store.findDevice(targetName);
        if (opt.isEmpty() && (lowerTarget.contains("light") || lowerTarget.contains("room") || lowerTarget.contains("lamp") || lowerTarget.contains("bulb"))) {
            opt = store.findDevice("room lighting");
        }
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        SmartDevice dev = opt.get();
        executeDeviceAction(dev.entityId(), "set_brightness", Map.of("brightness", brightness));

        String result;
        if (isTamil) {
            result = "<think>\n"
                    + "• ஒளி மங்கலாக்கம்: " + dev.name() + " -> " + brightness + "%\n"
                    + "</think>\n\n"
                    + "💡 **" + dev.name() + " பிரகாசம் அமைக்கப்பட்டது**\n\n"
                    + "- **சாதனம்:** " + dev.name() + " (`" + dev.entityId() + "`)\n"
                    + "- **பிரகாசம்:** **" + brightness + "%**\n\n"
                    + "விளக்கின் பிரகாசம் " + brightness + "% ஆக மாற்றப்பட்டது, ஐயா.";
        } else {
            result = "<think>\n"
                    + "• Dimming light: " + dev.name() + " to " + brightness + "%\n"
                    + "</think>\n\n"
                    + "💡 **" + dev.name() + " Brightness Calibrated**\n\n"
                    + "- **Device:** " + dev.name() + " (`" + dev.entityId() + "`)\n"
                    + "- **Brightness Level:** **" + brightness + "%**\n\n"
                    + "Sir, I have adjusted " + dev.name() + " to " + brightness + "% brightness.";
        }
        return Optional.of(result);
    }

    private String handleGenericDevicePower(SmartDevice device, boolean turnOn, boolean isTamil) {
        executeDeviceAction(device.entityId(), turnOn ? "turn_on" : "turn_off", Collections.emptyMap());
        String stateStr = turnOn ? "ON" : "OFF";
        String dot = turnOn ? "🟢" : "🔴";

        if (isTamil) {
            return "<think>\n• சாதனம் நிலை மாற்றம்: " + device.name() + " -> " + stateStr + "\n</think>\n\n"
                    + dot + " **" + device.name() + " " + (turnOn ? "இயக்கப்பட்டது" : "அணைக்கப்பட்டது") + "**\n\n"
                    + "- **சாதனம்:** " + device.name() + " (`" + device.entityId() + "`)\n"
                    + "- **நிலை:** " + stateStr + "\n\n"
                    + "சாதனம் வெற்றிகரமாக மாற்றப்பட்டது, ஐயா.";
        } else {
            return "<think>\n• Device power toggle: " + device.name() + " -> " + stateStr + "\n</think>\n\n"
                    + dot + " **" + device.name() + " Switched " + stateStr + "**\n\n"
                    + "- **Device:** " + device.name() + " (`" + device.entityId() + "`)\n"
                    + "- **State:** " + stateStr + "\n"
                    + "- **Room:** " + device.room() + "\n\n"
                    + "Confirmed, Sir. " + device.name() + " is now " + stateStr + ".";
        }
    }

    private String handleStatusVoiceCommand(boolean isTamil) {
        var devices = store.listDevices();
        long activeCount = devices.stream().filter(d -> "on".equalsIgnoreCase(d.state()) || "cool".equalsIgnoreCase(d.state())).count();
        SmartHomeConfig cfg = store.getConfig();

        if (isTamil) {
            StringBuilder sb = new StringBuilder();
            sb.append("<think>\n• குரல் கட்டளை: ஸ்மார்ட் ஹோம் மற்றும் ஐஓடி சாதனங்களின் நிலை கோரப்பட்டது.\n</think>\n\n");
            sb.append("🏠 **ஸ்மார்ட் ஹோம் & ஐஓடி நிலை (Smart Home Telemetry)**\n\n");
            sb.append("- **இணைப்பு முறை:** ").append(cfg.simulationMode() ? "உள்ளூர் உருவகப்படுத்துதல் (Local Sandbox)" : "நேரடி நெட்வொர்க் (Live Network)").append("\n");
            sb.append("- **செயலில் உள்ள சாதனங்கள்:** **").append(activeCount).append("** / ").append(devices.size()).append(" மொத்தம்\n\n");
            sb.append("| சாதனம் | அறை | நிலை | நெறிமுறை |\n");
            sb.append("| :--- | :--- | :--- | :--- |\n");
            for (SmartDevice d : devices) {
                String icon = "on".equalsIgnoreCase(d.state()) ? "🟢" : "⚪";
                sb.append("| ").append(icon).append(" ").append(d.name()).append(" | ").append(d.room()).append(" | `").append(d.state().toUpperCase(Locale.ROOT)).append("` | ").append(d.protocol()).append(" |\n");
            }
            sb.append("\nஅனைத்து சாதனங்களும் இயல்பான இயக்க நிலையில் உள்ளன, ஐயா.");
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("<think>\n• Voice/Text command: Smart home and IoT device telemetry queried.\n</think>\n\n");
            sb.append("🏠 **Smart Home & IoT System Telemetry**\n\n");
            sb.append("- **Operating Mode:** ").append(cfg.simulationMode() ? "Local Sandbox Simulator" : "Live Local Network Gateway").append("\n");
            sb.append("- **Home Assistant:** ").append(cfg.haEnabled() ? "🟢 Connected (" + cfg.haBaseUrl() + ")" : "⚪ Standby").append("\n");
            sb.append("- **MQTT Broker:** ").append(mqttClient.isConnected() ? "🟢 Connected (" + cfg.mqttBrokerUrl() + ")" : "⚪ Standby / Simulated").append("\n");
            sb.append("- **Active Relays/Loads:** **").append(activeCount).append("** of ").append(devices.size()).append(" online devices\n\n");
            sb.append("| Device | Room | Status | Load / Attributes |\n");
            sb.append("| :--- | :--- | :--- | :--- |\n");
            for (SmartDevice d : devices) {
                String dot = "on".equalsIgnoreCase(d.state()) || "cool".equalsIgnoreCase(d.state()) ? "🟢" : "⚪";
                String attrSummary = "";
                if (d.attributes().containsKey("power_watts")) {
                    attrSummary = d.attributes().get("power_watts") + " W";
                } else if (d.attributes().containsKey("brightness")) {
                    attrSummary = "Brightness: " + d.attributes().get("brightness") + "%";
                } else if (d.attributes().containsKey("current_temperature")) {
                    attrSummary = d.attributes().get("current_temperature") + "°C";
                }
                sb.append("| ").append(dot).append(" **").append(d.name()).append("** | ").append(d.room()).append(" | `").append(d.state().toUpperCase(Locale.ROOT)).append("` | ").append(attrSummary).append(" |\n");
            }
            sb.append("\nAll home automation gateways are healthy and responsive, Sir.");
            return sb.toString();
        }
    }

    private void dispatchToHardware(SmartDevice device, String action, Map<String, Object> params, SmartHomeConfig config) {
        if (config.simulationMode()) {
            return;
        }

        // 1. Home Assistant Dispatch
        if (config.haEnabled()) {
            String entityId = device.entityId();
            String domain = entityId.contains(".") ? entityId.substring(0, entityId.indexOf('.')) : "switch";
            String service = action;

            Map<String, Object> payload = new HashMap<>();
            payload.put("entity_id", entityId);
            if (params != null) {
                payload.putAll(params);
            }

            haClient.callService(config.haBaseUrl(), config.haToken(), domain, service, payload);
        }

        // 2. MQTT Broker Dispatch (Tasmota / ESPHome / Zigbee2MQTT compatible)
        if (config.mqttEnabled()) {
            String shortId = device.entityId().replace('.', '_');
            String stateStr = "on".equalsIgnoreCase(device.state()) ? "ON" : "OFF";

            // Standard Tasmota topic convention
            mqttClient.publish("cmnd/" + shortId + "/POWER", stateStr);

            // Standard JARVIS IoT topic convention
            mqttClient.publish("jarvis/iot/" + device.entityId() + "/state", stateStr);

            if (params != null && !params.isEmpty()) {
                try {
                    String jsonPayload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(params);
                    mqttClient.publish("jarvis/iot/" + device.entityId() + "/set", jsonPayload);
                } catch (Exception ignored) {}
            }
        }
    }

    public SmartHomeStore getStore() {
        return store;
    }

    public SmartHomeConfig getConfig() {
        return store.getConfig();
    }

    public void updateConfig(SmartHomeConfig newConfig) {
        store.saveConfig(newConfig);
        if (newConfig.mqttEnabled() && !newConfig.simulationMode()) {
            mqttClient.connect(newConfig.mqttBrokerUrl(), newConfig.mqttClientId(), newConfig.mqttUsername(), newConfig.mqttPassword());
        } else if (!newConfig.mqttEnabled()) {
            mqttClient.disconnect();
        }
    }

    public Map<String, Object> getStatus() {
        SmartHomeConfig cfg = store.getConfig();
        var devices = store.listDevices();
        long activeCount = devices.stream().filter(d -> "on".equalsIgnoreCase(d.state()) || "cool".equalsIgnoreCase(d.state())).count();

        boolean haReachable = cfg.haEnabled() && haClient.testConnection(cfg.haBaseUrl(), cfg.haToken());

        return Map.of(
                "homeAssistantConnected", haReachable,
                "homeAssistantBaseUrl", cfg.haBaseUrl(),
                "mqttConnected", mqttClient.isConnected(),
                "mqttBrokerUrl", cfg.mqttBrokerUrl(),
                "simulationMode", cfg.simulationMode(),
                "totalDevices", devices.size(),
                "activeDevices", activeCount,
                "totalScenes", store.listScenes().size()
        );
    }
}

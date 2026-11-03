-- Run in pgAdmin connected to the dedicated JARVIS database.
-- Adds Smart Home & IoT Automation tables, state persistence, scenes, and audit logs.
-- Compatible with Home Assistant REST/WebSocket API and local MQTT brokers (Mosquitto/EMQX).

BEGIN;

-- 1. Smart Devices Registry & Live State
CREATE TABLE IF NOT EXISTS jarvis.smart_devices (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_name name NOT NULL DEFAULT session_user,
    entity_id text NOT NULL CHECK (length(btrim(entity_id)) BETWEEN 3 AND 120),
    name text NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 120),
    device_type text NOT NULL CHECK (device_type IN ('LIGHT', 'SWITCH', 'CLIMATE', 'SENSOR', 'SCENE', 'MEDIA', 'LOCK', 'OTHER')),
    room text NOT NULL DEFAULT 'Living Room',
    state text NOT NULL DEFAULT 'off',
    protocol text NOT NULL DEFAULT 'SIMULATED' CHECK (protocol IN ('HOME_ASSISTANT', 'MQTT', 'SIMULATED', 'LOCAL')),
    attributes jsonb NOT NULL DEFAULT '{}'::jsonb,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_smart_device_entity UNIQUE (owner_name, entity_id)
);

-- 2. Smart Home Scenes & Macros
CREATE TABLE IF NOT EXISTS jarvis.smart_scenes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_name name NOT NULL DEFAULT session_user,
    scene_id text NOT NULL CHECK (length(btrim(scene_id)) BETWEEN 3 AND 120),
    name text NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 120),
    icon text NOT NULL DEFAULT 'film',
    description text NOT NULL DEFAULT '',
    actions jsonb NOT NULL DEFAULT '[]'::jsonb,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_smart_scene_id UNIQUE (owner_name, scene_id)
);

-- 3. Smart Home Telemetry & Audit Action Log
CREATE TABLE IF NOT EXISTS jarvis.smarthome_audit_logs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_name name NOT NULL DEFAULT session_user,
    source text NOT NULL DEFAULT 'VOICE' CHECK (source IN ('VOICE', 'REST_API', 'HOME_ASSISTANT', 'MQTT', 'SCENE', 'SYSTEM')),
    action text NOT NULL,
    target text NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    success boolean NOT NULL DEFAULT true,
    message text,
    executed_at timestamptz NOT NULL DEFAULT now()
);

-- 4. Home Assistant & MQTT Connection Configuration
CREATE TABLE IF NOT EXISTS jarvis.smarthome_config (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_name name NOT NULL DEFAULT session_user,
    ha_base_url text NOT NULL DEFAULT 'http://homeassistant.local:8123',
    ha_token text NOT NULL DEFAULT '',
    mqtt_broker_url text NOT NULL DEFAULT 'tcp://localhost:1883',
    mqtt_client_id text NOT NULL DEFAULT 'jarvis-ai-gateway',
    mqtt_username text NOT NULL DEFAULT '',
    mqtt_password text NOT NULL DEFAULT '',
    mqtt_base_topic text NOT NULL DEFAULT 'homeassistant',
    simulation_mode boolean NOT NULL DEFAULT true,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_smarthome_config_owner UNIQUE (owner_name)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_smart_devices_owner ON jarvis.smart_devices(owner_name, entity_id);
CREATE INDEX IF NOT EXISTS idx_smart_devices_room ON jarvis.smart_devices(owner_name, room);
CREATE INDEX IF NOT EXISTS idx_smart_scenes_owner ON jarvis.smart_scenes(owner_name, scene_id);
CREATE INDEX IF NOT EXISTS idx_smarthome_audit_time ON jarvis.smarthome_audit_logs(owner_name, executed_at DESC);

-- Enable Row-Level Security
ALTER TABLE jarvis.smart_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE jarvis.smart_devices FORCE ROW LEVEL SECURITY;
CREATE POLICY smart_devices_owner ON jarvis.smart_devices
    USING (owner_name = session_user)
    WITH CHECK (owner_name = session_user);

ALTER TABLE jarvis.smart_scenes ENABLE ROW LEVEL SECURITY;
ALTER TABLE jarvis.smart_scenes FORCE ROW LEVEL SECURITY;
CREATE POLICY smart_scenes_owner ON jarvis.smart_scenes
    USING (owner_name = session_user)
    WITH CHECK (owner_name = session_user);

ALTER TABLE jarvis.smarthome_audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE jarvis.smarthome_audit_logs FORCE ROW LEVEL SECURITY;
CREATE POLICY smarthome_audit_logs_owner ON jarvis.smarthome_audit_logs
    USING (owner_name = session_user)
    WITH CHECK (owner_name = session_user);

ALTER TABLE jarvis.smarthome_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE jarvis.smarthome_config FORCE ROW LEVEL SECURITY;
CREATE POLICY smarthome_config_owner ON jarvis.smarthome_config
    USING (owner_name = session_user)
    WITH CHECK (owner_name = session_user);

-- Stored Function: Upsert Smart Device
CREATE OR REPLACE FUNCTION jarvis.smart_device_upsert(
    p_entity_id text,
    p_name text,
    p_device_type text,
    p_room text DEFAULT 'Living Room',
    p_state text DEFAULT 'off',
    p_protocol text DEFAULT 'SIMULATED',
    p_attributes jsonb DEFAULT '{}'::jsonb
)
RETURNS jarvis.smart_devices
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, jarvis
AS $$
DECLARE
    result jarvis.smart_devices;
BEGIN
    INSERT INTO jarvis.smart_devices (
        owner_name, entity_id, name, device_type, room, state, protocol, attributes, updated_at
    )
    VALUES (
        session_user, p_entity_id, p_name, p_device_type, COALESCE(p_room, 'Living Room'),
        COALESCE(p_state, 'off'), COALESCE(p_protocol, 'SIMULATED'), COALESCE(p_attributes, '{}'::jsonb), now()
    )
    ON CONFLICT (owner_name, entity_id) DO UPDATE SET
        name = EXCLUDED.name,
        device_type = EXCLUDED.device_type,
        room = EXCLUDED.room,
        state = EXCLUDED.state,
        protocol = EXCLUDED.protocol,
        attributes = jarvis.smart_devices.attributes || EXCLUDED.attributes,
        updated_at = now()
    RETURNING * INTO result;
    RETURN result;
END;
$$;

-- Stored Function: Update Smart Device State
CREATE OR REPLACE FUNCTION jarvis.smart_device_update_state(
    p_entity_id text,
    p_state text,
    p_attributes jsonb DEFAULT NULL
)
RETURNS jarvis.smart_devices
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, jarvis
AS $$
DECLARE
    result jarvis.smart_devices;
BEGIN
    UPDATE jarvis.smart_devices
    SET
        state = p_state,
        attributes = CASE 
            WHEN p_attributes IS NOT NULL THEN jarvis.smart_devices.attributes || p_attributes 
            ELSE jarvis.smart_devices.attributes 
        END,
        updated_at = now()
    WHERE owner_name = session_user AND entity_id = p_entity_id
    RETURNING * INTO result;

    RETURN result;
END;
$$;

-- Stored Function: List Devices
CREATE OR REPLACE FUNCTION jarvis.smart_device_list()
RETURNS SETOF jarvis.smart_devices
LANGUAGE sql
SECURITY INVOKER
STABLE
SET search_path = pg_catalog, jarvis
AS $$
    SELECT * FROM jarvis.smart_devices
    WHERE owner_name = session_user AND is_active = true
    ORDER BY room ASC, name ASC;
$$;

-- Stored Function: Upsert Scene
CREATE OR REPLACE FUNCTION jarvis.smart_scene_upsert(
    p_scene_id text,
    p_name text,
    p_icon text DEFAULT 'film',
    p_description text DEFAULT '',
    p_actions jsonb DEFAULT '[]'::jsonb
)
RETURNS jarvis.smart_scenes
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, jarvis
AS $$
DECLARE
    result jarvis.smart_scenes;
BEGIN
    INSERT INTO jarvis.smart_scenes (
        owner_name, scene_id, name, icon, description, actions, updated_at
    )
    VALUES (
        session_user, p_scene_id, p_name, COALESCE(p_icon, 'film'),
        COALESCE(p_description, ''), COALESCE(p_actions, '[]'::jsonb), now()
    )
    ON CONFLICT (owner_name, scene_id) DO UPDATE SET
        name = EXCLUDED.name,
        icon = EXCLUDED.icon,
        description = EXCLUDED.description,
        actions = EXCLUDED.actions,
        updated_at = now()
    RETURNING * INTO result;
    RETURN result;
END;
$$;

-- Stored Function: List Scenes
CREATE OR REPLACE FUNCTION jarvis.smart_scene_list()
RETURNS SETOF jarvis.smart_scenes
LANGUAGE sql
SECURITY INVOKER
STABLE
SET search_path = pg_catalog, jarvis
AS $$
    SELECT * FROM jarvis.smart_scenes
    WHERE owner_name = session_user AND is_active = true
    ORDER BY name ASC;
$$;

-- Stored Function: Log Audit Action
CREATE OR REPLACE FUNCTION jarvis.smarthome_log_action(
    p_source text,
    p_action text,
    p_target text,
    p_payload jsonb DEFAULT '{}'::jsonb,
    p_success boolean DEFAULT true,
    p_message text DEFAULT NULL
)
RETURNS jarvis.smarthome_audit_logs
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, jarvis
AS $$
DECLARE
    result jarvis.smarthome_audit_logs;
BEGIN
    INSERT INTO jarvis.smarthome_audit_logs (
        owner_name, source, action, target, payload, success, message, executed_at
    )
    VALUES (
        session_user, p_source, p_action, p_target, COALESCE(p_payload, '{}'::jsonb),
        p_success, p_message, now()
    )
    RETURNING * INTO result;
    RETURN result;
END;
$$;

-- Stored Function: Save Configuration
CREATE OR REPLACE FUNCTION jarvis.smarthome_config_save(
    p_ha_url text,
    p_ha_token text,
    p_mqtt_url text,
    p_mqtt_client_id text,
    p_mqtt_username text,
    p_mqtt_password text,
    p_mqtt_topic text,
    p_simulation boolean
)
RETURNS jarvis.smarthome_config
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, jarvis
AS $$
DECLARE
    result jarvis.smarthome_config;
BEGIN
    INSERT INTO jarvis.smarthome_config (
        owner_name, ha_base_url, ha_token, mqtt_broker_url, mqtt_client_id,
        mqtt_username, mqtt_password, mqtt_base_topic, simulation_mode, updated_at
    )
    VALUES (
        session_user, p_ha_url, p_ha_token, p_mqtt_url, p_mqtt_client_id,
        p_mqtt_username, p_mqtt_password, p_mqtt_topic, p_simulation, now()
    )
    ON CONFLICT (owner_name) DO UPDATE SET
        ha_base_url = EXCLUDED.ha_base_url,
        ha_token = CASE WHEN length(EXCLUDED.ha_token) > 0 THEN EXCLUDED.ha_token ELSE jarvis.smarthome_config.ha_token END,
        mqtt_broker_url = EXCLUDED.mqtt_broker_url,
        mqtt_client_id = EXCLUDED.mqtt_client_id,
        mqtt_username = EXCLUDED.mqtt_username,
        mqtt_password = CASE WHEN length(EXCLUDED.mqtt_password) > 0 THEN EXCLUDED.mqtt_password ELSE jarvis.smarthome_config.mqtt_password END,
        mqtt_base_topic = EXCLUDED.mqtt_base_topic,
        simulation_mode = EXCLUDED.simulation_mode,
        updated_at = now()
    RETURNING * INTO result;
    RETURN result;
END;
$$;

-- Stored Function: Get Configuration
CREATE OR REPLACE FUNCTION jarvis.smarthome_config_get()
RETURNS jarvis.smarthome_config
LANGUAGE sql
SECURITY INVOKER
STABLE
SET search_path = pg_catalog, jarvis
AS $$
    SELECT * FROM jarvis.smarthome_config
    WHERE owner_name = session_user
    LIMIT 1;
$$;

-- Seed Default Smart Home Devices
SELECT jarvis.smart_device_upsert(
    'light.room_lighting',
    'Room Lighting',
    'LIGHT',
    'Living Room',
    'off',
    'SIMULATED',
    '{"brightness": 80, "color": "#f8fafc", "color_temp": 4000, "supported_modes": ["brightness", "color", "scenes"]}'::jsonb
);

SELECT jarvis.smart_device_upsert(
    'switch.desk_power_strip',
    'Desk Power Strip',
    'SWITCH',
    'Office',
    'on',
    'SIMULATED',
    '{"power_watts": 42.5, "voltage": 230.0, "current_amps": 0.18, "outlets": 4, "active_outlets": ["MacBook / PC", "Monitor 1", "Monitor 2"]}'::jsonb
);

SELECT jarvis.smart_device_upsert(
    'light.ceiling_light',
    'Ceiling Light',
    'LIGHT',
    'Living Room',
    'off',
    'SIMULATED',
    '{"brightness": 100, "color": "#ffe8b8"}'::jsonb
);

SELECT jarvis.smart_device_upsert(
    'climate.room_thermostat',
    'Room Climate & AC',
    'CLIMATE',
    'Living Room',
    'cool',
    'SIMULATED',
    '{"current_temperature": 24.2, "target_temperature": 22.0, "fan_mode": "auto", "hvac_modes": ["cool", "heat", "fan_only", "off"]}'::jsonb
);

SELECT jarvis.smart_device_upsert(
    'sensor.room_temperature',
    'Environment Sensor',
    'SENSOR',
    'Living Room',
    '24.2',
    'SIMULATED',
    '{"unit": "°C", "humidity": 48.5, "battery_pct": 92}'::jsonb
);

SELECT jarvis.smart_device_upsert(
    'binary_sensor.motion',
    'Motion Sensor',
    'SENSOR',
    'Living Room',
    'off',
    'SIMULATED',
    '{"motion_detected": false, "last_motion": "12m ago"}'::jsonb
);

-- Seed Default Scenes
SELECT jarvis.smart_scene_upsert(
    'scene.movie_mode',
    'Movie Mode',
    'film',
    'Dims room lighting to 15% cinematic indigo, turns off desk power strip and ambient lights',
    '[
        {"deviceId": "light.room_lighting", "action": "set_brightness", "value": 15},
        {"deviceId": "light.room_lighting", "action": "set_color", "value": "#3b82f6"},
        {"deviceId": "switch.desk_power_strip", "action": "turn_off"},
        {"deviceId": "light.ceiling_light", "action": "turn_off"}
    ]'::jsonb
);

SELECT jarvis.smart_scene_upsert(
    'scene.work_mode',
    'Work Mode',
    'briefcase',
    'Powers on desk power strip, sets room lighting to 100% crisp focused task light',
    '[
        {"deviceId": "switch.desk_power_strip", "action": "turn_on"},
        {"deviceId": "light.room_lighting", "action": "set_brightness", "value": 100},
        {"deviceId": "light.room_lighting", "action": "set_color", "value": "#ffffff"},
        {"deviceId": "light.ceiling_light", "action": "turn_on"}
    ]'::jsonb
);

SELECT jarvis.smart_scene_upsert(
    'scene.night_mode',
    'Night Mode',
    'moon',
    'Turns off all lights, powers off desk power strip, sets thermostat to energy saving',
    '[
        {"deviceId": "light.room_lighting", "action": "turn_off"},
        {"deviceId": "light.ceiling_light", "action": "turn_off"},
        {"deviceId": "switch.desk_power_strip", "action": "turn_off"},
        {"deviceId": "climate.room_thermostat", "action": "set_temperature", "value": 25.0}
    ]'::jsonb
);

SELECT jarvis.smart_scene_upsert(
    'scene.relax_mode',
    'Relax Mode',
    'coffee',
    'Soft warm amber ambient glow at 35% brightness',
    '[
        {"deviceId": "light.room_lighting", "action": "set_brightness", "value": 35},
        {"deviceId": "light.room_lighting", "action": "set_color", "value": "#f59e0b"},
        {"deviceId": "light.ceiling_light", "action": "turn_off"},
        {"deviceId": "switch.desk_power_strip", "action": "turn_on"}
    ]'::jsonb
);

SELECT jarvis.smart_scene_upsert(
    'scene.gaming_mode',
    'Gaming Mode',
    'gamepad-2',
    'Desk strip ON, cybernetic neon magenta & violet ambient glow',
    '[
        {"deviceId": "switch.desk_power_strip", "action": "turn_on"},
        {"deviceId": "light.room_lighting", "action": "set_brightness", "value": 45},
        {"deviceId": "light.room_lighting", "action": "set_color", "value": "#a855f7"}
    ]'::jsonb
);

COMMIT;

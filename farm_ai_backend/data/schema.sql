-- PoultryGuard AI Database Schema for Supabase PostgreSQL
-- Supporting multi-farm and multi-device (ESP32-DevKitC) telemetry

-- 1. Farms Table
CREATE TABLE IF NOT EXISTS farms (
    id VARCHAR(50) PRIMARY KEY, -- e.g. 'farm_a'
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

-- 2. Devices Table (ESP32-DevKitC units)
CREATE TABLE IF NOT EXISTS devices (
    id VARCHAR(50) PRIMARY KEY, -- e.g. 'esp32_devkitc_a' or MAC address
    farm_id VARCHAR(50) REFERENCES farms(id) ON DELETE CASCADE NOT NULL,
    name VARCHAR(100) NOT NULL,
    thingspeak_channel_id VARCHAR(50),
    thingspeak_read_api_key VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

-- 3. Telemetry Table: Environmental Sensor History
CREATE TABLE IF NOT EXISTS sensor_telemetry (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(50) REFERENCES devices(id) ON DELETE CASCADE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    temperature NUMERIC(5, 2) NOT NULL,
    humidity NUMERIC(5, 2) NOT NULL,
    ammonia NUMERIC(5, 2) NOT NULL,
    sound_level NUMERIC(5, 2) NOT NULL
);

-- Indexing created_at for fast time-series queries and graphs per device
CREATE INDEX IF NOT EXISTS idx_sensor_telemetry_device_created ON sensor_telemetry (device_id, created_at DESC);

-- 4. Disease Predictions Table: History of AI inferences
CREATE TABLE IF NOT EXISTS disease_predictions (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(50) REFERENCES devices(id) ON DELETE CASCADE NOT NULL,
    telemetry_id BIGINT REFERENCES sensor_telemetry(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    disease VARCHAR(100) NOT NULL, -- e.g. 'Respiratory', 'Digestive', 'None'
    risk_level VARCHAR(20) NOT NULL, -- 'LOW', 'MEDIUM', 'HIGH'
    confidence NUMERIC(4, 3) NOT NULL,
    recommendation TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_disease_predictions_device_created ON disease_predictions (device_id, created_at DESC);

-- 5. Settings Table: Dashboard Thresholds and Controls per Device
CREATE TABLE IF NOT EXISTS farm_settings (
    device_id VARCHAR(50) PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE,
    updated_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    vent_temp NUMERIC(4, 2) DEFAULT 26.0 NOT NULL,
    heater_temp NUMERIC(4, 2) DEFAULT 20.0 NOT NULL,
    lights_on_hour INTEGER DEFAULT 6 NOT NULL,
    lights_off_hour INTEGER DEFAULT 20 NOT NULL,
    sprinkler_threshold NUMERIC(4, 2) DEFAULT 29.5 NOT NULL,
    sms_alerts_enabled BOOLEAN DEFAULT TRUE NOT NULL,
    recipient_phone VARCHAR(20) DEFAULT '' NOT NULL
);

-- Seed initial default farm, device, and settings for backwards compatibility
INSERT INTO farms (id, name)
VALUES ('default_farm', 'Default Poultry Farm')
ON CONFLICT (id) DO NOTHING;

INSERT INTO devices (id, farm_id, name, thingspeak_channel_id, thingspeak_read_api_key)
VALUES ('default_device', 'default_farm', 'Shed 1 Controller (ESP32-DevKitC)', 'default_channel', 'default_key')
ON CONFLICT (id) DO NOTHING;

INSERT INTO farm_settings (device_id, vent_temp, heater_temp, lights_on_hour, lights_off_hour, sprinkler_threshold, sms_alerts_enabled, recipient_phone)
VALUES ('default_device', 26.00, 20.00, 6, 20, 29.50, TRUE, '')
ON CONFLICT (device_id) DO NOTHING;

-- Migration helpers for existing databases
ALTER TABLE devices ADD COLUMN IF NOT EXISTS thingspeak_channel_id VARCHAR(50);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS thingspeak_read_api_key VARCHAR(50);

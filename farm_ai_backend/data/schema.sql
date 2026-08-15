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
    kit_id VARCHAR(50),
    last_seen_at TIMESTAMPTZ,
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
ALTER TABLE devices ADD COLUMN IF NOT EXISTS kit_id VARCHAR(50);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;

-- 6. Batches Table
CREATE TABLE IF NOT EXISTS batches (
    id VARCHAR(50) PRIMARY KEY, -- e.g. 'BATCH-001'
    farm_id VARCHAR(50) REFERENCES farms(id) ON DELETE CASCADE NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    initial_count INTEGER NOT NULL CHECK (initial_count > 0),
    current_count INTEGER NOT NULL CHECK (current_count >= 0),
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL CHECK (status IN ('ACTIVE', 'SOLD', 'CLOSED')),
    breed VARCHAR(50) DEFAULT 'Broiler' NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    CONSTRAINT chk_count CHECK (current_count <= initial_count)
);

-- View for dynamic age_days calculation
CREATE OR REPLACE VIEW v_batches AS
SELECT 
    id,
    farm_id,
    start_date,
    end_date,
    initial_count,
    current_count,
    status,
    breed,
    created_at,
    CASE 
        WHEN status IN ('SOLD', 'CLOSED') AND end_date IS NOT NULL THEN (end_date - start_date)
        ELSE (CURRENT_DATE - start_date)
    END AS age_days
FROM batches;

-- Link existing tables to batches
ALTER TABLE sensor_telemetry ADD COLUMN IF NOT EXISTS batch_id VARCHAR(50) REFERENCES batches(id) ON DELETE SET NULL;
ALTER TABLE disease_predictions ADD COLUMN IF NOT EXISTS batch_id VARCHAR(50) REFERENCES batches(id) ON DELETE SET NULL;

-- Retention and Event Columns/Tables
ALTER TABLE public.sensor_telemetry ADD COLUMN IF NOT EXISTS sound_url TEXT;
ALTER TABLE public.sensor_telemetry ADD COLUMN IF NOT EXISTS image_url TEXT;

CREATE TABLE IF NOT EXISTS public.disease_events (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(50) REFERENCES public.devices(id) ON DELETE CASCADE NOT NULL,
    batch_id VARCHAR(50) REFERENCES public.batches(id) ON DELETE SET NULL,
    disease VARCHAR(100) NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    max_confidence NUMERIC(4, 3) NOT NULL,
    representative_image_url TEXT,
    representative_sound_url TEXT,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    prediction_count INTEGER DEFAULT 1 NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RESOLVED')),
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

ALTER TABLE public.disease_predictions ADD COLUMN IF NOT EXISTS event_id BIGINT REFERENCES public.disease_events(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS public.daily_telemetry (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(50) REFERENCES public.devices(id) ON DELETE CASCADE NOT NULL,
    batch_id VARCHAR(50) REFERENCES public.batches(id) ON DELETE SET NULL,
    date DATE NOT NULL,
    temp_avg NUMERIC(5, 2) NOT NULL,
    temp_min NUMERIC(5, 2) NOT NULL,
    temp_max NUMERIC(5, 2) NOT NULL,
    hum_avg NUMERIC(5, 2) NOT NULL,
    hum_min NUMERIC(5, 2) NOT NULL,
    hum_max NUMERIC(5, 2) NOT NULL,
    ammonia_avg NUMERIC(5, 2) NOT NULL,
    ammonia_min NUMERIC(5, 2) NOT NULL,
    ammonia_max NUMERIC(5, 2) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    CONSTRAINT unique_device_date UNIQUE (device_id, date)
);

GRANT ALL PRIVILEGES ON TABLE public.disease_events TO anon, authenticated;
GRANT ALL PRIVILEGES ON TABLE public.daily_telemetry TO anon, authenticated;

ALTER TABLE IF EXISTS public.disease_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.daily_telemetry ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow public insert on disease_events" ON public.disease_events;
DROP POLICY IF EXISTS "Allow public select on disease_events" ON public.disease_events;
DROP POLICY IF EXISTS "Allow public update on disease_events" ON public.disease_events;

DROP POLICY IF EXISTS "Allow public insert on daily_telemetry" ON public.daily_telemetry;
DROP POLICY IF EXISTS "Allow public select on daily_telemetry" ON public.daily_telemetry;
DROP POLICY IF EXISTS "Allow public update on daily_telemetry" ON public.daily_telemetry;

CREATE POLICY "Allow public insert on disease_events" ON public.disease_events FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public select on disease_events" ON public.disease_events FOR SELECT USING (true);
CREATE POLICY "Allow public update on disease_events" ON public.disease_events FOR UPDATE USING (true) WITH CHECK (true);

CREATE POLICY "Allow public insert on daily_telemetry" ON public.daily_telemetry FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public select on daily_telemetry" ON public.daily_telemetry FOR SELECT USING (true);
CREATE POLICY "Allow public update on daily_telemetry" ON public.daily_telemetry FOR UPDATE USING (true) WITH CHECK (true);

-- 7. Grant Privileges for Supabase API access (anon and authenticated roles)
GRANT ALL PRIVILEGES ON TABLE public.farms TO anon, authenticated;
GRANT ALL PRIVILEGES ON TABLE public.devices TO anon, authenticated;
GRANT ALL PRIVILEGES ON TABLE public.sensor_telemetry TO anon, authenticated;
GRANT ALL PRIVILEGES ON TABLE public.disease_predictions TO anon, authenticated;
GRANT ALL PRIVILEGES ON TABLE public.farm_settings TO anon, authenticated;
GRANT ALL PRIVILEGES ON TABLE public.batches TO anon, authenticated;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated;

-- Grant select on views
GRANT SELECT ON public.v_batches TO anon, authenticated;

-- Drop existing tables to ensure clean rebuild and prevent foreign key conflicts
DROP TABLE IF EXISTS public.farm_members CASCADE;
DROP TABLE IF EXISTS public.batch_mortality CASCADE;
DROP TABLE IF EXISTS public.veterinarians CASCADE;
DROP TABLE IF EXISTS public.device_kits CASCADE;
DROP TABLE IF EXISTS public.alerts CASCADE;
DROP TABLE IF EXISTS public.veterinary_cases CASCADE;
DROP TABLE IF EXISTS public.notifications CASCADE;
DROP TABLE IF EXISTS public.support_tickets CASCADE;
DROP TABLE IF EXISTS public.device_components CASCADE;
DROP TABLE IF EXISTS public.device_maintenance CASCADE;
DROP TABLE IF EXISTS public.orders CASCADE;
DROP TABLE IF EXISTS public.payments CASCADE;
DROP TABLE IF EXISTS public.subscriptions CASCADE;
DROP TABLE IF EXISTS public.profiles CASCADE;
DROP TABLE IF EXISTS public.role_permissions CASCADE;
DROP TABLE IF EXISTS public.permissions CASCADE;
DROP TABLE IF EXISTS public.roles CASCADE;

-- 8. Roles Table
CREATE TABLE IF NOT EXISTS roles (
    name VARCHAR(50) PRIMARY KEY -- 'FARMER', 'VETERINARIAN', 'ADMIN', 'SUPER_ADMIN'
);

-- 9. Permissions Table
CREATE TABLE IF NOT EXISTS permissions (
    id VARCHAR(100) PRIMARY KEY, -- e.g. 'read:telemetry', 'write:settings', 'approve:vets', 'manage:system'
    description TEXT
);

-- 10. Role Permissions Join Table
CREATE TABLE IF NOT EXISTS role_permissions (
    role VARCHAR(50) REFERENCES roles(name) ON DELETE CASCADE,
    permission VARCHAR(100) REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role, permission)
);

-- 11. Profiles Table
CREATE TABLE IF NOT EXISTS profiles (
    id UUID PRIMARY KEY, -- Matches auth.users.id from Supabase Auth
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    role VARCHAR(50) DEFAULT 'FARMER' REFERENCES roles(name),
    join_date TIMESTAMPTZ DEFAULT now() NOT NULL,
    approval_status VARCHAR(20) DEFAULT 'PENDING_APPROVAL' CHECK (approval_status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    rejection_reason TEXT
);

-- Seed initial roles
INSERT INTO roles (name) VALUES 
('FARMER'), 
('VETERINARIAN'), 
('ADMIN'), 
('SUPER_ADMIN')
ON CONFLICT (name) DO NOTHING;

-- Seed initial permissions
INSERT INTO permissions (id, description) VALUES
('VIEW_OWN_FARM', 'View own farm overview and status'),
('VIEW_ASSIGNED_FARMS', 'View assigned farms overview and status'),
('VIEW_TELEMETRY', 'View temperature, humidity, ammonia, and sound sensor telemetry'),
('VIEW_AI_PREDICTIONS', 'View disease prediction risk levels and alerts'),
('MANAGE_BATCHES', 'Start, close, and manage active poultry batches'),
('VIEW_BATCHES', 'View poultry batch history (read-only)'),
('RECORD_MORTALITY', 'Register mortality counts and logs'),
('VIEW_VET_CASES', 'View veterinarian cases and history'),
('DIAGNOSE_DISEASE', 'Diagnose and sign off on disease alerts'),
('MANAGE_VETS', 'Add, verify, and suspend veterinarian accounts'),
('ADD_DEVICE_KITS', 'Register new IoT device hardware kits'),
('ASSIGN_DEVICES', 'Link IoT devices to farms and farmers'),
('VIEW_ALL_FARMERS', 'View directory of all registered farmers'),
('MANAGE_FARMS', 'Manage system farms and structures'),
('MANAGE_SYSTEM_SETTINGS', 'Configure global threshold alarms and controls')
ON CONFLICT (id) DO NOTHING;

-- Seed role-permissions mappings
INSERT INTO role_permissions (role, permission) VALUES
('FARMER', 'VIEW_OWN_FARM'),
('FARMER', 'VIEW_TELEMETRY'),
('FARMER', 'VIEW_AI_PREDICTIONS'),
('FARMER', 'MANAGE_BATCHES'),
('FARMER', 'RECORD_MORTALITY'),
('FARMER', 'VIEW_VET_CASES'),

('VETERINARIAN', 'VIEW_ASSIGNED_FARMS'),
('VETERINARIAN', 'VIEW_TELEMETRY'),
('VETERINARIAN', 'VIEW_AI_PREDICTIONS'),
('VETERINARIAN', 'VIEW_BATCHES'),
('VETERINARIAN', 'RECORD_MORTALITY'),
('VETERINARIAN', 'VIEW_VET_CASES'),
('VETERINARIAN', 'DIAGNOSE_DISEASE'),

('ADMIN', 'VIEW_OWN_FARM'),
('ADMIN', 'VIEW_ASSIGNED_FARMS'),
('ADMIN', 'VIEW_TELEMETRY'),
('ADMIN', 'VIEW_AI_PREDICTIONS'),
('ADMIN', 'MANAGE_BATCHES'),
('ADMIN', 'RECORD_MORTALITY'),
('ADMIN', 'VIEW_VET_CASES'),
('ADMIN', 'MANAGE_VETS'),
('ADMIN', 'ADD_DEVICE_KITS'),
('ADMIN', 'ASSIGN_DEVICES'),
('ADMIN', 'VIEW_ALL_FARMERS'),
('ADMIN', 'MANAGE_FARMS'),
('ADMIN', 'MANAGE_SYSTEM_SETTINGS'),

('SUPER_ADMIN', 'VIEW_OWN_FARM'),
('SUPER_ADMIN', 'VIEW_ASSIGNED_FARMS'),
('SUPER_ADMIN', 'VIEW_TELEMETRY'),
('SUPER_ADMIN', 'VIEW_AI_PREDICTIONS'),
('SUPER_ADMIN', 'MANAGE_BATCHES'),
('SUPER_ADMIN', 'RECORD_MORTALITY'),
('SUPER_ADMIN', 'VIEW_VET_CASES'),
('SUPER_ADMIN', 'MANAGE_VETS'),
('SUPER_ADMIN', 'ADD_DEVICE_KITS'),
('SUPER_ADMIN', 'ASSIGN_DEVICES'),
('SUPER_ADMIN', 'VIEW_ALL_FARMERS'),
('SUPER_ADMIN', 'MANAGE_FARMS'),
('SUPER_ADMIN', 'MANAGE_SYSTEM_SETTINGS')
ON CONFLICT (role, permission) DO NOTHING;

-- Grant API access privileges
GRANT ALL PRIVILEGES ON TABLE public.roles TO anon, authenticated;
GRANT ALL PRIVILEGES ON TABLE public.permissions TO anon, authenticated;
GRANT ALL PRIVILEGES ON TABLE public.role_permissions TO anon, authenticated;
GRANT ALL PRIVILEGES ON TABLE public.profiles TO anon, authenticated;

-- 12. Farm Members Table
CREATE TABLE IF NOT EXISTS farm_members (
    id BIGSERIAL PRIMARY KEY,
    farm_id VARCHAR(50) REFERENCES farms(id) ON DELETE CASCADE NOT NULL,
    profile_id UUID REFERENCES profiles(id) ON DELETE CASCADE NOT NULL,
    role VARCHAR(50) NOT NULL, -- 'OWNER', 'MANAGER', 'WORKER'
    joined_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

-- 13. Batch Mortality Table
CREATE TABLE IF NOT EXISTS batch_mortality (
    id UUID PRIMARY KEY,
    batch_id VARCHAR(50) REFERENCES batches(id) ON DELETE CASCADE NOT NULL,
    death_count INTEGER NOT NULL CHECK (death_count > 0),
    reason VARCHAR(255) NOT NULL,
    notes TEXT,
    recorded_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    recorded_by VARCHAR(100) NOT NULL
);

-- 14. Veterinarians Table
CREATE TABLE IF NOT EXISTS veterinarians (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    specialty VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(100) UNIQUE NOT NULL,
    location VARCHAR(100),
    verification_status VARCHAR(20) DEFAULT 'PENDING' CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED', 'SUSPENDED')),
    availability VARCHAR(20) DEFAULT 'Available' NOT NULL
);

-- 15. Device Kits Table
CREATE TABLE IF NOT EXISTS device_kits (
    id VARCHAR(50) PRIMARY KEY, -- e.g. 'PG-KIT-0045'
    status VARCHAR(50) DEFAULT 'Available' CHECK (status IN ('Available', 'Active', 'Maintenance', 'Retired')),
    registered_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

-- 16. Alerts Table
CREATE TABLE IF NOT EXISTS alerts (
    id BIGSERIAL PRIMARY KEY,
    batch_id VARCHAR(50) REFERENCES batches(id) ON DELETE CASCADE,
    device_id VARCHAR(50) REFERENCES devices(id) ON DELETE CASCADE,
    prediction_id BIGINT REFERENCES disease_predictions(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    description TEXT NOT NULL,
    severity VARCHAR(20) DEFAULT 'MEDIUM' CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status VARCHAR(20) DEFAULT 'UNRESOLVED' CHECK (status IN ('UNRESOLVED', 'RESOLVED')),
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

-- 17. Veterinary Cases Table
CREATE TABLE IF NOT EXISTS veterinary_cases (
    id BIGSERIAL PRIMARY KEY,
    alert_id BIGINT REFERENCES alerts(id) ON DELETE SET NULL,
    batch_id VARCHAR(50) REFERENCES batches(id) ON DELETE CASCADE NOT NULL,
    veterinarian_id VARCHAR(50) REFERENCES veterinarians(id) ON DELETE SET NULL,
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ASSIGNED', 'DIAGNOSED', 'RESOLVED')),
    diagnosis TEXT,
    recommendation TEXT,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

-- 18. Notifications Table
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    profile_id UUID REFERENCES profiles(id) ON DELETE CASCADE NOT NULL,
    title VARCHAR(150) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

-- 19. Support Tickets Table
CREATE TABLE IF NOT EXISTS support_tickets (
    id VARCHAR(50) PRIMARY KEY, -- e.g. 'PG1024'
    farmer_name VARCHAR(100) NOT NULL,
    issue TEXT NOT NULL,
    device_id VARCHAR(50),
    priority VARCHAR(20) DEFAULT 'MEDIUM' CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    status VARCHAR(20) DEFAULT 'Unresolved' CHECK (status IN ('Unresolved', 'Resolved')),
    category VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

-- 20. Device Components Table
CREATE TABLE IF NOT EXISTS device_components (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(50) REFERENCES devices(id) ON DELETE CASCADE NOT NULL,
    component_name VARCHAR(100) NOT NULL, -- e.g. 'Ammonia Sensor', 'Ventilation Fan'
    status VARCHAR(50) DEFAULT 'HEALTHY' CHECK (status IN ('HEALTHY', 'FAULTY', 'DISCONNECTED')),
    last_calibrated_at TIMESTAMPTZ
);

-- 21. Device Maintenance Table
CREATE TABLE IF NOT EXISTS device_maintenance (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(50) REFERENCES devices(id) ON DELETE CASCADE NOT NULL,
    scheduled_date DATE NOT NULL,
    performed_at TIMESTAMPTZ,
    technician_name VARCHAR(100),
    description TEXT NOT NULL,
    status VARCHAR(20) DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);

-- 22. Orders Table
CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(50) PRIMARY KEY,
    farm_id VARCHAR(50) REFERENCES farms(id) ON DELETE CASCADE NOT NULL,
    order_date TIMESTAMPTZ DEFAULT now() NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SHIPPED', 'DELIVERED', 'CANCELLED'))
);

-- 23. Payments Table
CREATE TABLE IF NOT EXISTS payments (
    id VARCHAR(50) PRIMARY KEY,
    order_id VARCHAR(50) REFERENCES orders(id) ON DELETE CASCADE NOT NULL,
    payment_date TIMESTAMPTZ DEFAULT now() NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    method VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'COMPLETED' CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
);

-- 24. Subscriptions Table
CREATE TABLE IF NOT EXISTS subscriptions (
    id VARCHAR(50) PRIMARY KEY,
    farm_id VARCHAR(50) REFERENCES farms(id) ON DELETE CASCADE NOT NULL,
    plan_name VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED'))
);

-- Grant privileges for new tables
GRANT ALL PRIVILEGES ON TABLE public.farm_members TO anon, authenticated;
GRANT ALL PRIVILEGES ON TABLE public.batch_mortality TO anon, authenticated;
GRANT ALL PRIVILEGES ON TABLE public.veterinarians TO anon, authenticated;
GRANT ALL PRIVILEGES ON TABLE public.device_kits TO anon, authenticated;
GRANT ALL PRIVILEGES ON TABLE public.alerts TO anon, authenticated;
GRANT ALL PRIVILEGES ON TABLE public.veterinary_cases TO anon, authenticated;
GRANT ALL PRIVILEGES ON TABLE public.notifications TO anon, authenticated;
GRANT ALL PRIVILEGES ON TABLE public.support_tickets TO anon, authenticated;
GRANT ALL PRIVILEGES ON TABLE public.device_components TO anon, authenticated;
GRANT ALL PRIVILEGES ON TABLE public.device_maintenance TO anon, authenticated;
GRANT ALL PRIVILEGES ON TABLE public.orders TO anon, authenticated;
GRANT ALL PRIVILEGES ON TABLE public.payments TO anon, authenticated;
GRANT ALL PRIVILEGES ON TABLE public.subscriptions TO anon, authenticated;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated;

-- Enable Row Level Security (RLS) to secure tables
ALTER TABLE IF EXISTS public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.farms ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.farm_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.veterinarians ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.sensor_telemetry ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.disease_predictions ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.farm_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.batches ENABLE ROW LEVEL SECURITY;

-- Drop existing policies if any to prevent conflicts on rebuild
DROP POLICY IF EXISTS "Allow public insert on profiles" ON public.profiles;
DROP POLICY IF EXISTS "Allow public select on profiles" ON public.profiles;
DROP POLICY IF EXISTS "Allow public update on profiles" ON public.profiles;

DROP POLICY IF EXISTS "Allow public insert on farms" ON public.farms;
DROP POLICY IF EXISTS "Allow public select on farms" ON public.farms;
DROP POLICY IF EXISTS "Allow public update on farms" ON public.farms;

DROP POLICY IF EXISTS "Allow public insert on farm_members" ON public.farm_members;
DROP POLICY IF EXISTS "Allow public select on farm_members" ON public.farm_members;
DROP POLICY IF EXISTS "Allow public update on farm_members" ON public.farm_members;

DROP POLICY IF EXISTS "Allow public insert on veterinarians" ON public.veterinarians;
DROP POLICY IF EXISTS "Allow public select on veterinarians" ON public.veterinarians;
DROP POLICY IF EXISTS "Allow public update on veterinarians" ON public.veterinarians;

DROP POLICY IF EXISTS "Allow public insert on devices" ON public.devices;
DROP POLICY IF EXISTS "Allow public select on devices" ON public.devices;
DROP POLICY IF EXISTS "Allow public update on devices" ON public.devices;

DROP POLICY IF EXISTS "Allow public insert on sensor_telemetry" ON public.sensor_telemetry;
DROP POLICY IF EXISTS "Allow public select on sensor_telemetry" ON public.sensor_telemetry;

DROP POLICY IF EXISTS "Allow public insert on disease_predictions" ON public.disease_predictions;
DROP POLICY IF EXISTS "Allow public select on disease_predictions" ON public.disease_predictions;

DROP POLICY IF EXISTS "Allow public insert on farm_settings" ON public.farm_settings;
DROP POLICY IF EXISTS "Allow public select on farm_settings" ON public.farm_settings;
DROP POLICY IF EXISTS "Allow public update on farm_settings" ON public.farm_settings;

DROP POLICY IF EXISTS "Allow public insert on batches" ON public.batches;
DROP POLICY IF EXISTS "Allow public select on batches" ON public.batches;
DROP POLICY IF EXISTS "Allow public update on batches" ON public.batches;

-- RLS Policies for Profiles
CREATE POLICY "Allow public insert on profiles" ON public.profiles FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public select on profiles" ON public.profiles FOR SELECT USING (true);
CREATE POLICY "Allow public update on profiles" ON public.profiles FOR UPDATE USING (true) WITH CHECK (true);

-- RLS Policies for Farms
CREATE POLICY "Allow public insert on farms" ON public.farms FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public select on farms" ON public.farms FOR SELECT USING (true);
CREATE POLICY "Allow public update on farms" ON public.farms FOR UPDATE USING (true) WITH CHECK (true);

-- RLS Policies for Farm Members
CREATE POLICY "Allow public insert on farm_members" ON public.farm_members FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public select on farm_members" ON public.farm_members FOR SELECT USING (true);
CREATE POLICY "Allow public update on farm_members" ON public.farm_members FOR UPDATE USING (true) WITH CHECK (true);

-- RLS Policies for Veterinarians
CREATE POLICY "Allow public insert on veterinarians" ON public.veterinarians FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public select on veterinarians" ON public.veterinarians FOR SELECT USING (true);
CREATE POLICY "Allow public update on veterinarians" ON public.veterinarians FOR UPDATE USING (true) WITH CHECK (true);

-- RLS Policies for Devices
CREATE POLICY "Allow public insert on devices" ON public.devices FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public select on devices" ON public.devices FOR SELECT USING (true);
CREATE POLICY "Allow public update on devices" ON public.devices FOR UPDATE USING (true) WITH CHECK (true);

-- RLS Policies for Sensor Telemetry
CREATE POLICY "Allow public insert on sensor_telemetry" ON public.sensor_telemetry FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public select on sensor_telemetry" ON public.sensor_telemetry FOR SELECT USING (true);

-- RLS Policies for Disease Predictions
CREATE POLICY "Allow public insert on disease_predictions" ON public.disease_predictions FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public select on disease_predictions" ON public.disease_predictions FOR SELECT USING (true);

-- RLS Policies for Farm Settings
CREATE POLICY "Allow public insert on farm_settings" ON public.farm_settings FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public select on farm_settings" ON public.farm_settings FOR SELECT USING (true);
CREATE POLICY "Allow public update on farm_settings" ON public.farm_settings FOR UPDATE USING (true) WITH CHECK (true);

-- RLS Policies for Batches
CREATE POLICY "Allow public insert on batches" ON public.batches FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public select on batches" ON public.batches FOR SELECT USING (true);
CREATE POLICY "Allow public update on batches" ON public.batches FOR UPDATE USING (true) WITH CHECK (true);


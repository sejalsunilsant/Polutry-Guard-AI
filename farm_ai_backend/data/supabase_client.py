import os
from supabase import create_client, Client

# Load environment variables manually from .env if not loaded yet (useful for tests and scripts)
if not os.environ.get("SUPABASE_URL") or not os.environ.get("SUPABASE_KEY"):
    current_dir = os.path.dirname(os.path.abspath(__file__))
    possible_paths = [
        os.path.abspath(os.path.join(current_dir, "..", "..", ".env")),  # Root directory: Polutry-Guard-AI/.env
        os.path.abspath(os.path.join(current_dir, "..", ".env")),        # farm_ai_backend/.env
        os.path.abspath(os.path.join(os.getcwd(), ".env"))                # CWD/.env
    ]
    for path in possible_paths:
        if os.path.exists(path):
            with open(path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith("#") and "=" in line:
                        k, v = line.split("=", 1)
                        os.environ[k.strip()] = v.strip()
            break

url = os.environ.get("SUPABASE_URL")
key = os.environ.get("SUPABASE_KEY")

supabase: Client = None

if url and key and url != "your_supabase_url" and key != "your_supabase_anon_key":
    try:
        supabase = create_client(url, key)
        print("[Supabase] Client initialized successfully.")
    except Exception as e:
        print(f"[Supabase] Error initializing client: {e}")
else:
    print("[Supabase] WARNING: SUPABASE_URL or SUPABASE_KEY missing or unconfigured. Running in local fallback mode.")

def ensure_device_exists(device_id, farm_id="default_farm"):
    """
    Helper to satisfy foreign key constraints by auto-registering unknown devices/farms.
    """
    if supabase is None:
        return
    try:
        res = supabase.table("devices").select("id").eq("id", device_id).execute()
        if not res.data:
            print(f"[Supabase] Auto-registering new device '{device_id}' under farm '{farm_id}'...")
            # Ensure farm exists
            farm_res = supabase.table("farms").select("id").eq("id", farm_id).execute()
            if not farm_res.data:
                supabase.table("farms").insert({"id": farm_id, "name": f"Farm {farm_id}"}).execute()
            
            # Insert device
            supabase.table("devices").insert({
                "id": device_id,
                "farm_id": farm_id,
                "name": f"ESP32 Controller ({device_id})"
            }).execute()
            
            # Seed default settings for the device
            supabase.table("farm_settings").insert({
                "device_id": device_id,
                "vent_temp": 26.0,
                "heater_temp": 20.0,
                "lights_on_hour": 6,
                "lights_off_hour": 20,
                "sprinkler_threshold": 29.5,
                "sms_alerts_enabled": True,
                "recipient_phone": ""
            }).execute()
    except Exception as e:
        print(f"[Supabase Warning] Failed while ensuring device/farm registration: {e}")

def save_telemetry(device_id, temperature, humidity, ammonia, sound_level, farm_id="default_farm"):
    """
    Log telemetry readings into Supabase PostgreSQL for a specific device.
    """
    if supabase is None:
        print(f"[Supabase Fallback] Logged telemetry locally for device '{device_id}'.")
        return {"status": "fallback"}
    try:
        ensure_device_exists(device_id, farm_id)
        data = {
            "device_id": device_id,
            "temperature": float(temperature),
            "humidity": float(humidity),
            "ammonia": float(ammonia),
            "sound_level": float(sound_level)
        }
        res = supabase.table("sensor_telemetry").insert(data).execute()
        return {"status": "success", "data": res.data}
    except Exception as e:
        print(f"[Supabase Error] Failed to log telemetry: {e}")
        return {"status": "error", "message": str(e)}

def save_prediction(device_id, disease, risk_level, confidence, recommendation, telemetry_id=None, farm_id="default_farm"):
    """
    Log disease prediction logs into Supabase PostgreSQL for a specific device.
    """
    if supabase is None:
        print(f"[Supabase Fallback] Logged prediction locally for device '{device_id}': Disease={disease}, Risk={risk_level}.")
        return {"status": "fallback"}
    try:
        ensure_device_exists(device_id, farm_id)
        data = {
            "device_id": device_id,
            "telemetry_id": telemetry_id,
            "disease": disease,
            "risk_level": risk_level,
            "confidence": float(confidence),
            "recommendation": recommendation
        }
        res = supabase.table("disease_predictions").insert(data).execute()
        return {"status": "success", "data": res.data}
    except Exception as e:
        print(f"[Supabase Error] Failed to log prediction: {e}")
        return {"status": "error", "message": str(e)}

def get_settings(device_id):
    """
    Fetch the farm settings row for a specific device.
    """
    default_settings = {
        "device_id": device_id,
        "vent_temp": 26.0,
        "heater_temp": 20.0,
        "lights_on_hour": 6,
        "lights_off_hour": 20,
        "sprinkler_threshold": 29.5,
        "sms_alerts_enabled": True,
        "recipient_phone": ""
    }
    if supabase is None:
        return default_settings
    try:
        res = supabase.table("farm_settings").select("*").eq("device_id", device_id).execute()
        if res.data and len(res.data) > 0:
            return res.data[0]
        else:
            # Auto-register device to generate settings
            ensure_device_exists(device_id)
            res = supabase.table("farm_settings").select("*").eq("device_id", device_id).execute()
            return res.data[0] if res.data else default_settings
    except Exception as e:
        print(f"[Supabase Error] Failed to fetch settings for device '{device_id}': {e}")
        return default_settings

def update_settings(device_id, settings_dict):
    """
    Update settings parameters inside Supabase PostgreSQL for a specific device.
    """
    if supabase is None:
        print(f"[Supabase Fallback] Settings updated in local fallback state for device '{device_id}'.")
        return {"status": "fallback"}
    try:
        ensure_device_exists(device_id)
        res = supabase.table("farm_settings").update(settings_dict).eq("device_id", device_id).execute()
        return {"status": "success", "data": res.data}
    except Exception as e:
        print(f"[Supabase Error] Failed to update settings for device '{device_id}': {e}")
        return {"status": "error", "message": str(e)}


def get_device_thingspeak_config(device_id):
    """
    Fetches the ThingSpeak channel ID and read API key for a device.
    """
    if supabase is None:
        return {"status": "fallback", "thingspeak_channel_id": None, "thingspeak_read_api_key": None, "farm_id": None}
    try:
        res = supabase.table("devices").select("thingspeak_channel_id", "thingspeak_read_api_key", "farm_id").eq("id", device_id).execute()
        if res.data and len(res.data) > 0:
            device = res.data[0]
            return {
                "status": "success",
                "thingspeak_channel_id": device.get("thingspeak_channel_id"),
                "thingspeak_read_api_key": device.get("thingspeak_read_api_key"),
                "farm_id": device.get("farm_id")
            }
        return {"status": "error", "message": "Device not found"}
    except Exception as e:
        print(f"[Supabase Error] Failed to get device config: {e}")
        return {"status": "error", "message": str(e)}


def update_device_thingspeak_config(device_id, farm_id, channel_id, read_api_key):
    """
    Updates the ThingSpeak credentials for a device after verifying it belongs to the given farm (or registers it).
    """
    if supabase is None:
        print(f"[Supabase Fallback] Settings updated in local fallback state for device '{device_id}'.")
        return {"status": "fallback"}
    try:
        ensure_device_exists(device_id, farm_id)
        
        # Verify ownership
        verify_res = supabase.table("devices").select("farm_id").eq("id", device_id).execute()
        if verify_res.data and verify_res.data[0].get("farm_id") != farm_id:
            return {"status": "error", "message": "Unauthorized: Device belongs to a different farm"}
            
        res = supabase.table("devices").update({
            "thingspeak_channel_id": channel_id,
            "thingspeak_read_api_key": read_api_key
        }).eq("id", device_id).execute()
        return {"status": "success", "data": res.data}
    except Exception as e:
        print(f"[Supabase Error] Failed to update device config: {e}")
        return {"status": "error", "message": str(e)}


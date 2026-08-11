import os
# pyrefly: ignore [missing-import]
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
_mock_batches = {}

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
        active_batch = get_active_batch(farm_id)
        if active_batch:
            data["batch_id"] = active_batch.get("id")
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
        active_batch = get_active_batch(farm_id)
        if active_batch:
            data["batch_id"] = active_batch.get("id")
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


def get_last_prediction(device_id):
    """
    Fetch the most recent disease prediction for a device.
    """
    if supabase is None:
        return None
    try:
        res = supabase.table("disease_predictions")\
            .select("*")\
            .eq("device_id", device_id)\
            .order("created_at", desc=True)\
            .limit(1)\
            .execute()
        if res.data and len(res.data) > 0:
            return res.data[0]
        return None
    except Exception as e:
        print(f"[Supabase Error] Failed to fetch last prediction: {e}")
        return None


def get_last_telemetry(device_id, limit=1):
    """
    Fetch the most recent telemetry records for a device.
    """
    if supabase is None:
        return None
    try:
        res = supabase.table("sensor_telemetry")\
            .select("*")\
            .eq("device_id", device_id)\
            .order("created_at", desc=True)\
            .limit(limit)\
            .execute()
        if res.data and len(res.data) > 0:
            if limit == 1:
                return res.data[0]
            return res.data
        return [] if limit > 1 else None
    except Exception as e:
        print(f"[Supabase Error] Failed to fetch last telemetry: {e}")
        return [] if limit > 1 else None


def create_batch(batch_id, farm_id, start_date, initial_count, breed="Broiler", status="ACTIVE"):
    """
    Register a new poultry batch for a farm.
    """
    if supabase is None:
        batch = {
            "id": batch_id,
            "farm_id": farm_id,
            "start_date": start_date,
            "initial_count": int(initial_count),
            "current_count": int(initial_count),
            "breed": breed,
            "status": status
        }
        _mock_batches[batch_id] = batch
        print(f"[Supabase Fallback] Batch '{batch_id}' created locally.")
        return {"status": "success", "data": [batch]}
    try:
        data = {
            "id": batch_id,
            "farm_id": farm_id,
            "start_date": start_date,
            "initial_count": int(initial_count),
            "current_count": int(initial_count),
            "breed": breed,
            "status": status
        }
        res = supabase.table("batches").insert(data).execute()
        return {"status": "success", "data": res.data}
    except Exception as e:
        print(f"[Supabase Error] Failed to create batch: {e}")
        return {"status": "error", "message": str(e)}


def get_active_batch(farm_id):
    """
    Fetch the currently active batch (using the v_batches view so we get calculated age_days).
    """
    if supabase is None:
        for b in _mock_batches.values():
            if b.get("farm_id") == farm_id and b.get("status") == "ACTIVE":
                return b
        # Fallback initializer
        default_batch = {
            "id": "default_batch_id",
            "farm_id": farm_id,
            "start_date": "2026-08-01",
            "initial_count": 5000,
            "current_count": 5000,
            "breed": "Broiler",
            "status": "ACTIVE"
        }
        _mock_batches["default_batch_id"] = default_batch
        return default_batch
    try:
        res = supabase.table("v_batches")\
            .select("*")\
            .eq("farm_id", farm_id)\
            .eq("status", "ACTIVE")\
            .order("created_at", desc=True)\
            .limit(1)\
            .execute()
        if res.data and len(res.data) > 0:
            return res.data[0]
        return None
    except Exception as e:
        print(f"[Supabase Error] Failed to fetch active batch: {e}")
        return None


def update_batch_status(batch_id, status, end_date=None, current_count=None):
    """
    Update batch status (e.g. mark as SOLD or CLOSED).
    """
    if supabase is None:
        print(f"[Supabase Fallback] Batch '{batch_id}' status updated to '{status}'.")
        return {"status": "fallback"}
    try:
        data = {"status": status}
        if end_date:
            data["end_date"] = end_date
        if current_count is not None:
            data["current_count"] = int(current_count)
            
        res = supabase.table("batches").update(data).eq("id", batch_id).execute()
        return {"status": "success", "data": res.data}
    except Exception as e:
        print(f"[Supabase Error] Failed to update batch status: {e}")
        return {"status": "error", "message": str(e)}


def get_all_batches(farm_id):
    """
    Fetch all batches (active, sold, closed) for a farm, ordered by created_at.
    """
    if supabase is None:
        print(f"[Supabase Fallback] get_all_batches returning fallback list.")
        return []
    try:
        res = supabase.table("v_batches")\
            .select("*")\
            .eq("farm_id", farm_id)\
            .order("created_at", desc=True)\
            .execute()
        return res.data or []
    except Exception as e:
        print(f"[Supabase Error] Failed to fetch all batches: {e}")
        return []


def record_mortality(batch_id, death_count):
    """
    Safely decrement the current bird count of a batch due to mortality.
    """
    if supabase is None:
        batch = _mock_batches.get(batch_id)
        if not batch:
            batch = _mock_batches.get("default_batch_id")
            if not batch:
                return {"status": "error", "message": f"Batch '{batch_id}' not found."}
        current = batch.get("current_count", 0)
        if int(death_count) > current:
            return {"status": "error", "message": f"Mortality count {death_count} cannot exceed current batch count {current}."}
        batch["current_count"] = current - int(death_count)
        print(f"[Supabase Fallback] Decremented count by {death_count} locally to {batch['current_count']}.")
        return {"status": "success", "data": [batch]}
    try:
        # Fetch current count
        res_batch = supabase.table("batches").select("current_count").eq("id", batch_id).execute()
        if not res_batch.data or len(res_batch.data) == 0:
            return {"status": "error", "message": f"Batch '{batch_id}' not found."}
            
        current = res_batch.data[0]["current_count"]
        if int(death_count) > current:
            return {"status": "error", "message": f"Mortality count {death_count} cannot exceed current batch count {current}."}
        new_count = current - int(death_count)
        
        res = supabase.table("batches").update({"current_count": new_count}).eq("id", batch_id).execute()
        return {"status": "success", "data": res.data}
    except Exception as e:
        print(f"[Supabase Error] Failed to record batch mortality: {e}")
        return {"status": "error", "message": str(e)}




import os
import uuid
import time
# pyrefly: ignore [missing-import]
from supabase import create_client, Client
from cryptography.fernet import Fernet

# Initialize Fernet encryption for Wi-Fi credentials
_wifi_fernet = None
_encryption_key = os.environ.get("WIFI_ENCRYPTION_KEY")
if not _encryption_key:
    print("[Supabase Client] WARNING: WIFI_ENCRYPTION_KEY is missing. Generating a temporary key for testing...")
    _encryption_key = Fernet.generate_key().decode()
    
try:
    _wifi_fernet = Fernet(_encryption_key.encode())
except Exception as e:
    print(f"[Supabase Client] Error initializing Fernet: {e}")

def encrypt_wifi_password(password: str) -> str:
    if not _wifi_fernet:
        raise ValueError("Fernet encryption is not initialized.")
    return _wifi_fernet.encrypt(password.encode()).decode()

def decrypt_wifi_password(encrypted_password: str) -> str:
    if not _wifi_fernet:
        raise ValueError("Fernet encryption is not initialized.")
    return _wifi_fernet.decrypt(encrypted_password.encode()).decode()

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

import threading

_thread_local = threading.local()

class SupabaseProxy:
    def _get_client(self):
        if not hasattr(_thread_local, "client"):
            _thread_local.client = None
            if url and key and url != "your_supabase_url" and key != "your_supabase_anon_key":
                try:
                    _thread_local.client = create_client(url, key)
                except Exception as e:
                    print(f"[Supabase] Error initializing client for thread {threading.get_ident()}: {e}")
        return _thread_local.client

    def __getattr__(self, name):
        client = self._get_client()
        if client is None:
            raise AttributeError("Supabase client is not initialized.")
        return getattr(client, name)

    def __bool__(self):
        return self._get_client() is not None

    def __eq__(self, other):
        if other is None:
            return self._get_client() is None
        return super().__eq__(other)

supabase = SupabaseProxy()
_mock_batches = {}
_mock_mortality = []
_mock_alerts = []
_mock_cases = []
_mock_veterinarians = {
    "vet_1": {
        "id": "vet_1",
        "name": "Dr. Ramesh Kumar",
        "specialty": "Avian Pathology",
        "phone": "+919876543210",
        "email": "ramesh@poultryguard.ai",
        "location": "Ludhiana, Punjab",
        "verification_status": "VERIFIED",
        "availability": "Available",
        "photo_url": "https://randomuser.me/api/portraits/men/32.jpg",
        "license_number": "VET-IND-2021-9981",
        "qualification": "M.V.Sc (Avian Medicine)",
        "experience": 8
    },
    "vet_2": {
        "id": "vet_2",
        "name": "Dr. Priya Patel",
        "specialty": "Poultry Nutrition",
        "phone": "+918765432109",
        "email": "priya@poultryguard.ai",
        "location": "Anand, Gujarat",
        "verification_status": "VERIFIED",
        "availability": "Available",
        "photo_url": "https://randomuser.me/api/portraits/women/44.jpg",
        "license_number": "VET-IND-2018-4521",
        "qualification": "Ph.D. in Poultry Science",
        "experience": 12
    }
}

if not (url and key and url != "your_supabase_url" and key != "your_supabase_anon_key"):
    print("[Supabase] WARNING: SUPABASE_URL or SUPABASE_KEY missing or unconfigured. Running in local fallback mode.")

def ensure_device_exists(device_id, farm_id):
    """
    Helper to satisfy foreign key constraints by auto-registering unknown devices/farms.
    """
    if not supabase:
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

def clean_and_parse_ts(ts_str):
    if not ts_str:
        return None
    if ts_str.endswith('Z'):
        ts_str = ts_str[:-1] + '+00:00'
    if ' ' in ts_str and 'T' not in ts_str:
        ts_str = ts_str.replace(' ', 'T')
    try:
        from datetime import datetime
        return datetime.fromisoformat(ts_str)
    except Exception:
        return None

def upload_media_from_url(url, bucket_name, folder, file_name):
    """
    Downloads a file from url and uploads it to a Supabase Storage bucket.
    Returns the storage path (or public URL) of the uploaded file in Supabase.
    """
    if not url or not (url.startswith("http://") or url.startswith("https://")):
        return url
        
    # Check if already in Supabase storage
    if ".supabase.co" in url or "mock-supabase.co" in url:
        return url
        
    if not supabase:
        print(f"[Supabase Storage Fallback] Mock upload for URL: {url}")
        return f"https://mock-supabase.co/storage/v1/object/public/{bucket_name}/{folder}/{file_name}"
    try:
        import requests
        # 1. Download file from URL
        resp = requests.get(url, timeout=15)
        resp.raise_for_status()
        file_content = resp.content
        
        # 2. Ensure bucket exists
        try:
            supabase.storage.get_bucket(bucket_name)
        except Exception:
            try:
                supabase.storage.create_bucket(bucket_name, {"public": True})
            except Exception as ce:
                print(f"[Supabase Storage Warning] Could not create bucket: {ce}")
                
        # 3. Upload to Supabase Storage
        path_in_bucket = f"{folder}/{file_name}"
        supabase.storage.from_(bucket_name).upload(
            path=path_in_bucket,
            file=file_content,
            file_options={"cache-control": "3600", "upsert": "true"}
        )
        
        # 4. Get Public URL
        public_url = supabase.storage.from_(bucket_name).get_public_url(path_in_bucket)
        return public_url
    except Exception as e:
        print(f"[Supabase Storage Error] Failed to upload media from {url}: {e}")
        return url # fallback to original URL on error

def save_telemetry(device_id, temperature, humidity, ammonia, sound_level, farm_id, sound_url=None, image_url=None, created_at=None):
    """
    Log telemetry readings into Supabase PostgreSQL for a specific device.
    """
    if not supabase:
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
        
        from datetime import datetime, timezone
        ts = clean_and_parse_ts(created_at) if created_at else datetime.now(timezone.utc)
        ts_str = ts.strftime("%Y%m%d_%H%M%S")
        
        if sound_url is not None:
            file_ext = "wav"
            if "." in sound_url.split("/")[-1]:
                file_ext = sound_url.split("/")[-1].split(".")[-1].split("?")[0]
            file_name = f"{device_id}_{ts_str}.{file_ext}"
            data["sound_url"] = upload_media_from_url(sound_url, "poultry-media", "audio", file_name)
            
        if image_url is not None:
            file_ext = "jpg"
            if "." in image_url.split("/")[-1]:
                file_ext = image_url.split("/")[-1].split(".")[-1].split("?")[0]
            file_name = f"{device_id}_{ts_str}.{file_ext}"
            data["image_url"] = upload_media_from_url(image_url, "poultry-media", "images", file_name)
            
        if created_at is not None:
            data["created_at"] = created_at
            
        active_batch = get_active_batch(farm_id)
        batch_id = active_batch.get("id") if active_batch else None
        if batch_id:
            data["batch_id"] = batch_id
        res = supabase.table("sensor_telemetry").insert(data).execute()
        
        # Check environmental thresholds & trigger FCM notifications with cooldown/reset
        check_telemetry_thresholds_and_notify(
            device_id=device_id,
            temperature=temperature,
            humidity=humidity,
            ammonia=ammonia,
            sound_level=sound_level,
            farm_id=farm_id,
            batch_id=batch_id
        )
        
        return {"status": "success", "data": res.data}
    except Exception as e:
        print(f"[Supabase Error] Failed to log telemetry: {e}")
        return {"status": "error", "message": str(e)}


def save_prediction(device_id, disease, risk_level, confidence, recommendation, farm_id, telemetry_id=None, event_id=None):
    """
    Log disease prediction logs into Supabase PostgreSQL for a specific device.
    """
    if not supabase:
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
        if event_id is not None:
            data["event_id"] = event_id
            
        active_batch = get_active_batch(farm_id)
        if active_batch:
            data["batch_id"] = active_batch.get("id")
        res = supabase.table("disease_predictions").insert(data).execute()
        return {"status": "success", "data": res.data}
    except Exception as e:
        print(f"[Supabase Error] Failed to log prediction: {e}")
        return {"status": "error", "message": str(e)}

def get_settings(device_id, farm_id):
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
    if not supabase:
        return default_settings
    try:
        res = supabase.table("farm_settings").select("*").eq("device_id", device_id).execute()
        if res.data and len(res.data) > 0:
            return res.data[0]
        else:
            # Auto-register device to generate settings
            ensure_device_exists(device_id, farm_id)
            res = supabase.table("farm_settings").select("*").eq("device_id", device_id).execute()
            return res.data[0] if res.data else default_settings
    except Exception as e:
        print(f"[Supabase Error] Failed to fetch settings for device '{device_id}': {e}")
        return default_settings

def update_settings(device_id, settings_dict, farm_id):
    """
    Update settings parameters inside Supabase PostgreSQL for a specific device.
    """
    if not supabase:
        print(f"[Supabase Fallback] Settings updated in local fallback state for device '{device_id}'.")
        return {"status": "fallback"}
    try:
        ensure_device_exists(device_id, farm_id)
        res = supabase.table("farm_settings").update(settings_dict).eq("device_id", device_id).execute()
        return {"status": "success", "data": res.data}
    except Exception as e:
        print(f"[Supabase Error] Failed to update settings for device '{device_id}': {e}")
def get_device_thingspeak_config(device_id):
    """
    Fetch ThingSpeak credentials for a device from Supabase.
    """
    if not supabase:
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
        return {"status": "error", "message": f"Device '{device_id}' not found"}
    except Exception as e:
        print(f"[Supabase Error] Failed to fetch ThingSpeak config for device '{device_id}': {e}")
        return {"status": "error", "message": str(e)}

def update_device_thingspeak_config(device_id, farm_id, channel_id, read_api_key):
    """
    Update or initialize ThingSpeak channel and read key for a device.
    """
    if not supabase:
        print(f"[Supabase Fallback] ThingSpeak config updated in fallback state for device '{device_id}'.")
        return {"status": "fallback", "data": {"thingspeak_channel_id": channel_id}}
    try:
        ensure_device_exists(device_id, farm_id)
        res = supabase.table("devices").update({
            "thingspeak_channel_id": channel_id,
            "thingspeak_read_api_key": read_api_key
        }).eq("id", device_id).execute()
        return {"status": "success", "data": res.data}
    except Exception as e:
        print(f"[Supabase Error] Failed to update ThingSpeak config for device '{device_id}': {e}")
        return {"status": "error", "message": str(e)}


def get_last_prediction(device_id):
    """
    Fetch the most recent disease prediction for a device.
    """
    if not supabase:
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
    if not supabase:
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
    if not supabase:
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
    if not supabase:
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
    if not supabase:
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
    if not supabase:
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


def record_mortality(batch_id, death_count, record_id=None, reason="Unspecified", notes=None, recorded_at=None, recorded_by="Farmer"):
    """
    Save the detailed mortality record to batch_mortality, and safely decrement the current bird count of a batch.
    """
    if not record_id:
        record_id = str(uuid.uuid4())
        
    if not supabase:
        batch = _mock_batches.get(batch_id)
        if not batch:
            batch = _mock_batches.get("default_batch_id")
            if not batch:
                return {"status": "error", "message": f"Batch '{batch_id}' not found."}
        current = batch.get("current_count", 0)
        if int(death_count) > current:
            return {"status": "error", "message": f"Mortality count {death_count} cannot exceed current batch count {current}."}
        batch["current_count"] = current - int(death_count)
        
        # Add to _mock_mortality
        mock_record = {
            "id": record_id,
            "batch_id": batch_id,
            "death_count": int(death_count),
            "reason": reason,
            "notes": notes,
            "recorded_by": recorded_by
        }
        if recorded_at:
            if isinstance(recorded_at, (int, float)):
                import datetime
                dt = datetime.datetime.fromtimestamp(recorded_at / 1000.0, tz=datetime.timezone.utc)
                mock_record["recorded_at"] = dt.isoformat()
            else:
                mock_record["recorded_at"] = str(recorded_at)
        else:
            import datetime
            mock_record["recorded_at"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
            
        _mock_mortality.append(mock_record)
        print(f"[Supabase Fallback] Recorded mortality detailed & decremented count locally to {batch['current_count']}.")
        return {"status": "success", "data": batch}
        
    try:
        # Fetch current count
        res_batch = supabase.table("batches").select("current_count").eq("id", batch_id).execute()
        if not res_batch.data or len(res_batch.data) == 0:
            return {"status": "error", "message": f"Batch '{batch_id}' not found."}
            
        current = res_batch.data[0]["current_count"]
        if int(death_count) > current:
            return {"status": "error", "message": f"Mortality count {death_count} cannot exceed current batch count {current}."}
        new_count = current - int(death_count)
        
        # 1. Insert detailed mortality record into batch_mortality
        mort_data = {
            "id": record_id,
            "batch_id": batch_id,
            "death_count": int(death_count),
            "reason": reason or "Unspecified",
            "notes": notes,
            "recorded_by": recorded_by or "Farmer"
        }
        if recorded_at:
            if isinstance(recorded_at, (int, float)):
                import datetime
                dt = datetime.datetime.fromtimestamp(recorded_at / 1000.0, tz=datetime.timezone.utc)
                mort_data["recorded_at"] = dt.isoformat()
            else:
                mort_data["recorded_at"] = str(recorded_at)
        
        supabase.table("batch_mortality").insert(mort_data).execute()
        print(f"[Supabase Client] Successfully inserted detailed mortality record {record_id} for batch {batch_id}")

        # 2. Update batch current_count
        res = supabase.table("batches").update({"current_count": new_count}).eq("id", batch_id).execute()
        return {"status": "success", "data": res.data[0] if res.data else None}
    except Exception as e:
        print(f"[Supabase Error] Failed to record batch mortality: {e}")
        return {"status": "error", "message": str(e)}

def get_mortality_records(batch_id):
    """
    Fetch all mortality records for a batch, ordered by recorded_at desc.
    """
    if not supabase:
        return [m for m in _mock_mortality if m.get("batch_id") == batch_id]
    try:
        res = supabase.table("batch_mortality").select("*").eq("batch_id", batch_id).order("recorded_at", desc=True).execute()
        return res.data or []
    except Exception as e:
        print(f"[Supabase Error] Failed to fetch mortality records for batch {batch_id}: {e}")
        return []


import hashlib

def get_coordinates_for_location(location_str, profile_id):
    loc = (location_str or "").lower()
    if "pune" in loc:
        return 18.5204, 73.8567
    elif "mumbai" in loc:
        return 19.0760, 72.8777
    elif "bangalore" in loc or "bengaluru" in loc:
        return 12.9716, 77.5946
    elif "delhi" in loc:
        return 28.6139, 77.2090
    elif "dhaka" in loc:
        return 23.8103, 90.4125
    elif "gazipur" in loc:
        return 23.9999, 90.4203
    elif "savar" in loc:
        return 23.8583, 90.2667
    elif "kolkata" in loc:
        return 22.5726, 88.3639
    elif "chennai" in loc:
        return 13.0827, 80.2707
    elif "hyderabad" in loc:
        return 17.3850, 78.4867
    else:
        # Deterministic hash of profile_id using MD5
        hasher = hashlib.md5((profile_id or "").encode("utf-8"))
        h_val = int(hasher.hexdigest(), 16)
        lat_offset = (h_val % 100) / 1000.0
        lng_offset = ((h_val // 100) % 100) / 1000.0
        return 18.5204 + lat_offset - 0.04, 73.8567 + lng_offset - 0.04


def sync_user_profile(uid, name, email, role, farm_name=None, farm_location=None, phone=None,
                      specialty=None, location=None, photo_url=None, license_number=None, qualification=None, experience=None,
                      latitude=None, longitude=None):
    """
    Synchronizes user profile, farm, and members to Supabase.
    """
    if not supabase:
        print(f"[Supabase Fallback] Profile sync fallback for '{email}' (role: {role}).")
        if role == "VETERINARIAN":
            exp_val = None
            if experience is not None:
                try:
                    exp_val = int(experience)
                except ValueError:
                    exp_val = None
            _mock_veterinarians[uid] = {
                "id": uid,
                "name": name,
                "email": email,
                "role": role,
                "phone": phone or "",
                "specialty": specialty or "Avian Medicine",
                "location": location or farm_location or "Unspecified District",
                "photo_url": photo_url or "",
                "license_number": license_number or "",
                "qualification": qualification or "",
                "experience": exp_val,
                "verification_status": "PENDING",
                "availability": "Available"
            }
        return {"status": "fallback", "profile_id": uid}
    
    try:
        # Convert Firebase UID or simulated UID deterministically to a valid UUID format
        try:
            profile_uuid = str(uuid.UUID(uid))
        except ValueError:
            profile_uuid = str(uuid.uuid5(uuid.NAMESPACE_DNS, uid))
        
        # 1. Sync User Profile
        profile_data = {
            "id": profile_uuid,
            "name": name,
            "email": email,
            "role": role
        }
        
        profile_res = supabase.table("profiles").select("id").eq("id", profile_uuid).execute()
        if not profile_res.data:
            supabase.table("profiles").insert(profile_data).execute()
            print(f"[Supabase] Created user profile: {email} ({role})")
        else:
            supabase.table("profiles").update({
                "name": name,
                "email": email,
                "role": role
            }).eq("id", profile_uuid).execute()
            print(f"[Supabase] Updated user profile: {email}")
            
        # 2. If role is FARMER, sync Farm and Farm Member
        if role == "FARMER":
            farm_id = f"farm_{email.split('@')[0]}"
            farm_name = farm_name or f"{name}'s Farm"
            
            farm_res = supabase.table("farms").select("id").eq("id", farm_id).execute()
            farm_data = {
                "id": farm_id,
                "name": farm_name
            }
            if farm_location:
                farm_data["location"] = farm_location
                
            lat_val = None
            lng_val = None
            if latitude is not None and str(latitude).strip() != "":
                try:
                    lat_val = float(latitude)
                except (ValueError, TypeError):
                    pass
            if longitude is not None and str(longitude).strip() != "":
                try:
                    lng_val = float(longitude)
                except (ValueError, TypeError):
                    pass
                    
            if lat_val is None or lng_val is None:
                resolved_lat, resolved_lng = get_coordinates_for_location(farm_location or farm_name, profile_uuid)
                if lat_val is None:
                    lat_val = resolved_lat
                if lng_val is None:
                    lng_val = resolved_lng
                    
            farm_data["latitude"] = lat_val
            farm_data["longitude"] = lng_val

            if not farm_res.data:
                supabase.table("farms").insert(farm_data).execute()
                print(f"[Supabase] Created farm: {farm_name} ({farm_id})")
            else:
                supabase.table("farms").update(farm_data).eq("id", farm_id).execute()
                
            member_res = supabase.table("farm_members")\
                .select("id")\
                .eq("farm_id", farm_id)\
                .eq("profile_id", profile_uuid)\
                .execute()
                
            if not member_res.data:
                supabase.table("farm_members").insert({
                    "farm_id": farm_id,
                    "profile_id": profile_uuid,
                    "role": "OWNER"
                }).execute()
                print(f"[Supabase] Linked user {email} to farm {farm_id} as OWNER")
                
        # 3. If role is VETERINARIAN, sync Veterinarians table
        elif role == "VETERINARIAN":
            exp_val = None
            if experience is not None:
                try:
                    exp_val = int(experience)
                except ValueError:
                    exp_val = None

            lat_val = None
            lng_val = None
            if latitude is not None and str(latitude).strip() != "":
                try:
                    lat_val = float(latitude)
                except (ValueError, TypeError):
                    pass
            if longitude is not None and str(longitude).strip() != "":
                try:
                    lng_val = float(longitude)
                except (ValueError, TypeError):
                    pass
                    
            if lat_val is None or lng_val is None:
                resolved_lat, resolved_lng = get_coordinates_for_location(location or farm_location or name, uid)
                if lat_val is None:
                    lat_val = resolved_lat
                if lng_val is None:
                    lng_val = resolved_lng

            vet_res = supabase.table("veterinarians").select("id").eq("id", uid).execute()
            vet_data = {
                "id": uid,
                "name": name,
                "specialty": specialty or "Avian Medicine",
                "phone": phone or "",
                "email": email,
                "location": location or farm_location or "Unspecified District",
                "latitude": lat_val,
                "longitude": lng_val,
                "verification_status": "PENDING",
                "availability": "Available",
                "photo_url": photo_url or "",
                "license_number": license_number or "",
                "qualification": qualification or "",
                "experience": exp_val
            }
            if not vet_res.data:
                supabase.table("veterinarians").insert(vet_data).execute()
                print(f"[Supabase] Registered veterinarian: {email}")
            else:
                supabase.table("veterinarians").update({
                    "name": name,
                    "phone": phone or "",
                    "location": location or farm_location or "Unspecified District",
                    "specialty": specialty or "Avian Medicine",
                    "photo_url": photo_url or "",
                    "license_number": license_number or "",
                    "qualification": qualification or "",
                    "experience": exp_val
                }).eq("id", uid).execute()
                print(f"[Supabase] Updated veterinarian: {email}")
                
        return {"status": "success", "profile_id": profile_uuid}
    except Exception as e:
        print(f"[Supabase Error] Profile sync exception: {e}")
        return {"status": "error", "message": str(e)}


def supabase_register_user(name, email, password, role, farm_name=None, farm_location=None, total_sheds=4, floor_space_sq_ft=24000,
                           specialty=None, phone=None, location=None, photo_url=None, license_number=None, qualification=None, experience=None,
                           latitude=None, longitude=None):
    """
    Registers a new user in Supabase Auth and populates the matching profiles/farms/veterinarians tables.
    """
    if not supabase:
        # Simulated fallback mode
        uid = f"sim_{int(time.time() * 1000)}"
        profile_uuid = str(uuid.uuid5(uuid.NAMESPACE_DNS, uid))
        if role == "VETERINARIAN":
            exp_val = None
            if experience is not None:
                try:
                    exp_val = int(experience)
                except ValueError:
                    exp_val = None
            vet_obj = {
                "id": profile_uuid,
                "name": name,
                "email": email,
                "role": role,
                "phone": phone or "",
                "specialty": specialty or "Avian Medicine",
                "location": location or farm_location or "Unspecified District",
                "photo_url": photo_url or "",
                "license_number": license_number or "",
                "qualification": qualification or "",
                "experience": exp_val,
                "verification_status": "PENDING",
                "availability": "Available"
            }
            _mock_veterinarians[profile_uuid] = vet_obj
            return {
                "status": "success",
                "data": {
                    "uid": profile_uuid,
                    "name": name,
                    "email": email,
                    "role": role,
                    "phone": phone or "",
                    "specialty": specialty or "Avian Medicine",
                    "location": location or farm_location or "Unspecified District",
                    "photoUrl": photo_url or "",
                    "licenseNumber": license_number or "",
                    "qualification": qualification or "",
                    "experience": exp_val,
                    "joinDate": "Just now",
                    "approvalStatus": "APPROVED",
                    "token": f"mock_token_for_{profile_uuid}"
                }
            }
        return {
            "status": "success",
            "data": {
                "uid": profile_uuid,
                "name": name,
                "email": email,
                "role": role,
                "farmName": farm_name or "Greenfield Broilers",
                "latitude": latitude,
                "longitude": longitude,
                "joinDate": "Just now",
                "approvalStatus": "PENDING_APPROVAL" if role == "FARMER" else "APPROVED",
                "token": f"mock_token_for_{profile_uuid}"
            }
        }
    
    try:
        # 1. Register in Supabase Auth
        res = supabase.auth.sign_up({
            "email": email,
            "password": password,
            "options": {
                "data": {
                    "name": name,
                    "role": role
                }
            }
        })
        if not res.user:
            return {"status": "error", "message": "Failed to create user in Supabase Auth"}
        
        user_uuid = res.user.id
        
        # 2. Insert profile in profiles table
        profile_data = {
            "id": user_uuid,
            "name": name,
            "email": email,
            "role": role,
            "approval_status": "PENDING_APPROVAL" if role == "FARMER" else "APPROVED"
        }
        supabase.table("profiles").insert(profile_data).execute()
        
        # 3. If FARMER, create farm and membership
        if role == "FARMER":
            farm_id = f"farm_{email.split('@')[0]}"
            farm_name = farm_name or f"{name}'s Farm"
            
            farm_data = {
                "id": farm_id,
                "name": farm_name
            }
            if farm_location:
                farm_data["location"] = farm_location
                
            lat_val = None
            lng_val = None
            if latitude is not None and str(latitude).strip() != "":
                try:
                    lat_val = float(latitude)
                except (ValueError, TypeError):
                    pass
            if longitude is not None and str(longitude).strip() != "":
                try:
                    lng_val = float(longitude)
                except (ValueError, TypeError):
                    pass
                    
            if lat_val is None or lng_val is None:
                resolved_lat, resolved_lng = get_coordinates_for_location(farm_location or farm_name, user_uuid)
                if lat_val is None:
                    lat_val = resolved_lat
                if lng_val is None:
                    lng_val = resolved_lng
                    
            farm_data["latitude"] = lat_val
            farm_data["longitude"] = lng_val

            # Create farm
            supabase.table("farms").insert(farm_data).execute()
            
            # Create farm membership
            supabase.table("farm_members").insert({
                "farm_id": farm_id,
                "profile_id": user_uuid,
                "role": "OWNER"
            }).execute()
            
        # 4. If VETERINARIAN, create vet record
        elif role == "VETERINARIAN":
            exp_val = None
            if experience is not None:
                try:
                    exp_val = int(experience)
                except ValueError:
                    exp_val = None

            lat_val = None
            lng_val = None
            if latitude is not None and str(latitude).strip() != "":
                try:
                    lat_val = float(latitude)
                except (ValueError, TypeError):
                    pass
            if longitude is not None and str(longitude).strip() != "":
                try:
                    lng_val = float(longitude)
                except (ValueError, TypeError):
                    pass
                    
            if lat_val is None or lng_val is None:
                resolved_lat, resolved_lng = get_coordinates_for_location(location or farm_location or name, user_uuid)
                if lat_val is None:
                    lat_val = resolved_lat
                if lng_val is None:
                    lng_val = resolved_lng

            supabase.table("veterinarians").insert({
                "id": user_uuid,
                "name": name,
                "specialty": specialty or "Avian Medicine",
                "phone": phone or "",
                "email": email,
                "location": location or farm_location or "Unspecified District",
                "latitude": lat_val,
                "longitude": lng_val,
                "verification_status": "PENDING",
                "availability": "Available",
                "photo_url": photo_url or "",
                "license_number": license_number or "",
                "qualification": qualification or "",
                "experience": exp_val
            }).execute()
            
        # Get access token from session if available
        access_token = None
        if hasattr(res, "session") and res.session:
            access_token = res.session.access_token

        ret_data = {
            "uid": user_uuid,
            "name": name,
            "email": email,
            "role": role,
            "joinDate": "Just now",
            "approvalStatus": "PENDING_APPROVAL" if role == "FARMER" else "APPROVED",
            "token": access_token
        }
        if role == "VETERINARIAN":
            ret_data.update({
                "phone": phone or "",
                "specialty": specialty or "Avian Medicine",
                "location": location or farm_location or "Unspecified District",
                "photoUrl": photo_url or "",
                "licenseNumber": license_number or "",
                "qualification": qualification or "",
                "experience": exp_val
            })
        else:
            ret_data["farmName"] = farm_name or "Greenfield Broilers"

        return {
            "status": "success",
            "data": ret_data
        }
    except Exception as e:
        print(f"[Supabase Register Error] {e}")
        return {"status": "error", "message": str(e)}


def supabase_login_user(email, password):
    """
    Authenticates a user in Supabase Auth and fetches their profile from the database.
    """
    if not supabase:
        # Simulated fallback mode
        detected_role = "FARMER"
        if "vet" in email:
            detected_role = "VETERINARIAN"
        elif "admin" in email:
            detected_role = "ADMIN"
            
        sim_status = "APPROVED"
        sim_reason = None
        if email.startswith("pending"):
            sim_status = "PENDING_APPROVAL"
        elif email.startswith("rejected"):
            sim_status = "REJECTED"
            sim_reason = "Documents missing"
            
        if sim_status == "PENDING_APPROVAL":
            return {
                "status": "error",
                "message": "Account pending approval. Please wait for admin review.",
                "code": "PENDING_APPROVAL"
            }
        elif sim_status == "REJECTED":
            return {
                "status": "error",
                "message": f"Registration rejected. Reason: {sim_reason}",
                "code": "REJECTED"
            }
            
        sim_uid = f"sim_user_{int(time.time() * 1000)}"
        return {
            "status": "success",
            "data": {
                "uid": sim_uid,
                "name": email.split('@')[0].capitalize(),
                "email": email,
                "role": detected_role,
                "farmName": "Greenfield Broilers",
                "joinDate": "Just now",
                "approvalStatus": sim_status,
                "rejectionReason": sim_reason,
                "token": f"mock_token_for_{sim_uid}"
            }
        }
        
    try:
        # 1. Login with Supabase Auth
        res = supabase.auth.sign_in_with_password({
            "email": email,
            "password": password
        })
        if not res.user:
            return {"status": "error", "message": "Invalid credentials"}
            
        user_uuid = res.user.id
        
        # 2. Query profiles
        profile_res = supabase.table("profiles").select("*").eq("id", user_uuid).execute()
        if not profile_res.data:
            return {"status": "error", "message": "User profile not found in database"}
            
        profile = profile_res.data[0]
        role = profile.get("role", "FARMER")
        name = profile.get("name", "User")
        approval_status = profile.get("approval_status", "APPROVED")
        rejection_reason = profile.get("rejection_reason")
        
        if approval_status == "PENDING_APPROVAL":
            return {
                "status": "error",
                "message": "Account pending approval. Please wait for admin review.",
                "code": "PENDING_APPROVAL"
            }
        elif approval_status == "REJECTED":
            return {
                "status": "error",
                "message": f"Registration rejected. Reason: {rejection_reason or 'Unspecified'}",
                "code": "REJECTED"
            }
        
        # 3. Get farm name if farmer
        farm_name = "Greenfield Broilers"
        if role == "FARMER":
            farm_id = f"farm_{email.split('@')[0]}"
            farm_res = supabase.table("farms").select("name").eq("id", farm_id).execute()
            if farm_res.data:
                farm_name = farm_res.data[0].get("name", farm_name)
                
        # Get access token from session if available
        access_token = None
        if hasattr(res, "session") and res.session:
            access_token = res.session.access_token

        return {
            "status": "success",
            "data": {
                "uid": user_uuid,
                "name": name,
                "email": email,
                "role": role,
                "farmName": farm_name,
                "joinDate": profile.get("join_date", "Just now"),
                "approvalStatus": approval_status,
                "rejectionReason": rejection_reason,
                "token": access_token
            }
        }
    except Exception as e:
        print(f"[Supabase Login Error] {e}. Falling back to simulation mode.")
        detected_role = "FARMER"
        if "vet" in email:
            detected_role = "VETERINARIAN"
        elif "admin" in email:
            detected_role = "ADMIN"
            
        sim_status = "APPROVED"
        sim_reason = None
        if email.startswith("pending"):
            sim_status = "PENDING_APPROVAL"
        elif email.startswith("rejected"):
            sim_status = "REJECTED"
            sim_reason = "Documents missing"
            
        if sim_status == "PENDING_APPROVAL":
            return {
                "status": "error",
                "message": "Account pending approval. Please wait for admin review.",
                "code": "PENDING_APPROVAL"
            }
        elif sim_status == "REJECTED":
            return {
                "status": "error",
                "message": f"Registration rejected. Reason: {sim_reason}",
                "code": "REJECTED"
            }
            
        sim_uid = f"sim_user_{int(time.time() * 1000)}"
        return {
            "status": "success",
            "data": {
                "uid": sim_uid,
                "name": email.split('@')[0].capitalize(),
                "email": email,
                "role": detected_role,
                "farmName": "Greenfield Broilers",
                "joinDate": "Just now",
                "approvalStatus": sim_status,
                "rejectionReason": sim_reason,
                "token": f"mock_token_for_{sim_uid}"
            }
        }


def supabase_forgot_password(email):
    """
    Triggers password reset flow in Supabase Auth.
    """
    if not supabase:
        return {"status": "success", "message": "Reset link sent (simulated)"}
    try:
        supabase.auth.reset_password_for_email(email)
        return {"status": "success", "message": "Reset link sent"}
    except Exception as e:
        print(f"[Supabase Forgot Password Error] {e}")
        return {"status": "error", "message": str(e)}


def get_pending_farmers():
    """
    Fetch all farmer profiles that are pending approval, including their farm details.
    """
    if not supabase:
        return [
            {
                "id": "mock_farmer_1",
                "name": "Sachine pending",
                "email": "pending_farmer@example.com",
                "role": "FARMER",
                "join_date": "2026-08-14T05:00:00+00:00",
                "approval_status": "PENDING_APPROVAL",
                "farm_members": [
                    {
                        "farm_id": "farm_sachine",
                        "farms": {
                            "name": "Sachine's Greenfield Poultry"
                        }
                    }
                ]
            }
        ]
    try:
        res = supabase.table("profiles")\
            .select("*, farm_members(farm_id, farms(name))")\
            .eq("role", "FARMER")\
            .eq("approval_status", "PENDING_APPROVAL")\
            .execute()
        return res.data or []
    except Exception as e:
        print(f"[Supabase Error] Failed to fetch pending farmers: {e}")
        return []


def review_farmer(profile_id, action, rejection_reason=None):
    """
    Approve or reject a pending farmer registration.
    """
    if action not in ["APPROVE", "REJECT"]:
        return {"status": "error", "message": "Invalid review action"}
        
    status_val = "APPROVED" if action == "APPROVE" else "REJECTED"
    
    if not supabase:
        print(f"[Supabase Fallback] Farmer '{profile_id}' reviewed: {status_val}")
        return {"status": "success", "data": [{"id": profile_id, "approval_status": status_val}]}
        
    try:
        update_data = {
            "approval_status": status_val,
            "rejection_reason": rejection_reason if action == "REJECT" else None
        }
        res = supabase.table("profiles").update(update_data).eq("id", profile_id).execute()
        return {"status": "success", "data": res.data}
    except Exception as e:
        print(f"[Supabase Error] Failed to review farmer: {e}")
        return {"status": "error", "message": str(e)}


def find_matching_active_event(device_id, disease):
    """
    Finds an active disease event for the device and disease where the last updated
    time (end_time) is within the last 24 hours.
    """
    if not supabase:
        return None
    try:
        from datetime import datetime, timedelta, timezone
        time_limit = (datetime.now(timezone.utc) - timedelta(hours=24)).isoformat()
        res = supabase.table("disease_events")\
            .select("*")\
            .eq("device_id", device_id)\
            .eq("disease", disease)\
            .eq("status", "ACTIVE")\
            .gte("end_time", time_limit)\
            .execute()
        if res.data and len(res.data) > 0:
            return res.data[0]
        return None
    except Exception as e:
        print(f"[Supabase Error] find_matching_active_event failed: {e}")
        return None


def create_disease_event(device_id, batch_id, disease, risk_level, confidence, image_url=None, sound_url=None):
    """
    Creates a new disease event.
    """
    if not supabase:
        return None
    try:
        from datetime import datetime, timezone
        now_str = datetime.now(timezone.utc).isoformat()
        data = {
            "device_id": device_id,
            "batch_id": batch_id,
            "disease": disease,
            "risk_level": risk_level,
            "max_confidence": float(confidence),
            "representative_image_url": image_url,
            "representative_sound_url": sound_url,
            "start_time": now_str,
            "end_time": now_str,
            "prediction_count": 1,
            "status": "ACTIVE"
        }
        res = supabase.table("disease_events").insert(data).execute()
        if res.data and len(res.data) > 0:
            return res.data[0]
        return None
    except Exception as e:
        print(f"[Supabase Error] create_disease_event failed: {e}")
        return None


def update_disease_event(event, new_confidence, new_image_url=None, new_sound_url=None):
    """
    Updates an existing disease event by incrementing prediction count, updating end_time,
    and conditionally updating max_confidence and representative media URLs.
    """
    if not supabase:
        return None
    try:
        from datetime import datetime, timezone
        now_str = datetime.now(timezone.utc).isoformat()
        
        event_id = event["id"]
        current_count = event.get("prediction_count", 1)
        max_conf = float(event.get("max_confidence", 0.0))
        
        update_data = {
            "prediction_count": current_count + 1,
            "end_time": now_str
        }
        
        if float(new_confidence) > max_conf:
            update_data["max_confidence"] = float(new_confidence)
            if new_image_url:
                update_data["representative_image_url"] = new_image_url
            if new_sound_url:
                update_data["representative_sound_url"] = new_sound_url
                
        res = supabase.table("disease_events").update(update_data).eq("id", event_id).execute()
        if res.data and len(res.data) > 0:
            return res.data[0]
        return None
    except Exception as e:
        print(f"[Supabase Error] update_disease_event failed: {e}")
        return None


# Mock FCM token cache for offline/testing mode
_mock_fcm_tokens = {}

def update_user_fcm_token(profile_id: str, fcm_token: str) -> bool:
    """
    Update or store a user's (Farmer/Veterinarian) FCM device token in Supabase profiles.
    """
    if not profile_id or not fcm_token:
        return False
    _mock_fcm_tokens[str(profile_id)] = fcm_token
    if not supabase:
        print(f"[Supabase Fallback] Cached FCM token locally for profile '{profile_id}'.")
        return True
    try:
        supabase.table("profiles").update({"fcm_token": fcm_token}).eq("id", str(profile_id)).execute()
        return True
    except Exception as e:
        print(f"[Supabase Error] update_user_fcm_token failed: {e}")
        return False


def remove_invalid_fcm_tokens(invalid_tokens: list):
    """
    Cleans up expired or unregistered FCM device tokens from profiles table.
    """
    if not invalid_tokens:
        return
    for t in invalid_tokens:
        for k, v in list(_mock_fcm_tokens.items()):
            if v == t:
                _mock_fcm_tokens.pop(k, None)
    if not supabase:
        return
    try:
        for t in invalid_tokens:
            supabase.table("profiles").update({"fcm_token": None}).eq("fcm_token", t).execute()
    except Exception as e:
        print(f"[Supabase Error] remove_invalid_fcm_tokens failed: {e}")


def get_farm_fcm_tokens(farm_id: str) -> list:
    """
    Fetches all active FCM device tokens for farmers, managers, and assigned vets for a farm.
    """
    tokens = []
    # Check mock token cache first
    for tok in _mock_fcm_tokens.values():
        if tok and tok not in tokens:
            tokens.append(tok)

    if not supabase or not farm_id:
        return tokens

    try:
        # 1. Fetch profile_ids from farm_members for this farm
        members_res = supabase.table("farm_members").select("profile_id").eq("farm_id", farm_id).execute()
        profile_ids = [m["profile_id"] for m in (members_res.data or []) if m.get("profile_id")]

        if profile_ids:
            prof_res = supabase.table("profiles").select("fcm_token").in_("id", profile_ids).execute()
            for row in (prof_res.data or []):
                tok = row.get("fcm_token")
                if tok and tok.strip() and tok not in tokens:
                    tokens.append(tok.strip())

        # 2. If no tokens found on farm_members, fallback to any active profile tokens
        if not tokens:
            admin_res = supabase.table("profiles").select("fcm_token").execute()
            for row in (admin_res.data or []):
                tok = row.get("fcm_token")
                if tok and tok.strip() and tok not in tokens:
                    tokens.append(tok.strip())

        return tokens
    except Exception as e:
        print(f"[Supabase Error] get_farm_fcm_tokens failed: {e}")
        return tokens


def _dispatch_fcm_for_alert(batch_id, device_id, title, description, severity):
    """
    Helper to resolve farm info and trigger FCM push notification for an alert.
    """
    try:
        from services.fcm_service import FCMService
        farm_id = "default_farm"
        farm_name = "Poultry Farm"

        if supabase:
            if batch_id:
                b_res = supabase.table("batches").select("farm_id").eq("id", str(batch_id)).execute()
                if b_res.data and len(b_res.data) > 0:
                    farm_id = b_res.data[0].get("farm_id", farm_id)
            elif device_id:
                d_res = supabase.table("devices").select("farm_id").eq("id", str(device_id)).execute()
                if d_res.data and len(d_res.data) > 0:
                    farm_id = d_res.data[0].get("farm_id", farm_id)

            f_res = supabase.table("farms").select("name").eq("id", str(farm_id)).execute()
            if f_res.data and len(f_res.data) > 0:
                farm_name = f_res.data[0].get("name", farm_name)

        # Categorize alert type from title
        alert_type = "DISEASE"
        title_lower = (title or "").lower()
        if "ammonia" in title_lower:
            alert_type = "AMMONIA"
        elif "heat" in title_lower or "temp" in title_lower:
            alert_type = "TEMPERATURE"
        elif "humid" in title_lower:
            alert_type = "HUMIDITY"
        elif "sound" in title_lower or "noise" in title_lower or "vocal" in title_lower:
            alert_type = "SOUND"

        FCMService.send_alert_to_farm(
            farm_id=farm_id,
            farm_name=farm_name,
            alert_type=alert_type,
            severity=severity or "HIGH",
            title=title or f"{alert_type} Alert",
            message=description or "Action required in poultry house.",
            device_id=device_id,
            batch_id=batch_id
        )
    except Exception as ex:
        print(f"[Supabase FCM Dispatch Warning] {ex}")


def check_telemetry_thresholds_and_notify(device_id, temperature, humidity, ammonia, sound_level, farm_id=None, batch_id=None):
    """
    Checks environmental telemetry against alert thresholds and dispatches FCM notifications with cooldown.
    Resets alert state when metrics return to normal baseline.
    """
    try:
        from services.fcm_service import FCMService, cooldown_tracker
        farm_id = farm_id or "default_farm"
        farm_name = "Poultry Farm"
        if supabase:
            try:
                f_res = supabase.table("farms").select("name").eq("id", str(farm_id)).execute()
                if f_res.data and len(f_res.data) > 0:
                    farm_name = f_res.data[0].get("name", farm_name)
            except Exception:
                pass

        # 1. Ammonia Threshold
        if ammonia is not None:
            nh3 = float(ammonia)
            if nh3 >= 25.0:
                FCMService.send_alert_to_farm(
                    farm_id=farm_id,
                    farm_name=farm_name,
                    alert_type="AMMONIA",
                    severity="CRITICAL",
                    title="Critical Ammonia Hazard",
                    message=f"Hazardous ammonia detected ({nh3:.1f} ppm, >25 ppm safe threshold). Turn exhaust fans to 100% immediately.",
                    device_id=device_id,
                    batch_id=batch_id,
                    current_value=nh3
                )
            elif nh3 < 20.0:
                cooldown_tracker.reset_alert(farm_id, device_id, "AMMONIA")

        # 2. Temperature (Heat Stress) Threshold
        if temperature is not None:
            temp = float(temperature)
            if temp >= 35.0:
                FCMService.send_alert_to_farm(
                    farm_id=farm_id,
                    farm_name=farm_name,
                    alert_type="TEMPERATURE",
                    severity="WARNING",
                    title="Heat Stress Warning",
                    message=f"High temperature detected ({temp:.1f}°C). Activate foggers and evaporative cooling pads immediately.",
                    device_id=device_id,
                    batch_id=batch_id,
                    current_value=temp
                )
            elif temp <= 30.0:
                cooldown_tracker.reset_alert(farm_id, device_id, "TEMPERATURE")

        # 3. Humidity Threshold
        if humidity is not None:
            hum = float(humidity)
            if hum >= 85.0:
                FCMService.send_alert_to_farm(
                    farm_id=farm_id,
                    farm_name=farm_name,
                    alert_type="HUMIDITY",
                    severity="WARNING",
                    title="Excess Humidity Warning",
                    message=f"Excessive humidity detected ({hum:.1f}%). Increase ventilation to suppress aerosol disease transmission.",
                    device_id=device_id,
                    batch_id=batch_id,
                    current_value=hum
                )
            elif hum <= 75.0:
                cooldown_tracker.reset_alert(farm_id, device_id, "HUMIDITY")

        # 4. Sound (Acoustic Distress) Threshold
        if sound_level is not None:
            snd = float(sound_level)
            if snd >= 75.0:
                FCMService.send_alert_to_farm(
                    farm_id=farm_id,
                    farm_name=farm_name,
                    alert_type="SOUND",
                    severity="WARNING",
                    title="Abnormal Vocalization Alert",
                    message=f"Abnormal flock acoustic level detected ({snd:.1f} dB). Check shed for distress or equipment anomalies.",
                    device_id=device_id,
                    batch_id=batch_id,
                    current_value=snd
                )
            elif snd <= 60.0:
                cooldown_tracker.reset_alert(farm_id, device_id, "SOUND")
    except Exception as e:
        print(f"[Telemetry Threshold Notification Error] {e}")


def create_alert(batch_id, device_id, prediction_id, title, description, severity):
    """
    Create a new alert record and trigger FCM push notification to the farm.
    """
    if not supabase:
        alert_obj = {
            "id": int(time.time()),
            "batch_id": batch_id,
            "device_id": device_id,
            "prediction_id": prediction_id,
            "title": title,
            "description": description,
            "severity": severity or "MEDIUM",
            "status": "UNRESOLVED",
            "created_at": "now()"
        }
        _mock_alerts.append(alert_obj)
        _dispatch_fcm_for_alert(batch_id, device_id, title, description, severity)
        return alert_obj
    try:
        data = {
            "batch_id": batch_id,
            "device_id": device_id,
            "prediction_id": prediction_id,
            "title": title,
            "description": description,
            "severity": severity,
            "status": "UNRESOLVED"
        }
        res = supabase.table("alerts").insert(data).execute()
        alert_result = res.data[0] if (res.data and len(res.data) > 0) else data
        
        # Dispatch FCM push notification
        _dispatch_fcm_for_alert(batch_id, device_id, title, description, severity)
        return alert_result
    except Exception as e:
        print(f"[Supabase Error] create_alert failed: {e}")
        return None


def create_db_alert(batch_id, device_id, prediction_id, title, description, severity):
    return create_alert(batch_id, device_id, prediction_id, title, description, severity)


def get_all_devices():
    """
    Fetch all provisioned devices.
    """
    if not supabase:
        return []
    try:
        res = supabase.table("devices")\
            .select("id, farm_id")\
            .execute()
        return res.data or []
    except Exception as e:
        print(f"[Supabase Error] get_all_devices failed: {e}")
        return []


def get_devices_with_thingspeak():
    """
    Fetch all devices that have a ThingSpeak channel configured.
    """
    if not supabase:
        return []
    try:
        res = supabase.table("devices")\
            .select("id, farm_id, thingspeak_channel_id, thingspeak_read_api_key")\
            .not_.is_("thingspeak_channel_id", "null")\
            .execute()
        return res.data or []
    except Exception as e:
        print(f"[Supabase Error] get_devices_with_thingspeak failed: {e}")
        return []


def get_relative_path_from_url(public_url, bucket_name="poultry-media"):
    if not public_url:
        return None
    marker = f"/public/{bucket_name}/"
    if marker in public_url:
        return public_url.split(marker)[1]
    return None

def safe_cleanup_telemetry(device_id, date_str, start_time, end_time):
    """
    Deletes raw telemetry only after verifying aggregation is complete,
    and removes associated normal media files from Supabase Storage.
    """
    if not supabase:
        return False
    try:
        # 1. Verify aggregation exists
        check_res = supabase.table("daily_telemetry")\
            .select("id")\
            .eq("device_id", device_id)\
            .eq("date", date_str)\
            .execute()
        if check_res.data and len(check_res.data) > 0:
            # 2. Fetch raw telemetry records to identify media files
            raw_res = supabase.table("sensor_telemetry")\
                .select("sound_url, image_url")\
                .eq("device_id", device_id)\
                .gte("created_at", start_time)\
                .lte("created_at", end_time)\
                .execute()
                
            if raw_res.data:
                for record in raw_res.data:
                    sound_url = record.get("sound_url")
                    image_url = record.get("image_url")
                    
                    # Check sound media
                    if sound_url and "supabase.co" in sound_url:
                        # Check if referenced in disease_events
                        res_sound = supabase.table("disease_events")\
                            .select("id")\
                            .eq("representative_sound_url", sound_url)\
                            .execute()
                        if not res_sound.data:
                            # Not referenced, delete from storage
                            rel_path = get_relative_path_from_url(sound_url, "poultry-media")
                            if rel_path:
                                try:
                                    supabase.storage.from_("poultry-media").remove([rel_path])
                                    print(f"[Supabase Storage Cleanup] Deleted sound clip: {rel_path}")
                                except Exception as se:
                                    print(f"[Supabase Storage Cleanup Error] Failed to delete sound {rel_path}: {se}")
                                    
                    # Check image media
                    if image_url and "supabase.co" in image_url:
                        res_image = supabase.table("disease_events")\
                            .select("id")\
                            .eq("representative_image_url", image_url)\
                            .execute()
                        if not res_image.data:
                            rel_path = get_relative_path_from_url(image_url, "poultry-media")
                            if rel_path:
                                try:
                                    supabase.storage.from_("poultry-media").remove([rel_path])
                                    print(f"[Supabase Storage Cleanup] Deleted image: {rel_path}")
                                except Exception as ie:
                                    print(f"[Supabase Storage Cleanup Error] Failed to delete image {rel_path}: {ie}")
            
            # 3. Delete raw rows
            supabase.table("sensor_telemetry")\
                .delete()\
                .eq("device_id", device_id)\
                .gte("created_at", start_time)\
                .lte("created_at", end_time)\
                .execute()
            print(f"[Supabase Cleanup] Safely deleted raw telemetry for device {device_id} on {date_str}")
            return True
        else:
            print(f"[Supabase Cleanup Warning] Skipping telemetry deletion for device {device_id} on {date_str}: No daily aggregation found!")
            return False
    except Exception as e:
        print(f"[Supabase Error] safe_cleanup_telemetry failed for device {device_id} on {date_str}: {e}")
        return False


def get_all_farmers():
    """
    Fetch all farmer profiles from Supabase.
    """
    if not supabase:
        return []
    try:
        res = supabase.table("profiles")\
            .select("*, farm_members(farm_id, farms(*, batches(*)))")\
            .eq("role", "FARMER")\
            .execute()
        data = res.data or []
        
        # Populate and save coordinates for existing farmers with missing/NULL coordinates
        for profile in data:
            for member in profile.get("farm_members") or []:
                farm = member.get("farms")
                if farm and (farm.get("latitude") is None or farm.get("longitude") is None):
                    loc_str = farm.get("location") or farm.get("name") or profile.get("name")
                    lat, lng = get_coordinates_for_location(loc_str, profile.get("id"))
                    farm["latitude"] = lat
                    farm["longitude"] = lng
                    try:
                        supabase.table("farms").update({
                            "latitude": lat,
                            "longitude": lng
                        }).eq("id", farm.get("id")).execute()
                        print(f"[Supabase] Auto-populated coordinates for farm {farm.get('id')}: {lat}, {lng}")
                    except Exception as ue:
                        print(f"[Supabase Error] Failed to auto-populate coordinates for farm {farm.get('id')}: {ue}")
        return data
    except Exception as e:
        print(f"[Supabase Error] Failed to fetch all farmers: {e}")
        return []


def get_all_veterinarians():
    """
    Fetch all veterinarian profiles from Supabase.
    """
    if not supabase:
        return list(_mock_veterinarians.values())
    try:
        res = supabase.table("veterinarians").select("*").execute()
        data = res.data or []
        
        # Populate and save coordinates for existing vets with missing/NULL coordinates
        for vet in data:
            if vet.get("latitude") is None or vet.get("longitude") is None:
                loc_str = vet.get("location") or vet.get("name")
                lat, lng = get_coordinates_for_location(loc_str, vet.get("id"))
                vet["latitude"] = lat
                vet["longitude"] = lng
                try:
                    supabase.table("veterinarians").update({
                        "latitude": lat,
                        "longitude": lng
                    }).eq("id", vet.get("id")).execute()
                    print(f"[Supabase] Auto-populated coordinates for vet {vet.get('id')}: {lat}, {lng}")
                except Exception as ue:
                    print(f"[Supabase Error] Failed to auto-populate coordinates for vet {vet.get('id')}: {ue}")
        return data
    except Exception as e:
        print(f"[Supabase Error] Failed to fetch all veterinarians: {e}")
        return []

def create_device_kit(
    device_id: str,
    name: str,
    kit_id: str = "",
    serial_number: str = "",
    firmware_version: str = "",
    thingspeak_channel_id: str = None,
    thingspeak_read_api_key: str = None,
    thingspeak_write_api_key: str = None
):
    """
    Creates a new IoT device kit entry in the `device_kits` table.
    This table has no farm_id constraint, so kits can be pre-registered
    in warehouse inventory before being assigned to a farm.

    When the admin assigns the kit via assign_device_to_farm(), a corresponding
    row is created in `devices` with farm_id set.

    The thingspeak_write_api_key is stored server-side only and never returned
    to the farmer app.
    """
    if not supabase:
        print(f"[Supabase Fallback] create_device_kit called for '{device_id}' in fallback mode.")
        return {"status": "fallback", "data": {"id": device_id}}
    try:
        # Check if kit already exists in device_kits
        existing = supabase.table("device_kits").select("id").eq("id", device_id).execute()
        if existing.data:
            return {"status": "error", "message": f"Device kit '{device_id}' already exists."}

        kit_data = {
            "id": device_id,
            "status": "Available",
        }
        extras = {}
        if name:
            extras["name"] = name
        if kit_id:
            extras["kit_id"] = kit_id
        if serial_number:
            extras["serial_number"] = serial_number
        if firmware_version:
            extras["firmware_version"] = firmware_version
        if thingspeak_channel_id:
            extras["thingspeak_channel_id"] = thingspeak_channel_id
        if thingspeak_read_api_key:
            extras["thingspeak_read_api_key"] = thingspeak_read_api_key
        if thingspeak_write_api_key:
            extras["thingspeak_write_api_key"] = thingspeak_write_api_key

        try:
            res = supabase.table("device_kits").insert({**kit_data, **extras}).execute()
        except Exception as extra_err:
            if "PGRST204" in str(extra_err) or "column" in str(extra_err).lower():
                print(f"[Supabase] device_kits missing extended columns; inserting base row: {extra_err}")
                res = supabase.table("device_kits").insert(kit_data).execute()
            else:
                raise extra_err

        row = res.data[0] if res.data else {**kit_data, **extras}
        row.setdefault("kit_id", kit_id)
        row.setdefault("name", name)
        return {"status": "success", "data": row}
    except Exception as e:
        print(f"[Supabase Error] create_device_kit failed for '{device_id}': {e}")
        return {"status": "error", "message": str(e)}


def assign_device_to_farm(device_id: str, farmer_profile_id: str):
    """
    Assigns a device to a farmer's farm.
    """
    if not supabase:
        print(f"[Supabase Fallback] assign_device_to_farm called in fallback mode.")
        return {"status": "fallback", "deviceId": device_id, "farmerProfileId": farmer_profile_id}
    try:
        # 1. Verify kit exists in device_kits
        kit_res = supabase.table("device_kits").select("id, status").eq("id", device_id).execute()
        if not kit_res.data:
            # Also check if it already exists as a provisioned device in devices table
            dev_check = supabase.table("devices").select("id, farm_id").eq("id", device_id).execute()
            if not dev_check.data:
                return {"status": "error", "message": f"Device kit '{device_id}' not found. Register it first via POST /admin/kits."}
            existing_farm = dev_check.data[0].get("farm_id")
            if existing_farm:
                return {
                    "status": "error",
                    "message": f"Device '{device_id}' is already assigned to farm '{existing_farm}'. Unassign it first."
                }
        else:
            kit = kit_res.data[0]
            if kit.get("status") == "Active":
                return {
                    "status": "error",
                    "message": f"Device kit '{device_id}' is already assigned (status=Active). Unassign it first."
                }

        # 2. Verify farmer profile
        profile_res = supabase.table("profiles").select("id, name, approval_status").eq("id", farmer_profile_id).execute()
        if not profile_res.data:
            return {"status": "error", "message": f"Farmer profile '{farmer_profile_id}' not found."}
        profile = profile_res.data[0]
        if profile.get("approval_status") != "APPROVED":
            return {
                "status": "error",
                "message": f"Farmer '{profile.get('name')}' is not yet approved (status: {profile.get('approval_status')})."
            }

        # 3. Resolve farm_id from farm_members
        member_res = supabase.table("farm_members").select("farm_id").eq("profile_id", farmer_profile_id).execute()
        if not member_res.data:
            return {"status": "error", "message": f"No farm membership found for profile '{farmer_profile_id}'. Farmer may not have a farm yet."}
        farm_id = member_res.data[0].get("farm_id")

        # 4. Ensure farm exists
        farm_res = supabase.table("farms").select("id").eq("id", farm_id).execute()
        if not farm_res.data:
            return {"status": "error", "message": f"Farm '{farm_id}' not found in farms table."}

        # 5. Fetch kit metadata and ThingSpeak credentials from device_kits
        kit_name = f"ESP32 Controller ({device_id})"
        thingspeak_channel_id = None
        thingspeak_read_api_key = None
        thingspeak_write_api_key = None
        try:
            kit_full_res = supabase.table("device_kits").select(
                "id, status, thingspeak_channel_id, thingspeak_read_api_key, thingspeak_write_api_key, name"
            ).eq("id", device_id).execute()
            if kit_full_res.data:
                kit_full = kit_full_res.data[0]
                thingspeak_channel_id = kit_full.get("thingspeak_channel_id")
                thingspeak_read_api_key = kit_full.get("thingspeak_read_api_key")
                thingspeak_write_api_key = kit_full.get("thingspeak_write_api_key")
                kit_name = kit_full.get("name") or f"ESP32 Controller ({device_id})"
        except Exception:
            kit_name = f"ESP32 Controller ({device_id})"

        # 6. Upsert into devices table
        device_row = {
            "id": device_id,
            "farm_id": farm_id,
            "name": kit_name,
        }
        if thingspeak_channel_id:
            device_row["thingspeak_channel_id"] = thingspeak_channel_id
        if thingspeak_read_api_key:
            device_row["thingspeak_read_api_key"] = thingspeak_read_api_key

        dev_existing = supabase.table("devices").select("id").eq("id", device_id).execute()
        if dev_existing.data:
            supabase.table("devices").update(device_row).eq("id", device_id).execute()
        else:
            supabase.table("devices").insert(device_row).execute()

        # 7. Mark device_kits.status = 'Active'
        try:
            supabase.table("device_kits").update({"status": "Active"}).eq("id", device_id).execute()
        except Exception:
            pass

        # 8. Ensure farm_settings row exists
        settings_res = supabase.table("farm_settings").select("device_id").eq("device_id", device_id).execute()
        if not settings_res.data:
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
            print(f"[Supabase] Auto-created farm_settings for device '{device_id}'")

        return {
            "status": "success",
            "data": {
                "deviceId": device_id,
                "farmId": farm_id,
                "farmerProfileId": farmer_profile_id,
                "farmerName": profile.get("name")
            }
        }
    except Exception as e:
        print(f"[Supabase Error] assign_device_to_farm failed: {e}")
        return {"status": "error", "message": str(e)}


def get_all_kits():
    """
    Fetches all registered device kits from Supabase for the admin panel.
    Reads from device_kits (pre-assigned inventory) and merges with devices
    (provisioned/assigned) to give a complete view.
    thingspeak_write_api_key is NEVER returned.
    """
    if not supabase:
        return []
    try:
        # Fetch pre-registered kits (not yet assigned to a farm)
        kit_res = supabase.table("device_kits").select(
            "id, status, registered_at, name, kit_id, serial_number, firmware_version, thingspeak_channel_id, thingspeak_read_api_key"
        ).execute()
        kits_raw = kit_res.data or []

        # Fetch provisioned devices (assigned to a farm)
        dev_res = supabase.table("devices").select(
            "id, name, farm_id, thingspeak_channel_id, thingspeak_read_api_key, created_at"
        ).execute()
        devices_raw = dev_res.data or []
        devices_by_id = {d["id"]: d for d in devices_raw}

        # Resolve farm and farmer names
        farms_res = supabase.table("farms").select("id, name, farm_members(profiles(id, name))").execute()
        farm_info = {}
        if farms_res.data:
            for f in farms_res.data:
                fid = f.get("id")
                fname = f.get("name") or ""
                members = f.get("farm_members") or []
                farmer_id = ""
                farmer_name = ""
                if members and len(members) > 0:
                    profile = members[0].get("profiles") or {}
                    farmer_id = profile.get("id") or ""
                    farmer_name = profile.get("name") or ""
                farm_info[fid] = {
                    "farm_name": fname,
                    "farmer_id": farmer_id,
                    "farmer_name": farmer_name
                }

        # Merge: device_kits entries enriched with devices data where available
        result = []
        seen = set()
        for kit in kits_raw:
            dev = devices_by_id.get(kit["id"], {})
            finfo = farm_info.get(dev.get("farm_id"), {})
            merged = {
                "id": kit["id"],
                "name": kit.get("name") or dev.get("name") or kit.get("id"),
                "kit_id": kit.get("kit_id") or kit.get("id"),
                "serial_number": kit.get("serial_number") or "",
                "firmware_version": kit.get("firmware_version") or "",
                "farm_id": dev.get("farm_id"),
                "farm_name": finfo.get("farm_name", ""),
                "farmer_id": finfo.get("farmer_id", ""),
                "farmer_name": finfo.get("farmer_name", ""),
                "thingspeak_channel_id": dev.get("thingspeak_channel_id") or kit.get("thingspeak_channel_id"),
                "thingspeak_read_api_key": dev.get("thingspeak_read_api_key") or kit.get("thingspeak_read_api_key"),
                "created_at": dev.get("created_at") or kit.get("registered_at"),
                "lifecycle_status": kit.get("status") or ("Active" if dev.get("farm_id") else "Available"),
            }
            result.append(merged)
            seen.add(kit["id"])

        # Also include devices that were provisioned without going through device_kits
        for dev in devices_raw:
            if dev["id"] not in seen:
                finfo = farm_info.get(dev.get("farm_id"), {})
                result.append({
                    "id": dev["id"],
                    "name": dev.get("name") or dev["id"],
                    "kit_id": dev["id"],
                    "serial_number": "",
                    "firmware_version": "",
                    "farm_id": dev.get("farm_id"),
                    "farm_name": finfo.get("farm_name", ""),
                    "farmer_id": finfo.get("farmer_id", ""),
                    "farmer_name": finfo.get("farmer_name", ""),
                    "thingspeak_channel_id": dev.get("thingspeak_channel_id"),
                    "thingspeak_read_api_key": dev.get("thingspeak_read_api_key"),
                    "created_at": dev.get("created_at"),
                    "lifecycle_status": "Active" if dev.get("farm_id") else "Available",
                })

        return result
    except Exception as e:
        print(f"[Supabase Error] get_all_kits failed: {e}")
        return []


def verify_token_and_get_user(auth_header: str):
    """
    Verifies the JWT token from the Authorization header and returns user metadata.
    """
    if not auth_header or not auth_header.startswith("Bearer "):
        return None, "Missing or invalid authorization header"
        
    token = auth_header.split(" ", 1)[1].strip()
    
    # Handle local mock testing tokens
    if token.startswith("mock_token_for_"):
        uid = token.replace("mock_token_for_", "")
        return {"uid": uid, "role": "FARMER"}, None
        
    if not supabase:
        return None, "Backend is running in offline fallback mode. Cannot verify JWT."
        
    try:
        user_res = supabase.auth.get_user(token)
        if not user_res or not user_res.user:
            return None, "Invalid or expired authorization token"
            
        user_uuid = user_res.user.id
        
        # Query database to retrieve user's role
        profile_res = supabase.table("profiles").select("role").eq("id", user_uuid).execute()
        role = "FARMER"
        if profile_res.data:
            role = profile_res.data[0].get("role", "FARMER")
            
        return {"uid": user_uuid, "role": role}, None
    except Exception as e:
        print(f"[Supabase JWT Verification Error] {e}")
        return None, f"Token validation failed: {str(e)}"


def save_device_wifi_config(device_id: str, ssid: str, password: str, farmer_id: str):
    """
    Saves Wi-Fi configuration details to the database in an encrypted format.
    Validates that the farmer actually owns/is assigned this device.
    """
    if not supabase:
        return {"status": "error", "message": "Database client not configured. Cannot save Wi-Fi configuration."}
        
    try:
        # 1. Resolve farm_id from farm_members for this farmer_id
        member_res = supabase.table("farm_members").select("farm_id").eq("profile_id", farmer_id).execute()
        if not member_res.data:
            return {"status": "error", "message": f"Unauthorized: No farm membership associated with profile '{farmer_id}'."}
        farm_id = member_res.data[0].get("farm_id")
        
        # 2. Verify that the device is registered and assigned to this farm
        device_res = supabase.table("devices").select("farm_id").eq("id", device_id).execute()
        if not device_res.data:
            return {"status": "error", "message": f"Device '{device_id}' is not registered."}
        
        assigned_farm_id = device_res.data[0].get("farm_id")
        if assigned_farm_id != farm_id:
            return {"status": "error", "message": f"Unauthorized: Device '{device_id}' is not assigned to your farm."}
            
        # 3. Encrypt the Wi-Fi password
        encrypted_pwd = encrypt_wifi_password(password)
        
        # 4. Upsert Wi-Fi configuration into device_wifi_configs
        wifi_data = {
            "device_id": device_id,
            "wifi_ssid": ssid,
            "encrypted_wifi_password": encrypted_pwd,
            "updated_at": "now()"
        }
        
        res = supabase.table("device_wifi_configs").upsert(wifi_data).execute()
        
        return {
            "status": "success",
            "message": "Wi-Fi configuration saved"
        }
    except Exception as e:
        print(f"[Supabase Error] save_device_wifi_config failed: {e}")
        return {"status": "error", "message": str(e)}


def get_alerts(batch_id=None):
    if not supabase:
        if batch_id:
            return [a for a in _mock_alerts if a.get("batch_id") == batch_id]
        return _mock_alerts
    try:
        query = supabase.table("alerts").select("*")
        if batch_id:
            query = query.eq("batch_id", batch_id)
        res = query.order("created_at", desc=True).execute()
        data = res.data or []
        for alert in data:
            alert["id"] = str(alert.get("id"))
        return data
    except Exception as e:
        print(f"[Supabase Error] get_alerts failed: {e}")
        return []

def create_db_alert(batch_id, device_id, prediction_id, title, description, severity):
    if not supabase:
        new_id = len(_mock_alerts) + 1
        alert = {
            "id": str(new_id),
            "batch_id": batch_id,
            "device_id": device_id,
            "prediction_id": prediction_id,
            "title": title,
            "description": description,
            "severity": severity,
            "status": "UNRESOLVED",
            "created_at": "now()"
        }
        _mock_alerts.append(alert)
        return alert
    try:
        data = {
            "batch_id": batch_id,
            "device_id": device_id,
            "prediction_id": prediction_id,
            "title": title,
            "description": description,
            "severity": severity,
            "status": "UNRESOLVED"
        }
        res = supabase.table("alerts").insert(data).execute()
        if res.data and len(res.data) > 0:
            alert = dict(res.data[0])
            alert["id"] = str(alert.get("id"))
            return alert
        return None
    except Exception as e:
        print(f"[Supabase Error] create_db_alert failed: {e}")
        return None

def update_alert_status(alert_id, status):
    if not supabase:
        for alert in _mock_alerts:
            if alert.get("id") == str(alert_id):
                alert["status"] = status
                return True
        return False
    try:
        res = supabase.table("alerts").update({"status": status}).eq("id", int(alert_id)).execute()
        return True
    except Exception as e:
        print(f"[Supabase Error] update_alert_status failed: {e}")
        return False

def get_cases(vet_id=None, batch_id=None):
    if not supabase:
        cases = list(_mock_cases)
        if vet_id:
            cases = [c for c in cases if c.get("veterinarian_id") == vet_id]
        if batch_id:
            cases = [c for c in cases if c.get("batch_id") == batch_id]
        return cases
    try:
        query = supabase.table("veterinary_cases").select("*")
        if vet_id:
            query = query.eq("veterinarian_id", vet_id)
        if batch_id:
            query = query.eq("batch_id", batch_id)
        res = query.order("created_at", desc=True).execute()
        data = res.data or []
        for case in data:
            case["id"] = str(case.get("id"))
            if case.get("alert_id") is not None:
                case["alert_id"] = str(case.get("alert_id"))
        return data
    except Exception as e:
        print(f"[Supabase Error] get_cases failed: {e}")
        return []

def create_case(alert_id, batch_id, veterinarian_id, status):
    new_id = str(uuid.uuid4())
    case = {
        "id": new_id,
        "alert_id": str(alert_id) if alert_id else None,
        "batch_id": batch_id,
        "veterinarian_id": veterinarian_id,
        "status": status,
        "diagnosis": None,
        "recommendation": None,
        "treatment": None,
        "follow_up_instructions": None,
        "created_at": "now()",
        "updated_at": "now()"
    }
    if not supabase:
        _mock_cases.append(case)
        return case
    try:
        data = {
            "batch_id": batch_id,
            "status": status
        }
        if alert_id is not None:
            data["alert_id"] = int(alert_id)
        if veterinarian_id is not None:
            data["veterinarian_id"] = veterinarian_id
        res = supabase.table("veterinary_cases").insert(data).execute()
        if res.data and len(res.data) > 0:
            result = dict(res.data[0])
            result["id"] = str(result.get("id"))
            if result.get("alert_id") is not None:
                result["alert_id"] = str(result.get("alert_id"))
            return result
        # Supabase returned no data — fall back to mock
        print("[Supabase] create_case: insert returned no data, falling back to mock storage")
        _mock_cases.append(case)
        return case
    except Exception as e:
        print(f"[Supabase Error] create_case failed: {e}")
        print("[Supabase] Falling back to mock storage for this case")
        _mock_cases.append(case)
        return case

def update_case(case_id, veterinarian_id, status, diagnosis, recommendation, treatment, follow_up_instructions):
    # Try mock storage first (works for both offline and fallback cases)
    for case in _mock_cases:
        if case.get("id") == str(case_id):
            case["veterinarian_id"] = veterinarian_id
            case["status"] = status
            case["diagnosis"] = diagnosis
            case["recommendation"] = recommendation
            case["treatment"] = treatment
            case["follow_up_instructions"] = follow_up_instructions
            case["updated_at"] = "now()"
            return True
    if not supabase:
        return False
    try:
        data = {
            "status": status,
            "diagnosis": diagnosis,
            "recommendation": recommendation,
            "treatment": treatment,
            "follow_up_instructions": follow_up_instructions,
            "updated_at": "now()"
        }
        if veterinarian_id is not None:
            data["veterinarian_id"] = veterinarian_id
        res = supabase.table("veterinary_cases").update(data).eq("id", str(case_id)).execute()
        return True
    except Exception as e:
        print(f"[Supabase Error] update_case failed: {e}")
        return False


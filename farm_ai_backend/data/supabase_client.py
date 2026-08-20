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
        if active_batch:
            data["batch_id"] = active_batch.get("id")
        res = supabase.table("sensor_telemetry").insert(data).execute()
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
        return {"status": "error", "message": str(e)}


def get_device_thingspeak_config(device_id):
    """
    Fetches the ThingSpeak channel ID and read API key for a device.
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
        return {"status": "error", "message": "Device not found"}
    except Exception as e:
        print(f"[Supabase Error] Failed to get device config: {e}")
        return {"status": "error", "message": str(e)}


def update_device_thingspeak_config(device_id, farm_id, channel_id, read_api_key):
    """
    Updates the ThingSpeak credentials for a device after verifying it belongs to the given farm (or registers it).
    """
    if not supabase:
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


def record_mortality(batch_id, death_count):
    """
    Safely decrement the current bird count of a batch due to mortality.
    """
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


def sync_user_profile(uid, name, email, role, farm_name=None, farm_location=None, phone=None,
                      specialty=None, location=None, photo_url=None, license_number=None, qualification=None, experience=None):
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
            if not farm_res.data:
                supabase.table("farms").insert({
                    "id": farm_id,
                    "name": farm_name
                }).execute()
                print(f"[Supabase] Created farm: {farm_name} ({farm_id})")
            else:
                supabase.table("farms").update({
                    "name": farm_name
                }).eq("id", farm_id).execute()
                
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

            vet_res = supabase.table("veterinarians").select("id").eq("id", uid).execute()
            vet_data = {
                "id": uid,
                "name": name,
                "specialty": specialty or "Avian Medicine",
                "phone": phone or "",
                "email": email,
                "location": location or farm_location or "Unspecified District",
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
                           specialty=None, phone=None, location=None, photo_url=None, license_number=None, qualification=None, experience=None):
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
            
            # Create farm
            supabase.table("farms").insert({
                "id": farm_id,
                "name": farm_name
            }).execute()
            
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
            supabase.table("veterinarians").insert({
                "id": user_uuid,
                "name": name,
                "specialty": specialty or "Avian Medicine",
                "phone": phone or "",
                "email": email,
                "location": location or farm_location or "Unspecified District",
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
        print(f"[Supabase Login Error] {e}")
        return {"status": "error", "message": str(e)}


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


def create_alert(batch_id, device_id, prediction_id, title, description, severity):
    """
    Create a new alert record.
    """
    if not supabase:
        return None
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
            return res.data[0]
        return None
    except Exception as e:
        print(f"[Supabase Error] create_alert failed: {e}")
        return None


def get_devices_with_thingspeak():
    """
    Fetch all devices configured with a ThingSpeak channel ID.
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
            .select("*, farm_members(farm_id, farms(name))")\
            .eq("role", "FARMER")\
            .execute()
        return res.data or []
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
        return res.data or []
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
    to farmer-facing API endpoints.
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
        # Store extra metadata in columns if they exist (migrations may add them)
        # We attempt upsert; if a column is missing Supabase will error and we catch it below
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

        # Try with extras first; fall back to base row only if extras cause a column error
        try:
            res = supabase.table("device_kits").insert({**kit_data, **extras}).execute()
        except Exception as extra_err:
            if "PGRST204" in str(extra_err) or "column" in str(extra_err).lower():
                print(f"[Supabase] device_kits missing extended columns; inserting base row: {extra_err}")
                res = supabase.table("device_kits").insert(kit_data).execute()
            else:
                raise extra_err

        row = res.data[0] if res.data else {**kit_data, **extras}
        # Always return kit_id and name in the response dict for the admin UI
        row.setdefault("kit_id", kit_id)
        row.setdefault("name", name)
        return {"status": "success", "data": row}
    except Exception as e:
        print(f"[Supabase Error] create_device_kit failed for '{device_id}': {e}")
        return {"status": "error", "message": str(e)}


def assign_device_to_farm(device_id: str, farmer_profile_id: str):
    """
    Assigns a device to a farmer's farm.

    Flow:
    1. Verify kit exists in device_kits and is Available (not already assigned).
    2. Verify farmer profile exists and is APPROVED.
    3. Resolve farm_id from farm_members (source of truth — never trust Android).
    4. Ensure farm exists in farms table (create if needed).
    5. Create row in devices (id=device_id, farm_id=farm_id) OR update if exists.
    6. Mark device_kits.status = 'Active'.
    7. Auto-create farm_settings row if missing.
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
            # Already a provisioned device
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

        # 3. Resolve farm_id from farm_members (authoritative — never trust Android-provided farm_id)
        member_res = supabase.table("farm_members").select("farm_id").eq("profile_id", farmer_profile_id).execute()
        if not member_res.data:
            return {"status": "error", "message": f"No farm membership found for profile '{farmer_profile_id}'. Farmer may not have a farm yet."}
        farm_id = member_res.data[0].get("farm_id")

        # 4. Ensure farm exists
        farm_res = supabase.table("farms").select("id").eq("id", farm_id).execute()
        if not farm_res.data:
            return {"status": "error", "message": f"Farm '{farm_id}' not found in farms table."}

        # 5. Get ThingSpeak credentials from device_kits row (may or may not have extra columns)
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

        # 6. Upsert into devices table (create provisioned device row with farm_id)
        device_row = {
            "id": device_id,
            "farm_id": farm_id,
            "name": kit_name if 'kit_name' in dir() else f"ESP32 Controller ({device_id})",
        }
        if thingspeak_channel_id:
            device_row["thingspeak_channel_id"] = thingspeak_channel_id
        if thingspeak_read_api_key:
            device_row["thingspeak_read_api_key"] = thingspeak_read_api_key
        # thingspeak_write_api_key only stored if column exists in devices

        # Check if device row already exists in devices table
        dev_existing = supabase.table("devices").select("id").eq("id", device_id).execute()
        if dev_existing.data:
            # Update farm_id
            supabase.table("devices").update({"farm_id": farm_id}).eq("id", device_id).execute()
        else:
            supabase.table("devices").insert(device_row).execute()

        # 7. Mark device_kits.status = 'Active'
        try:
            supabase.table("device_kits").update({"status": "Active"}).eq("id", device_id).execute()
        except Exception:
            pass  # device_kits row may not exist if device was pre-existing

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
        kit_res = supabase.table("device_kits").select("id, status, registered_at").execute()
        kits_raw = kit_res.data or []

        # Fetch provisioned devices (assigned to a farm)
        dev_res = supabase.table("devices").select(
            "id, name, farm_id, thingspeak_channel_id, last_seen_at, created_at"
        ).execute()
        devices_raw = dev_res.data or []
        devices_by_id = {d["id"]: d for d in devices_raw}

        # Merge: device_kits entries enriched with devices data where available
        result = []
        seen = set()
        for kit in kits_raw:
            dev = devices_by_id.get(kit["id"], {})
            merged = {
                "id": kit["id"],
                "name": dev.get("name") or kit.get("id"),
                "farm_id": dev.get("farm_id"),
                "thingspeak_channel_id": dev.get("thingspeak_channel_id"),
                "last_seen_at": dev.get("last_seen_at"),
                "created_at": dev.get("created_at") or kit.get("registered_at"),
                "lifecycle_status": kit.get("status", "Available"),
            }
            result.append(merged)
            seen.add(kit["id"])

        # Also include devices that were provisioned without going through device_kits
        for dev in devices_raw:
            if dev["id"] not in seen:
                result.append({
                    "id": dev["id"],
                    "name": dev.get("name"),
                    "farm_id": dev.get("farm_id"),
                    "thingspeak_channel_id": dev.get("thingspeak_channel_id"),
                    "last_seen_at": dev.get("last_seen_at"),
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

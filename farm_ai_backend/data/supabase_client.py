import os
import uuid
import time
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


def sync_user_profile(uid, name, email, role, farm_name=None, farm_location=None, phone=None):
    """
    Synchronizes user profile, farm, and members to Supabase.
    """
    if not supabase:
        print(f"[Supabase Fallback] Profile sync fallback for '{email}' (role: {role}).")
        return {"status": "fallback", "profile_id": None}
    
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
            vet_res = supabase.table("veterinarians").select("id").eq("id", uid).execute()
            vet_data = {
                "id": uid,
                "name": name,
                "specialty": "Avian Medicine",
                "phone": phone or "",
                "email": email,
                "location": farm_location or "Unspecified District",
                "verification_status": "PENDING",
                "availability": "Available"
            }
            if not vet_res.data:
                supabase.table("veterinarians").insert(vet_data).execute()
                print(f"[Supabase] Registered veterinarian: {email}")
            else:
                supabase.table("veterinarians").update({
                    "name": name,
                    "phone": phone or "",
                    "location": farm_location or "Unspecified District"
                }).eq("id", uid).execute()
                print(f"[Supabase] Updated veterinarian: {email}")
                
        return {"status": "success", "profile_id": profile_uuid}
    except Exception as e:
        print(f"[Supabase Error] Profile sync exception: {e}")
        return {"status": "error", "message": str(e)}


def supabase_register_user(name, email, password, role, farm_name=None, farm_location=None, total_sheds=4, floor_space_sq_ft=24000):
    """
    Registers a new user in Supabase Auth and populates the matching profiles/farms/veterinarians tables.
    """
    if not supabase:
        # Simulated fallback mode
        uid = f"sim_{int(time.time() * 1000)}"
        profile_uuid = str(uuid.uuid5(uuid.NAMESPACE_DNS, uid))
        return {
            "status": "success",
            "data": {
                "uid": profile_uuid,
                "name": name,
                "email": email,
                "role": role,
                "farmName": farm_name or "Greenfield Broilers",
                "joinDate": "Just now",
                "approvalStatus": "PENDING_APPROVAL" if role == "FARMER" else "APPROVED"
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
            supabase.table("veterinarians").insert({
                "id": user_uuid,
                "name": name,
                "specialty": "Avian Medicine",
                "phone": "",
                "email": email,
                "location": farm_location or "Unspecified District",
                "verification_status": "PENDING",
                "availability": "Available"
            }).execute()
            
        return {
            "status": "success",
            "data": {
                "uid": user_uuid,
                "name": name,
                "email": email,
                "role": role,
                "farmName": farm_name or "Greenfield Broilers",
                "joinDate": "Just now",
                "approvalStatus": "PENDING_APPROVAL" if role == "FARMER" else "APPROVED"
            }
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
            
        return {
            "status": "success",
            "data": {
                "uid": f"sim_user_{int(time.time() * 1000)}",
                "name": email.split('@')[0].capitalize(),
                "email": email,
                "role": detected_role,
                "farmName": "Greenfield Broilers",
                "joinDate": "Just now",
                "approvalStatus": sim_status,
                "rejectionReason": sim_reason
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
                "rejectionReason": rejection_reason
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




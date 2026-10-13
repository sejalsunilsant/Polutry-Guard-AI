import os
import sys
import time
from datetime import datetime, timedelta, date, timezone
import threading
from services.thingspeak_service import ThingSpeakService
from data.supabase_client import (
    save_telemetry, get_devices_with_thingspeak, get_last_telemetry,
    supabase, safe_cleanup_telemetry
)

# Load env variables (supports candidate paths and container ENV)
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
for env_path in [
    os.path.abspath(os.path.join(BASE_DIR, ".env")),
    os.path.abspath(os.path.join(BASE_DIR, "..", ".env")),
    os.path.abspath(os.path.join(os.getcwd(), ".env")),
]:
    if os.path.exists(env_path):
        try:
            with open(env_path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith("#") and "=" in line:
                        k, v = line.split("=", 1)
                        clean_k = k.strip()
                        clean_v = v.strip().strip('"').strip("'")
                        if clean_k not in os.environ:
                            os.environ[clean_k] = clean_v
            break
        except Exception:
            pass

def clean_and_parse_ts(ts_str):
    if not ts_str:
        return None
    if ts_str.endswith('Z'):
        ts_str = ts_str[:-1] + '+00:00'
    if ' ' in ts_str and 'T' not in ts_str:
        ts_str = ts_str.replace(' ', 'T')
    try:
        return datetime.fromisoformat(ts_str)
    except Exception:
        return None

def run_ingestion(poll_interval: int = None):
    if poll_interval is None:
        poll_interval = int(os.environ.get("TELEMETRY_POLL_INTERVAL", "20"))
    print(f"[Scheduler] Telemetry Ingestion Job Started (polling every {poll_interval}s).")
    while True:
        try:
            devices = get_devices_with_thingspeak()
            for d in devices:
                device_id = d.get("id")
                farm_id = d.get("farm_id")
                channel_id = d.get("thingspeak_channel_id")
                read_key = d.get("thingspeak_read_api_key")
                
                if not channel_id:
                    continue
                    
                # 1. Fetch latest feeds from ThingSpeak (up to 10 feeds to catch intermediate entries)
                try:
                    feed_data = ThingSpeakService.fetch_latest_feed(channel_id, read_key, results=10)
                except Exception as ex:
                    print(f"[Scheduler] Failed to fetch ThingSpeak feed for device {device_id}: {ex}")
                    continue
                    
                feeds = feed_data.get("feeds", [])
                if not feeds:
                    continue
                    
                channel_info = feed_data.get("channel", {})
                
                # Get last telemetry record from DB
                last_telemetry = get_last_telemetry(device_id)
                last_db_time = None
                if last_telemetry and last_telemetry.get("created_at"):
                    last_db_time = clean_and_parse_ts(last_telemetry["created_at"])
                    
                # Process uningested feeds in chronological order
                for feed in feeds:
                    feed_created_at_str = feed.get("created_at")
                    if not feed_created_at_str:
                        continue
                        
                    feed_time = clean_and_parse_ts(feed_created_at_str)
                    if feed_time and last_db_time and feed_time <= last_db_time:
                        continue
                        
                    try:
                        parsed = ThingSpeakService.parse_and_map_feed(channel_info, feed)
                        validated = ThingSpeakService.validate_data(parsed)
                    except Exception as p_err:
                        print(f"[Scheduler] Could not parse feed entry {feed.get('entry_id')}: {p_err}")
                        continue
                        
                    temp = validated.get("temperature")
                    hum = validated.get("humidity")
                    ammonia = validated.get("ammonia")
                    
                    if temp is None or hum is None:
                        continue
                    if ammonia is None:
                        ammonia = 0.0
                        
                    sound_level = validated.get("sound_level", 50.0)
                    sound_url = validated.get("sound_url")
                    image_url = validated.get("image_url")
                    
                    save_telemetry(
                        device_id=device_id,
                        temperature=temp,
                        humidity=hum,
                        ammonia=ammonia,
                        sound_level=sound_level,
                        farm_id=farm_id,
                        sound_url=sound_url,
                        image_url=image_url,
                        created_at=feed_created_at_str
                    )
                    last_db_time = feed_time
                    print(f"[Scheduler] Successfully ingested telemetry for device {device_id} (feed {feed.get('entry_id')}) at {feed_created_at_str}")
                    
        except Exception as e:
            print(f"[Scheduler] Ingestion error: {e}")
            
        time.sleep(poll_interval)

def run_aggregation_and_cleanup():
    print("[Scheduler] Daily Aggregation & Cleanup Job Started.")
    retention_days = int(os.environ.get("TELEMETRY_RETENTION_DAYS", 30))
    
    while True:
        try:
            if supabase:
                today = date.today()
                
                # Fetch devices
                devices = get_devices_with_thingspeak()
                for d in devices:
                    device_id = d.get("id")
                    
                    # Find oldest record to see what dates we need to aggregate
                    oldest_res = supabase.table("sensor_telemetry")\
                        .select("created_at")\
                        .eq("device_id", device_id)\
                        .order("created_at", desc=False)\
                        .limit(1)\
                        .execute()
                        
                    if not oldest_res.data:
                        continue
                        
                    oldest_time_str = oldest_res.data[0].get("created_at")
                    oldest_time = clean_and_parse_ts(oldest_time_str)
                    if not oldest_time:
                        continue
                        
                    oldest_date = oldest_time.date()
                    
                    # Iterate from oldest date up to yesterday (inclusive)
                    current_date = oldest_date
                    while current_date < today:
                        date_str = current_date.isoformat()
                        
                        # A. Check if already aggregated
                        check_res = supabase.table("daily_telemetry")\
                            .select("id")\
                            .eq("device_id", device_id)\
                            .eq("date", date_str)\
                            .execute()
                            
                        # B. If not aggregated, aggregate
                        if not check_res.data:
                            start_time = datetime.combine(current_date, datetime.min.time()).isoformat() + "+00:00"
                            end_time = datetime.combine(current_date, datetime.max.time()).isoformat() + "+00:00"
                            
                            telemetry_res = supabase.table("sensor_telemetry")\
                                .select("*")\
                                .eq("device_id", device_id)\
                                .gte("created_at", start_time)\
                                .lte("created_at", end_time)\
                                .execute()
                                
                            if telemetry_res.data:
                                temps = [float(r["temperature"]) for r in telemetry_res.data if r.get("temperature") is not None]
                                hums = [float(r["humidity"]) for r in telemetry_res.data if r.get("humidity") is not None]
                                ammonias = [float(r["ammonia"]) for r in telemetry_res.data if r.get("ammonia") is not None]
                                
                                if temps and hums and ammonias:
                                    batch_id = None
                                    for r in telemetry_res.data:
                                        if r.get("batch_id"):
                                            batch_id = r["batch_id"]
                                            break
                                            
                                    daily_data = {
                                        "device_id": device_id,
                                        "batch_id": batch_id,
                                        "date": date_str,
                                        "temp_avg": sum(temps) / len(temps),
                                        "temp_min": min(temps),
                                        "temp_max": max(temps),
                                        "hum_avg": sum(hums) / len(hums),
                                        "hum_min": min(hums),
                                        "hum_max": max(hums),
                                        "ammonia_avg": sum(ammonias) / len(ammonias),
                                        "ammonia_min": min(ammonias),
                                        "ammonia_max": max(ammonias)
                                    }
                                    
                                    try:
                                        supabase.table("daily_telemetry").insert(daily_data).execute()
                                        print(f"[Scheduler] Aggregated date {date_str} for device {device_id}")
                                    except Exception as ie:
                                        print(f"[Scheduler] Failed to insert daily telemetry: {ie}")
                                        
                        # C. Safe cleanup check
                        retention_threshold = today - timedelta(days=retention_days)
                        if current_date < retention_threshold:
                            start_time = datetime.combine(current_date, datetime.min.time()).isoformat() + "+00:00"
                            end_time = datetime.combine(current_date, datetime.max.time()).isoformat() + "+00:00"
                            safe_cleanup_telemetry(device_id, date_str, start_time, end_time)
                            
                        current_date += timedelta(days=1)
                        
        except Exception as e:
            print(f"[Scheduler] Aggregation and cleanup error: {e}")
            
        time.sleep(3600)  # Sleep for 1 hour

_scheduler_started = False
_scheduler_lock = threading.Lock()

def start_background_scheduler(interval_seconds: int = None):
    """
    Starts background threads for telemetry ingestion and daily aggregation.
    Can be safely called from app.py on server startup.
    """
    global _scheduler_started
    with _scheduler_lock:
        if _scheduler_started:
            return
        _scheduler_started = True
        
        poll_interval = interval_seconds if interval_seconds is not None else int(os.environ.get("TELEMETRY_POLL_INTERVAL", "20"))
        t1 = threading.Thread(target=run_ingestion, args=(poll_interval,), daemon=True, name="TelemetryIngestionThread")
        t2 = threading.Thread(target=run_aggregation_and_cleanup, daemon=True, name="TelemetryAggregationThread")
        t1.start()
        t2.start()
        print(f"[Scheduler] Background scheduler initialized (poll interval: {poll_interval}s).")

if __name__ == '__main__':
    start_background_scheduler()
    print("[Scheduler Process Started] Running telemetry ingestion and daily aggregation threads.")
    
    # Keep main thread alive
    while True:
        time.sleep(1)

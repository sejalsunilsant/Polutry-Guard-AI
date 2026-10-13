import requests
import os
from data.supabase_client import supabase, get_device_thingspeak_config

class ThingSpeakService:
    @staticmethod
    def fetch_latest_feed(channel_id: str, api_key: str = None, results: int = 1):
        """
        Fetch the latest feed entries and channel metadata from ThingSpeak.
        """
        if not channel_id:
            raise ValueError("ThingSpeak channel ID is required")
            
        url = f"https://api.thingspeak.com/channels/{channel_id}/feeds.json"
        params = {"results": max(1, int(results))}
        if api_key:
            params["api_key"] = api_key
            
        try:
            response = requests.get(url, params=params, timeout=10)
            if response.status_code == 404:
                raise ValueError(f"ThingSpeak channel '{channel_id}' not found or unauthorized (requires read API key)")
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            raise ValueError(f"HTTP request to ThingSpeak failed: {str(e)}")

    @staticmethod
    def parse_and_map_feed(channel_info: dict, feed: dict):
        """
        Parse a single feed entry using channel metadata and sensible fallbacks.
        """
        if not channel_info or not feed:
            raise ValueError("Invalid feed or channel metadata")
            
        field_mapping = {}
        for key, label in channel_info.items():
            if key.startswith("field") and isinstance(label, str):
                label_lower = label.strip().lower()
                if any(k in label_lower for k in ["temperature", "temp", "celsius", "celcius"]):
                    field_mapping[key] = "temperature"
                elif any(k in label_lower for k in ["humidity", "hum", "moisture"]):
                    field_mapping[key] = "humidity"
                elif any(k in label_lower for k in ["ammonia", "nh3", "gas", "ppm"]):
                    field_mapping[key] = "ammonia"
                elif any(k in label_lower for k in ["sound_url", "audiourl"]):
                    field_mapping[key] = "sound_url"
                elif any(k in label_lower for k in ["image_url", "photourl", "camurl", "picurl"]):
                    field_mapping[key] = "image_url"
                elif any(k in label_lower for k in ["sound", "noise", "audio", "db", "acoustic"]):
                    field_mapping[key] = "sound_level"
                elif any(k in label_lower for k in ["device_id", "deviceid", "node_id", "kit_id"]):
                    field_mapping[key] = "device_id"

        # Baseline fallback mapping for standard 3-sensor kits
        default_mapping = {
            "field1": "temperature",
            "field2": "humidity",
            "field3": "ammonia",
        }
        final_mapping = {**default_mapping, **field_mapping}
        
        parsed = {}
        for ts_field, standard_name in final_mapping.items():
            parsed[standard_name] = feed.get(ts_field)
            
        # Only assign media URLs if the value is actually an HTTP/HTTPS URL
        sound_val = str(parsed.get("sound_url") or "").strip()
        parsed["sound_url"] = sound_val if sound_val.startswith(("http://", "https://")) else None

        image_val = str(parsed.get("image_url") or "").strip()
        parsed["image_url"] = image_val if image_val.startswith(("http://", "https://")) else None

        return parsed

    @classmethod
    def parse_and_map_data(cls, data: dict, feed_index: int = -1):
        """
        Parse JSON from ThingSpeak and dynamically map the response fields.
        Defaults to the latest feed in the returned list.
        """
        if not data or "channel" not in data or "feeds" not in data or not data["feeds"]:
            raise ValueError("Empty or invalid response data structure received from ThingSpeak")
            
        channel_info = data["channel"]
        feeds = data["feeds"]
        feed = feeds[feed_index] if abs(feed_index) <= len(feeds) else feeds[-1]
        return cls.parse_and_map_feed(channel_info, feed)

    @staticmethod
    def validate_data(parsed: dict):
        """
        Validate data types and physical boundaries:
        - Temperature: -40.0 to 80.0 Celsius
        - Humidity: 0.0 to 100.0 percent
        - Ammonia: >= 0.0 ppm
        """
        validated = {}
        
        # Temperature Validation
        temp_val = parsed.get("temperature")
        if temp_val is not None and str(temp_val).strip() != "":
            try:
                temp_float = float(temp_val)
                if not (-40.0 <= temp_float <= 80.0):
                    raise ValueError(f"Temperature value {temp_float}°C is out of reasonable range (-40 to 80)")
                validated["temperature"] = temp_float
            except (TypeError, ValueError) as e:
                if "out of reasonable range" in str(e):
                    raise
                raise ValueError(f"Could not parse temperature '{temp_val}' to float")
        else:
            validated["temperature"] = None

        # Humidity Validation
        hum_val = parsed.get("humidity")
        if hum_val is not None and str(hum_val).strip() != "":
            try:
                hum_float = float(hum_val)
                if not (0.0 <= hum_float <= 100.0):
                    raise ValueError(f"Humidity value {hum_float}% is out of bounds (0 to 100)")
                validated["humidity"] = hum_float
            except (TypeError, ValueError) as e:
                if "out of bounds" in str(e):
                    raise
                raise ValueError(f"Could not parse humidity '{hum_val}' to float")
        else:
            validated["humidity"] = None

        # Ammonia Validation
        nh3_val = parsed.get("ammonia")
        if nh3_val is not None and str(nh3_val).strip() != "":
            try:
                nh3_float = float(nh3_val)
                if nh3_float < 0.0:
                    raise ValueError(f"Ammonia value {nh3_float} ppm cannot be negative")
                validated["ammonia"] = nh3_float
            except (TypeError, ValueError) as e:
                if "cannot be negative" in str(e):
                    raise
                raise ValueError(f"Could not parse ammonia '{nh3_val}' to float")
        else:
            validated["ammonia"] = None

        # Sound level validation
        sound_val = parsed.get("sound_level")
        if sound_val is not None and str(sound_val).strip() != "":
            try:
                validated["sound_level"] = max(0.0, float(sound_val))
            except (TypeError, ValueError):
                validated["sound_level"] = 50.0
        else:
            validated["sound_level"] = 50.0

        # Standardize strings & URLs
        sound_url_cand = str(parsed.get("sound_url") or "").strip()
        validated["sound_url"] = sound_url_cand if sound_url_cand.startswith(("http://", "https://")) else None

        image_url_cand = str(parsed.get("image_url") or "").strip()
        validated["image_url"] = image_url_cand if image_url_cand.startswith(("http://", "https://")) else None

        dev_cand = str(parsed.get("device_id") or "").strip()
        validated["device_id"] = dev_cand if dev_cand else None
        
        return validated

    @classmethod
    def authenticate_and_get_config(cls, device_id: str, farm_id: str):
        """
        Retrieves ThingSpeak credentials from the database for the device and
        verifies that it belongs to the given farm (multiple farmers isolation).
        """
        # If running in local fallback state (no Supabase initialized)
        if not supabase:
            print(f"[ThingSpeak Ingestion] WARNING: running in local fallback mode. Device '{device_id}' mock authenticated.")
            return {
                "authenticated": True,
                "thingspeak_channel_id": os.environ.get("THINGSPEAK_DEFAULT_CHANNEL_ID", "default_channel"),
                "thingspeak_read_api_key": os.environ.get("THINGSPEAK_DEFAULT_READ_API_KEY", "default_key")
            }
            
        config = get_device_thingspeak_config(device_id)
        if config["status"] != "success":
            return {"authenticated": False, "error": f"Device registration check failed: {config.get('message')}"}
            
        # Verify ownership / farm matching
        if config.get("farm_id") != farm_id:
            return {"authenticated": False, "error": "Unauthorized access: Device is registered to a different farm"}
            
        # Verify ThingSpeak API settings are set
        if not config.get("thingspeak_channel_id"):
            return {"authenticated": False, "error": "Device is not yet configured with a ThingSpeak Channel ID"}
            
        return {
            "authenticated": True,
            "thingspeak_channel_id": config.get("thingspeak_channel_id"),
            "thingspeak_read_api_key": config.get("thingspeak_read_api_key")
        }

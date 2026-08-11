import requests
import os
from data.supabase_client import supabase, get_device_thingspeak_config

class ThingSpeakService:
    @staticmethod
    def fetch_latest_feed(channel_id: str, api_key: str = None):
        """
        Fetch the latest feed entry and channel metadata from ThingSpeak.
        """
        if not channel_id:
            raise ValueError("ThingSpeak channel ID is required")
            
        url = f"https://api.thingspeak.com/channels/{channel_id}/feeds.json"
        params = {"results": 1}
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
    def parse_and_map_data(data: dict):
        """
        Parse JSON from ThingSpeak and dynamically map the response fields
        based on the channel metadata, falling back to standard index positions.
        """
        if not data or "channel" not in data or "feeds" not in data or not data["feeds"]:
            raise ValueError("Empty or invalid response data structure received from ThingSpeak")
            
        channel_info = data["channel"]
        feed = data["feeds"][0]
        
        # Look at metadata field labels to dynamically map fields
        field_mapping = {}
        for key, label in channel_info.items():
            if key.startswith("field") and isinstance(label, str):
                label_lower = label.strip().lower()
                if "temperature" in label_lower or "temp" in label_lower or "celsius" in label_lower or "celcius" in label_lower:
                    field_mapping[key] = "temperature"
                elif "humidity" in label_lower or "hum" in label_lower or "moisture" in label_lower:
                    field_mapping[key] = "humidity"
                elif "ammonia" in label_lower or "nh3" in label_lower or "gas" in label_lower or "ppm" in label_lower:
                    field_mapping[key] = "ammonia"
                elif "sound" in label_lower or "noise" in label_lower or "audio" in label_lower or "mic" in label_lower or "voice" in label_lower or "db" in label_lower:
                    field_mapping[key] = "sound_url"
                elif "image" in label_lower or "photo" in label_lower or "cam" in label_lower or "pic" in label_lower or "camera" in label_lower:
                    field_mapping[key] = "image_url"
                elif "device" in label_lower or "id" in label_lower or "mac" in label_lower or "node" in label_lower or "esp" in label_lower:
                    field_mapping[key] = "device_id"

        # Fallback default positions if they weren't explicitly labeled in ThingSpeak metadata
        default_mapping = {
            "field1": "temperature",
            "field2": "humidity",
            "field3": "ammonia",
            "field4": "sound_url",
            "field5": "image_url",
            "field6": "device_id"
        }
        
        # Merge maps prioritizing channel-configured labels
        final_mapping = {**default_mapping, **field_mapping}
        
        parsed = {}
        for ts_field, standard_name in final_mapping.items():
            parsed[standard_name] = feed.get(ts_field)
            
        return parsed

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

        # Standardize strings
        validated["sound_url"] = str(parsed.get("sound_url") or "").strip()
        validated["image_url"] = str(parsed.get("image_url") or "").strip()
        validated["device_id"] = str(parsed.get("device_id") or "").strip()
        
        return validated

    @classmethod
    def authenticate_and_get_config(cls, device_id: str, farm_id: str):
        """
        Retrieves ThingSpeak credentials from the database for the device and
        verifies that it belongs to the given farm (multiple farmers isolation).
        """
        # If running in local fallback state (no Supabase initialized)
        if supabase is None:
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

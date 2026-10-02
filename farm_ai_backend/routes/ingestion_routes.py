from flask import Blueprint, request, jsonify
import numpy as np
import os
from data.supabase_client import (
    save_telemetry, get_last_prediction, get_last_telemetry,
    update_device_thingspeak_config
)
from services.thingspeak_service import ThingSpeakService
from ml.manager import ModelManager
from ml.preprocessing import (
    preprocess_sensor_data, scale_sensor_features, engineer_sensor_features,
    preprocess_audio, should_trigger_audio_processing, is_valid_sound_clip,
    preprocess_image, should_trigger_image_capture
)
from ml.fusion.decision_engine import fuse_and_store

ingestion_bp = Blueprint("ingestion_bp", __name__)


def validate_telemetry_payload(data: dict) -> dict:
    """
    Validate data types and physical boundaries:
    - Temperature: -40.0 to 80.0 Celsius
    - Humidity: 0.0 to 100.0 percent
    - Ammonia: >= 0.0 ppm
    - Sound level: >= 0.0 dB
    """
    validated = {}
    
    # Temperature Validation
    temp_val = data.get("temperature") if data.get("temperature") is not None else data.get("temp")
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
    hum_val = data.get("humidity") if data.get("humidity") is not None else data.get("hum")
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
    nh3_val = data.get("ammonia") if data.get("ammonia") is not None else (data.get("nh3") or data.get("gas"))
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
    sound_val = data.get("sound_level") if data.get("sound_level") is not None else (data.get("soundLevel") or data.get("sound") or data.get("noise"))
    if sound_val is not None and str(sound_val).strip() != "":
        try:
            sound_float = float(sound_val)
            if sound_float < 0.0:
                raise ValueError(f"Sound level {sound_float} dB cannot be negative")
            validated["sound_level"] = sound_float
        except (TypeError, ValueError) as e:
            if "cannot be negative" in str(e):
                raise
            raise ValueError(f"Could not parse sound level '{sound_val}' to float")
    else:
        validated["sound_level"] = 50.0

    # Optional media URLs
    validated["sound_url"] = str(data.get("sound_url") or data.get("soundUrl") or "").strip() or None
    validated["image_url"] = str(data.get("image_url") or data.get("imageUrl") or "").strip() or None
    
    return validated


@ingestion_bp.route('/ingest/thingspeak', methods=['POST'])
def ingest_thingspeak():
    """
    Ingest latest data from ThingSpeak for a given device and farm,
    then executes the preprocessing, inference, and decision fusion layers.
    """
    try:
        data = request.get_json() or {}
        device_id = data.get('deviceId') or data.get('device_id')
        farm_id = data.get('farmId') or data.get('farm_id')
        
        # Override parameters
        override_channel_id = data.get('channelId') or data.get('channel_id')
        override_read_api_key = data.get('readApiKey') or data.get('read_api_key')
        force_audio = data.get('forceAudio') or data.get('force_audio') or False
        force_image = data.get('forceImage') or data.get('force_image') or False
        
        if not device_id or not farm_id:
            return jsonify({'error': 'deviceId and farmId are required parameters'}), 400
            
        # 1. Device Authorization / Config Retrieval
        auth_info = ThingSpeakService.authenticate_and_get_config(device_id, farm_id)
        if not auth_info.get('authenticated'):
            return jsonify({'error': auth_info.get('error', 'Device authorization failed')}), 401
            
        # Select active credentials
        channel_id = override_channel_id or auth_info.get('thingspeak_channel_id')
        read_api_key = override_read_api_key or auth_info.get('thingspeak_read_api_key')
        
        if not channel_id:
            return jsonify({'error': 'No ThingSpeak channel_id configured for this device. Please register it first.'}), 400
            
        # 2. Fetch data from ThingSpeak
        try:
            feed_response = ThingSpeakService.fetch_latest_feed(channel_id, read_api_key)
        except ValueError as ve:
            return jsonify({'error': str(ve)}), 400
            
        # 3. Parse and standardize fields
        try:
            parsed_data = ThingSpeakService.parse_and_map_data(feed_response)
        except ValueError as ve:
            return jsonify({'error': f"Failed to parse ThingSpeak data: {str(ve)}"}), 422
            
        # Ensure device_id is correctly mapped/defaulted
        if not parsed_data.get('device_id'):
            parsed_data['device_id'] = device_id
            
        # 4. Validate raw inputs
        try:
            validated_data = ThingSpeakService.validate_data(parsed_data)
        except ValueError as ve:
            return jsonify({'error': f"Data validation failed: {str(ve)}"}), 422
            
        # 5. Preprocessing Layer: Telemetry
        temp_raw = validated_data.get('temperature')
        hum_raw = validated_data.get('humidity')
        ammonia_raw = validated_data.get('ammonia')
        
        # Parse inputs (converts F to C, cleans suffixes, fills missing)
        temp, hum, ammonia = preprocess_sensor_data(temp_raw, hum_raw, ammonia_raw)
        
        # Scale features
        sensor_features = [temp, hum, ammonia]
        scaled_features = scale_sensor_features(sensor_features, method="minmax")
        
        # Feature Engineering (THI, gas stress)
        engineered = engineer_sensor_features(temp, hum, ammonia)
        
        # Determine sound level from feed or default
        sound_level = 50.0
        
        # Extract media URLs
        sound_url = validated_data.get('sound_url')
        image_url = validated_data.get('image_url')

        # 5.2 Store Telemetry to Supabase
        telemetry_id = None
        try:
            telemetry_res = save_telemetry(
                device_id=device_id,
                temperature=temp,
                humidity=hum,
                ammonia=ammonia,
                sound_level=sound_level,
                farm_id=farm_id,
                sound_url=sound_url,
                image_url=image_url
            )
            if telemetry_res and telemetry_res.get("status") == "success" and telemetry_res.get("data"):
                telemetry_id = telemetry_res["data"][0].get("id")
        except Exception as se:
            print(f"[Ingestion Route] Database telemetry logging failed: {se}")
            
        # Construct response payload
        response_payload = {
            'status': 'success',
            'telemetry_id': telemetry_id,
            'temperature': temp,
            'humidity': hum,
            'ammonia': ammonia,
            'sound_level': sound_level,
            'sound_url': sound_url,
            'image_url': image_url,
            'sensor_features': sensor_features,
            'scaled_sensor_features': scaled_features,
            'engineered_features': engineered,
            'device_id': device_id,
            'farm_id': farm_id
        }
        
        return jsonify(response_payload), 200
        
    except Exception as e:
        print(f"[Ingestion API] Exception in ingest_thingspeak: {e}")
        return jsonify({'error': f"Internal server error: {str(e)}"}), 500


@ingestion_bp.route('/device/configure-thingspeak', methods=['POST'])
def configure_device_thingspeak():
    """
    Endpoint for a farmer to configure or update the ThingSpeak credentials for their device.
    """
    try:
        data = request.get_json() or {}
        device_id = data.get('deviceId') or data.get('device_id')
        farm_id = data.get('farmId') or data.get('farm_id')
        channel_id = data.get('channelId') or data.get('channel_id')
        read_api_key = data.get('readApiKey') or data.get('read_api_key')
        
        if not device_id or not farm_id:
            return jsonify({'error': 'deviceId and farmId are required parameters'}), 400
            
        if not channel_id:
            return jsonify({'error': 'channelId is a required parameter'}), 400
            
        res = update_device_thingspeak_config(device_id, farm_id, str(channel_id), read_api_key)
        
        if res.get('status') == 'error':
            return jsonify({'error': res.get('message')}), 400
            
        return jsonify({
            'status': 'success',
            'message': f'Device {device_id} ThingSpeak credentials updated successfully.',
            'data': res.get('data')
        }), 200
        
    except Exception as e:
        print(f"[Ingestion API] Exception in configure_device_thingspeak: {e}")
        return jsonify({'error': f"Internal server error: {str(e)}"}), 500


@ingestion_bp.route('/ingest/telemetry', methods=['POST'])
@ingestion_bp.route('/telemetry/ingest', methods=['POST'])
def ingest_telemetry():
    """
    Direct Production Telemetry Ingestion Endpoint.
    Receives JSON telemetry directly from IoT hardware nodes (ESP32, Raspberry Pi, etc.),
    validates data, executes feature engineering, and logs directly to Supabase PostgreSQL.
    """
    try:
        data = request.get_json() or {}
        device_id = data.get('deviceId') or data.get('device_id')
        farm_id = data.get('farmId') or data.get('farm_id')
        
        if not device_id or not farm_id:
            return jsonify({'error': 'deviceId and farmId are required parameters'}), 400
            
        # 1. Validate raw inputs
        try:
            validated_data = validate_telemetry_payload(data)
        except ValueError as ve:
            return jsonify({'error': f"Data validation failed: {str(ve)}"}), 422
            
        # 2. Preprocessing Layer: Telemetry
        temp_raw = validated_data.get('temperature')
        hum_raw = validated_data.get('humidity')
        ammonia_raw = validated_data.get('ammonia')
        sound_level = validated_data.get('sound_level', 50.0)
        
        # Parse inputs (converts F to C, cleans suffixes, fills missing)
        temp, hum, ammonia = preprocess_sensor_data(temp_raw, hum_raw, ammonia_raw)
        
        # Scale features
        sensor_features = [temp, hum, ammonia]
        scaled_features = scale_sensor_features(sensor_features, method="minmax")
        
        # Feature Engineering (THI, gas stress)
        engineered = engineer_sensor_features(temp, hum, ammonia)
        
        # Extract media URLs
        sound_url = validated_data.get('sound_url')
        image_url = validated_data.get('image_url')

        # 3. Store Telemetry to Supabase
        telemetry_id = None
        try:
            telemetry_res = save_telemetry(
                device_id=device_id,
                temperature=temp,
                humidity=hum,
                ammonia=ammonia,
                sound_level=sound_level,
                farm_id=farm_id,
                sound_url=sound_url,
                image_url=image_url
            )
            if telemetry_res and telemetry_res.get("status") == "success" and telemetry_res.get("data"):
                telemetry_id = telemetry_res["data"][0].get("id")
        except Exception as se:
            print(f"[Ingestion Route] Database telemetry logging failed: {se}")
            
        # Construct response payload
        response_payload = {
            'status': 'success',
            'telemetry_id': telemetry_id,
            'temperature': temp,
            'humidity': hum,
            'ammonia': ammonia,
            'sound_level': sound_level,
            'sound_url': sound_url,
            'image_url': image_url,
            'sensor_features': sensor_features,
            'scaled_sensor_features': scaled_features,
            'engineered_features': engineered,
            'device_id': device_id,
            'farm_id': farm_id
        }
        
        return jsonify(response_payload), 200
        
    except Exception as e:
        print(f"[Ingestion API] Exception in ingest_telemetry: {e}")
        return jsonify({'error': f"Internal server error: {str(e)}"}), 500


@ingestion_bp.route('/device/configure-wifi', methods=['POST'])
def configure_device_wifi():
    """
    Endpoint for a farmer to upload and save Wi-Fi configuration details in the database.
    Requires Authorization: Bearer <JWT>
    """
    try:
        from data.supabase_client import verify_token_and_get_user, save_device_wifi_config
        
        # 1. Authorize user using JWT
        auth_header = request.headers.get("Authorization")
        user_info, auth_error = verify_token_and_get_user(auth_header)
        if auth_error:
            return jsonify({'error': auth_error}), 401
            
        data = request.get_json() or {}
        device_id = (data.get('deviceId') or data.get('device_id') or '').strip()
        ssid = (data.get('ssid') or '').strip()
        password = data.get('password')
        
        if not device_id:
            return jsonify({'error': 'deviceId is a required field'}), 400
        if not ssid:
            return jsonify({'error': 'ssid is a required field'}), 400
        if password is None:
            return jsonify({'error': 'password is a required field'}), 400
            
        # 2. Save Wi-Fi config (verifies ownership/assignment under the hood)
        res = save_device_wifi_config(device_id, ssid, password, user_info['uid'])
        
        if res.get('status') == 'error':
            return jsonify({'error': res.get('message')}), 400
            
        return jsonify({
            'status': 'success',
            'message': 'Wi-Fi configuration saved'
        }), 200
        
    except Exception as e:
        print(f"[Ingestion API] Exception in configure_device_wifi: {e}")
        return jsonify({'error': f"Internal server error: {str(e)}"}), 500

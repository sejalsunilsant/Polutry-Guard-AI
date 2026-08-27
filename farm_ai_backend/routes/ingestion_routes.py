from flask import Blueprint, request, jsonify
import numpy as np
import librosa
import os
from services.thingspeak_service import ThingSpeakService
from data.supabase_client import update_device_thingspeak_config, save_telemetry, get_last_prediction, get_last_telemetry
from ml.manager import ModelManager
from ml.preprocessing import (
    preprocess_sensor_data, scale_sensor_features, engineer_sensor_features,
    preprocess_audio, should_trigger_audio_processing, is_valid_sound_clip,
    preprocess_image, should_trigger_image_capture
)
from ml.fusion.decision_engine import fuse_and_store

ingestion_bp = Blueprint("ingestion_bp", __name__)

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


from flask import Blueprint, request, jsonify
import numpy as np
import librosa
import os
from services.thingspeak_service import ThingSpeakService
from data.supabase_client import update_device_thingspeak_config, save_telemetry
from ml.manager import ModelManager
from ml.preprocessing import (
    preprocess_sensor_data, scale_sensor_features, engineer_sensor_features,
    preprocess_audio, should_trigger_audio_processing, is_valid_sound_clip,
    preprocess_image, should_trigger_image_capture
)
from ml.fusion.decision_engine import fuse_and_store

ingestion_bp = Blueprint("ingestion_bp", __name__)

@ingestion_bp.route('/api/v1/ingest/thingspeak', methods=['POST'])
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
        
        # 5.1 Run Telemetry Model Inference (XGBoost via ModelManager)
        sensor_pred = ModelManager.predict_sensor(sensor_features)
        
        # 6. Preprocessing & Inference Layer: Audio (Conditional Ingestion)
        sound_url = validated_data.get('sound_url')
        
        # Determine sound level from feed or default
        sound_level = 50.0
        
        should_process_audio = should_trigger_audio_processing(
            sound_level=sound_level, 
            last_processed_time=None,  # We can extend this to lookup in DB later
            force_request=force_audio,
            sound_threshold=78.0
        )
        
        sound_pred = None
        audio_metadata = None
        
        if sound_url and should_process_audio:
            try:
                waveform, sr = preprocess_audio(sound_url)
                is_valid = is_valid_sound_clip(waveform, sr)
                
                # Extract spectrogram from the preprocessed noise-reduced waveform
                mel = librosa.feature.melspectrogram(y=waveform, sr=sr, n_mels=128)
                log_mel = librosa.power_to_db(mel)
                
                # Pad/trim to 173 width
                if log_mel.shape[1] < 173:
                    pad = 173 - log_mel.shape[1]
                    log_mel = np.pad(log_mel, ((0, 0), (0, pad)))
                else:
                    log_mel = log_mel[:, :173]
                
                # Run sound inference
                sound_pred = ModelManager.predict_sound(log_mel)
                
                audio_metadata = {
                    'duration_seconds': float(len(waveform) / sr),
                    'sample_rate': sr,
                    'is_valid': is_valid,
                    'peak_amplitude': float(np.max(np.abs(waveform)))
                }
            except Exception as ae:
                print(f"[Ingestion Route] Audio preprocessing/inference error: {ae}")
                audio_metadata = {'error': str(ae)}
        else:
            audio_metadata = {
                'processed': False,
                'status': 'skipped_by_trigger_rules' if sound_url else 'no_url_available'
            }

        # 7. Preprocessing & Inference Layer: Image (Conditional Ingestion)
        image_url = validated_data.get('image_url')
        should_process_image = should_trigger_image_capture(
            temp=temp,
            hum=hum,
            sound=sound_level,
            last_capture_time=None,  # We can extend this to lookup in DB later
            time_threshold_minutes=10
        ) or force_image
        
        image_pred = None
        image_metadata = None
        
        if image_url and should_process_image:
            try:
                img_array = preprocess_image(image_url, target_size=(224, 224))
                # Run image inference
                image_pred = ModelManager.predict_image(img_array)
                
                image_metadata = {
                    'shape': list(img_array.shape),
                    'mean_pixel_value': float(np.mean(img_array))
                }
            except Exception as ie:
                print(f"[Ingestion Route] Image preprocessing/inference error: {ie}")
                image_metadata = {'error': str(ie)}
        else:
            image_metadata = {
                'processed': False,
                'status': 'skipped_by_trigger_rules' if image_url else 'no_url_available'
            }

        # 8. Store Telemetry and run Decision Fusion
        telemetry_id = None
        try:
            telemetry_res = save_telemetry(
                device_id=device_id,
                temperature=temp,
                humidity=hum,
                ammonia=ammonia,
                sound_level=sound_level,
                farm_id=farm_id
            )
            if telemetry_res and telemetry_res.get("status") == "success" and telemetry_res.get("data"):
                telemetry_id = telemetry_res["data"][0].get("id")
        except Exception as se:
            print(f"[Ingestion Route] Database telemetry logging failed: {se}")
            
        # Execute Decision Fusion and save prediction
        fused_result = fuse_and_store(
            device_id=device_id,
            telemetry_id=telemetry_id,
            temp=temp,
            hum=hum,
            ammonia=ammonia,
            sound_level=sound_level,
            sensor_pred=sensor_pred,
            sound_pred=sound_pred,
            image_pred=image_pred,
            farm_id=farm_id
        )
        
        # Construct response payload
        response_payload = {
            'temperature': temp,
            'humidity': hum,
            'ammonia': ammonia,
            'sensor_features': sensor_features,
            'scaled_sensor_features': scaled_features,
            'engineered_features': engineered,
            'sensor_prediction': sensor_pred,
            'audio_processed': audio_metadata.get('processed', True) if audio_metadata else False,
            'audio_metadata': audio_metadata,
            'audio_prediction': sound_pred,
            'image_processed': image_metadata.get('processed', True) if image_metadata else False,
            'image_metadata': image_metadata,
            'image_prediction': image_pred,
            'decision_fusion': fused_result,
            'device_id': device_id,
            'farm_id': farm_id
        }
        
        return jsonify(response_payload), 200
        
    except Exception as e:
        print(f"[Ingestion API] Exception in ingest_thingspeak: {e}")
        return jsonify({'error': f"Internal server error: {str(e)}"}), 500


@ingestion_bp.route('/api/v1/device/configure-thingspeak', methods=['POST'])
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

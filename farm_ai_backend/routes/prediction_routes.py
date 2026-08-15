import os
import tempfile
from flask import Blueprint, request, jsonify
import numpy as np
import librosa
from ml.predictor import predict_disease
from ml.manager import ModelManager
from ml.audio.preprocessing import extract_spectrogram
from ml.preprocessing import (
    preprocess_audio, is_valid_sound_clip, preprocess_image,
    should_trigger_audio_processing, should_trigger_image_capture
)

prediction_bp = Blueprint("prediction_bp", __name__)

@prediction_bp.route('/api/v1/predictions/latest', methods=['GET'])
def get_latest_prediction_endpoint():
    try:
        device_id = request.args.get('deviceId')
        if not device_id:
            return jsonify({
                'status': 'error',
                'message': 'deviceId is a required query parameter'
            }), 400
            
        from data.supabase_client import get_last_prediction
        pred = get_last_prediction(device_id)
        
        if not pred:
            return jsonify({
                'status': 'error',
                'message': f"No predictions found for device '{device_id}'"
            }), 404
            
        return jsonify({
            'riskLevel': pred.get('risk_level', 'LOW'),
            'confidence': float(pred.get('confidence', 0.0)),
            'recommendation': pred.get('recommendation', '')
        }), 200
    except Exception as e:
        print(f"[Prediction API] Error in get_latest_prediction_endpoint: {e}")
        return jsonify({'status': 'error', 'message': str(e)}), 500

@prediction_bp.route('/api/v1/predict-disease', methods=['POST'])
def predict_disease_endpoint():
    try:
        data = request.get_json() or {}
        device_id = data.get('deviceId') or data.get('device_id')
        farm_id = data.get('farmId') or data.get('farm_id')

        if not device_id or not farm_id:
            return jsonify({
                'status': 'error',
                'message': 'deviceId and farmId are required fields'
            }), 400

        # Fetch the latest telemetry record from the database
        from data.supabase_client import get_last_telemetry, supabase
        telemetry_record = get_last_telemetry(device_id)

        if not telemetry_record:
            if not supabase:
                temp = 24.0
                humid = 60.0
                ammonia = 10.0
                sound = 50.0
                sound_url = None
                image_url = None
                telemetry_id = None
                print("[Prediction API] WARNING: Supabase not initialized. Using local fallback telemetry values.")
            else:
                return jsonify({
                    'status': 'error',
                    'message': f"No telemetry data found for device '{device_id}'"
                }), 400
        else:
            # 1. Verify telemetry freshness (less than 5 minutes old)
            from datetime import datetime, timezone
            created_at_str = telemetry_record.get('created_at')
            
            def parse_timestamp(ts_str):
                if not ts_str:
                    return None
                if ts_str.endswith('Z'):
                    ts_str = ts_str[:-1] + '+00:00'
                try:
                    if ' ' in ts_str and 'T' not in ts_str:
                        ts_str = ts_str.replace(' ', 'T')
                    return datetime.fromisoformat(ts_str)
                except Exception:
                    return None

            telemetry_time = None
            if created_at_str:
                telemetry_time = parse_timestamp(created_at_str)
                
            if telemetry_time:
                now = datetime.now(timezone.utc)
                elapsed_seconds = (now - telemetry_time).total_seconds()
                if elapsed_seconds > 300:
                    return jsonify({
                        'status': 'error',
                        'message': f"Telemetry data for device '{device_id}' is stale ({int(elapsed_seconds)} seconds old). Freshness limit is 5 minutes."
                    }), 400
            
            # 2. Verify all telemetry metrics exist
            temp = telemetry_record.get('temperature')
            humid = telemetry_record.get('humidity')
            ammonia = telemetry_record.get('ammonia')
            sound = telemetry_record.get('sound_level')
            sound_url = telemetry_record.get('sound_url')
            image_url = telemetry_record.get('image_url')
            telemetry_id = telemetry_record.get('id')

            if temp is None or humid is None or ammonia is None or sound is None:
                return jsonify({
                    'status': 'error',
                    'message': 'Telemetry record contains insufficient sensor data'
                }), 400

            temp = float(temp)
            humid = float(humid)
            ammonia = float(ammonia)
            sound = float(sound)

        # 3. Duplicate Protection: Return existing prediction for this telemetry_id
        if telemetry_id and supabase:
            try:
                existing_pred = supabase.table("disease_predictions")\
                    .select("*")\
                    .eq("telemetry_id", telemetry_id)\
                    .execute()
                if existing_pred.data and len(existing_pred.data) > 0:
                    pred = existing_pred.data[0]
                    print(f"[Prediction API] Returning cached prediction for telemetry_id '{telemetry_id}'")
                    return jsonify({
                        'riskLevel': pred.get('risk_level', 'LOW'),
                        'confidence': float(pred.get('confidence', 0.0)),
                        'recommendation': pred.get('recommendation', '')
                    }), 200
            except Exception as ex:
                print(f"[Prediction API] Error checking duplicate predictions: {ex}")

        # 4. Rate-Limit inputs for Audio / Image
        from data.supabase_client import get_last_prediction
        prev_prediction = get_last_prediction(device_id)
        telemetry_history = get_last_telemetry(device_id, limit=2)
        prev_telemetry = None
        if isinstance(telemetry_history, list) and len(telemetry_history) >= 2:
            prev_telemetry = telemetry_history[1]
            
        last_processed_time = None
        last_capture_time = None
        prev_temp = None
        prev_hum = None
        prev_sound = None
        
        if prev_prediction:
            last_processed_time = parse_timestamp(prev_prediction.get("created_at"))
            last_capture_time = last_processed_time
            
        if prev_telemetry:
            prev_temp = prev_telemetry.get("temperature")
            prev_hum = prev_telemetry.get("humidity")
            prev_sound = prev_telemetry.get("sound_level")

        # 5. Conditional Multi-Modal Predictions
        sound_pred = None
        should_process_audio = should_trigger_audio_processing(
            sound_level=sound,
            last_processed_time=last_processed_time,
            force_request=False,
            sound_threshold=78.0
        )
        
        if sound_url and should_process_audio:
            try:
                waveform, sr = preprocess_audio(sound_url)
                is_valid = is_valid_sound_clip(waveform, sr)
                if is_valid:
                    mel = librosa.feature.melspectrogram(y=waveform, sr=sr, n_mels=128)
                    log_mel = librosa.power_to_db(mel)
                    if log_mel.shape[1] < 173:
                        pad = 173 - log_mel.shape[1]
                        log_mel = np.pad(log_mel, ((0, 0), (0, pad)))
                    else:
                        log_mel = log_mel[:, :173]
                    sound_pred = ModelManager.predict_sound(log_mel)
            except Exception as ae:
                print(f"[Prediction API] Audio model processing failed: {ae}")

        image_pred = None
        should_process_image = should_trigger_image_capture(
            temp=temp,
            hum=humid,
            sound=sound,
            prev_temp=prev_temp,
            prev_hum=prev_hum,
            prev_sound=prev_sound,
            last_capture_time=last_capture_time,
            time_threshold_minutes=10
        )
        
        if image_url and should_process_image:
            try:
                img_array = preprocess_image(image_url, target_size=(224, 224))
                image_pred = ModelManager.predict_image(img_array)
            except Exception as ie:
                print(f"[Prediction API] Image model processing failed: {ie}")

        # 6. Environmental Base Prediction (XGBoost)
        sensor_pred = ModelManager.predict_sensor([temp, humid, ammonia])
        
        # 7. Decision Fusion
        from ml.fusion.decision_engine import fuse_decisions
        fused_disease, confidence, prob_map = fuse_decisions(temp, humid, ammonia, sensor_pred, sound_pred, image_pred)
        
        if fused_disease == "Healthy":
            risk_level = "LOW"
        elif confidence >= 0.70:
            risk_level = "HIGH"
        else:
            risk_level = "MEDIUM"
            
        if ammonia >= 25.0:
            risk_level = "HIGH"
            
        # 8. Dynamic Recommendations
        if ammonia >= 25.0:
            recommendation = f"CRITICAL AMMONIA ALERT: Air quality is hazardous ({ammonia} ppm). Exhaust fans must run at 100% capacity."
        elif fused_disease == "Newcastle":
            recommendation = f"NEWCASTLE WARNING: High risk of Newcastle ({int(confidence * 100)}% confidence). Ensure quarantine and inspect flock."
        elif fused_disease == "Avian Influenza":
            recommendation = f"AVIAN INFLUENZA WARNING: High risk of Avian Influenza ({int(confidence * 100)}% confidence). Check for visual lethargy."
        elif fused_disease == "Coccidiosis":
            recommendation = f"COCCIDIOSIS DETECTED: Risk of digestive infection ({int(confidence * 100)}% confidence). Keep litter dry."
        elif fused_disease == "Fowlpox":
            recommendation = f"FOWLPOX ALERT: Risk of Fowlpox ({int(confidence * 100)}% confidence). Check comb/wattle lesions."
        elif fused_disease == "Infectious Bronchitis":
            recommendation = f"INFECTIOUS BRONCHITIS ALERT: Risk of IB respiratory infection ({int(confidence * 100)}% confidence). Gasping vocalizations possible."
        else:
            recommendation = "LOW RISK: Environment parameters are within stable ranges."

        # 9. Handle Normal vs. Abnormal disease grouping & media retention
        is_abnormal = (risk_level in ["MEDIUM", "HIGH"]) and (fused_disease != "Healthy")
        
        event_id = None
        
        from data.supabase_client import get_active_batch
        active_batch = get_active_batch(farm_id)
        batch_id = active_batch.get("id") if active_batch else None
        
        if is_abnormal and supabase:
            from data.supabase_client import find_matching_active_event, create_disease_event, update_disease_event
            matching_event = find_matching_active_event(device_id, fused_disease)
            
            if matching_event:
                updated_event = update_disease_event(
                    event=matching_event,
                    new_confidence=confidence,
                    new_image_url=image_url,
                    new_sound_url=sound_url
                )
                if updated_event:
                    event_id = updated_event["id"]
            else:
                new_event = create_disease_event(
                    device_id=device_id,
                    batch_id=batch_id,
                    disease=fused_disease,
                    risk_level=risk_level,
                    confidence=confidence,
                    image_url=image_url,
                    sound_url=sound_url
                )
                if new_event:
                    event_id = new_event["id"]

        # 10. Log to Supabase PostgreSQL database
        from data.supabase_client import save_prediction
        saved_pred_id = None
        pred_res = save_prediction(
            device_id=device_id,
            disease=fused_disease,
            risk_level=risk_level,
            confidence=confidence,
            recommendation=recommendation,
            farm_id=farm_id,
            telemetry_id=telemetry_id,
            event_id=event_id
        )
        if pred_res.get("status") == "success" and pred_res.get("data"):
            saved_pred_id = pred_res["data"][0].get("id")

        # 11. Create alert for abnormal predictions
        if is_abnormal and saved_pred_id and supabase:
            from data.supabase_client import create_alert
            alert_title = f"Disease Alert: {fused_disease} ({risk_level})"
            create_alert(
                batch_id=batch_id,
                device_id=device_id,
                prediction_id=saved_pred_id,
                title=alert_title,
                description=recommendation,
                severity=risk_level
            )

        return jsonify({
            'riskLevel': risk_level,
            'confidence': confidence,
            'recommendation': recommendation
        })
    except Exception as e:
        print(f"[Prediction API] Disease prediction error: {e}")
        return jsonify({'error': str(e)}), 500

@prediction_bp.route('/api/v1/predict-sound', methods=['POST'])
def predict_sound_endpoint():
    try:
        if 'file' not in request.files:
            return jsonify({'error': 'No file part in the request'}), 400
        
        file = request.files['file']
        if file.filename == '':
            return jsonify({'error': 'No selected file'}), 400
        
        if file:
            # Create a temporary file to save the uploaded audio
            with tempfile.NamedTemporaryFile(delete=False, suffix=".wav") as temp_file:
                temp_path = temp_file.name
                file.save(temp_path)

            # Perform prediction
            log_mel = extract_spectrogram(temp_path)
            if log_mel is None:
                result = {
                    "prediction": "None",
                    "confidence": 0.0,
                    "probabilities": {"Healthy": 0.0, "Sick": 0.0, "None": 1.0},
                    "status": "error",
                    "message": "Failed to extract spectrogram from audio file."
                }
            else:
                result = ModelManager.predict_sound(log_mel)

            # Cleanup
            try:
                os.remove(temp_path)
            except Exception as e:
                print(f"[Prediction API] Error deleting temp file {temp_path}: {e}")

            if result.get("status") == "error":
                return jsonify({'error': result.get("message")}), 500

            return jsonify(result)
    except Exception as e:
        print(f"[Prediction API] Sound prediction error: {e}")
        return jsonify({'error': str(e)}), 500

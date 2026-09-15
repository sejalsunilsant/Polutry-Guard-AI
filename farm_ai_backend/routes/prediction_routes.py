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

@prediction_bp.route('/predictions/latest', methods=['GET'])
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

@prediction_bp.route('/predict-disease', methods=['POST'])
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
        from data.supabase_client import get_active_batch, get_mortality_records
        active_batch = get_active_batch(farm_id)
        batch_id = active_batch.get("id") if active_batch else None
        recent_deaths = 0
        if batch_id:
            mort_records = get_mortality_records(batch_id)
            recent_deaths = sum(int(r.get("death_count", 0) or r.get("deathCount", 0)) for r in mort_records)

        from ml.fusion.decision_engine import fuse_decisions
        fused_disease, confidence, prob_map = fuse_decisions(temp, humid, ammonia, sensor_pred, sound_pred, image_pred, recent_deaths=recent_deaths)
        
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
        elif fused_disease == "Infectious Coryza":
            recommendation = f"INFECTIOUS CORYZA ALERT: Elevated risk of acute respiratory infection ({int(confidence * 100)}% confidence). Consult vet for antimicrobial therapy, isolate birds with facial swelling, and sanitize water lines."
        elif fused_disease == "Fowlpox":
            recommendation = f"FOWLPOX ALERT: High risk of viral Fowlpox ({int(confidence * 100)}% confidence). Isolate symptomatic birds, control mosquito vectors, and apply topical antiseptics to lesions."
        elif fused_disease == "Newcastle":
            recommendation = f"NEWCASTLE WARNING: High risk of Newcastle ({int(confidence * 100)}% confidence). Ensure quarantine and inspect flock."
        elif fused_disease == "Avian Influenza":
            recommendation = f"AVIAN INFLUENZA WARNING: High risk of Avian Influenza ({int(confidence * 100)}% confidence). Check for visual lethargy."
        elif fused_disease == "Coccidiosis":
            recommendation = f"COCCIDIOSIS DETECTED: Risk of digestive infection ({int(confidence * 100)}% confidence). Keep litter dry."
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

@prediction_bp.route('/predict-sound', methods=['POST'])
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


def upload_media_bytes(file_bytes, bucket_name, folder, file_name):
    """
    Uploads raw file bytes to Supabase Storage and returns the public URL.
    """
    from data.supabase_client import supabase
    if not supabase:
        print(f"[Supabase Storage Fallback] Mock upload for file bytes: {file_name}")
        return f"https://mock-supabase.co/storage/v1/object/public/{bucket_name}/{folder}/{file_name}"
    try:
        # Ensure bucket exists
        try:
            supabase.storage.get_bucket(bucket_name)
        except Exception:
            try:
                supabase.storage.create_bucket(bucket_name, {"public": True})
            except Exception as ce:
                print(f"[Supabase Storage Warning] Could not create bucket: {ce}")
                
        path_in_bucket = f"{folder}/{file_name}"
        supabase.storage.from_(bucket_name).upload(
            path=path_in_bucket,
            file=file_bytes,
            file_options={"cache-control": "3600", "upsert": "true"}
        )
        public_url = supabase.storage.from_(bucket_name).get_public_url(path_in_bucket)
        return public_url
    except Exception as e:
        print(f"[Supabase Storage Error] Failed to upload media: {e}")
        return None


@prediction_bp.route('/guardian/predict', methods=['POST'])
def guardian_predict_endpoint():
    try:
        import time
        import datetime
        
        device_id = request.form.get('deviceId') or request.form.get('device_id')
        farm_id = request.form.get('farmId') or request.form.get('farm_id')
        
        if not device_id or not farm_id:
            return jsonify({
                'status': 'error',
                'message': 'deviceId and farmId are required form parameters'
            }), 400

        image_file = request.files.get('image')
        sound_file = request.files.get('sound')

        if not image_file and not sound_file:
            return jsonify({
                'status': 'error',
                'message': 'At least one input (image or sound) is required'
            }), 400

        # Upload and Predict Image
        image_url = None
        image_pred = None
        if image_file:
            # Read bytes for storage upload
            image_bytes = image_file.read()
            image_file.seek(0)
            
            # Save and run prediction model
            img_array = preprocess_image(image_file, target_size=(224, 224))
            image_pred = ModelManager.predict_image(img_array)
            
            # Upload to Supabase Storage
            ts_str = int(time.time())
            filename = f"guardian_{device_id}_{ts_str}.jpg"
            image_url = upload_media_bytes(image_bytes, "poultry-media", "images", filename)

        # Upload and Predict Sound
        sound_url = None
        sound_pred = None
        if sound_file:
            # Read bytes for storage upload
            sound_bytes = sound_file.read()
            sound_file.seek(0)
            
            # Save file to a temporary location for librosa processing
            with tempfile.NamedTemporaryFile(delete=False, suffix=".wav") as temp_file:
                temp_path = temp_file.name
                sound_file.save(temp_path)
            
            try:
                waveform, sr = preprocess_audio(temp_path)
                if is_valid_sound_clip(waveform, sr):
                    mel = librosa.feature.melspectrogram(y=waveform, sr=sr, n_mels=128)
                    log_mel = librosa.power_to_db(mel)
                    if log_mel.shape[1] < 173:
                        pad = 173 - log_mel.shape[1]
                        log_mel = np.pad(log_mel, ((0, 0), (0, pad)))
                    else:
                        log_mel = log_mel[:, :173]
                    sound_pred = ModelManager.predict_sound(log_mel)
            except Exception as se:
                print(f"[Prediction API] Sound preprocessing failed: {se}")
            finally:
                # Cleanup temp sound file
                try:
                    os.remove(temp_path)
                except Exception:
                    pass
            
            # Upload to Supabase Storage
            ts_str = int(time.time())
            filename = f"guardian_{device_id}_{ts_str}.wav"
            sound_url = upload_media_bytes(sound_bytes, "poultry-media", "audio", filename)

        # Retrieve Telemetry Environment Context (Prior)
        from data.supabase_client import get_last_telemetry, save_telemetry
        telemetry_record = get_last_telemetry(device_id)
        if telemetry_record:
            temp = float(telemetry_record.get('temperature', 24.0))
            humid = float(telemetry_record.get('humidity', 60.0))
            ammonia = float(telemetry_record.get('ammonia', 10.0))
            sound_level = float(telemetry_record.get('sound_level', 50.0))
        else:
            temp = 24.0
            humid = 60.0
            ammonia = 10.0
            sound_level = 50.0

        # Save sensor telemetry to database referencing the newly uploaded image/sound URLs
        tel_res = save_telemetry(
            device_id=device_id,
            temperature=temp,
            humidity=humid,
            ammonia=ammonia,
            sound_level=sound_level,
            farm_id=farm_id,
            sound_url=sound_url,
            image_url=image_url
        )
        telemetry_id = None
        if tel_res.get("status") == "success" and tel_res.get("data"):
            telemetry_id = tel_res["data"][0].get("id")

        # Get Environmental Sensor Prior Prediction
        sensor_pred = ModelManager.predict_sensor([temp, humid, ammonia])

        # Fuse visual, acoustic, and environmental sensors
        from data.supabase_client import get_active_batch, get_mortality_records
        active_batch = get_active_batch(farm_id)
        batch_id = active_batch.get("id") if active_batch else None
        recent_deaths = 0
        if batch_id:
            mort_records = get_mortality_records(batch_id)
            recent_deaths = sum(int(r.get("death_count", 0) or r.get("deathCount", 0)) for r in mort_records)

        from ml.fusion.decision_engine import fuse_decisions
        fused_disease, confidence, prob_map = fuse_decisions(
            temp=temp,
            hum=humid,
            ammonia=ammonia,
            sensor_pred=sensor_pred,
            sound_pred=sound_pred,
            image_pred=image_pred,
            recent_deaths=recent_deaths
        )

        # Map risk level
        if fused_disease == "Healthy":
            risk_level = "LOW"
        elif confidence >= 0.70:
            risk_level = "HIGH"
        else:
            risk_level = "MEDIUM"
            
        if ammonia >= 25.0:
            risk_level = "HIGH"

        # Generate biosecurity recommendations
        if ammonia >= 25.0:
            recommendation = f"CRITICAL AMMONIA ALERT: Air quality is hazardous ({ammonia} ppm). Exhaust ventilation fans must run at 100% capacity to flush the house and prevent permanent respiratory tract burns."
        elif fused_disease == "Infectious Coryza":
            recommendation = f"INFECTIOUS CORYZA ALERT: Elevated risk of acute respiratory bacterial infection ({int(confidence * 100)}% confidence). Acoustic gasping/rales and facial swelling detected. Consult flock veterinarian for antimicrobial therapy, separate symptomatic birds, and sanitize water lines."
        elif fused_disease == "Fowlpox":
            recommendation = f"FOWLPOX ALERT: High risk of Fowlpox infection ({int(confidence * 100)}% confidence). Visual check reveals possible comb/wattle lesions. Isolate symptomatic birds, control mosquitos, and apply antiseptic."
        elif fused_disease == "Newcastle":
            recommendation = f"NEWCASTLE DISEASE WARNING: High risk of Newcastle infection ({int(confidence * 100)}% confidence). Acoustic/visual metrics show gasping and abnormal posture. Quarantine affected birds and contact your vet immediately."
        elif fused_disease == "Avian Influenza":
            recommendation = f"AVIAN INFLUENZA WARNING: High risk of Avian Influenza ({int(confidence * 100)}% confidence). Visual monitors show extreme lethargy and abnormal appearance. Alert biosecurity officers and isolate the flock."
        elif fused_disease == "Coccidiosis":
            recommendation = f"COCCIDIOSIS DETECTED: Elevated risk of digestive infection ({int(confidence * 100)}% confidence). Visual cues show huddling/lying. Ensure composted litter is dry, feed is dry, and treat with coccidiostats."
        elif fused_disease == "Infectious Bronchitis":
            recommendation = f"INFECTIOUS BRONCHITIS ALERT: High risk of IB respiratory infection ({int(confidence * 100)}% confidence). Detected heavy coughing/gasping. Stabilize shed temperature and mist disinfectant to suppress aerosol transmission."
        else:
            recommendation = "LOW DISEASE RISK: Environment parameters (Temp, Humidity, Ammonia) and flock behavior (sound, visual movement) are all within ideal comfort zones."

        # Handle abnormal disease event creation
        is_abnormal = (risk_level in ["MEDIUM", "HIGH"]) and (fused_disease != "Healthy")
        event_id = None
        
        from data.supabase_client import get_active_batch, supabase
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

        # Log prediction to Supabase PostgreSQL database
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

        # Create alert for abnormal predictions
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

        timestamp_str = datetime.datetime.now(datetime.timezone.utc).isoformat()
        
        return jsonify({
            "status": "success",
            "condition": fused_disease,
            "riskLevel": risk_level,
            "confidence": float(confidence),
            "recommendation": recommendation,
            "imageUrl": image_url,
            "soundUrl": sound_url,
            "timestamp": timestamp_str
        }), 200
    except Exception as e:
        print(f"[Prediction API] Guardian prediction error: {e}")
        return jsonify({'status': 'error', 'message': str(e)}), 500


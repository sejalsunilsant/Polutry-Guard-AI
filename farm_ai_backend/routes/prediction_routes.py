import os
import tempfile
from flask import Blueprint, request, jsonify
from ml.predictor import predict_disease
from ml.manager import ModelManager
from ml.audio.preprocessing import extract_spectrogram

prediction_bp = Blueprint("prediction_bp", __name__)

@prediction_bp.route('/api/v1/predict-disease', methods=['POST'])
def predict_disease_endpoint():
    try:
        data = request.get_json() or {}
        temp = data.get('temperature', 24.0)
        humid = data.get('humidity', 60.0)
        ammonia = data.get('ammonia', 10.0)
        sound = data.get('soundLevel', 50.0)
        device_id = data.get('deviceId', 'default_device')
        farm_id = data.get('farmId', 'default_farm')

        # 1. Base prediction using XGBoost trained on Temperature and Humidity
        sensor_pred = ModelManager.predict_sensor([temp, humid, ammonia])
        
        # 2. Call the fusion engine with sensor predictor results
        from ml.fusion.decision_engine import fuse_decisions
        fused_disease, confidence, prob_map = fuse_decisions(temp, humid, ammonia, sensor_pred, None, None)
        
        # 3. Determine risk level
        if fused_disease == "Healthy":
            risk_level = "LOW"
        elif confidence >= 0.70:
            risk_level = "HIGH"
        else:
            risk_level = "MEDIUM"
            
        if ammonia >= 25.0:
            risk_level = "HIGH"
            
        # 4. Formulate recommendations
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

        # Log to Supabase PostgreSQL database
        from data.supabase_client import save_telemetry, save_prediction
        telemetry_id = None
        try:
            telemetry_res = save_telemetry(device_id, temp, humid, ammonia, sound, farm_id)
            if telemetry_res and telemetry_res.get("status") == "success" and telemetry_res.get("data"):
                telemetry_id = telemetry_res["data"][0].get("id")
        except Exception as se:
            print(f"[Supabase Logging] Telemetry insertion failed: {se}")

        save_prediction(device_id, fused_disease, risk_level, confidence, recommendation, telemetry_id, farm_id)

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

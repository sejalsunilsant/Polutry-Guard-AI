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
        xgb_result = predict_disease(temp, humid)
        
        # Default fallback values
        risk_level = "LOW"
        confidence = 0.95
        recommendation = "Environment is stable and within ideal comfort range."
        disease_name = "None"
        
        if xgb_result and xgb_result.get("status") == "success":
            predicted_class = xgb_result.get("prediction")
            confidence = xgb_result.get("confidence")
            disease_name = predicted_class if predicted_class else "None"
            
            # Map model class to risk level
            if predicted_class == "Respiratory":
                risk_level = "HIGH"
                recommendation = "ELEVATED RESPIRATORY RISK: XGBoost model predicts Respiratory incidence. Ensure ventilation rates are adequate."
            elif predicted_class == "Digestive":
                risk_level = "MEDIUM"
                recommendation = "DIGESTIVE RISK: XGBoost model predicts Digestive incidence. Keep litter dry to prevent coccidiosis spores."
            elif predicted_class == "Other":
                risk_level = "MEDIUM"
                recommendation = "MEDIUM RISK: Standard biosecurity alerts active. Inspect flock health sweeps."
            else:
                risk_level = "LOW"
                recommendation = "LOW RISK: Ideal environment conditions."
        
        # 2. Safety Overrides (Ammonia & Sound Level thresholds)
        if ammonia >= 25.0:
            risk_level = "HIGH"
            confidence = max(confidence, 0.92)
            recommendation = "HIGH DISEASE RISK: Critical Ammonia levels (>25 ppm). Turn exhaust fans to 100% and treat wet litter immediately."
        elif temp >= 30.0 and ammonia >= 18.0:
            risk_level = "HIGH"
            confidence = max(confidence, 0.90)
            recommendation = "HIGH DISEASE RISK: Combined Heat Stress and elevated Ammonia. Increase ventilation and run cooling misters."
        elif sound >= 78.0:
            risk_level = "HIGH"
            confidence = max(confidence, 0.88)
            recommendation = "HIGH EVENT RISK: High noise levels detected (>78 dB). Check for flock panic, stampede, or power failure."
        elif ammonia >= 18.0 or temp >= 28.0 or humid >= 75.0:
            # Upgrade to medium if not already high
            if risk_level != "HIGH":
                risk_level = "MEDIUM"
                confidence = max(confidence, 0.75)
                recommendation = "MEDIUM RISK: Slight sensor deviations (high temp, humidity, or gas). Increase air cycling ratios."

        # Log to Supabase PostgreSQL database
        from data.supabase_client import save_telemetry, save_prediction
        telemetry_id = None
        try:
            telemetry_res = save_telemetry(device_id, temp, humid, ammonia, sound, farm_id)
            if telemetry_res and telemetry_res.get("status") == "success" and telemetry_res.get("data"):
                telemetry_id = telemetry_res["data"][0].get("id")
        except Exception as se:
            print(f"[Supabase Logging] Telemetry insertion failed: {se}")

        save_prediction(device_id, disease_name, risk_level, confidence, recommendation, telemetry_id, farm_id)

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

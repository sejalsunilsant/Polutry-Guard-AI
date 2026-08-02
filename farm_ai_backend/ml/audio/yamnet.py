import os
import numpy as np
import onnxruntime as ort
import librosa
from .preprocessing import extract_spectrogram

LABELS = ["Healthy", "Sick", "None"]

# Resolve path relative to this script
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATHS = [
    os.path.join(BASE_DIR, "..", "model", "poultry_cnn_model.onnx"),
    os.path.join(BASE_DIR, "..", "..", "ML_Models", "poultry_cnn_model.onnx"),
    os.path.abspath(os.path.join(BASE_DIR, "..", "..", "ML_Models", "poultry_cnn_model.onnx")),
    r"D:\poltry_gard_ai_repo\Polutry-Guard-AI\farm_ai_backend\ml\model\poultry_cnn_model.onnx"
]

session = None
model_load_error = None

# Attempt to load model once at import
for path in MODEL_PATHS:
    if os.path.exists(path):
        try:
            print(f"[Sound Classifier] Loading ONNX model from: {path}")
            session = ort.InferenceSession(path, providers=['CPUExecutionProvider'])
            print("[Sound Classifier] ONNX model loaded successfully.")
            break
        except Exception as e:
            print(f"[Sound Classifier] Error loading from {path}: {e}")
            model_load_error = str(e)

if session is None:
    print("[Sound Classifier] WARNING: Could not load sound classification model. Inference will run in fallback mock mode.")


def predict_sound(file_path, confidence_threshold=0.5):
    """
    Predict health status from audio file.
    Returns:
        dict: {
            "prediction": str ("Healthy" | "Sick" | "None" | "Uncertain"),
            "confidence": float,
            "probabilities": dict mapping class -> float probability,
            "status": str ("success" | "error" | "fallback")
        }
    """
    if session is None:
        # Fallback Mock Mode when model file is missing or failed to load
        # Analyzes simple audio metrics using librosa to provide realistic fallback responses
        try:
            y, sr = librosa.load(file_path, sr=22050, duration=4)
            rms = librosa.feature.rms(y=y)
            mean_rms = float(np.mean(rms))
            
            # Simple mock heuristic: higher amplitude indicates potentially higher vocalization/stress
            if mean_rms > 0.08:
                prediction = "Sick"
                confidence = 0.72
                probs = {"Healthy": 0.18, "Sick": 0.72, "None": 0.10}
            else:
                prediction = "Healthy"
                confidence = 0.85
                probs = {"Healthy": 0.85, "Sick": 0.05, "None": 0.10}
                
            return {
                "prediction": prediction,
                "confidence": confidence,
                "probabilities": probs,
                "status": "fallback",
                "message": f"Running in acoustic fallback mode. Model load error: {model_load_error}"
            }
        except Exception as e:
            return {
                "prediction": "None",
                "confidence": 0.0,
                "probabilities": {"Healthy": 0.0, "Sick": 0.0, "None": 1.0},
                "status": "error",
                "message": f"Acoustic fallback failed: {str(e)}"
            }

    try:
        # 1. Preprocess audio
        log_mel = extract_spectrogram(file_path)
        if log_mel is None:
            return {
                "prediction": "None",
                "confidence": 0.0,
                "probabilities": {"Healthy": 0.0, "Sick": 0.0, "None": 1.0},
                "status": "error",
                "message": "Failed to extract spectrogram from audio file."
            }

        # 2. Reshape for ONNX input: (batch_size, height, width, channels) -> (1, 128, 173, 1)
        input_data = log_mel[np.newaxis, ..., np.newaxis].astype(np.float32)

        # 3. Perform Inference
        input_name = session.get_inputs()[0].name
        predictions = session.run(None, {input_name: input_data})[0]
        probabilities = predictions[0]

        # 4. Map outputs
        pred_class_idx = int(np.argmax(probabilities))
        confidence = float(probabilities[pred_class_idx])
        prediction = LABELS[pred_class_idx]

        # Apply confidence threshold
        if confidence < confidence_threshold:
            prediction = "Uncertain"

        # Format probabilities dict
        probs_dict = {LABELS[i]: float(probabilities[i]) for i in range(len(LABELS))}

        return {
            "prediction": prediction,
            "confidence": confidence,
            "probabilities": probs_dict,
            "status": "success"
        }

    except Exception as e:
        print(f"[Sound Classifier] Inference error: {e}")
        return {
            "prediction": "None",
            "confidence": 0.0,
            "probabilities": {"Healthy": 0.0, "Sick": 0.0, "None": 1.0},
            "status": "error",
            "message": f"Inference execution failed: {str(e)}"
        }

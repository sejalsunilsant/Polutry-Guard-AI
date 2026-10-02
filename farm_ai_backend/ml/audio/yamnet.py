import os
import numpy as np
import librosa
from services.audio_predictor import predict_flock_sound, get_audio_predictor

LABELS = ["Healthy", "Sick"]

def predict_sound(file_path, confidence_threshold=0.5):
    """
    Predict flock health status from audio file using AudioPredictor (YAMNet + XGBoost).
    Returns:
        dict: {
            "prediction": str ("Healthy" | "Sick" | "Uncertain"),
            "confidence": float,
            "probabilities": dict mapping class -> float probability,
            "status": str ("success" | "fallback" | "error"),
            "scope": "binary_respiratory_flock_distress"
        }
    """
    try:
        return predict_flock_sound(file_path, confidence_threshold=confidence_threshold)
    except Exception as e:
        print(f"[Sound Classifier] predict_sound failure: {e}")
        return {
            "prediction": "Healthy",
            "confidence": 0.5,
            "probabilities": {"Healthy": 0.5, "Sick": 0.5},
            "status": "error",
            "scope": "binary_respiratory_flock_distress",
            "message": str(e)
        }

import os
import numpy as np
from services.audio_predictor import predict_flock_sound, get_audio_predictor

class SoundPredictor:
    """
    Inference adapter for Sound Classification.
    Delegates to AudioPredictor (YAMNet + XGBoost) for acoustic health predictions.
    """
    _mode = "yamnet_xgboost"
    _labels = ["Healthy", "Sick"]

    @classmethod
    def load_model(cls):
        """
        Initializes and preloads the AudioPredictor model.
        """
        try:
            predictor = get_audio_predictor()
            cls._mode = "yamnet_xgboost"
            return cls._mode
        except Exception as e:
            print(f"[Sound Predictor] Model preload warning: {e}")
            cls._mode = "fallback"
            return cls._mode

    @classmethod
    def predict(cls, audio_input, confidence_threshold=0.5):
        """
        Predict flock health class from audio file path, buffer, or waveform array.
        Args:
            audio_input: File path (str), file-like object, or numpy waveform array.
            confidence_threshold (float): Minimum confidence threshold.
        Returns:
            dict: {
                "prediction": str ("Healthy" | "Sick" | "Uncertain"),
                "confidence": float,
                "probabilities": dict mapping class -> float probability,
                "symptoms": dict mapping symptom -> probability,
                "status": str ("success" | "fallback" | "error"),
                "scope": "binary_respiratory_flock_distress"
            }
        """
        try:
            result = predict_flock_sound(audio_input, confidence_threshold=confidence_threshold)
            probs = result.get("probabilities", {})
            
            # Map sound classes to acoustic symptom indicators
            symptoms = {
                "respiratory_sounds": probs.get("Sick", 0.0),
                "normal_acoustic_pattern": probs.get("Healthy", 0.0)
            }
            result["symptoms"] = symptoms
            return result
        except Exception as e:
            print(f"[Sound Predictor] Prediction error: {e}")
            return {
                "prediction": "Healthy",
                "confidence": 0.5,
                "probabilities": {"Healthy": 0.5, "Sick": 0.5},
                "symptoms": {"respiratory_sounds": 0.5, "normal_acoustic_pattern": 0.5},
                "status": "error",
                "scope": "binary_respiratory_flock_distress",
                "message": str(e)
            }

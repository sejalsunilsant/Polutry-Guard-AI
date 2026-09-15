from __future__ import annotations

import os
from typing import Any, Dict, List, Union
import numpy as np
import pandas as pd

from Apurva_Model.Environment.environmental_predictor import (
    CLASS_MAPPING,
    ENVIRONMENTAL_CLASSES,
    load_environmental_model,
    load_environmental_preprocessor,
    predict_environmental_risk,
    normalize_probabilities,
)


class SensorPredictor:
    _model = None
    _preprocessor = None
    _classes = ["Healthy", "Fowlpox", "Infectious Coryza"]

    @classmethod
    def load_model(cls):
        """
        Load XGBoost environmental model and preprocessor once (Singleton).
        """
        if cls._model is not None and cls._preprocessor is not None:
            return cls._model, cls._classes

        try:
            print("[Sensor Predictor] Loading Apurva XGBoost model and preprocessor...")
            cls._model = load_environmental_model()
            cls._preprocessor = load_environmental_preprocessor()
            cls._classes = list(ENVIRONMENTAL_CLASSES)
            print(f"[Sensor Predictor] Apurva Environmental Model loaded successfully with classes: {cls._classes}")
        except Exception as e:
            print(f"[Sensor Predictor] Error loading Apurva environmental model: {e}")
            cls._model = None
            cls._preprocessor = None
            cls._classes = ["Healthy", "Fowlpox", "Infectious Coryza"]

        return cls._model, cls._classes

    @classmethod
    def predict(cls, sensor_features: Union[List[float], Dict[str, Any], np.ndarray]) -> Dict[str, Any]:
        """
        Predict disease incidence using environmental sensor features.
        Supports:
            - list/tuple: [temperature, humidity, ammonia] or [temperature, humidity]
            - dict: {'temperature': ..., 'humidity': ..., 'ammonia': ..., 'breed': ..., 'mortality_rate': ...}

        Returns:
            dict: {
                "prediction": str ("Healthy" | "Fowlpox" | "Infectious Coryza"),
                "confidence": float,
                "probabilities": dict mapping class -> float probability,
                "status": str ("success" | "fallback" | "error")
            }
        """
        cls.load_model()

        if sensor_features is None:
            return {
                "prediction": "Healthy",
                "confidence": 0.0,
                "probabilities": {"Healthy": 1.0, "Fowlpox": 0.0, "Infectious Coryza": 0.0},
                "status": "error",
                "message": "Sensor features cannot be None",
            }

        # Parse inputs into payload dictionary
        payload: Dict[str, Any] = {}
        if isinstance(sensor_features, (list, tuple, np.ndarray)):
            if len(sensor_features) < 2:
                return {
                    "prediction": "Healthy",
                    "confidence": 0.0,
                    "probabilities": {"Healthy": 1.0, "Fowlpox": 0.0, "Infectious Coryza": 0.0},
                    "status": "error",
                    "message": "Expected at least [temperature, humidity]",
                }
            temp = float(sensor_features[0])
            hum = float(sensor_features[1])
            ammonia = float(sensor_features[2]) if len(sensor_features) > 2 else 10.0
            payload = {
                "temperature": temp,
                "humidity": hum,
                "ammonia": ammonia,
            }
        elif isinstance(sensor_features, dict):
            payload = dict(sensor_features)
        else:
            return {
                "prediction": "Healthy",
                "confidence": 0.0,
                "probabilities": {"Healthy": 1.0, "Fowlpox": 0.0, "Infectious Coryza": 0.0},
                "status": "error",
                "message": f"Unsupported sensor features type: {type(sensor_features)}",
            }

        # Execute prediction with Apurva model
        if cls._model is not None and cls._preprocessor is not None:
            try:
                res = predict_environmental_risk(payload)
                return {
                    "prediction": res["predicted_class"],
                    "confidence": float(res["confidence"]),
                    "probabilities": res["probabilities"],
                    "status": "success",
                    "model": "XGBoost (Apurva)",
                }
            except Exception as ex:
                print(f"[Sensor Predictor] Prediction error with Apurva model: {ex}. Falling back...")

        # Graceful fallback heuristic mode if model artifact is unavailable
        temp = float(payload.get("temperature", payload.get("temp", 22.0)))
        hum = float(payload.get("humidity", payload.get("hum", 60.0)))
        nh3 = float(payload.get("ammonia", 10.0))

        probs = {"Healthy": 0.85, "Fowlpox": 0.08, "Infectious Coryza": 0.07}
        if nh3 >= 25.0 or temp >= 32.0:
            probs = {"Healthy": 0.15, "Fowlpox": 0.20, "Infectious Coryza": 0.65}
        elif hum >= 80.0:
            probs = {"Healthy": 0.30, "Fowlpox": 0.45, "Infectious Coryza": 0.25}

        pred_class = max(probs, key=probs.get)
        return {
            "prediction": pred_class,
            "confidence": float(probs[pred_class]),
            "probabilities": probs,
            "status": "fallback",
            "model": "XGBoost Heuristic Fallback",
        }

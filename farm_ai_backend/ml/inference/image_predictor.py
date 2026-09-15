from __future__ import annotations

import os
from typing import Any, BinaryIO, Dict, Optional, Union
import numpy as np
from PIL import Image

from Apurva_Model.image_predictor import (
    CANONICAL_CNN_CLASSES,
    load_image_model,
    predict_image as apurva_predict_image,
)


class ImagePredictor:
    _model = None
    _idx_to_class = None
    _device = None
    _classes = ["Fowlpox", "Infectious Coryza", "Healthy"]

    @classmethod
    def load_model(cls):
        """
        Loads the Apurva ResNet18 PyTorch model once (Singleton).
        """
        if cls._model is not None:
            return "pytorch"

        try:
            print("[Image Predictor] Loading Apurva ResNet18 PyTorch model...")
            cls._model, cls._idx_to_class, cls._device = load_image_model()
            cls._classes = list(CANONICAL_CNN_CLASSES)
            print(f"[Image Predictor] Apurva ResNet18 loaded successfully on {cls._device}")
            return "pytorch"
        except Exception as e:
            print(f"[Image Predictor] Error loading Apurva ResNet18 model: {e}. Fallback enabled.")
            cls._model = None
            return "fallback"

    @classmethod
    def predict(
        cls,
        image_input: Union[str, BinaryIO, bytes, Image.Image, np.ndarray],
        confidence_threshold: float = 0.5,
    ) -> Dict[str, Any]:
        """
        Predict poultry disease from image input (array, file, bytes, PIL).

        Returns:
            dict: {
                "prediction": str ("Healthy" | "Fowlpox" | "Infectious Coryza" | "Uncertain"),
                "confidence": float,
                "probabilities": dict mapping class -> float probability,
                "symptoms": dict mapping symptom -> float probability,
                "status": str ("success" | "fallback" | "error")
            }
        """
        cls.load_model()

        if image_input is None:
            return {
                "prediction": "Healthy",
                "confidence": 0.0,
                "probabilities": {"Healthy": 1.0, "Fowlpox": 0.0, "Infectious Coryza": 0.0},
                "symptoms": {"scabby_lesions": 0.0, "facial_swelling_nasal_discharge": 0.0, "normal_posture_activity": 1.0},
                "status": "error",
                "message": "Input image cannot be None",
            }

        try:
            res = apurva_predict_image(image_input)
            pred_class = res["predicted_class"]
            confidence = float(res["confidence"])

            if confidence < confidence_threshold:
                pred_class = "Uncertain"

            return {
                "prediction": pred_class,
                "predicted_class": res["predicted_class"],
                "confidence": confidence,
                "probabilities": res["probabilities"],
                "symptoms": res.get("symptoms", {}),
                "status": "success",
                "model": "ResNet18 (Apurva)",
            }
        except Exception as ex:
            print(f"[Image Predictor] Apurva image prediction error: {ex}. Using heuristic fallback...")

        # Fallback heuristic mode
        probs = {"Healthy": 0.70, "Fowlpox": 0.15, "Infectious Coryza": 0.15}
        pred_class = max(probs, key=probs.get)
        return {
            "prediction": pred_class,
            "predicted_class": pred_class,
            "confidence": float(probs[pred_class]),
            "probabilities": probs,
            "symptoms": {
                "scabby_lesions": 0.15,
                "facial_swelling_nasal_discharge": 0.15,
                "normal_posture_activity": 0.70,
            },
            "status": "fallback",
            "model": "Image Heuristic Fallback",
        }

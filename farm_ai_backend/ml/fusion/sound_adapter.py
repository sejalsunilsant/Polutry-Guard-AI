"""
Sound Model Adapter for Multi-Modal Fusion Engine.

Adapts the output of the existing SoundPredictor (which predicts Healthy, Sick, None)
into the feature format expected by the multi-modal fusion engine (Healthy, Fowlpox, Infectious Coryza),
without modifying the existing sound classification model in any way.
"""

from __future__ import annotations

from typing import Any, Dict, Optional


class SoundAdapter:
    """
    Adapter converting acoustic classification outputs into multi-modal fusion evidence.
    """

    @staticmethod
    def adapt_for_fusion(sound_pred: Optional[Dict[str, Any]]) -> Dict[str, Any]:
        """
        Convert raw SoundPredictor response into fusion-ready disease and symptom probabilities.

        Args:
            sound_pred (dict, optional): Output from SoundPredictor.predict()

        Returns:
            dict: {
                "active": bool,
                "confidence": float,
                "disease_priors": {
                    "Healthy": float,
                    "Infectious Coryza": float,
                    "Fowlpox": float
                },
                "symptoms": {
                    "respiratory_sounds": float,
                    "normal_acoustic_pattern": float
                },
                "raw_prediction": str
            }
        """
        if not sound_pred or sound_pred.get("status") == "error":
            return {
                "active": False,
                "confidence": 0.0,
                "disease_priors": {"Healthy": 0.0, "Infectious Coryza": 0.0, "Fowlpox": 0.0},
                "symptoms": {"respiratory_sounds": 0.0, "normal_acoustic_pattern": 0.0},
                "raw_prediction": "None",
            }

        probs = sound_pred.get("probabilities") or {}
        symptoms = sound_pred.get("symptoms") or {}

        respiratory_sounds = float(symptoms.get("respiratory_sounds", probs.get("Sick", 0.0)))
        normal_sound = float(symptoms.get("normal_acoustic_pattern", probs.get("Healthy", 0.0)))
        raw_pred = str(sound_pred.get("prediction", "None"))
        conf = float(sound_pred.get("confidence", 0.0))

        # Infectious Coryza is an acute respiratory infection (coughing, gasping, sneezing)
        # Wet/diphtheritic Fowlpox also has respiratory tract lesions
        # Healthy acoustic pattern supports normal flock status
        coryza_acoustic = respiratory_sounds * 0.70
        fowlpox_acoustic = respiratory_sounds * 0.30
        healthy_acoustic = normal_sound

        total = coryza_acoustic + fowlpox_acoustic + healthy_acoustic
        if total > 0.0:
            disease_priors = {
                "Healthy": healthy_acoustic / total,
                "Infectious Coryza": coryza_acoustic / total,
                "Fowlpox": fowlpox_acoustic / total,
            }
        else:
            disease_priors = {"Healthy": 1.0, "Infectious Coryza": 0.0, "Fowlpox": 0.0}

        return {
            "active": True,
            "confidence": conf,
            "disease_priors": disease_priors,
            "symptoms": {
                "respiratory_sounds": respiratory_sounds,
                "normal_acoustic_pattern": normal_sound,
            },
            "raw_prediction": raw_pred,
        }

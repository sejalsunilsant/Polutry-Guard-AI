from __future__ import annotations

import os
from functools import lru_cache
from pathlib import Path
from typing import Any, Dict, List, Optional, Union

import joblib
import numpy as np
import pandas as pd

from Apurva_Model.preprocessor import (
    EnvironmentalInputError,
    EnvironmentalPreprocessor,
    normalize_probabilities,
    parse_environmental_input,
    DEFAULT_BREED,
    PREPROCESSOR_PATH,
)

CLASS_MAPPING = {"Fowlpox": 0, "Infectious Coryza": 1, "Healthy": 2}
INVERSE_CLASS_MAPPING = {0: "Fowlpox", 1: "Infectious Coryza", 2: "Healthy"}
ENVIRONMENTAL_CLASSES = ["Fowlpox", "Infectious Coryza", "Healthy"]

XGBOOST_MODEL_PATH = Path(__file__).resolve().parent / "poultry_environmental_xgboost.pkl"
MODEL_PATH = Path(__file__).resolve().parent / "Environmental_factorModel.pkl"


def round_floats(obj: Any, precision: int = 6) -> Any:
    """Recursively round floating point numbers in dict/list structures."""
    if isinstance(obj, float):
        return round(obj, precision)
    if isinstance(obj, dict):
        return {k: round_floats(v, precision) for k, v in obj.items()}
    if isinstance(obj, list):
        return [round_floats(x, precision) for x in obj]
    return obj


class EnvironmentalModelUnavailable(FileNotFoundError):
    """Raised when the XGBoost environmental model checkpoint is missing."""


def get_model_path() -> Path:
    """Return verified path to environmental XGBoost model."""
    if XGBOOST_MODEL_PATH.exists():
        return XGBOOST_MODEL_PATH
    if MODEL_PATH.exists():
        return MODEL_PATH
    raise EnvironmentalModelUnavailable(
        f"Environmental model not found at {XGBOOST_MODEL_PATH} or {MODEL_PATH}."
    )


@lru_cache(maxsize=1)
def load_environmental_model() -> Any:
    """Load and cache the trained XGBoost environmental model in memory."""
    model_path = get_model_path()
    return joblib.load(model_path)


# Backwards compatibility alias
load_environmental_artifact = load_environmental_model


@lru_cache(maxsize=1)
def load_environmental_preprocessor() -> EnvironmentalPreprocessor:
    """Load and cache the trained preprocessor pipeline in memory."""
    if not PREPROCESSOR_PATH.exists():
        raise FileNotFoundError(f"Preprocessor artifact not found at: {PREPROCESSOR_PATH}")
    return EnvironmentalPreprocessor.load(PREPROCESSOR_PATH)


def predict_environment(
    breed: Optional[str] = None,
    temperature: Optional[Union[float, str]] = None,
    humidity: Optional[Union[float, str]] = None,
    ammonia: Optional[Union[float, str]] = None,
    mortality_rate: Optional[Union[float, str]] = None,
    egg_production: Optional[Union[float, str]] = None,
    amount_of_feeding: Optional[Union[float, str]] = None,
    feed_consumption: Optional[Union[float, str]] = None,
    **kwargs: Any,
) -> Dict[str, Any]:
    """
    Run disease classification inference on environmental and farm factors.
    Supports either keyword arguments or unpacked dictionary payloads.

    Features:
        breed: Poultry breed ('White Leghorn', 'Rhode Island Red', 'Broiler Ross 308')
        temperature: Ambient housing temperature (°C)
        humidity: Relative humidity (%)
        ammonia: Ammonia concentration (ppm)
        mortality_rate: Flock mortality rate (%)
        egg_production: Egg production rate (%)
        amount_of_feeding / feed_consumption: Daily feed intake (g/bird/day)

    Returns:
        Multiclass probability distribution for Healthy, Fowlpox, and Infectious Coryza.
    """
    params: Dict[str, Any] = {**kwargs}
    if breed is not None:
        params["breed"] = breed
    if temperature is not None:
        params["temperature"] = temperature
    if humidity is not None:
        params["humidity"] = humidity
    if ammonia is not None:
        params["ammonia"] = ammonia
    if mortality_rate is not None:
        params["mortality_rate"] = mortality_rate
    if egg_production is not None:
        params["egg_production"] = egg_production
    if amount_of_feeding is not None:
        params["amount_of_feeding"] = amount_of_feeding
    elif feed_consumption is not None:
        params["amount_of_feeding"] = feed_consumption

    return predict_environmental_disease(params)


def predict_environmental_disease(input_data: Union[Dict[str, Any], pd.DataFrame]) -> Dict[str, Any]:
    """
    Execute inference pipeline using the pre-fitted preprocessor and trained XGBoost model.
    """
    if isinstance(input_data, dict):
        payload = input_data
    elif isinstance(input_data, pd.DataFrame):
        payload = input_data.iloc[0].to_dict()
    else:
        raise EnvironmentalInputError(f"Expected dict or DataFrame, got {type(input_data).__name__}")

    preprocessor = load_environmental_preprocessor()
    model = load_environmental_model()

    # Preprocess feature vector
    X_proc = preprocessor.transform_dict(payload)

    # Predict probabilities
    raw_probs = model.predict_proba(X_proc)[0]
    norm_probs = normalize_probabilities(raw_probs)

    prob_map = {
        cls_name: float(norm_probs[cls_idx])
        for cls_name, cls_idx in CLASS_MAPPING.items()
    }

    predicted_class = max(prob_map, key=prob_map.get)
    confidence = float(prob_map[predicted_class])

    result = {
        "model": "XGBoost",
        "predicted_class": predicted_class,
        "prediction": predicted_class,
        "confidence": confidence,
        "probabilities": {
            "Healthy": prob_map.get("Healthy", 0.0),
            "Fowlpox": prob_map.get("Fowlpox", 0.0),
            "Infectious Coryza": prob_map.get("Infectious Coryza", 0.0),
        },
        "status": "success",
    }

    return round_floats(result, precision=6)


def predict_environmental_risk(*args: Any, **kwargs: Any) -> Dict[str, Any]:
    """
    Primary API entry point for environmental risk prediction.
    Can be called either with positional dict: predict_environmental_risk(dict_payload)
    or keyword arguments: predict_environmental_risk(breed="White Leghorn", temperature=30.5, ...)
    """
    if args:
        if isinstance(args[0], dict):
            combined_dict = {**args[0], **kwargs}
            return predict_environmental_disease(combined_dict)
        elif isinstance(args[0], str) and "breed" not in kwargs:
            kwargs["breed"] = args[0]
            return predict_environment(**kwargs)

    return predict_environment(**kwargs)

from __future__ import annotations

import os
from pathlib import Path
from typing import Any, Dict, List, Optional, Union

import joblib
import numpy as np
import pandas as pd
from sklearn.base import BaseEstimator, TransformerMixin
from sklearn.impute import SimpleImputer
from sklearn.preprocessing import OneHotEncoder, StandardScaler

import sys
import types

ALL_FEATURES = [
    "Breed",
    "Temperature_C",
    "Humidity_percent",
    "Ammonia_ppm",
    "Mortality_Rate_percent",
    "Egg_Production_percent",
    "Amount_of_Feeding_g_bird_day",
]
CATEGORICAL_FEATURES = ["Breed"]
NUMERIC_FEATURES = [
    "Temperature_C",
    "Humidity_percent",
    "Ammonia_ppm",
    "Mortality_Rate_percent",
    "Egg_Production_percent",
    "Amount_of_Feeding_g_bird_day",
]
SUPPORTED_BREEDS = ["White Leghorn", "Rhode Island Red", "Broiler Ross 308"]
DEFAULT_BREED = "Broiler Ross 308"

BREED_ALIASES = {
    "white leghorn": "White Leghorn",
    "rhode island red": "Rhode Island Red",
    "broiler ross 308": "Broiler Ross 308",
    "broiler": "Broiler Ross 308",
    "layer": "White Leghorn",
}

FEATURE_ALIASES = {
    "temperature": "Temperature_C",
    "temp": "Temperature_C",
    "temperature_c": "Temperature_C",
    "humidity": "Humidity_percent",
    "hum": "Humidity_percent",
    "humidity_percent": "Humidity_percent",
    "ammonia": "Ammonia_ppm",
    "ammonia_ppm": "Ammonia_ppm",
    "mortality": "Mortality_Rate_percent",
    "mortality_rate": "Mortality_Rate_percent",
    "mortality_rate_percent": "Mortality_Rate_percent",
    "egg_production": "Egg_Production_percent",
    "egg_production_percent": "Egg_Production_percent",
    "amount_of_feeding": "Amount_of_Feeding_g_bird_day",
    "feed_consumption": "Amount_of_Feeding_g_bird_day",
    "amount_of_feeding_g_bird_day": "Amount_of_Feeding_g_bird_day",
}

PREPROCESSOR_PATH = Path(__file__).resolve().parent / "preprocessor.pkl"


class EnvironmentalInputError(ValueError):
    """Raised when environmental input features are malformed or invalid."""


def normalize_probabilities(probs: Union[np.ndarray, List[float]]) -> np.ndarray:
    """Normalize raw probabilities so they sum strictly to 1.0."""
    arr = np.asarray(probs, dtype=np.float64)
    total = np.sum(arr)
    if total <= 0:
        return np.ones_like(arr) / len(arr)
    return (arr / total).astype(np.float64)


def parse_environmental_input(input_data: Dict[str, Any]) -> pd.DataFrame:
    """
    Parse a raw dictionary from API / sensor / UI into a standardized DataFrame.
    """
    if not isinstance(input_data, dict):
        raise EnvironmentalInputError(f"Expected dict, got {type(input_data).__name__}")

    normalized: Dict[str, Any] = {}
    for k, v in input_data.items():
        key_clean = str(k).strip().lower()
        canon_key = FEATURE_ALIASES.get(key_clean, k)
        normalized[canon_key] = v

    # Handle breed
    breed_val = normalized.get("Breed", DEFAULT_BREED)
    if breed_val is None or pd.isna(breed_val):
        breed_val = DEFAULT_BREED
    breed_clean = str(breed_val).strip().lower()
    normalized["Breed"] = BREED_ALIASES.get(breed_clean, str(breed_val).strip())

    # Build single-row DataFrame
    df = pd.DataFrame([normalized])

    # Defaults for missing numeric values
    defaults = {
        "Temperature_C": 22.0,
        "Humidity_percent": 60.0,
        "Ammonia_ppm": 10.0,
        "Mortality_Rate_percent": 0.5,
        "Egg_Production_percent": 0.0 if normalized["Breed"] == "Broiler Ross 308" else 80.0,
        "Amount_of_Feeding_g_bird_day": 120.0,
    }

    for col in NUMERIC_FEATURES:
        if col not in df.columns or pd.isna(df[col].iloc[0]):
            df[col] = defaults[col]
        else:
            try:
                df[col] = pd.to_numeric(df[col])
            except Exception as e:
                raise EnvironmentalInputError(f"Could not convert feature '{col}' value '{df[col].iloc[0]}' to numeric: {e}")

    return df[ALL_FEATURES]


class EnvironmentalPreprocessor(BaseEstimator, TransformerMixin):
    """
    Reusable, production-grade preprocessor for environmental disease prediction.
    Handles categorical Breed encoding, numeric imputations, and automatic mapping.
    """

    def __init__(self, scale_numeric: bool = False):
        self.scale_numeric = scale_numeric
        self.numeric_features = list(NUMERIC_FEATURES)
        self.categorical_features = list(CATEGORICAL_FEATURES)
        self.all_features = list(ALL_FEATURES)
        self.supported_breeds = list(SUPPORTED_BREEDS)
        self.default_breed = DEFAULT_BREED

        # Imputer for numerical columns
        self.num_imputer = SimpleImputer(strategy="median")
        self.scaler = StandardScaler() if scale_numeric else None

        # Categorical encoder for Breed with fixed known categories
        self.breed_encoder = OneHotEncoder(
            categories=[self.supported_breeds],
            handle_unknown="ignore",
            sparse_output=False,
        )

        self.feature_names_out_: List[str] = []
        self.is_fitted_: bool = False

    def _normalize_breed(self, breed_val: Any) -> str:
        if breed_val is None or pd.isna(breed_val):
            return self.default_breed
        clean = str(breed_val).strip().lower()
        return BREED_ALIASES.get(clean, str(breed_val).strip())

    def _prepare_df(self, X: Union[pd.DataFrame, Dict[str, Any], List[Dict[str, Any]]]) -> pd.DataFrame:
        if isinstance(X, dict):
            return parse_environmental_input(X)
        elif isinstance(X, list):
            return pd.concat([parse_environmental_input(item) for item in X], ignore_index=True)
        elif isinstance(X, pd.DataFrame):
            df = X.copy()
            rename_map = {}
            for col in df.columns:
                lower = str(col).strip().lower()
                if lower in FEATURE_ALIASES:
                    rename_map[col] = FEATURE_ALIASES[lower]
            if rename_map:
                df = df.rename(columns=rename_map)

            if "Breed" not in df.columns:
                df["Breed"] = self.default_breed
            else:
                df["Breed"] = df["Breed"].apply(self._normalize_breed)

            for num_col in self.numeric_features:
                if num_col not in df.columns:
                    df[num_col] = 0.0
                else:
                    df[num_col] = pd.to_numeric(df[num_col], errors="coerce")

            return df[self.all_features]
        else:
            raise EnvironmentalInputError(f"Unsupported input type for preprocessor: {type(X)}")

    def fit(self, X: Union[pd.DataFrame, Dict[str, Any]], y: Optional[Any] = None) -> "EnvironmentalPreprocessor":
        df = self._prepare_df(X)

        self.num_imputer.fit(df[self.numeric_features])
        if self.scaler is not None:
            imputed_num = self.num_imputer.transform(df[self.numeric_features])
            self.scaler.fit(imputed_num)

        breed_reshaped = df[["Breed"]].to_numpy()
        self.breed_encoder.fit(breed_reshaped)

        encoded_breed_cols = [f"Breed_{b}" for b in self.supported_breeds]
        self.feature_names_out_ = encoded_breed_cols + self.numeric_features
        self.is_fitted_ = True
        return self

    def transform(self, X: Union[pd.DataFrame, Dict[str, Any], List[Dict[str, Any]]]) -> np.ndarray:
        if not self.is_fitted_:
            raise RuntimeError("EnvironmentalPreprocessor must be fitted before transforming data.")

        df = self._prepare_df(X)

        breed_reshaped = df[["Breed"]].to_numpy()
        breed_encoded = self.breed_encoder.transform(breed_reshaped)

        num_imputed = self.num_imputer.transform(df[self.numeric_features])
        if self.scaler is not None:
            num_processed = self.scaler.transform(num_imputed)
        else:
            num_processed = num_imputed

        X_out = np.hstack([breed_encoded, num_processed]).astype(np.float32)
        return X_out

    def transform_dict(self, data_dict: Dict[str, Any]) -> np.ndarray:
        """Helper to transform a single sensor/API dictionary into a 2D feature array."""
        return self.transform(data_dict)

    def get_feature_names_out(self, input_features: Optional[List[str]] = None) -> List[str]:
        return list(self.feature_names_out_)

    def save(self, filepath: Union[str, Path] = PREPROCESSOR_PATH) -> None:
        os.makedirs(os.path.dirname(filepath), exist_ok=True)
        joblib.dump(self, filepath)

    @classmethod
    def load(cls, filepath: Union[str, Path] = PREPROCESSOR_PATH) -> "EnvironmentalPreprocessor":
        _register_unpickle_aliases()
        if not os.path.exists(filepath):
            raise FileNotFoundError(f"Preprocessor artifact not found at: {filepath}")
        return joblib.load(filepath)


def _register_unpickle_aliases():
    """Register dummy module paths in sys.modules so legacy pickle loads without Poultry_Guard_ML."""
    this_module = sys.modules[__name__]
    for mod_name in [
        "Poultry_Guard_ML",
        "Poultry_Guard_ML.environmental",
        "Poultry_Guard_ML.environmental.preprocessing",
    ]:
        if mod_name not in sys.modules:
            sys.modules[mod_name] = types.ModuleType(mod_name)
    sys.modules["Poultry_Guard_ML.environmental.preprocessing.preprocessor"] = this_module


_register_unpickle_aliases()

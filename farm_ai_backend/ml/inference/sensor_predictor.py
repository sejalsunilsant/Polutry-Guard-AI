import os
import json
import numpy as np
import pandas as pd
from xgboost import XGBClassifier

class SensorPredictor:
    _model = None
    _classes = None

    @classmethod
    def load_model(cls):
        """
        Load XGBoost model and disease classes once (Singleton).
        """
        if cls._model is not None:
            return cls._model, cls._classes
            
        base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        model_path = os.path.join(base_dir, "model", "xgboost_model.json")
        classes_path = os.path.join(base_dir, "model", "disease_classes.json")
        
        if os.path.exists(model_path) and os.path.exists(classes_path):
            try:
                print(f"[Sensor Predictor] Loading XGBoost model from: {model_path}")
                cls._model = XGBClassifier()
                cls._model.load_model(model_path)
                
                with open(classes_path, "r", encoding="utf-8") as f:
                    cls._classes = json.load(f)
                    
                print(f"[Sensor Predictor] Model and classes ({cls._classes}) loaded successfully.")
            except Exception as e:
                print(f"[Sensor Predictor] Error loading model files: {e}")
                cls._model = None
                cls._classes = None
        else:
            print(f"[Sensor Predictor] WARNING: Model files not found at {model_path}. Fallback mock mode enabled.")
            cls._model = None
            cls._classes = ["None", "Respiratory", "Digestive"]
            
        return cls._model, cls._classes

    @classmethod
    def predict(cls, sensor_features):
        """
        Predict disease incidence using environmental sensor features.
        Args:
            sensor_features (list): Preprocessed list [temperature, humidity, ammonia]
        Returns:
            dict: {
                "prediction": str ("None" | "Respiratory" | "Digestive"),
                "confidence": float,
                "probabilities": dict mapping class -> float probability,
                "status": str ("success" | "fallback" | "error")
            }
        """
        cls.load_model()
        
        if not sensor_features or len(sensor_features) < 2:
            return {
                "prediction": "None",
                "confidence": 0.0,
                "probabilities": {},
                "status": "error",
                "message": "Invalid sensor features array. Expected [temperature, humidity, ammonia]"
            }
            
        temp = float(sensor_features[0])
        hum = float(sensor_features[1])
        
        # XGBoost model expects humidity as a fraction [0.0, 1.0] matching training distribution
        if hum > 1.0:
            hum_fraction = hum / 100.0
        else:
            hum_fraction = hum
            
        if cls._model is None:
            # Fallback mock mode: deterministic calculations based on stress thresholds
            # High ammonia or temperature stress raises risk
            probabilities = {c: 0.05 for c in cls._classes}
            if temp >= 30.0:
                probabilities["Respiratory"] = 0.65
                probabilities["None"] = 0.25
                probabilities["Digestive"] = 0.10
            elif hum_fraction >= 0.75:
                probabilities["Digestive"] = 0.60
                probabilities["None"] = 0.25
                probabilities["Respiratory"] = 0.15
            else:
                probabilities["None"] = 0.90
                probabilities["Respiratory"] = 0.05
                probabilities["Digestive"] = 0.05
                
            pred_class = max(probabilities, key=probabilities.get)
            return {
                "prediction": pred_class,
                "confidence": float(probabilities[pred_class]),
                "probabilities": probabilities,
                "status": "fallback"
            }

        try:
            # Prepare feature DataFrame matching training columns
            df_features = pd.DataFrame(
                [[temp, hum_fraction]], 
                columns=["Temperature", "Humidity"]
            )
            
            # Predict probabilities
            probabilities = cls._model.predict_proba(df_features)[0]
            pred_idx = int(np.argmax(probabilities))
            
            prediction = cls._classes[pred_idx]
            confidence = float(probabilities[pred_idx])
            
            probs_dict = {cls._classes[i]: float(probabilities[i]) for i in range(len(cls._classes))}
            
            return {
                "prediction": prediction,
                "confidence": confidence,
                "probabilities": probs_dict,
                "status": "success"
            }
        except Exception as e:
            print(f"[Sensor Predictor] Prediction error: {e}")
            return {
                "prediction": "None",
                "confidence": 0.0,
                "probabilities": {},
                "status": "error",
                "message": f"Inference execution failed: {str(e)}"
            }

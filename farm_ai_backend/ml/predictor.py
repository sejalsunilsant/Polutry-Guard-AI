import os
import json
import numpy as np
import pandas as pd
from xgboost import XGBClassifier

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(BASE_DIR, "model", "xgboost_model.json")
CLASSES_PATH = os.path.join(BASE_DIR, "model", "disease_classes.json")

model = None
classes = None

def load_model():
    global model, classes
    if model is not None:
        return model, classes
        
    if os.path.exists(MODEL_PATH) and os.path.exists(CLASSES_PATH):
        try:
            print(f"[ML Predictor] Loading XGBoost model from: {MODEL_PATH}")
            model = XGBClassifier()
            model.load_model(MODEL_PATH)
            
            with open(CLASSES_PATH, "r", encoding="utf-8") as f:
                classes = json.load(f)
                
            print(f"[ML Predictor] Model and classes ({classes}) loaded successfully.")
        except Exception as e:
            print(f"[ML Predictor] Error loading model: {e}")
            model = None
            classes = None
    else:
        print(f"[ML Predictor] Warning: Model files not found at {MODEL_PATH}")
        
    return model, classes

# Initialize on import
load_model()

def predict_disease(temperature, humidity):
    """
    Perform disease prediction using XGBoost.
    humidity is passed as a percentage (e.g. 61.5) and is converted to a fraction (e.g. 0.615) to match training distribution.
    """
    global model, classes
    if model is None or classes is None:
        # Load again if it wasn't loaded
        load_model()
        if model is None or classes is None:
            return None
            
    try:
        # Convert humidity percentage to fraction
        humidity_fraction = humidity / 100.0
        
        # Prepare feature DataFrame matching training columns
        df_features = pd.DataFrame(
            [[temperature, humidity_fraction]], 
            columns=["Temperature", "Humidity"]
        )
        
        # Predict probabilities
        probabilities = model.predict_proba(df_features)[0]
        pred_idx = int(np.argmax(probabilities))
        
        prediction = classes[pred_idx]
        confidence = float(probabilities[pred_idx])
        
        # Format mapping dict
        probs_dict = {classes[i]: float(probabilities[i]) for i in range(len(classes))}
        
        return {
            "prediction": prediction,
            "confidence": confidence,
            "probabilities": probs_dict,
            "status": "success"
        }
    except Exception as e:
        print(f"[ML Predictor] Inference failed: {e}")
        return None

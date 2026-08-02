import pandas as pd
import numpy as np
from xgboost import XGBClassifier
from sklearn.preprocessing import LabelEncoder
import json
import os

def train():
    data_path = r"D:\poltry_gard_ai_repo\Polutry-Guard-AI\poultry_farm_dataset.xlsx"
    if not os.path.exists(data_path):
        print(f"Error: Dataset not found at {data_path}")
        return

    df = pd.read_excel(data_path)
    
    # Preprocess
    target = "Disease_Incidence"
    df[target] = df[target].fillna("None")
    
    X = df[["Temperature", "Humidity"]].copy()
    y_raw = df[target].values
    
    le = LabelEncoder()
    y = le.fit_transform(y_raw)
    
    classes = le.classes_.tolist()
    print("Classes trained:", classes)
    
    # Model
    model = XGBClassifier(
        n_estimators=200,
        max_depth=4,
        learning_rate=0.1,
        objective="multi:softprob",
        num_class=len(classes),
        eval_metric="mlogloss",
        random_state=42
    )
    
    print("Fitting XGBoost model...")
    model.fit(X, y)
    
    # Save model and class mapping
    models_dir = r"D:\poltry_gard_ai_repo\Polutry-Guard-AI\farm_ai_backend\ml\model"
    os.makedirs(models_dir, exist_ok=True)
    
    model_path = os.path.join(models_dir, "xgboost_model.json")
    classes_path = os.path.join(models_dir, "disease_classes.json")
    
    model.save_model(model_path)
    with open(classes_path, 'w', encoding='utf-8') as f:
        json.dump(classes, f)
        
    print(f"Model saved to {model_path}")
    print(f"Classes saved to {classes_path}")

if __name__ == "__main__":
    train()

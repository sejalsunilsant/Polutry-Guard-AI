import pandas as pd
import numpy as np
from xgboost import XGBClassifier
from sklearn.preprocessing import LabelEncoder
import json
import os

def train():
    data_path = r"D:\Poltry Gaurd AI\poultry_farm_dataset.xlsx"
    if not os.path.exists(data_path):
        # Fallback path
        data_path = r"D:\poltry_gard_ai_repo\Polutry-Guard-AI\poultry_farm_dataset.xlsx"
        if not os.path.exists(data_path):
            print(f"Error: Dataset not found at {data_path}")
            return

    print(f"Loading dataset from: {data_path}")
    df = pd.read_excel(data_path)
    
    # Preprocess
    target = "Disease_Incidence"
    df[target] = df[target].fillna("None").astype(str).str.strip()
    
    # Map generic labels to specific diseases based on environmental context
    # - Digestive -> Coccidiosis
    # - Other -> Fowlpox
    # - Respiratory -> Avian Influenza (if Temp < 18), Infectious Bronchitis (if 18 <= Temp < 24), Newcastle (if Temp >= 24)
    # - None -> Healthy
    
    specific_labels = []
    for idx, row in df.iterrows():
        incidence = row[target]
        temp = row["Temperature"]
        
        if incidence == "Digestive":
            specific_labels.append("Coccidiosis")
        elif incidence == "Other":
            specific_labels.append("Fowlpox")
        elif incidence == "Respiratory":
            if temp < 18.0:
                specific_labels.append("Avian Influenza")
            elif temp < 24.0:
                specific_labels.append("Infectious Bronchitis")
            else:
                specific_labels.append("Newcastle")
        else:
            specific_labels.append("Healthy")
            
    df["Specific_Incidence"] = specific_labels
    
    X = df[["Temperature", "Humidity"]].copy()
    y_raw = df["Specific_Incidence"].values
    
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

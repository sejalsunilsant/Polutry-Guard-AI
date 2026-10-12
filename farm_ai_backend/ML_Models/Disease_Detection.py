import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler, LabelEncoder
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report, confusion_matrix

# Load data
df = pd.read_excel("D:/poltry_gard_ai_repo/poultry_farm_dataset.xlsx")

# Target column
target = "Disease_Incidence"

# Encode target (multi-class)
target_encoder = LabelEncoder()
df[target] = target_encoder.fit_transform(df[target])

# Encode Weather
if "Weather_Condition" in df.columns:
    weather_encoder = LabelEncoder()
    df["Weather_Condition"] = weather_encoder.fit_transform(df["Weather_Condition"])

# Split
X = df.drop(columns=[target])
y = df[target]

X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=42, stratify=y
)

# Scale
scaler = StandardScaler()
X_train = scaler.fit_transform(X_train)
X_test = scaler.transform(X_test)

# Model (handle imbalance)
model = RandomForestClassifier(
    n_estimators=200,
    class_weight="balanced",
    random_state=42
)

model.fit(X_train, y_train)

# Predict
y_pred = model.predict(X_test)

# Evaluation
print(classification_report(
    y_test, y_pred,
    target_names=target_encoder.classes_
))

print(confusion_matrix(y_test, y_pred))
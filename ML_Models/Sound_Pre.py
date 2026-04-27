import os
import numpy as np
import librosa
import tensorflow_hub as hub
import tensorflow as tf

from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report
from sklearn.metrics import confusion_matrix


##############################################
# Load pretrained YAMNet
##############################################

print("Loading YAMNet...")

yamnet = hub.load(
"https://tfhub.dev/google/yamnet/1"
)



##############################################
# Extract embeddings
#Training...
#               precision    recall  f1-score   support

#      Healthy       0.86      0.79      0.83       400
#         Sick       0.81      0.87      0.84       400

#     accuracy                           0.83       800
#    macro avg       0.83      0.83      0.83       800
# weighted avg       0.83      0.83      0.83       800

# [[317  83]
#  [ 51 349]]
# 0.9088750000000001
##############################################

def extract_embedding(file_path):

    try:
        # YAMNet expects 16kHz
        audio,sr = librosa.load(
            file_path,
            sr=16000,
            duration=5
        )


        # convert to tensor
        waveform=tf.convert_to_tensor(
            audio,
            dtype=tf.float32
        )


        scores,embeddings,spectrogram = yamnet(
            waveform
        )


        # average embeddings across frames
        features=np.mean(
            embeddings.numpy(),
            axis=0
        )

        return features


    except Exception as e:
        print(
        "Skipped:",
        file_path,
        e
        )
        return None



##############################################
# Load Dataset
##############################################

def load_data(base_path):

    X=[]
    y=[]


    labels={
        "Healthy":0,
        "Sick":1
    }


    for label,val in labels.items():

        folder=os.path.join(
            base_path,
            label
        )

        files=[
            f for f in os.listdir(folder)
            if f.endswith(".wav")
        ]


        print(
        f"{label}: {len(files)}"
        )


        # optional debug limit
        # files=files[:500]


        for i,file in enumerate(files):

            print(
            f"{label} {i+1}/{len(files)}"
            )

            path=os.path.join(
                folder,
                file
            )


            feat=extract_embedding(
                path
            )


            if feat is not None:
                X.append(feat)
                y.append(val)



    return np.array(X),np.array(y)



##############################################
# MAIN
##############################################

dataset_path = r"D:\Poltry Gaurd AI\dataset\archive\SmartEars A Practical Framework for Poultry Respiratory Monitoring via Spectrogram-Based Audio Classification and AI-Assisted Labeling"


print("Loading audio...")

X,y=load_data(
dataset_path
)


print(
"Feature shape:",
X.shape
)


##############################################
# Split
##############################################

X_train,X_test,y_train,y_test = train_test_split(
X,
y,
test_size=.2,
random_state=42,
stratify=y
)



##############################################
# Classifier on embeddings
##############################################
from xgboost import XGBClassifier

model = XGBClassifier(
    n_estimators=500,
    max_depth=8,
    learning_rate=0.05,
    subsample=0.8,
    colsample_bytree=0.8
)



print("Training...")

model.fit(
X_train,
y_train
)



##############################################
# Evaluate
##############################################

pred=model.predict(
X_test
)


print(
classification_report(
y_test,
pred,
target_names=[
"Healthy",
"Sick"
]
)
)


print(
confusion_matrix(
y_test,
pred
)
)

from sklearn.metrics import roc_auc_score

probs=model.predict_proba(
X_test
)[:,1]

print(
roc_auc_score(
y_test,
probs
))


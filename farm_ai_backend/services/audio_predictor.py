import os
import json
import numpy as np
import librosa
from xgboost import XGBClassifier

class AudioPredictor:
    """
    Production inference service for flock acoustic health classification.
    Binary classification: Healthy vs. Sick (Flock Respiratory Stress).
    Pipeline: 16kHz audio -> YAMNet Embeddings (1024-d) -> [Mean, Max, Std] Pooling (3072-d) -> XGBoost.
    """
    _instance = None
    _xgb_model = None
    _yamnet_model = None
    _classes_metadata = None
    _labels = ["Healthy", "Sick"]
    _initialized = False

    @classmethod
    def get_instance(cls):
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    def __init__(self):
        self._load_resources()

    def _resolve_model_paths(self):
        base_dir = os.path.dirname(os.path.abspath(__file__))
        candidate_model_paths = [
            os.path.join(base_dir, "..", "ml", "model", "sound_xgb_model.json"),
            os.path.join(base_dir, "..", "..", "ML_Models", "sound_xgb_model.json"),
            os.path.abspath(os.path.join(base_dir, "..", "ml", "model", "sound_xgb_model.json")),
            os.path.abspath(os.path.join(base_dir, "..", "..", "ML_Models", "sound_xgb_model.json"))
        ]
        candidate_class_paths = [
            os.path.join(base_dir, "..", "ml", "model", "sound_classes.json"),
            os.path.join(base_dir, "..", "..", "ML_Models", "sound_classes.json")
        ]

        model_path = next((p for p in candidate_model_paths if os.path.exists(p)), None)
        class_path = next((p for p in candidate_class_paths if os.path.exists(p)), None)
        return model_path, class_path

    def _load_resources(self):
        if self._initialized:
            return

        model_path, class_path = self._resolve_model_paths()

        # 1. Load XGBoost Model
        if model_path and os.path.exists(model_path):
            try:
                print(f"[AudioPredictor] Loading XGBoost sound model from: {model_path}")
                self._xgb_model = XGBClassifier()
                self._xgb_model.load_model(model_path)
                print("[AudioPredictor] Sound XGBoost model loaded successfully.")
            except Exception as e:
                print(f"[AudioPredictor] Error loading XGBoost model: {e}")
                self._xgb_model = None
        else:
            print(f"[AudioPredictor] Warning: sound_xgb_model.json not found in paths: {model_path}")

        # 2. Load Class Metadata
        if class_path and os.path.exists(class_path):
            try:
                with open(class_path, "r", encoding="utf-8") as f:
                    self._classes_metadata = json.load(f)
                    self._labels = self._classes_metadata.get("classes", ["Healthy", "Sick"])
            except Exception as e:
                print(f"[AudioPredictor] Error reading classes metadata: {e}")

        self._initialized = True

    def _get_yamnet(self):
        if self._yamnet_model is None:
            try:
                print("[AudioPredictor] Loading YAMNet model for inference...")
                import tensorflow_hub as hub
                self._yamnet_model = hub.load("https://tfhub.dev/google/yamnet/1")
                print("[AudioPredictor] YAMNet model loaded.")
            except Exception as e:
                print(f"[AudioPredictor] Failed to load YAMNet: {e}")
                self._yamnet_model = None
        return self._yamnet_model

    def extract_features(self, audio_source, sr=16000, duration=5.0):
        """
        Extracts 3072-dimensional YAMNet feature representation from audio.
        """
        import tensorflow as tf
        try:
            if isinstance(audio_source, str):
                if not os.path.exists(audio_source):
                    raise FileNotFoundError(f"Audio file not found: {audio_source}")
                audio, _ = librosa.load(audio_source, sr=sr, duration=duration)
            elif hasattr(audio_source, "read"): # file-like object
                audio, _ = librosa.load(audio_source, sr=sr, duration=duration)
            else: # numpy array
                audio = np.asarray(audio_source, dtype=np.float32)
                if sr != 16000:
                    audio = librosa.resample(audio, orig_sr=sr, target_sr=16000)

            # Pad or trim to target duration
            target_len = int(sr * duration)
            if len(audio) < target_len:
                audio = np.pad(audio, (0, target_len - len(audio)), mode='constant')
            else:
                audio = audio[:target_len]

            yamnet = self._get_yamnet()
            if yamnet is None:
                raise RuntimeError("YAMNet embedding model is not available.")

            waveform = tf.convert_to_tensor(audio, dtype=tf.float32)
            scores, embeddings, spectrogram = yamnet(waveform)

            emb_np = embeddings.numpy()
            mean_feat = np.mean(emb_np, axis=0)
            max_feat = np.max(emb_np, axis=0)
            std_feat = np.std(emb_np, axis=0)
            return np.concatenate([mean_feat, max_feat, std_feat])
        except Exception as e:
            print(f"[AudioPredictor] Feature extraction error: {e}")
            return None

    def predict(self, audio_source, confidence_threshold=0.5):
        """
        Predict flock health status from audio input.
        Returns:
            dict: {
                "prediction": "Healthy" | "Sick" | "Uncertain",
                "confidence": float,
                "probabilities": {"Healthy": float, "Sick": float},
                "status": "success" | "fallback" | "error",
                "scope": "binary_respiratory_flock_distress"
            }
        """
        self._load_resources()

        # If model is not loaded, fallback to acoustic intensity heuristics
        if self._xgb_model is None:
            return self._fallback_acoustic_prediction(audio_source)

        try:
            features = self.extract_features(audio_source)
            if features is None:
                return self._fallback_acoustic_prediction(audio_source)

            feature_vector = features.reshape(1, -1)
            probabilities = self._xgb_model.predict_proba(feature_vector)[0]

            pred_idx = int(np.argmax(probabilities))
            confidence = float(probabilities[pred_idx])
            prediction = self._labels[pred_idx]

            if confidence < confidence_threshold:
                prediction = "Uncertain"

            probs_dict = {
                self._labels[i]: float(probabilities[i]) for i in range(len(self._labels))
            }

            return {
                "prediction": prediction,
                "confidence": round(confidence, 4),
                "probabilities": {k: round(v, 4) for k, v in probs_dict.items()},
                "status": "success",
                "scope": "binary_respiratory_flock_distress"
            }
        except Exception as e:
            print(f"[AudioPredictor] Prediction error: {e}")
            return self._fallback_acoustic_prediction(audio_source, error_msg=str(e))

    def _fallback_acoustic_prediction(self, audio_source, error_msg=None):
        """
        Fallback when ML model / YAMNet is unavailable or failed.
        """
        try:
            if isinstance(audio_source, str) and os.path.exists(audio_source):
                y, sr = librosa.load(audio_source, sr=16000, duration=4)
            elif hasattr(audio_source, "read"):
                audio_source.seek(0)
                y, sr = librosa.load(audio_source, sr=16000, duration=4)
            else:
                y = np.asarray(audio_source, dtype=np.float32)

            rms = float(np.mean(librosa.feature.rms(y=y)))
            # Heuristic: Elevated acoustic volume indicates potential flock distress
            if rms > 0.08:
                prediction = "Sick"
                confidence = 0.70
                probs = {"Healthy": 0.30, "Sick": 0.70}
            else:
                prediction = "Healthy"
                confidence = 0.85
                probs = {"Healthy": 0.85, "Sick": 0.15}

            return {
                "prediction": prediction,
                "confidence": confidence,
                "probabilities": probs,
                "status": "fallback",
                "scope": "binary_respiratory_flock_distress",
                "message": f"Acoustic fallback executed. Note: {error_msg}" if error_msg else "Acoustic fallback executed."
            }
        except Exception as e:
            return {
                "prediction": "Healthy",
                "confidence": 0.5,
                "probabilities": {"Healthy": 0.5, "Sick": 0.5},
                "status": "error",
                "scope": "binary_respiratory_flock_distress",
                "message": f"Inference and fallback failed: {str(e)}"
            }

# Module-level convenience functions
_predictor_instance = None

def get_audio_predictor():
    global _predictor_instance
    if _predictor_instance is None:
        _predictor_instance = AudioPredictor.get_instance()
    return _predictor_instance

def predict_flock_sound(audio_source, confidence_threshold=0.5):
    return get_audio_predictor().predict(audio_source, confidence_threshold)

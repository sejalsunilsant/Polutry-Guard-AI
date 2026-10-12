from ml.inference.sensor_predictor import SensorPredictor
from ml.inference.sound_predictor import SoundPredictor
from ml.inference.image_predictor import ImagePredictor

class ModelManager:
    @staticmethod
    def initialize_all_models():
        """
        Explicitly preloads all machine learning models if pre-warming is requested.
        Each predictor also supports thread-safe on-demand lazy initialization.
        """
        print("[Model Manager] Explicitly preloading all models...")
        
        # Load Apurva Environmental model (XGBoost)
        SensorPredictor.load_model()
        
        # Load Sound model (Existing TFLite/ONNX/Fallback preserved)
        SoundPredictor.load_model()
        
        # Load Apurva Visual model (PyTorch ResNet-18)
        ImagePredictor.load_model()
        
        print("[Model Manager] All models initialized and ready in memory.")

    @staticmethod
    def predict_sensor(sensor_features):
        """
        Predict disease incidence using pre-loaded XGBoost model.
        Args:
            sensor_features (list): Preprocessed list [temp, hum, ammonia]
        """
        return SensorPredictor.predict(sensor_features)

    @staticmethod
    def predict_sound(spectrogram, confidence_threshold=0.5):
        """
        Predict sound class using pre-loaded TFLite/ONNX model.
        Args:
            spectrogram (np.ndarray): log-mel spectrogram array
        """
        return SoundPredictor.predict(spectrogram, confidence_threshold)

    @staticmethod
    def predict_image(image_array, confidence_threshold=0.5):
        """
        Predict behavior class using pre-loaded ONNX/TFLite/Heuristics model.
        Args:
            image_array (np.ndarray): Preprocessed image array
        """
        return ImagePredictor.predict(image_array, confidence_threshold)

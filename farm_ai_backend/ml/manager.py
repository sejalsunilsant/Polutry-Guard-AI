from ml.inference.sensor_predictor import SensorPredictor
from ml.inference.sound_predictor import SoundPredictor
from ml.inference.image_predictor import ImagePredictor

class ModelManager:
    @staticmethod
    def initialize_all_models():
        """
        Load all machine learning models into memory at application start.
        """
        print("[Model Manager] Initializing and preloading all models...")
        
        # Load Sensor model (XGBoost)
        SensorPredictor.load_model()
        
        # Load Sound model (TFLite/ONNX/Fallback)
        SoundPredictor.load_model()
        
        # Load Image model (ONNX/TFLite/Fallback)
        ImagePredictor.load_model()
        
        print("[Model Manager] All models preloaded in memory and ready.")

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

import os
import numpy as np

class ImagePredictor:
    _onnx_session = None
    _tflite_interpreter = None
    _mode = None  # "onnx", "tflite", or "fallback"
    _classes = ["Normal", "Huddling", "Lethargic", "Crowding"]

    @classmethod
    def load_model(cls):
        """
        Loads the image classification model from ml/model/ once (Singleton).
        Falls back to analysis heuristics if no model is found in the directory.
        """
        if cls._mode is not None:
            return cls._mode

        base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        onnx_path = os.path.join(base_dir, "model", "poultry_image_model.onnx")
        tflite_path = os.path.join(base_dir, "model", "poultry_image_model.tflite")

        # 1. Try ONNX loader
        if os.path.exists(onnx_path):
            try:
                import onnxruntime as ort
                print(f"[Image Predictor] Loading ONNX model from: {onnx_path}")
                cls._onnx_session = ort.InferenceSession(onnx_path, providers=['CPUExecutionProvider'])
                cls._mode = "onnx"
                print("[Image Predictor] ONNX model loaded successfully.")
                return cls._mode
            except Exception as e:
                print(f"[Image Predictor] ONNX loading failed: {e}")

        # 2. Try TFLite loader
        if os.path.exists(tflite_path):
            try:
                try:
                    import tflite_runtime.interpreter as tflite
                except ImportError:
                    import tensorflow.lite as tflite
                print(f"[Image Predictor] Loading TFLite model from: {tflite_path}")
                cls._tflite_interpreter = tflite.Interpreter(model_path=tflite_path)
                cls._tflite_interpreter.allocate_tensors()
                cls._mode = "tflite"
                print("[Image Predictor] TFLite model loaded successfully.")
                return cls._mode
            except Exception as e:
                print(f"[Image Predictor] TFLite loading failed: {e}")

        # 3. Fallback Heuristics
        print("[Image Predictor] WARNING: Image classifier running in fallback heuristics mode (No model file found).")
        cls._mode = "fallback"
        return cls._mode

    @classmethod
    def predict(cls, image_array, confidence_threshold=0.5):
        """
        Predict behavior or disease classification from preprocessed image.
        Args:
            image_array (np.ndarray): Normalized float array of shape (224, 224, 3)
        Returns:
            dict: {
                "prediction": str ("Normal" | "Huddling" | "Lethargic" | "Crowding" | "Uncertain"),
                "confidence": float,
                "probabilities": dict mapping class -> float probability,
                "status": str ("success" | "fallback" | "error")
            }
        """
        cls.load_model()

        if image_array is None:
            return {
                "prediction": "Uncertain",
                "confidence": 0.0,
                "probabilities": {},
                "status": "error",
                "message": "Input image array cannot be None"
            }

        # Ensure correct shape: (224, 224, 3) and expand to batch size (1, 224, 224, 3)
        if image_array.ndim == 3:
            input_data = image_array[np.newaxis, ...].astype(np.float32)
        elif image_array.ndim == 4:
            input_data = image_array.astype(np.float32)
        else:
            return {
                "prediction": "Uncertain",
                "confidence": 0.0,
                "probabilities": {},
                "status": "error",
                "message": f"Unsupported image array shape: {image_array.shape}"
            }

        if cls._mode == "onnx":
            try:
                input_name = cls._onnx_session.get_inputs()[0].name
                predictions = cls._onnx_session.run(None, {input_name: input_data})[0]
                probabilities = predictions[0]
                return cls._format_output(probabilities, confidence_threshold, "success")
            except Exception as e:
                print(f"[Image Predictor] ONNX inference error: {e}")
                return {"prediction": "Uncertain", "confidence": 0.0, "probabilities": {}, "status": "error", "message": str(e)}

        elif cls._mode == "tflite":
            try:
                input_details = cls._tflite_interpreter.get_input_details()
                output_details = cls._tflite_interpreter.get_output_details()

                cls._tflite_interpreter.set_tensor(input_details[0]['index'], input_data)
                cls._tflite_interpreter.invoke()
                probabilities = cls._tflite_interpreter.get_tensor(output_details[0]['index'])[0]
                return cls._format_output(probabilities, confidence_threshold, "success")
            except Exception as e:
                print(f"[Image Predictor] TFLite inference error: {e}")
                return {"prediction": "Uncertain", "confidence": 0.0, "probabilities": {}, "status": "error", "message": str(e)}

        else:
            # Fallback Image Heuristic Analysis
            # 1. Check for dark/offline frames (e.g. camera covered or night time)
            mean_intensity = float(np.mean(image_array))
            std_dev = float(np.std(image_array))
            
            probabilities = {c: 0.05 for c in cls._classes}
            
            if mean_intensity < 0.1:
                # Dark/unusable image
                probabilities = {
                    "Normal": 0.05,
                    "Huddling": 0.05,
                    "Lethargic": 0.05,
                    "Crowding": 0.85
                }
            elif std_dev < 0.08:
                # Very low contrast/variance (mostly uniform color/chickens blocking camera)
                probabilities = {
                    "Crowding": 0.70,
                    "Normal": 0.10,
                    "Huddling": 0.10,
                    "Lethargic": 0.10
                }
            else:
                # Analyze color distribution (R vs G vs B mean values)
                r_mean = float(np.mean(image_array[..., 0]))
                g_mean = float(np.mean(image_array[..., 1]))
                b_mean = float(np.mean(image_array[..., 2]))
                
                # Heuristic: normal environment has high green/blue values (composting/litters)
                # cold stress/huddling might show high grouping colors (high red combs clumping or gray backgrounds)
                if r_mean > g_mean and r_mean > b_mean:
                    probabilities["Normal"] = 0.65
                    probabilities["Lethargic"] = 0.20
                    probabilities["Huddling"] = 0.10
                    probabilities["Crowding"] = 0.05
                elif b_mean > r_mean:
                    probabilities["Huddling"] = 0.60
                    probabilities["Lethargic"] = 0.25
                    probabilities["Normal"] = 0.10
                    probabilities["Crowding"] = 0.05
                else:
                    probabilities["Lethargic"] = 0.55
                    probabilities["Normal"] = 0.25
                    probabilities["Huddling"] = 0.15
                    probabilities["Crowding"] = 0.05

            pred_class = max(probabilities, key=probabilities.get)
            confidence = probabilities[pred_class]
            
            if confidence < confidence_threshold:
                pred_class = "Uncertain"
                
            return {
                "prediction": pred_class,
                "confidence": float(confidence),
                "probabilities": probabilities,
                "status": "fallback"
            }

    @classmethod
    def _format_output(cls, probabilities, confidence_threshold, status):
        pred_idx = int(np.argmax(probabilities))
        confidence = float(probabilities[pred_idx])
        prediction = cls._classes[pred_idx]

        if confidence < confidence_threshold:
            prediction = "Uncertain"

        probs_dict = {cls._classes[i]: float(probabilities[i]) for i in range(len(cls._classes))}

        return {
            "prediction": prediction,
            "confidence": confidence,
            "probabilities": probs_dict,
            "status": status
        }

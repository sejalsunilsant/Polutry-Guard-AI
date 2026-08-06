import os
import numpy as np

class SoundPredictor:
    _tflite_interpreter = None
    _onnx_session = None
    _mode = None  # "tflite", "onnx", or "fallback"
    _labels = ["Healthy", "Sick", "None"]

    @classmethod
    def load_model(cls):
        """
        Load TFLite model once. Gracefully falls back to ONNX (onnxruntime)
        if TFLite is unavailable, or mock mode if neither works.
        """
        if cls._mode is not None:
            return cls._mode

        base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        tflite_path = os.path.join(base_dir, "model", "poultry_cnn_model.tflite")
        onnx_path = os.path.join(base_dir, "model", "poultry_cnn_model.onnx")

        # 1. Try TFLite loader first
        if os.path.exists(tflite_path):
            try:
                # Try importing tflite_runtime first, then fallback to tensorflow
                try:
                    import tflite_runtime.interpreter as tflite
                except ImportError:
                    try:
                        import tensorflow.lite as tflite
                    except ImportError:
                        raise ImportError("TFLite runtime libraries (tflite-runtime or tensorflow) not installed.")

                print(f"[Sound Predictor] Loading TFLite model from: {tflite_path}")
                cls._tflite_interpreter = tflite.Interpreter(model_path=tflite_path)
                cls._tflite_interpreter.allocate_tensors()
                cls._mode = "tflite"
                print("[Sound Predictor] TFLite model loaded successfully.")
                return cls._mode
            except Exception as e:
                print(f"[Sound Predictor] TFLite initialization failed: {e}. Trying ONNX fallback...")

        # 2. Try ONNX fallback
        if os.path.exists(onnx_path):
            try:
                import onnxruntime as ort
                print(f"[Sound Predictor] Loading fallback ONNX model from: {onnx_path}")
                cls._onnx_session = ort.InferenceSession(onnx_path, providers=['CPUExecutionProvider'])
                cls._mode = "onnx"
                print("[Sound Predictor] ONNX model loaded successfully.")
                return cls._mode
            except Exception as e:
                print(f"[Sound Predictor] ONNX initialization failed: {e}")

        # 3. Fallback Mock Mode
        print("[Sound Predictor] WARNING: Sound classifier running in local mock fallback mode.")
        cls._mode = "fallback"
        return cls._mode

    @classmethod
    def predict(cls, spectrogram, confidence_threshold=0.5):
        """
        Predict flock health class from a log-mel spectrogram.
        Args:
            spectrogram (np.ndarray): Preprocessed mel spectrogram, shape (128, 173) or similar.
        Returns:
            dict: {
                "prediction": str ("Healthy" | "Sick" | "None" | "Uncertain"),
                "confidence": float,
                "probabilities": dict mapping class -> float probability,
                "status": str ("success" | "fallback" | "error")
            }
        """
        cls.load_model()

        if spectrogram is None:
            return {
                "prediction": "None",
                "confidence": 0.0,
                "probabilities": {},
                "status": "error",
                "message": "Input spectrogram cannot be None"
            }

        # Ensure correct input shape: (1, 128, 173, 1)
        # Pad or crop width to exactly 173 if needed
        if spectrogram.ndim == 2:
            if spectrogram.shape[0] != 128:
                # Resize or pad height to 128
                pad_h = 128 - spectrogram.shape[0]
                if pad_h > 0:
                    spectrogram = np.pad(spectrogram, ((0, pad_h), (0, 0)))
                else:
                    spectrogram = spectrogram[:128, :]
            
            if spectrogram.shape[1] != 173:
                pad_w = 173 - spectrogram.shape[1]
                if pad_w > 0:
                    spectrogram = np.pad(spectrogram, ((0, 0), (0, pad_w)))
                else:
                    spectrogram = spectrogram[:, :173]
                    
            input_data = spectrogram[np.newaxis, ..., np.newaxis].astype(np.float32)
        elif spectrogram.ndim == 4:
            input_data = spectrogram.astype(np.float32)
        else:
            return {
                "prediction": "None",
                "confidence": 0.0,
                "probabilities": {},
                "status": "error",
                "message": f"Unsupported spectrogram dimensions: {spectrogram.shape}"
            }

        # Execute predictions based on active mode
        if cls._mode == "tflite":
            try:
                input_details = cls._tflite_interpreter.get_input_details()
                output_details = cls._tflite_interpreter.get_output_details()

                cls._tflite_interpreter.set_tensor(input_details[0]['index'], input_data)
                cls._tflite_interpreter.invoke()
                probabilities = cls._tflite_interpreter.get_tensor(output_details[0]['index'])[0]

                return cls._format_output(probabilities, confidence_threshold, "success")
            except Exception as e:
                print(f"[Sound Predictor] TFLite inference failure: {e}")
                return {"prediction": "None", "confidence": 0.0, "probabilities": {}, "status": "error", "message": str(e)}

        elif cls._mode == "onnx":
            try:
                input_name = cls._onnx_session.get_inputs()[0].name
                predictions = cls._onnx_session.run(None, {input_name: input_data})[0]
                probabilities = predictions[0]

                return cls._format_output(probabilities, confidence_threshold, "success")
            except Exception as e:
                print(f"[Sound Predictor] ONNX inference failure: {e}")
                return {"prediction": "None", "confidence": 0.0, "probabilities": {}, "status": "error", "message": str(e)}

        else:
            # Fallback mock calculations based on spectrogram energy
            mean_intensity = float(np.mean(spectrogram))
            probs = {l: 0.05 for l in cls._labels}
            # Simple heuristic mock
            if mean_intensity > -10.0: # higher sound intensity
                probs["Sick"] = 0.75
                probs["Healthy"] = 0.15
                probs["None"] = 0.10
            else:
                probs["Healthy"] = 0.82
                probs["Sick"] = 0.08
                probs["None"] = 0.10

            pred_class = max(probs, key=probs.get)
            confidence = probs[pred_class]
            
            if confidence < confidence_threshold:
                pred_class = "Uncertain"

            return {
                "prediction": pred_class,
                "confidence": float(confidence),
                "probabilities": probs,
                "status": "fallback"
            }

    @classmethod
    def _format_output(cls, probabilities, confidence_threshold, status):
        pred_idx = int(np.argmax(probabilities))
        confidence = float(probabilities[pred_idx])
        prediction = cls._labels[pred_idx]

        if confidence < confidence_threshold:
            prediction = "Uncertain"

        probs_dict = {cls._labels[i]: float(probabilities[i]) for i in range(len(cls._labels))}

        return {
            "prediction": prediction,
            "confidence": confidence,
            "probabilities": probs_dict,
            "status": status
        }

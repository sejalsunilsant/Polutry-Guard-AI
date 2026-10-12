import unittest
import os
import sys
import numpy as np

# Ensure backend root is on sys.path
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

from Apurva_Model.image_predictor import load_image_model, predict_image
from Apurva_Model.Environment.environmental_predictor import (
    load_environmental_model,
    predict_environmental_risk,
    ENVIRONMENTAL_CLASSES
)
from ml.manager import ModelManager

class TestMLModels(unittest.TestCase):
    def test_image_resnet18_model_loading(self):
        """Verify Apurva ResNet-18 PyTorch model checkpoint loads cleanly."""
        model, idx_to_class, device = load_image_model()
        self.assertIsNotNone(model)
        self.assertEqual(len(idx_to_class), 3)
        self.assertIn(0, idx_to_class)
        self.assertIn("Healthy", list(idx_to_class.values()))

    def test_image_prediction_synthetic(self):
        """Verify image prediction works with synthetic numpy RGB image array."""
        synthetic_img = np.zeros((224, 224, 3), dtype=np.uint8)
        result = predict_image(synthetic_img)
        self.assertIn("predicted_class", result)
        self.assertIn("confidence", result)
        self.assertIn("probabilities", result)
        self.assertIn(result["predicted_class"], ["Healthy", "Fowlpox", "Infectious Coryza"])

    def test_environmental_model_loading(self):
        """Verify Apurva XGBoost environmental model checkpoint and preprocessor load."""
        model = load_environmental_model()
        self.assertIsNotNone(model)

    def test_environmental_prediction(self):
        """Verify environmental prediction with standard sensor readings."""
        test_payload = {
            "temperature": 23.0,
            "humidity": 55.0,
            "ammonia": 8.0,
            "breed": "Broiler Ross 308"
        }
        res = predict_environmental_risk(test_payload)
        self.assertEqual(res["status"], "success")
        self.assertIn(res["predicted_class"], ENVIRONMENTAL_CLASSES)
        self.assertGreaterEqual(res["confidence"], 0.0)
        self.assertLessEqual(res["confidence"], 1.0)

    def test_model_manager_sensor_predict(self):
        """Verify ModelManager.predict_sensor handles feature list input."""
        res = ModelManager.predict_sensor([22.0, 60.0, 10.0])
        self.assertIn(res["status"], ["success", "fallback"])
        self.assertIn("prediction", res)
        self.assertIn(res["prediction"], ["Healthy", "Fowlpox", "Infectious Coryza"])

if __name__ == '__main__':
    unittest.main()

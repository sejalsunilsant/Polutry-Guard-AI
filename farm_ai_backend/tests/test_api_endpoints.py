import unittest
import json
import os
import sys

# Ensure backend root is on sys.path
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

from app import app

class TestAPIEndpoints(unittest.TestCase):
    def setUp(self):
        self.app = app
        self.app.testing = True
        self.client = self.app.test_client()

    def test_health_check(self):
        """Test GET /health returns 200 and healthy status."""
        response = self.client.get('/health')
        self.assertEqual(response.status_code, 200)
        data = json.loads(response.data)
        self.assertEqual(data.get("status"), "healthy")
        self.assertIn("service", data)

    def test_predict_disease_validation(self):
        """Test POST /predict-disease rejects request missing mandatory deviceId and farmId."""
        response = self.client.post(
            '/predict-disease',
            data=json.dumps({"temperature": 24.5}),
            content_type='application/json'
        )
        self.assertEqual(response.status_code, 400)
        data = json.loads(response.data)
        self.assertEqual(data.get("status"), "error")
        self.assertIn("deviceId and farmId are required", data.get("message", ""))

    def test_predictions_latest_endpoint(self):
        """Test GET /predictions/latest returns default structure or 400 when missing deviceId."""
        response = self.client.get('/predictions/latest')
        self.assertEqual(response.status_code, 400)

        response_with_dev = self.client.get('/predictions/latest?deviceId=ESP32-01')
        self.assertEqual(response_with_dev.status_code, 200)
        data = json.loads(response_with_dev.data)
        self.assertIn("riskLevel", data)

    def test_active_batch_guard_route(self):
        """Test GET /farms/batches/active guard route handles empty farmId gracefully."""
        response = self.client.get('/farms/batches/active')
        self.assertEqual(response.status_code, 200)
        data = json.loads(response.data)
        self.assertEqual(data.get("status"), "success")

    def test_user_sync_validation_error(self):
        """Test POST /users/sync rejects requests missing mandatory credentials."""
        response = self.client.post(
            '/users/sync',
            data=json.dumps({"uid": "test-uid"}),
            content_type='application/json'
        )
        self.assertEqual(response.status_code, 400)
        data = json.loads(response.data)
        self.assertIn("error", data)

if __name__ == '__main__':
    unittest.main()

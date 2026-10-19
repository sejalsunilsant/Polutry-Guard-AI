import unittest
import os
import sys
import json
from unittest.mock import patch, MagicMock

# Ensure backend root is on sys.path
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

from app import app
from services.image_pipeline_service import ImagePipelineService
from Apurva_Model.image_predictor import load_image_model
from data.supabase_client import (
    get_image_prediction_status,
    claim_image_for_processing,
    save_image_prediction_status,
    get_all_completed_image_paths,
    _mock_image_processing_status
)

class TestImageWatcherLifecycle(unittest.TestCase):
    def setUp(self):
        self.app = app
        self.app.testing = True
        self.client = self.app.test_client()

    def test_gunicorn_conf_port_binding(self):
        """Verify gunicorn.conf.py binds to 0.0.0.0:$PORT with 1 worker and 2 threads."""
        import importlib.util
        conf_path = os.path.join(BASE_DIR, "gunicorn.conf.py")
        self.assertTrue(os.path.exists(conf_path), "gunicorn.conf.py must exist")

        spec = importlib.util.spec_from_file_location("gunicorn_conf", conf_path)
        conf = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(conf)

        self.assertEqual(conf.workers, 1, "Gunicorn workers must be 1 for Render 512MB RAM budget")
        self.assertEqual(conf.threads, 2, "Gunicorn threads must be 2 for concurrent requests")
        self.assertTrue(conf.bind.startswith("0.0.0.0:"), "Gunicorn must bind to 0.0.0.0")

    def test_health_check_fast_response(self):
        """Verify health check returns immediately without triggering model loading."""
        response = self.client.get('/health')
        self.assertEqual(response.status_code, 200)
        data = json.loads(response.data)
        self.assertEqual(data.get("status"), "healthy")

    def test_root_endpoint_fast_response(self):
        """Verify root endpoint returns immediately."""
        response = self.client.get('/')
        self.assertEqual(response.status_code, 200)
        data = json.loads(response.data)
        self.assertEqual(data.get("status"), "online")

    def test_resnet18_model_singleton_caching(self):
        """Verify Apurva ResNet18 model is cached as a singleton and not reloaded repeatedly."""
        model1, mapping1, dev1 = load_image_model()
        model2, mapping2, dev2 = load_image_model()
        self.assertIs(model1, model2, "Model instance must be identical singleton")
        self.assertEqual(dev1.type, "cpu")

    def test_watcher_detects_only_new_images(self):
        """Verify watcher ignores known baseline images and triggers only on newly added images."""
        ImagePipelineService._seen_files = {"images/img_existing_1.jpg", "images/img_existing_2.jpg"}

        with patch('services.image_pipeline_service.supabase', True):
            # Case 1: Storage returns only existing images -> 0 new images processed
            with patch.object(ImagePipelineService, 'list_all_storage_images', return_value=["images/img_existing_1.jpg", "images/img_existing_2.jpg"]):
                with patch.object(ImagePipelineService, 'process_single_image') as mock_process:
                    results = ImagePipelineService.check_and_process_new_images()
                    self.assertEqual(len(results), 0)
                    mock_process.assert_not_called()

            # Case 2: A genuinely new image is uploaded -> triggers prediction for ONLY that new image
            new_img = "images/camera_new_upload_2026.jpg"
            with patch.object(ImagePipelineService, 'list_all_storage_images', return_value=["images/img_existing_1.jpg", "images/img_existing_2.jpg", new_img]):
                with patch.object(ImagePipelineService, 'process_single_image', return_value={"status": "success", "file_path": new_img}) as mock_process:
                    results = ImagePipelineService.check_and_process_new_images()
                    self.assertEqual(len(results), 1)
                    mock_process.assert_called_once_with(new_img)
                    self.assertIn(new_img, ImagePipelineService._seen_files)

    def test_mark_file_handled(self):
        """Verify mark_file_handled registers file path so it will not be re-processed."""
        test_file = "images/handled_test_123.jpg"
        test_url = "https://mock.supabase.co/images/handled_test_123.jpg"
        ImagePipelineService.mark_file_handled(test_file, test_url)
        self.assertIn(test_file, ImagePipelineService._seen_files)
        self.assertIn(test_file, ImagePipelineService._processed_cache)
        self.assertIn(test_url, ImagePipelineService._processed_cache)

    def test_webhook_endpoint_various_payloads(self):
        """Verify POST /ml/process-image handles direct and Supabase storage webhook records."""
        with patch.object(ImagePipelineService, 'process_single_image', return_value={"status": "success"}) as mock_proc:
            # Format 1: Direct name
            resp1 = self.client.post('/ml/process-image', json={"name": "farm_1/shed_1/cam.jpg"})
            self.assertEqual(resp1.status_code, 200)
            mock_proc.assert_called_with("farm_1/shed_1/cam.jpg")

            # Format 2: Supabase Storage Webhook payload { record: { name: ... } }
            resp2 = self.client.post('/ml/process-image', json={"record": {"name": "farm_2/shed_2/cam2.jpg"}})
            self.assertEqual(resp2.status_code, 200)
            mock_proc.assert_called_with("farm_2/shed_2/cam2.jpg")

            # Format 3: Missing name -> 400 Bad Request
            resp3 = self.client.post('/ml/process-image', json={})
            self.assertEqual(resp3.status_code, 400)

    def test_database_level_idempotency_never_repredicts(self):
        """
        Verify that processing an image stores status in DB and a repeated attempt
        (due to webhook retry or worker reload) NEVER re-invokes the ML model.
        """
        import uuid
        unique_file = f"farm_x/shed_y/idempotent_test_{uuid.uuid4().hex[:8]}.jpg"
        fake_bytes = b"fake_jpeg_image_bytes_123"

        # Mock download and prediction
        with patch('requests.get') as mock_req, \
             patch('ml.inference.image_predictor.ImagePredictor.predict') as mock_pred, \
             patch('services.image_pipeline_service.supabase') as mock_sb:

            mock_sb.storage.from_().download.return_value = fake_bytes
            mock_pred.return_value = {
                "prediction": "Healthy",
                "predicted_class": "Healthy",
                "confidence": 0.95,
                "probabilities": {"Healthy": 0.95, "Fowlpox": 0.03, "Infectious Coryza": 0.02},
                "status": "success"
            }

            # First run: should process and persist status to DB
            res1 = ImagePipelineService.process_single_image(unique_file)
            self.assertEqual(res1.get("status"), "success")
            self.assertEqual(mock_pred.call_count, 1)

            # Verify status in database
            db_status = get_image_prediction_status(unique_file)
            self.assertIsNotNone(db_status)
            self.assertEqual(db_status.get("status"), "COMPLETED")
            self.assertEqual(db_status.get("disease"), "Healthy")

            # Second run (immediate retry): must be skipped and NEVER invoke model again
            res2 = ImagePipelineService.process_single_image(unique_file)
            self.assertEqual(res2.get("status"), "skipped")
            self.assertEqual(res2.get("reason"), "already_predicted")
            self.assertEqual(mock_pred.call_count, 1, "ML model must NOT be called a second time!")

    def test_backend_restart_preserves_database_status(self):
        """
        Simulate backend restart or Gunicorn worker respawn where in-memory sets are empty.
        The DB status must be respected and prevent re-prediction.
        """
        import uuid
        restarted_file = f"farm_x/shed_y/after_restart_test_{uuid.uuid4().hex[:8]}.jpg"

        # Record as completed in DB
        save_image_prediction_status(
            image_path=restarted_file,
            status="COMPLETED",
            disease="Fowlpox",
            confidence=0.88,
            risk_level="HIGH"
        )

        # Clear in-memory caches to simulate worker restart / clean process
        ImagePipelineService._processed_cache.clear()
        ImagePipelineService._seen_files.clear()

        with patch('ml.inference.image_predictor.ImagePredictor.predict') as mock_pred:
            # Process attempt after restart
            res = ImagePipelineService.process_single_image(restarted_file)
            self.assertEqual(res.get("status"), "skipped")
            self.assertEqual(res.get("reason"), "already_predicted")
            mock_pred.assert_not_called()

        # Verify get_all_completed_image_paths seeds cache properly
        completed_paths = get_all_completed_image_paths()
        self.assertIn(restarted_file, completed_paths)

    def test_concurrent_claim_lock_prevents_duplicate_runs(self):
        """
        Verify database claim lock prevents two concurrent requests from processing the same image.
        """
        import uuid
        race_file = f"farm_x/shed_y/concurrent_race_test_{uuid.uuid4().hex[:8]}.jpg"

        # Worker 1 claims
        claimed_1, _ = claim_image_for_processing(race_file)
        self.assertTrue(claimed_1, "First worker must successfully claim")

        # Worker 2 attempts to claim while Worker 1 is still processing
        claimed_2, existing_2 = claim_image_for_processing(race_file)
        self.assertFalse(claimed_2, "Second concurrent worker must be rejected")
        self.assertIsNotNone(existing_2)
        self.assertEqual(existing_2.get("status"), "PROCESSING")

        # Worker 1 finishes and saves status
        save_image_prediction_status(
            image_path=race_file,
            status="COMPLETED",
            disease="Healthy",
            confidence=0.99
        )

        # Worker 3 arrives after completion
        claimed_3, existing_3 = claim_image_for_processing(race_file)
        self.assertFalse(claimed_3, "Future worker must be rejected because status is COMPLETED")
        self.assertEqual(existing_3.get("status"), "COMPLETED")

if __name__ == '__main__':
    unittest.main()

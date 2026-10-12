import unittest
import os
import sys

# Ensure backend root is on sys.path
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

from services.fcm_service import AlertCooldownTracker, FCMService

class TestFCMNotifications(unittest.TestCase):
    def setUp(self):
        self.tracker = AlertCooldownTracker(cooldown_seconds=60)
        self.farm_id = "test_farm_001"
        self.device_id = "test_dev_001"

    def test_alert_activation(self):
        """First threshold crossing triggers immediate notification."""
        should_send = self.tracker.should_notify(
            farm_id=self.farm_id,
            device_id=self.device_id,
            alert_type="AMMONIA",
            severity="HIGH",
            current_value=26.0
        )
        self.assertTrue(should_send)

    def test_alert_cooldown_suppression(self):
        """Repeated reading above threshold within cooldown is suppressed."""
        # First trigger
        self.tracker.should_notify(
            farm_id=self.farm_id,
            device_id=self.device_id,
            alert_type="AMMONIA",
            severity="HIGH",
            current_value=26.0
        )
        # Immediate subsequent trigger with same severity
        should_send_again = self.tracker.should_notify(
            farm_id=self.farm_id,
            device_id=self.device_id,
            alert_type="AMMONIA",
            severity="HIGH",
            current_value=27.0
        )
        self.assertFalse(should_send_again)

    def test_alert_severity_escalation(self):
        """Severity increase (e.g. HIGH -> CRITICAL) bypasses cooldown."""
        self.tracker.should_notify(
            farm_id=self.farm_id,
            device_id=self.device_id,
            alert_type="AMMONIA",
            severity="HIGH",
            current_value=26.0
        )
        # Escalation to CRITICAL
        escalated = self.tracker.should_notify(
            farm_id=self.farm_id,
            device_id=self.device_id,
            alert_type="AMMONIA",
            severity="CRITICAL",
            current_value=35.0
        )
        self.assertTrue(escalated)

    def test_alert_reset_on_normal(self):
        """Reset clears state so future threshold crosses notify immediately."""
        self.tracker.should_notify(
            farm_id=self.farm_id,
            device_id=self.device_id,
            alert_type="AMMONIA",
            severity="HIGH",
            current_value=26.0
        )
        # Metric returns to normal
        self.tracker.reset_alert(self.farm_id, self.device_id, "AMMONIA")
        
        # New spike after recovery
        should_send_new = self.tracker.should_notify(
            farm_id=self.farm_id,
            device_id=self.device_id,
            alert_type="AMMONIA",
            severity="HIGH",
            current_value=28.0
        )
        self.assertTrue(should_send_new)

    def test_fcm_dispatch_simulator_mode(self):
        """FCMService handles simulated/missing credentials gracefully without crashing."""
        res = FCMService.send_multicast(
            fcm_tokens=["mock_token_12345"],
            title="Test Alert",
            body="Test message body",
            data_payload={"alert_type": "AMMONIA"}
        )
        self.assertIn("success", res)

if __name__ == '__main__':
    unittest.main()

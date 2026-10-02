"""
PoultryGuard AI - Firebase Cloud Messaging (FCM) Alert Testing Script
----------------------------------------------------------------------
This utility tests FCM push alerts with notification deduplication and cooldown:
- Tests critical threshold alerts (Ammonia, Heat Stress, Humidity, Sound, Disease).
- Verifies payload structure (farmName, alertType, severity, message).
- Tests cooldown and deduplication state machine.
- Supports direct token dispatch and simulator mode.

Usage:
  - Interactive: python test_fcm_alert.py
  - CLI direct: python test_fcm_alert.py --token <FCM_TOKEN> --type ammonia
  - Cooldown test: python test_fcm_alert.py --test-cooldown
"""

import os
import sys
import argparse
import json
import time
from datetime import datetime, timezone

# Ensure UTF-8 console output on Windows
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

# Add backend directory to path
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

from services.fcm_service import FCMService, AlertCooldownTracker, cooldown_tracker
from data.supabase_client import update_user_fcm_token, get_farm_fcm_tokens

ALERT_TEMPLATES = {
    "ammonia": {
        "alert_type": "AMMONIA",
        "severity": "CRITICAL",
        "title": "Critical Ammonia Hazard",
        "message": (
            "Hazardous air quality detected in Shed 2 (34.2 ppm, >25 ppm threshold). "
            "Exhaust ventilation fans must run at 100% immediately to flush the shed."
        ),
        "value": 34.2
    },
    "heat_stress": {
        "alert_type": "TEMPERATURE",
        "severity": "WARNING",
        "title": "Heat Stress Warning",
        "message": (
            "High temperature detected in Shed 1 (36.8°C, Humidity 82%). "
            "High flock heat stress risk. Activate evaporative cooling pads and misting foggers."
        ),
        "value": 36.8
    },
    "humidity": {
        "alert_type": "HUMIDITY",
        "severity": "WARNING",
        "title": "Excess Humidity Warning",
        "message": (
            "Excessive humidity detected in Shed 3 (88.5%). "
            "Increase ventilation to suppress aerosol pathogen transmission and wet litter."
        ),
        "value": 88.5
    },
    "sound": {
        "alert_type": "SOUND",
        "severity": "WARNING",
        "title": "Abnormal Vocalization Alert",
        "message": (
            "Abnormal acoustic distress / gasping detected (78.4 dB). "
            "Acoustic monitoring indicates potential respiratory coughing or predator disturbance."
        ),
        "value": 78.4
    },
    "disease": {
        "alert_type": "DISEASE",
        "severity": "HIGH",
        "title": "Disease Outbreak Risk: Infectious Coryza",
        "message": (
            "Infectious Coryza detected with 89% confidence in Batch #B-104. "
            "Facial swelling and rales detected. Isolate symptomatic birds and consult veterinarian."
        ),
        "value": 0.89
    },
    "mortality": {
        "alert_type": "MORTALITY",
        "severity": "CRITICAL",
        "title": "Urgent Mortality Spike",
        "message": (
            "Mortality spike detected in Shed 2 (18 birds in past 2 hours). "
            "Immediate flock biosecurity inspection required."
        ),
        "value": 18
    }
}


def test_cooldown_behavior():
    """Demonstrates and verifies alert deduplication and cooldown mechanism."""
    tracker = AlertCooldownTracker(cooldown_seconds=10)
    farm_id = "test_farm_cooldown"
    device_id = "esp32_test_1"
    alert_type = "AMMONIA"

    print("\n" + "=" * 65)
    print(" [TEST] NOTIFICATION DEDUPLICATION & COOLDOWN STATE MACHINE")
    print("=" * 65)

    # 1. First threshold crossing -> Must notify
    should_send = tracker.should_notify(farm_id, device_id, alert_type, "HIGH", current_value=26.0)
    print(f" 1. Threshold crossed (Ammonia 26 ppm, HIGH)     -> Notify: {should_send} [EXPECTED: True]")

    # 2. Immediate repeat (still high) -> Must be suppressed / deduplicated
    should_send = tracker.should_notify(farm_id, device_id, alert_type, "HIGH", current_value=27.0)
    print(f" 2. Still above threshold (Ammonia 27 ppm, HIGH)  -> Notify: {should_send} [EXPECTED: False - Suppressed]")

    # 3. Severity escalation (HIGH -> CRITICAL) -> Must notify immediately
    should_send = tracker.should_notify(farm_id, device_id, alert_type, "CRITICAL", current_value=35.0)
    print(f" 3. Escalated severity (Ammonia 35 ppm, CRITICAL) -> Notify: {should_send} [EXPECTED: True - Escalation]")

    # 4. Metric returns to normal baseline -> Resets alert state
    tracker.reset_alert(farm_id, device_id, alert_type)
    print(f" 4. Metric returns to normal (Ammonia 16 ppm)     -> Alert state reset.")

    # 5. Next threshold crossing -> Must notify again immediately
    should_send = tracker.should_notify(farm_id, device_id, alert_type, "HIGH", current_value=28.0)
    print(f" 5. New threshold crossing after recovery         -> Notify: {should_send} [EXPECTED: True]")
    print("=" * 65)
    print(" [SUCCESS] Cooldown and deduplication test completed successfully!\n")


def send_test_alert(token: str, alert_key: str, farm_id: str = "default_farm", farm_name: str = "Poultry Farm"):
    template = ALERT_TEMPLATES.get(alert_key, ALERT_TEMPLATES["ammonia"])
    alert_type = template["alert_type"]
    severity = template["severity"]
    title = template["title"]
    message = template["message"]

    print("\n" + "=" * 65)
    print(" 🚨 POULTRYGUARD AI - DISPATCHING FCM PUSH ALERT")
    print("=" * 65)
    print(f" Farm Name : {farm_name} ({farm_id})")
    print(f" Alert Type: {alert_type}")
    print(f" Severity  : {severity}")
    print(f" Title     : {title}")
    print(f" Message   : {message}")
    print("-" * 65)

    if token and token.strip() and token != "SIMULATOR":
        # Direct Token Dispatch
        payload = {
            "farmId": farm_id,
            "farmName": farm_name,
            "alertType": alert_type,
            "severity": severity,
            "title": title,
            "message": message,
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
        result = FCMService.send_to_token(
            fcm_token=token,
            title=f"[{farm_name}] {severity} {alert_type.title()} Alert",
            body=message,
            data_payload=payload
        )
    else:
        # Farm Multicast / Simulator Dispatch
        result = FCMService.send_alert_to_farm(
            farm_id=farm_id,
            farm_name=farm_name,
            alert_type=alert_type,
            severity=severity,
            title=title,
            message=message,
            bypass_cooldown=True
        )

    print("\nRESULT:")
    print(json.dumps(result, indent=2))
    print("=" * 65)
    return result


def interactive_menu():
    print("\n" + "=" * 55)
    print("   POULTRYGUARD AI - FCM PUSH ALERT TEST UTILITY")
    print("=" * 55)

    print("\nSelect Action:")
    print(" 1) Test FCM Push Notification to Device / Farm")
    print(" 2) Test Alert Deduplication & Cooldown State Machine")
    print(" 3) Register / Cache Mock Device Token for User")
    choice = input("\nEnter choice [1-3] (default: 1): ").strip() or "1"

    if choice == "2":
        test_cooldown_behavior()
        return

    if choice == "3":
        prof_id = input("Enter User Profile ID: ").strip() or "test_user_1"
        tok = input("Enter Device FCM Token: ").strip() or "sample_fcm_token_1234567890"
        update_user_fcm_token(prof_id, tok)
        print(f"[OK] Token registered for profile '{prof_id}'.")
        return

    print("\nSelect Alert Scenario:")
    print(" 1) Critical Ammonia Hazard (>25 ppm)")
    print(" 2) Heat Stress & High Humidity Warning")
    print(" 3) Excess Humidity Warning (>85%)")
    print(" 4) Abnormal Flock Vocalization / Acoustic Distress (>75 dB)")
    print(" 5) Disease Outbreak Warning (Infectious Coryza - 89% conf)")
    print(" 6) Mortality Spike Alert (18 birds)")
    alert_choice = input("\nEnter choice [1-6] (default: 1): ").strip() or "1"

    type_map = {
        "1": "ammonia",
        "2": "heat_stress",
        "3": "humidity",
        "4": "sound",
        "5": "disease",
        "6": "mortality"
    }
    alert_key = type_map.get(alert_choice, "ammonia")

    token = input("\nEnter Target FCM Device Token (or press ENTER for Farm Dispatch / Simulator): ").strip()
    farm_id = input("Enter Farm ID (default: default_farm): ").strip() or "default_farm"
    farm_name = input("Enter Farm Name (default: Green Valley Poultry): ").strip() or "Green Valley Poultry"

    send_test_alert(token=token, alert_key=alert_key, farm_id=farm_id, farm_name=farm_name)


def main():
    parser = argparse.ArgumentParser(description="PoultryGuard AI FCM Alert Tester")
    parser.add_argument("--token", help="Target FCM device token")
    parser.add_argument("--type", choices=list(ALERT_TEMPLATES.keys()), default="ammonia", help="Alert type")
    parser.add_argument("--farm-id", default="default_farm", help="Farm ID")
    parser.add_argument("--farm-name", default="Green Valley Poultry", help="Farm Name")
    parser.add_argument("--test-cooldown", action="store_true", help="Run deduplication & cooldown unit test")

    args = parser.parse_args()

    if args.test_cooldown:
        test_cooldown_behavior()
    elif args.token or args.type != "ammonia":
        send_test_alert(token=args.token, alert_key=args.type, farm_id=args.farm_id, farm_name=args.farm_name)
    else:
        interactive_menu()


if __name__ == "__main__":
    main()

import os
import sys
import time
import json
from datetime import datetime, timezone
from typing import Dict, List, Optional, Any

# Automatically load .env if not loaded yet
def _load_env_if_needed():
    current_dir = os.path.dirname(os.path.abspath(__file__))
    possible_paths = [
        os.path.abspath(os.path.join(current_dir, "..", "..", ".env")),
        os.path.abspath(os.path.join(current_dir, "..", ".env")),
        os.path.abspath(os.path.join(os.getcwd(), ".env")),
    ]
    for path in possible_paths:
        if os.path.exists(path):
            try:
                with open(path, "r", encoding="utf-8") as f:
                    for line in f:
                        line = line.strip()
                        if line and not line.startswith("#") and "=" in line:
                            k, v = line.split("=", 1)
                            val = v.strip().strip('"').strip("'")
                            if k.strip() not in os.environ:
                                os.environ[k.strip()] = val
            except Exception:
                pass
            break

_load_env_if_needed()

# Global Firebase Admin initialization state
_firebase_initialized = False
_firebase_admin_module = None
_messaging_module = None

try:
    import firebase_admin
    from firebase_admin import credentials, messaging
    _firebase_admin_module = firebase_admin
    _messaging_module = messaging
except ImportError:
    print("[FCM Service] Python 'firebase-admin' package not installed in current environment.")
    print("[FCM Service] To enable live FCM push delivery: pip install firebase-admin")
    print("[FCM Service] Running in robust local simulator mode.\n")



class AlertCooldownTracker:
    """
    Manages notification deduplication and cooldown per (device_id/farm_id, alert_type).
    State Machine:
      - Threshold crossed -> Send push notification, set active = True.
      - Still above threshold -> Deduplicate / cooldown (suppress spam).
      - Returns to normal -> Reset alert state so future spikes trigger immediately.
    """
    def __init__(self, cooldown_seconds: int = 1800):  # default 30 minutes
        self.cooldown_seconds = cooldown_seconds
        # key: f"{farm_id}_{device_id}_{alert_type}" -> state dict
        self._states: Dict[str, Dict[str, Any]] = {}

    def _get_key(self, farm_id: str, device_id: Optional[str], alert_type: str) -> str:
        dev = device_id or "all_devices"
        return f"{farm_id}:{dev}:{alert_type.upper()}"

    def should_notify(
        self,
        farm_id: str,
        device_id: Optional[str],
        alert_type: str,
        severity: str,
        current_value: Optional[float] = None
    ) -> bool:
        """
        Determines whether a notification should be dispatched based on cooldown and severity changes.
        """
        key = self._get_key(farm_id, device_id, alert_type)
        now = time.time()
        state = self._states.get(key)

        if not state or not state.get("active"):
            # New alert activation
            self._states[key] = {
                "active": True,
                "last_notified_at": now,
                "severity": severity.upper(),
                "last_value": current_value,
            }
            return True

        # Existing active alert: Check for severity escalation
        prev_severity = state.get("severity", "MEDIUM")
        severity_order = {"LOW": 1, "MEDIUM": 2, "WARNING": 3, "HIGH": 4, "CRITICAL": 5}
        is_escalated = severity_order.get(severity.upper(), 2) > severity_order.get(prev_severity, 2)

        # Check if cooldown period has elapsed
        time_since_last = now - state.get("last_notified_at", 0)
        cooldown_elapsed = time_since_last >= self.cooldown_seconds

        if is_escalated or cooldown_elapsed:
            state["last_notified_at"] = now
            state["severity"] = severity.upper()
            state["last_value"] = current_value
            return True

        # Suppress repeated notification
        return False

    def reset_alert(self, farm_id: str, device_id: Optional[str], alert_type: str):
        """
        Resets alert state when metric returns to normal baseline.
        """
        key = self._get_key(farm_id, device_id, alert_type)
        if key in self._states:
            self._states[key]["active"] = False


# Singleton cooldown tracker instance
cooldown_tracker = AlertCooldownTracker(cooldown_seconds=1800)


class FCMService:
    """
    Firebase Cloud Messaging (FCM) Service for PoultryGuard AI.
    Handles push notification dispatch, message formatting, and graceful token cleanup.
    """

    @classmethod
    def initialize(cls) -> bool:
        global _firebase_initialized
        if _firebase_initialized:
            return True

        if not _firebase_admin_module:
            return False

        # Look for service account path in environment or local paths
        cert_path = os.getenv("FIREBASE_SERVICE_ACCOUNT_KEY")

        if not cert_path or not os.path.exists(cert_path):
            # Check default candidate locations in backend
            candidates = [
                os.path.join(os.path.dirname(__file__), "..", "serviceAccountKey.json"),
                os.path.join(os.path.dirname(__file__), "..", "firebase_credentials.json"),
                os.path.join(os.path.dirname(__file__), "..", "..", "serviceAccountKey.json"),
            ]
            for candidate in candidates:
                if os.path.exists(candidate):
                    cert_path = os.path.abspath(candidate)
                    break

        try:
            if cert_path and os.path.exists(cert_path):
                # Verify whether this is a client config (google-services.json) or admin private key
                try:
                    with open(cert_path, "r", encoding="utf-8") as jf:
                        raw_json = json.load(jf)

                    if "private_key" in raw_json and "client_email" in raw_json:
                        cred = credentials.Certificate(cert_path)
                        _firebase_admin_module.initialize_app(cred)
                        _firebase_initialized = True
                        print(f"[FCM Service] Initialized successfully with Service Account at: {cert_path}")
                        return True
                    elif "project_info" in raw_json:
                        proj_id = raw_json.get("project_info", {}).get("project_id", "")
                        print(f"\n[FCM Service] Notice: '{cert_path}' is an Android client config (google-services.json).")
                        print("  * 'google-services.json' belongs in Android app: PoultryGuardAndroid/app/google-services.json")
                        print("  * Server push dispatch requires the Firebase Service Account Private Key.")
                        print("  * Get it from: Firebase Console -> Project Settings -> Service Accounts -> 'Generate new private key'")
                        print(f"  * Save it to: farm_ai_backend/serviceAccountKey.json (Project: {proj_id})\n")
                        return False
                    else:
                        print(f"[FCM Service] Unrecognized credentials format in '{cert_path}'.")
                        return False
                except Exception as pe:
                    print(f"[FCM Service] Error reading credentials file: {pe}")
                    return False

            # Check if environment has standard project ID or default credentials
            proj_id = os.getenv("GOOGLE_CLOUD_PROJECT") or os.getenv("FIREBASE_PROJECT_ID")
            if proj_id:
                try:
                    _firebase_admin_module.initialize_app(options={"projectId": proj_id})
                    _firebase_initialized = True
                    print(f"[FCM Service] Initialized with project ID: {proj_id}")
                    return True
                except Exception:
                    pass

            return False
        except Exception as e:
            print(f"[FCM Service] Warning during Firebase Admin initialization: {e}")
            return False

    @classmethod
    def send_to_token(
        cls,
        fcm_token: str,
        title: str,
        body: str,
        data_payload: Optional[Dict[str, str]] = None
    ) -> Dict[str, Any]:
        """
        Sends an FCM push notification to a single device token.
        """
        if not fcm_token or not fcm_token.strip():
            return {"success": False, "error": "Empty or missing FCM token"}

        data_payload = data_payload or {}
        # Ensure all data values are strings for FCM compatibility
        safe_data = {str(k): str(v) for k, v in data_payload.items()}

        if not _messaging_module or not cls.initialize():
            # Fallback Simulation / Logging
            print("\n[FCM SIMULATOR] Direct Push Notification:")
            print(f"  Token   : {fcm_token[:16]}...{fcm_token[-8:] if len(fcm_token) > 24 else ''}")
            print(f"  Title   : {title}")
            print(f"  Body    : {body}")
            print(f"  Data    : {json.dumps(safe_data)}")
            return {"success": True, "simulated": True, "message": "Logged to console (FCM credentials pending)"}

        try:
            message = _messaging_module.Message(
                notification=_messaging_module.Notification(
                    title=title,
                    body=body,
                ),
                data=safe_data,
                token=fcm_token,
                android=_messaging_module.AndroidConfig(
                    priority="high",
                    notification=_messaging_module.AndroidNotification(
                        sound="default",
                        channel_id="poultry_guard_alerts_channel",
                        priority="high",
                    ),
                ),
            )
            response = _messaging_module.send(message)
            return {"success": True, "message_id": response}
        except Exception as e:
            err_str = str(e)
            print(f"[FCM Service] Failed to send push notification to token: {err_str}")
            return {"success": False, "error": err_str}

    @classmethod
    def send_multicast(
        cls,
        fcm_tokens: List[str],
        title: str,
        body: str,
        data_payload: Optional[Dict[str, str]] = None
    ) -> Dict[str, Any]:
        """
        Sends an FCM push notification to a list of device tokens.
        """
        valid_tokens = [t for t in fcm_tokens if t and t.strip()]
        if not valid_tokens:
            return {"success": False, "error": "No valid FCM tokens provided", "sent_count": 0}

        data_payload = data_payload or {}
        safe_data = {str(k): str(v) for k, v in data_payload.items()}

        if not _messaging_module or not cls.initialize():
            print(f"\n[FCM SIMULATOR] Multicast Push Notification to {len(valid_tokens)} device(s):")
            print(f"  Title   : {title}")
            print(f"  Body    : {body}")
            print(f"  Data    : {json.dumps(safe_data)}")
            return {
                "success": True,
                "simulated": True,
                "sent_count": len(valid_tokens),
                "invalid_tokens": [],
            }

        try:
            multicast_msg = _messaging_module.MulticastMessage(
                notification=_messaging_module.Notification(
                    title=title,
                    body=body,
                ),
                data=safe_data,
                tokens=valid_tokens,
                android=_messaging_module.AndroidConfig(
                    priority="high",
                    notification=_messaging_module.AndroidNotification(
                        sound="default",
                        channel_id="poultry_guard_alerts_channel",
                        priority="high",
                    ),
                ),
            )
            batch_response = _messaging_module.send_multicast(multicast_msg)

            invalid_tokens = []
            for idx, resp in enumerate(batch_response.responses):
                if not resp.success:
                    # Token may be expired or unregistered
                    err_code = resp.exception.code if hasattr(resp.exception, "code") else "UNKNOWN"
                    invalid_tokens.append({"token": valid_tokens[idx], "code": err_code})

            return {
                "success": True,
                "sent_count": batch_response.success_count,
                "failure_count": batch_response.failure_count,
                "invalid_tokens": invalid_tokens,
            }
        except Exception as e:
            print(f"[FCM Service] Multicast error: {e}")
            return {"success": False, "error": str(e), "sent_count": 0}

    @classmethod
    def send_alert_to_farm(
        cls,
        farm_id: str,
        farm_name: str,
        alert_type: str,
        severity: str,
        title: str,
        message: str,
        device_id: Optional[str] = None,
        batch_id: Optional[str] = None,
        bypass_cooldown: bool = False,
        current_value: Optional[float] = None
    ) -> Dict[str, Any]:
        """
        Sends an alert notification to all registered farmer/veterinarian devices linked to a farm,
        incorporating notification deduplication and cooldown.
        """
        # 1. Deduplication / Cooldown evaluation
        if not bypass_cooldown:
            should_send = cooldown_tracker.should_notify(
                farm_id=farm_id,
                device_id=device_id,
                alert_type=alert_type,
                severity=severity,
                current_value=current_value,
            )
            if not should_send:
                print(f"[FCM Cooldown] Alert '{alert_type}' for farm '{farm_id}' suppressed (cooldown active).")
                return {
                    "success": True,
                    "suppressed": True,
                    "message": "Notification suppressed by deduplication cooldown filter.",
                }

        # 2. Fetch device FCM tokens for farm members & assigned vets from Supabase
        from data.supabase_client import get_farm_fcm_tokens, remove_invalid_fcm_tokens
        fcm_tokens = get_farm_fcm_tokens(farm_id)

        if not fcm_tokens:
            print(f"[FCM Service] No active device tokens found for farm '{farm_id}'.")
            return {
                "success": False,
                "error": f"No active device tokens found for farm {farm_id}",
                "sent_count": 0,
            }

        # 3. Format structured payload
        # Required notification details: farm name, alert type, severity, short message
        formatted_title = f"[{farm_name or 'Poultry Farm'}] {severity.upper()} {alert_type.replace('_', ' ').title()}"
        formatted_body = message

        payload = {
            "farmId": farm_id or "",
            "farmName": farm_name or "Poultry Farm",
            "alertType": alert_type.upper(),
            "severity": severity.upper(),
            "deviceId": device_id or "",
            "batchId": batch_id or "",
            "title": title,
            "message": message,
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }

        # 4. Dispatch multicast notification
        result = cls.send_multicast(
            fcm_tokens=fcm_tokens,
            title=formatted_title,
            body=formatted_body,
            data_payload=payload,
        )

        # 5. Clean up any invalid/unregistered tokens reported by FCM
        invalid_list = result.get("invalid_tokens", [])
        if invalid_list:
            bad_tokens = [item["token"] for item in invalid_list if "token" in item]
            if bad_tokens:
                remove_invalid_fcm_tokens(bad_tokens)

        return result

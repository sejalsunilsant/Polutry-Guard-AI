import os
import time
import threading
from datetime import datetime, timezone
import requests

from ml.manager import ModelManager
from ml.inference.image_predictor import ImagePredictor
from ml.fusion.decision_engine import fuse_decisions
from data.supabase_client import (
    supabase,
    get_last_telemetry,
    save_telemetry,
    save_prediction,
    ensure_device_exists,
    get_active_batch,
    get_mortality_records,
    find_matching_active_event,
    create_disease_event,
    update_disease_event,
    create_alert
)

BUCKET_NAME = "poultry-media"

class ImagePipelineService:
    _is_running = False
    _processed_cache = set()
    _lock = threading.Lock()

    @classmethod
    def start_background_watcher(cls, interval_seconds=20):
        """
        Starts the background worker that:
        1. Scans and processes all existing unpredicted backlog images.
        2. Continuously watches for newly uploaded images.
        """
        with cls._lock:
            if cls._is_running:
                return
            cls._is_running = True

        thread = threading.Thread(
            target=cls._run_loop,
            args=(interval_seconds,),
            daemon=True,
            name="ImageMLPipelineWatcher"
        )
        thread.start()
        print(f"[Image Pipeline] Background watcher started (poll interval: {interval_seconds}s).")

    @classmethod
    def _run_loop(cls, interval_seconds):
        # 1. Backlog scan on startup
        print("[Image Pipeline] Running initial backlog scan for unpredicted images...")
        try:
            cls.process_all_unprocessed_images()
        except Exception as e:
            print(f"[Image Pipeline] Error in initial backlog scan: {e}")

        # 2. Continuous polling loop
        while True:
            try:
                time.sleep(interval_seconds)
                cls.process_all_unprocessed_images()
            except Exception as e:
                print(f"[Image Pipeline] Error in background watcher loop: {e}")

    @classmethod
    def list_all_storage_images(cls, prefix=""):
        """
        Recursively lists all image file paths in the Supabase bucket.
        """
        if not supabase:
            return []

        image_files = []
        try:
            items = supabase.storage.from_(BUCKET_NAME).list(path=prefix)
            if not items:
                return []

            for item in items:
                name = item.get("name")
                if not name:
                    continue

                item_path = f"{prefix}/{name}".strip("/") if prefix else name
                
                # Check if it's an image file
                ext = name.lower().split(".")[-1] if "." in name else ""
                if ext in ["jpg", "jpeg", "png", "webp"]:
                    image_files.append(item_path)
                elif item.get("id") is None or item.get("metadata") is None:
                    # It's a directory / folder: recurse into it
                    sub_files = cls.list_all_storage_images(prefix=item_path)
                    image_files.extend(sub_files)
        except Exception as ex:
            print(f"[Image Pipeline] Failed listing storage objects at '{prefix}': {ex}")

        return image_files

    @classmethod
    def is_image_already_predicted(cls, image_url, file_path):
        """
        Checks if this image file has already been processed and saved in the DB.
        """
        if file_path in cls._processed_cache or image_url in cls._processed_cache:
            return True

        if not supabase:
            return False

        try:
            # Check 1: In sensor_telemetry by image_url
            res1 = supabase.table("sensor_telemetry")\
                .select("id")\
                .or_(f"image_url.eq.{image_url},image_url.ilike.%{file_path}%")\
                .limit(1)\
                .execute()
            if res1.data and len(res1.data) > 0:
                cls._processed_cache.add(file_path)
                return True

            # Check 2: In disease_events by representative_image_url
            res2 = supabase.table("disease_events")\
                .select("id")\
                .or_(f"representative_image_url.eq.{image_url},representative_image_url.ilike.%{file_path}%")\
                .limit(1)\
                .execute()
            if res2.data and len(res2.data) > 0:
                cls._processed_cache.add(file_path)
                return True
        except Exception as e:
            print(f"[Image Pipeline] Error checking duplicate prediction for '{file_path}': {e}")

        return False

    @classmethod
    def parse_farm_and_shade(cls, file_path):
        """
        Extracts farm_id and device_id (shade) from the storage file path.
        Standard path convention: farm_id/device_id/image_filename.jpg
        Fallbacks supported:
        - device_id/image_filename.jpg -> ('default_farm', device_id)
        - camera/image_filename.jpg    -> ('default_farm', 'default_device')
        """
        parts = [p for p in file_path.split("/") if p]
        
        if len(parts) >= 3:
            # e.g. farm_a/shed_1_cam/image_20261002.jpg
            farm_id = parts[0]
            device_id = parts[1]
        elif len(parts) == 2:
            # e.g. shed_1_cam/image_20261002.jpg
            device_id = parts[0]
            farm_id = "default_farm"
        else:
            # e.g. image_20261002.jpg or camera/image_20261002.jpg
            farm_id = "default_farm"
            device_id = "default_device"

        return farm_id, device_id

    @classmethod
    def get_public_url(cls, file_path):
        """
        Returns the public URL for an object in Supabase Storage.
        """
        if not supabase:
            return f"https://mock-supabase.co/storage/v1/object/public/{BUCKET_NAME}/{file_path}"
        try:
            return supabase.storage.from_(BUCKET_NAME).get_public_url(file_path)
        except Exception:
            return f"https://supabase.co/storage/v1/object/public/{BUCKET_NAME}/{file_path}"

    @classmethod
    def process_single_image(cls, file_path):
        """
        Downloads the image from Supabase Storage, runs Apurva ResNet18 and
        Decision Fusion, associates with farm/shade telemetry, and saves prediction.
        """
        image_url = cls.get_public_url(file_path)

        # 1. Skip if already processed
        if cls.is_image_already_predicted(image_url, file_path):
            return {"status": "skipped", "reason": "already_predicted"}

        farm_id, device_id = cls.parse_farm_and_shade(file_path)
        print(f"[Image Pipeline] Processing image '{file_path}' for Farm: '{farm_id}', Shade/Device: '{device_id}'...")

        # 2. Download image bytes
        image_bytes = None
        if supabase:
            try:
                image_bytes = supabase.storage.from_(BUCKET_NAME).download(file_path)
            except Exception as dl_err:
                print(f"[Image Pipeline] Supabase Storage download error for '{file_path}': {dl_err}. Trying HTTP download...")
                try:
                    resp = requests.get(image_url, timeout=15)
                    if resp.status_code == 200:
                        image_bytes = resp.content
                except Exception as http_err:
                    print(f"[Image Pipeline] HTTP download failed: {http_err}")

        if not image_bytes:
            print(f"[Image Pipeline] Failed to download image bytes for '{file_path}'. Will retry later.")
            return {"status": "error", "reason": "download_failed"}

        # 3. Run Image Model Prediction (Apurva PyTorch ResNet-18)
        try:
            image_pred = ImagePredictor.predict(image_bytes)
        except Exception as pred_err:
            print(f"[Image Pipeline] Image prediction error for '{file_path}': {pred_err}")
            return {"status": "error", "reason": "prediction_failed"}

        # 4. Fetch Corresponding Environmental Telemetry Context
        telemetry_record = get_last_telemetry(device_id)
        if telemetry_record:
            temp = float(telemetry_record.get('temperature', 24.0))
            humid = float(telemetry_record.get('humidity', 60.0))
            ammonia = float(telemetry_record.get('ammonia', 10.0))
            sound_level = float(telemetry_record.get('sound_level', 50.0))
            existing_tel_id = telemetry_record.get('id')
            existing_img = telemetry_record.get('image_url')
        else:
            temp, humid, ammonia, sound_level = 24.0, 60.0, 10.0, 50.0
            existing_tel_id = None
            existing_img = None

        # 5. Attach image_url to telemetry
        telemetry_id = existing_tel_id
        # If latest telemetry row has no image assigned yet, update it
        if existing_tel_id and not existing_img and supabase:
            try:
                supabase.table("sensor_telemetry")\
                    .update({"image_url": image_url})\
                    .eq("id", existing_tel_id)\
                    .execute()
            except Exception as up_err:
                print(f"[Image Pipeline] Error updating telemetry row with image: {up_err}")
        else:
            # Save new telemetry record linked with image_url
            tel_res = save_telemetry(
                device_id=device_id,
                temperature=temp,
                humidity=humid,
                ammonia=ammonia,
                sound_level=sound_level,
                farm_id=farm_id,
                image_url=image_url
            )
            if tel_res.get("status") == "success" and tel_res.get("data"):
                telemetry_id = tel_res["data"][0].get("id")

        # 6. Environmental Base Prediction & Decision Fusion
        sensor_pred = ModelManager.predict_sensor([temp, humid, ammonia])

        active_batch = get_active_batch(farm_id)
        batch_id = active_batch.get("id") if active_batch else None
        recent_deaths = 0
        if batch_id:
            mort_records = get_mortality_records(batch_id)
            recent_deaths = sum(int(r.get("death_count", 0) or r.get("deathCount", 0)) for r in mort_records)

        fused_disease, confidence, prob_map = fuse_decisions(
            temp=temp,
            hum=humid,
            ammonia=ammonia,
            sensor_pred=sensor_pred,
            sound_pred=None,
            image_pred=image_pred,
            recent_deaths=recent_deaths
        )

        if fused_disease == "Healthy":
            risk_level = "LOW"
        elif confidence >= 0.70:
            risk_level = "HIGH"
        else:
            risk_level = "MEDIUM"

        if ammonia >= 25.0:
            risk_level = "HIGH"

        # 7. Generate Biosecurity Recommendation
        if ammonia >= 25.0:
            recommendation = f"CRITICAL AMMONIA ALERT: Air quality hazardous ({ammonia} ppm). Run exhaust fans at 100% capacity."
        elif fused_disease == "Infectious Coryza":
            recommendation = f"INFECTIOUS CORYZA ALERT: Facial swelling/discharge detected ({int(confidence * 100)}% confidence). Isolate symptomatic birds and sanitize water lines."
        elif fused_disease == "Fowlpox":
            recommendation = f"FOWLPOX ALERT: Scabs/lesions detected ({int(confidence * 100)}% confidence). Isolate affected birds and apply topical antiseptics."
        elif fused_disease == "Newcastle":
            recommendation = f"NEWCASTLE WARNING: High risk ({int(confidence * 100)}% confidence). Quarantine affected birds and consult flock veterinarian."
        elif fused_disease == "Avian Influenza":
            recommendation = f"AVIAN INFLUENZA WARNING: High risk ({int(confidence * 100)}% confidence). Severe lethargy/abnormal posture detected."
        elif fused_disease == "Coccidiosis":
            recommendation = f"COCCIDIOSIS DETECTED: Risk of digestive infection ({int(confidence * 100)}% confidence). Ensure litter is dry."
        else:
            recommendation = "LOW RISK: Flock visual and environmental conditions are healthy and stable."

        # 8. Handle Disease Event Tracking & Alerts for Abnormal Detections
        is_abnormal = (risk_level in ["MEDIUM", "HIGH"]) and (fused_disease != "Healthy")
        event_id = None

        if is_abnormal and supabase:
            matching_event = find_matching_active_event(device_id, fused_disease)
            if matching_event:
                updated_event = update_disease_event(
                    event=matching_event,
                    new_confidence=confidence,
                    new_image_url=image_url
                )
                if updated_event:
                    event_id = updated_event["id"]
            else:
                new_event = create_disease_event(
                    device_id=device_id,
                    batch_id=batch_id,
                    disease=fused_disease,
                    risk_level=risk_level,
                    confidence=confidence,
                    image_url=image_url
                )
                if new_event:
                    event_id = new_event["id"]

        # 9. Save Disease Prediction to Supabase Database
        pred_res = save_prediction(
            device_id=device_id,
            disease=fused_disease,
            risk_level=risk_level,
            confidence=confidence,
            recommendation=recommendation,
            farm_id=farm_id,
            telemetry_id=telemetry_id,
            event_id=event_id
        )

        saved_pred_id = None
        if pred_res.get("status") == "success" and pred_res.get("data"):
            saved_pred_id = pred_res["data"][0].get("id")

        if is_abnormal and saved_pred_id and supabase:
            alert_title = f"Disease Alert: {fused_disease} ({risk_level})"
            create_alert(
                batch_id=batch_id,
                device_id=device_id,
                prediction_id=saved_pred_id,
                title=alert_title,
                description=recommendation,
                severity=risk_level
            )

        # 10. Mark file as processed in cache
        cls._processed_cache.add(file_path)
        cls._processed_cache.add(image_url)

        print(f"[Image Pipeline] Successfully processed '{file_path}': Disease={fused_disease}, Risk={risk_level}, Conf={confidence:.2f}")

        return {
            "status": "success",
            "file_path": file_path,
            "imageUrl": image_url,
            "farm_id": farm_id,
            "device_id": device_id,
            "disease": fused_disease,
            "risk_level": risk_level,
            "confidence": float(confidence),
            "telemetry_id": telemetry_id
        }

    @classmethod
    def process_all_unprocessed_images(cls):
        """
        Finds all images in the bucket and processes any that do not have predictions.
        """
        all_images = cls.list_all_storage_images()
        if not all_images:
            return []

        results = []
        for img_path in all_images:
            try:
                res = cls.process_single_image(img_path)
                if res.get("status") == "success":
                    results.append(res)
            except Exception as e:
                print(f"[Image Pipeline] Error processing image '{img_path}': {e}")

        return results

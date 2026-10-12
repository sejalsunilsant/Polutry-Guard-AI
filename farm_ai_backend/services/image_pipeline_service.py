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

BUCKET_NAME = os.environ.get("SUPABASE_MEDIA_BUCKET", "poultry-media")

class ImagePipelineService:
    _is_running = False
    _watcher_started = False
    _processed_cache = set()
    _seen_files = set()
    _lock = threading.Lock()

    @classmethod
    def mark_file_handled(cls, file_path, image_url=None):
        """Marks a file as seen and handled to prevent duplicate watcher triggers."""
        if file_path:
            cls._seen_files.add(file_path)
            cls._processed_cache.add(file_path)
        if image_url:
            cls._processed_cache.add(image_url)

    @classmethod
    def start_background_watcher(cls, interval_seconds=60, startup_delay_seconds=15, sync_on_startup=False):
        """
        Starts the background worker as a strict singleton:
        1. Delays initial activity by startup_delay_seconds so Flask binds and passes Render health check promptly.
        2. Snapshots existing storage files and completed database records into baseline without heavy inference.
        3. Polls strictly for NEWLY uploaded images only.
        """
        with cls._lock:
            if cls._watcher_started:
                return
            cls._watcher_started = True

        thread = threading.Thread(
            target=cls._run_loop,
            args=(interval_seconds, startup_delay_seconds, sync_on_startup),
            daemon=True,
            name="ImageMLPipelineWatcher"
        )
        thread.start()
        print(f"[Image Pipeline] Background watcher scheduled (delay: {startup_delay_seconds}s, poll: {interval_seconds}s).")

    @classmethod
    def _run_loop(cls, interval_seconds, startup_delay_seconds, sync_on_startup):
        # 1. Grace Period: Allow Flask and Gunicorn to bind to 0.0.0.0:$PORT and pass port scan
        if startup_delay_seconds > 0:
            time.sleep(startup_delay_seconds)

        # 2. Baseline Storage & DB Snapshot: seed known files without downloading or predicting them
        try:
            from data.supabase_client import get_all_completed_image_paths
            completed_in_db = get_all_completed_image_paths()
            cls._seen_files.update(completed_in_db)
            cls._processed_cache.update(completed_in_db)

            existing_images = cls.list_all_storage_images()
            cls._seen_files.update(existing_images)
            print(f"[Image Pipeline] Initial baseline registered {len(cls._seen_files)} image(s) ({len(completed_in_db)} from DB).")
        except Exception as e:
            print(f"[Image Pipeline] Note during initial baseline snapshot: {e}")

        # 3. Optional backlog processing (disabled by default to prevent boot OOM)
        if sync_on_startup or os.environ.get("SYNC_BACKLOG_ON_STARTUP", "false").lower() == "true":
            print("[Image Pipeline] Running backlog scan for unpredicted images (SYNC_BACKLOG_ON_STARTUP=true)...")
            try:
                cls.process_all_unprocessed_images()
            except Exception as e:
                print(f"[Image Pipeline] Error in backlog scan: {e}")
        else:
            print("[Image Pipeline] Watcher active: pipeline triggers ONLY when a new image is uploaded.")

        cls._is_running = True

        # 4. Continuous watcher: triggers ONLY when a new image file is uploaded
        while cls._is_running:
            try:
                time.sleep(interval_seconds)
                cls.check_and_process_new_images()
            except Exception as e:
                print(f"[Image Pipeline] Error in background watcher loop: {e}")

    @classmethod
    def check_and_process_new_images(cls):
        """
        Polls storage and processes ONLY newly uploaded images that were not in baseline.
        Does not re-download or re-process existing images.
        """
        if not supabase:
            return []

        try:
            current_images = cls.list_all_storage_images()
            if not current_images:
                return []

            # Identify genuinely new files that appeared since last check
            new_images = [img for img in current_images if img not in cls._seen_files]
            if not new_images:
                return []

            print(f"[Image Pipeline] Detected {len(new_images)} newly uploaded image(s): {new_images}")
            results = []
            for img_path in new_images:
                cls._seen_files.add(img_path)
                try:
                    res = cls.process_single_image(img_path)
                    if res.get("status") == "success":
                        results.append(res)
                except Exception as ex:
                    print(f"[Image Pipeline] Error processing newly uploaded image '{img_path}': {ex}")
                finally:
                    import gc
                    gc.collect()

            return results
        except Exception as e:
            print(f"[Image Pipeline] Error checking for new images: {e}")
            return []

    @classmethod
    def trigger_single_image_async(cls, file_path):
        """
        Asynchronously triggers prediction pipeline for a single newly uploaded image.
        """
        if not file_path:
            return
        cls._seen_files.add(file_path)

        def _worker():
            try:
                cls.process_single_image(file_path)
            except Exception as e:
                print(f"[Image Pipeline] Async trigger error for '{file_path}': {e}")
            finally:
                import gc
                gc.collect()

        t = threading.Thread(target=_worker, daemon=True, name=f"ImageML-{file_path[:16]}")
        t.start()

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
        Checks in-memory cache first, then queries Supabase database status.
        """
        if file_path in cls._processed_cache or (image_url and image_url in cls._processed_cache):
            return True

        try:
            from data.supabase_client import get_image_prediction_status
            status_rec = get_image_prediction_status(file_path, image_url)
            if status_rec and str(status_rec.get("status", "")).upper() == "COMPLETED":
                cls.mark_file_handled(file_path, image_url)
                return True
        except Exception as e:
            print(f"[Image Pipeline] Note checking prediction status: {e}")

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
        Enforces database-level idempotency to prevent duplicate predictions across
        restarts, worker respawns, or webhook retries.
        """
        if not file_path:
            return {"status": "error", "reason": "file_path_required"}

        image_url = cls.get_public_url(file_path)

        # 1. Skip if already processed according to in-memory cache or DB status
        if cls.is_image_already_predicted(image_url, file_path):
            print(f"[Image Pipeline] Skipping '{file_path}': already predicted in database.")
            return {"status": "skipped", "reason": "already_predicted", "file_path": file_path, "imageUrl": image_url}

        farm_id, device_id = cls.parse_farm_and_shade(file_path)

        # 2. Database-level idempotency claim
        from data.supabase_client import (
            claim_image_for_processing,
            save_image_prediction_status
        )
        claimed, existing_rec = claim_image_for_processing(
            image_path=file_path,
            image_url=image_url,
            device_id=device_id,
            farm_id=farm_id
        )
        if not claimed:
            print(f"[Image Pipeline] Skipping '{file_path}': already claimed or completed in database.")
            cls.mark_file_handled(file_path, image_url)
            return {
                "status": "skipped",
                "reason": "already_claimed_or_completed",
                "file_path": file_path,
                "imageUrl": image_url,
                "details": existing_rec
            }

        print(f"[Image Pipeline] Processing image '{file_path}' for Farm: '{farm_id}', Shade/Device: '{device_id}'...")

        # 3. Download image bytes
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
            save_image_prediction_status(
                image_path=file_path,
                status="FAILED",
                image_url=image_url,
                device_id=device_id,
                farm_id=farm_id,
                error_message="download_failed"
            )
            return {"status": "error", "reason": "download_failed"}

        # 4. Run Image Model Prediction (Apurva PyTorch ResNet-18)
        try:
            image_pred = ImagePredictor.predict(image_bytes)
        except Exception as pred_err:
            print(f"[Image Pipeline] Image prediction error for '{file_path}': {pred_err}")
            save_image_prediction_status(
                image_path=file_path,
                status="FAILED",
                image_url=image_url,
                device_id=device_id,
                farm_id=farm_id,
                error_message=str(pred_err)
            )
            return {"status": "error", "reason": "prediction_failed"}
        finally:
            try:
                del image_bytes
            except Exception:
                pass

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

        # 10. Persist completion status to Supabase for database-level idempotency
        save_image_prediction_status(
            image_path=file_path,
            status="COMPLETED",
            image_url=image_url,
            disease=fused_disease,
            confidence=float(confidence),
            risk_level=risk_level,
            device_id=device_id,
            farm_id=farm_id,
            telemetry_id=telemetry_id,
            prediction_id=saved_pred_id,
            event_id=event_id
        )

        # 11. Mark file as processed in cache
        cls._seen_files.add(file_path)
        cls._processed_cache.add(file_path)
        cls._processed_cache.add(image_url)

        # Force garbage collection to free intermediate tensors and image buffers immediately
        import gc
        gc.collect()

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
            "telemetry_id": telemetry_id,
            "prediction_id": saved_pred_id,
            "event_id": event_id
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
            finally:
                import gc
                gc.collect()

        return results

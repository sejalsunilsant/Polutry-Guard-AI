import os
from data.supabase_client import save_prediction

def fuse_decisions(temp, hum, ammonia, sensor_pred, sound_pred, image_pred):
    """
    Weighted decision fusion engine combining environmental sensors (XGBoost),
    audio monitoring (TFLite/ONNX), and image monitoring (Heuristics/ONNX).
    
    Returns:
        tuple: (fused_disease, confidence, probabilities)
    """
    # Target categories
    classes = ["Healthy", "Respiratory Disease", "Digestive Disease", "Other"]
    prob_map = {c: 0.0 for c in classes}
    
    # 1. Base weights for active modalities
    w_sensor = 0.35
    w_sound = 0.40
    w_image = 0.25
    
    # Adjust weights if any modality failed
    active_modalities = 3
    if not sensor_pred or sensor_pred.get("status") == "error":
        active_modalities -= 1
        w_sensor = 0.0
    if not sound_pred or sound_pred.get("status") == "error":
        active_modalities -= 1
        w_sound = 0.0
    if not image_pred or image_pred.get("status") == "error":
        active_modalities -= 1
        w_image = 0.0
        
    # Re-normalize weights if some modalities are missing
    total_w = w_sensor + w_sound + w_image
    if total_w > 0.0:
        w_sensor /= total_w
        w_sound /= total_w
        w_image /= total_w
    else:
        # Fallback if everything is broken
        return "Healthy", 1.0, {"Healthy": 1.0, "Respiratory Disease": 0.0, "Digestive Disease": 0.0, "Other": 0.0}

    # 2. Add Sensor (XGBoost) Contribution
    if w_sensor > 0.0:
        # XGBoost output keys: None, Respiratory, Digestive, Other
        sensor_probs = sensor_pred.get("probabilities") or {}
        for key, p in sensor_probs.items():
            if key == "None":
                prob_map["Healthy"] += p * w_sensor
            elif key == "Respiratory":
                prob_map["Respiratory Disease"] += p * w_sensor
            elif key == "Digestive":
                prob_map["Digestive Disease"] += p * w_sensor
            else:
                prob_map["Other"] += p * w_sensor

    # 3. Add Sound Contribution
    if w_sound > 0.0:
        # Sound outputs: Healthy, Sick, None
        sound_class = sound_pred.get("prediction")
        sound_conf = sound_pred.get("confidence") or 0.0
        sound_probs = sound_pred.get("probabilities") or {}
        
        if sound_class == "Sick":
            prob_map["Respiratory Disease"] += sound_conf * w_sound
            # Distribute remaining sound confidence
            prob_map["Other"] += (1.0 - sound_conf) * 0.5 * w_sound
            prob_map["Digestive Disease"] += (1.0 - sound_conf) * 0.5 * w_sound
        elif sound_class == "Healthy":
            prob_map["Healthy"] += sound_conf * w_sound
            prob_map["Other"] += (1.0 - sound_conf) * w_sound
        else:
            # None or Unknown
            prob_map["Other"] += w_sound

    # 4. Add Image Contribution
    if w_image > 0.0:
        # Image outputs: Normal, Huddling, Lethargic, Crowding
        image_class = image_pred.get("prediction")
        image_conf = image_pred.get("confidence") or 0.0
        
        if image_class == "Normal":
            prob_map["Healthy"] += image_conf * w_image
            prob_map["Other"] += (1.0 - image_conf) * w_image
        elif image_class == "Huddling":
            prob_map["Respiratory Disease"] += image_conf * w_image
            prob_map["Other"] += (1.0 - image_conf) * w_image
        elif image_class == "Lethargic":
            # Lethargy is highly correlated with both respiratory and digestive diseases
            prob_map["Respiratory Disease"] += (image_conf * 0.5) * w_image
            prob_map["Digestive Disease"] += (image_conf * 0.5) * w_image
            prob_map["Other"] += (1.0 - image_conf) * w_image
        elif image_class == "Crowding":
            prob_map["Other"] += image_conf * w_image
            prob_map["Healthy"] += (1.0 - image_conf) * w_image

    # 5. Apply Critical Environmental & Multi-Modal Overrides (Heuristics)
    # Ammonia levels >= 25.0 ppm cause severe respiratory lining burns, high disease probability
    if ammonia is not None and float(ammonia) >= 25.0:
        prob_map["Respiratory Disease"] = max(prob_map["Respiratory Disease"], 0.85)
        # Redraw balance
        rem = 1.0 - prob_map["Respiratory Disease"]
        total_others = prob_map["Healthy"] + prob_map["Digestive Disease"] + prob_map["Other"]
        if total_others > 0.0:
            for k in ["Healthy", "Digestive Disease", "Other"]:
                prob_map[k] = (prob_map[k] / total_others) * rem
        else:
            prob_map["Healthy"] = rem * 0.5
            prob_map["Other"] = rem * 0.5

    # Combined Heat Stress & Ammonia
    elif temp is not None and float(temp) >= 30.0 and ammonia is not None and float(ammonia) >= 18.0:
        prob_map["Respiratory Disease"] = max(prob_map["Respiratory Disease"], 0.70)
        prob_map["Other"] = max(prob_map["Other"], 0.20)

    # Multi-modal sickness warning (Sick Sound + Lethargic Image)
    if sound_pred and sound_pred.get("prediction") == "Sick" and image_pred and image_pred.get("prediction") == "Lethargic":
        prob_map["Respiratory Disease"] = max(prob_map["Respiratory Disease"], 0.90)

    # 6. Select final decision
    fused_disease = max(prob_map, key=prob_map.get)
    confidence = prob_map[fused_disease]
    
    return fused_disease, float(confidence), prob_map


def fuse_and_store(device_id, telemetry_id, temp, hum, ammonia, sound_level, sensor_pred, sound_pred, image_pred, farm_id="default_farm"):
    """
    Fuses predictions from all modalities, calculates risk levels,
    generates biosecurity recommendations, and stores the results to Supabase.
    """
    # Run weighted fusion
    fused_disease, confidence, prob_map = fuse_decisions(temp, hum, ammonia, sensor_pred, sound_pred, image_pred)
    
    # 1. Determine risk level
    if fused_disease == "Healthy":
        risk_level = "LOW"
    elif confidence >= 0.75:
        risk_level = "HIGH"
    else:
        risk_level = "MEDIUM"
        
    # Double check ammonia risk overrides
    if ammonia is not None and float(ammonia) >= 25.0:
        risk_level = "HIGH"
        
    # 2. Formulate dynamic recommendations based on fused inputs
    if ammonia is not None and float(ammonia) >= 25.0:
        recommendation = (
            f"CRITICAL AMMONIA WARNING: Ammonia is extremely high ({ammonia} ppm). "
            "Turn exhaust ventilation fans to 100% immediately to flush the air and prevent permanent respiratory tract burns."
        )
    elif fused_disease == "Respiratory Disease":
        sound_conf = sound_pred.get("confidence", 0.0) if sound_pred else 0.0
        recommendation = (
            f"RESPIRATORY DISEASE ALERT: High probability of respiratory infection ({int(confidence * 100)}% confidence). "
            f"Acoustic monitor detected sick gurgle/cough vocalizations ({int(sound_conf * 100)}% confidence). "
            "Inspect ventilation levels, spray antiviral sanitizer mist, and isolate showing birds."
        )
    elif fused_disease == "Digestive Disease":
        img_conf = image_pred.get("confidence", 0.0) if image_pred else 0.0
        recommendation = (
            f"DIGESTIVE DISEASE ALERT: Risk of digestive infection ({int(confidence * 100)}% confidence). "
            f"Camera sensors detected lethargic posture behaviors ({int(img_conf * 100)}% confidence). "
            "Ensure litter is dry to prevent coccidiosis spore germination, inspect feed sanitation, and query vet."
        )
    elif fused_disease == "Other":
        if sound_level is not None and float(sound_level) >= 78.0:
            recommendation = (
                f"NOISE STRESS WARNING: Sound levels are critical ({sound_level} dB). "
                "flock is screaming or panic stampeding. Inspect shed immediately for predator entry or power failures."
            )
        else:
            recommendation = (
                "ENVIRONMENTAL STRESS ACTIVE: Environmental sensors show slight temperature/humidity stress. "
                "Adjust intake vents to stabilize comfort zones."
            )
    else:
        # Healthy
        recommendation = (
            "LOW DISEASE RISK: Environment parameters (Temp, Humidity, Ammonia) and flock behavior "
            "(sound, visual movement) are all within ideal comfort zones."
        )

    # 3. Store result to Supabase database
    # In order to store it, convert the fused_disease string (e.g. 'Respiratory Disease')
    # to database ENUM mappings: 'Respiratory', 'Digestive', 'None'
    db_disease = "None"
    if fused_disease == "Respiratory Disease":
        db_disease = "Respiratory"
    elif fused_disease == "Digestive Disease":
        db_disease = "Digestive"
    elif fused_disease == "Other":
        db_disease = "Other"

    db_res = save_prediction(
        device_id=device_id,
        disease=db_disease,
        risk_level=risk_level,
        confidence=confidence,
        recommendation=recommendation,
        telemetry_id=telemetry_id,
        farm_id=farm_id
    )
    
    return {
        "disease": fused_disease,
        "risk_level": risk_level,
        "confidence": confidence,
        "recommendation": recommendation,
        "db_status": db_res.get("status", "fallback"),
        "probabilities": prob_map
    }

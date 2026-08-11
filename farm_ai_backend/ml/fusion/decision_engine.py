import os
from data.supabase_client import save_prediction

def fuse_decisions(temp, hum, ammonia, sensor_pred, sound_pred, image_pred, egg_drop=False, feed_drop=False):
    """
    Weighted decision fusion engine combining environmental sensors (XGBoost),
    audio monitoring (TFLite/ONNX), and image monitoring (Heuristics/ONNX).
    
    Returns:
        tuple: (fused_disease, confidence, probabilities)
    """
    # Specific target categories
    classes = ["Healthy", "Coccidiosis", "Newcastle", "Avian Influenza", "Fowlpox", "Infectious Bronchitis"]
    prob_map = {c: 0.0 for c in classes}
    
    # 1. Base weights for active modalities
    w_sensor = 0.35  # Environment prior
    w_sound = 0.40   # Acoustic monitoring
    w_image = 0.25   # Visual behavior monitoring
    
    # Adjust weights if any modality failed or is missing
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
        return "Healthy", 1.0, {"Healthy": 1.0, "Coccidiosis": 0.0, "Newcastle": 0.0, "Avian Influenza": 0.0, "Fowlpox": 0.0, "Infectious Bronchitis": 0.0}

    # 2. Add Sensor (XGBoost) Environmental Context Contribution (Prior Probability)
    # The XGBoost model predicts: Coccidiosis, Fowlpox, Healthy, Infectious Bronchitis, Newcastle.
    # Note: Avian Influenza is mapped dynamically as environmental context prior.
    if w_sensor > 0.0:
        sensor_probs = sensor_pred.get("probabilities") or {}
        for key, p in sensor_probs.items():
            if key in prob_map:
                prob_map[key] += p * w_sensor
                
        # Temperature + Humidity cold stress adjustment: Avian Influenza is highly associated with cold stress
        if temp is not None and float(temp) < 18.0:
            # Shift some of the environmental prior to Avian Influenza
            ib_prior = sensor_probs.get("Infectious Bronchitis", 0.0)
            nc_prior = sensor_probs.get("Newcastle", 0.0)
            shift = (ib_prior * 0.4 + nc_prior * 0.4) * w_sensor
            prob_map["Avian Influenza"] += shift
            prob_map["Infectious Bronchitis"] = max(0.0, prob_map["Infectious Bronchitis"] - (ib_prior * 0.4 * w_sensor))
            prob_map["Newcastle"] = max(0.0, prob_map["Newcastle"] - (nc_prior * 0.4 * w_sensor))

    # 3. Add Sound Acoustic Symptoms Contribution
    if w_sound > 0.0:
        sound_symptoms = sound_pred.get("symptoms") or {}
        resp_sounds = sound_symptoms.get("respiratory_sounds", 0.0)
        normal_sound = sound_symptoms.get("normal_acoustic_pattern", 0.0)
        
        # Respiratory sounds (coughing/gasping) can indicate Newcastle, IB, or AI
        if resp_sounds > 0.0:
            # Distribute based on disease relevance
            prob_map["Newcastle"] += resp_sounds * w_sound * 0.4
            prob_map["Infectious Bronchitis"] += resp_sounds * w_sound * 0.4
            prob_map["Avian Influenza"] += resp_sounds * w_sound * 0.2
            
        if normal_sound > 0.0:
            prob_map["Healthy"] += normal_sound * w_sound

    # 4. Add Image Visual Symptoms Contribution
    if w_image > 0.0:
        img_symptoms = image_pred.get("symptoms") or {}
        lethargy = img_symptoms.get("lethargy", 0.0)
        sitting_lying = img_symptoms.get("sitting_lying", 0.0)
        abnormal_posture = img_symptoms.get("abnormal_posture", 0.0)
        reduced_activity = img_symptoms.get("reduced_activity", 0.0)
        abnormal_appearance = img_symptoms.get("abnormal_appearance", 0.0)
        scabby_lesions = img_symptoms.get("scabby_lesions", 0.0)
        normal_vis = img_symptoms.get("normal_posture_activity", 0.0)
        
        # Coccidiosis: lethargy, sitting/lying, abnormal posture, reduced activity
        cocc_vis = (sitting_lying * 0.4 + lethargy * 0.2 + abnormal_posture * 0.2 + reduced_activity * 0.2)
        prob_map["Coccidiosis"] += cocc_vis * w_image
        
        # Newcastle: abnormal posture/behavior, respiratory signs
        newc_vis = (abnormal_posture * 0.6 + lethargy * 0.4)
        prob_map["Newcastle"] += newc_vis * w_image
        
        # Avian Influenza: lethargy, reduced activity, abnormal appearance
        ai_vis = (lethargy * 0.4 + reduced_activity * 0.3 + abnormal_appearance * 0.3)
        prob_map["Avian Influenza"] += ai_vis * w_image
        
        # Fowlpox: scabby/raised lesions on comb/wattles/eyelids, mouth/throat lesions
        fowl_vis = (scabby_lesions * 0.8 + lethargy * 0.2)
        prob_map["Fowlpox"] += fowl_vis * w_image
        
        if normal_vis > 0.0:
            prob_map["Healthy"] += normal_vis * w_image

    # 5. Apply Critical Environmental & Multi-Modal Overrides (Heuristics)
    
    # Ammonia (air-quality/respiratory stress feature)
    # Ammonia >= 25.0 ppm causes severe respiratory lining burns and high susceptibility
    if ammonia is not None:
        nh3 = float(ammonia)
        if nh3 >= 25.0:
            # Boost respiratory disease priors significantly
            prob_map["Infectious Bronchitis"] = max(prob_map["Infectious Bronchitis"], 0.45)
            prob_map["Newcastle"] = max(prob_map["Newcastle"], 0.35)
            prob_map["Avian Influenza"] = max(prob_map["Avian Influenza"], 0.20)
            
            # Reduce Healthy probability
            prob_map["Healthy"] = max(0.0, prob_map["Healthy"] - 0.60)
        elif nh3 >= 18.0:
            # Moderate stress boost
            prob_map["Infectious Bronchitis"] = max(prob_map["Infectious Bronchitis"], 0.25)
            prob_map["Newcastle"] = max(prob_map["Newcastle"], 0.20)
            prob_map["Healthy"] = max(0.0, prob_map["Healthy"] - 0.25)

    # Production/Feeding drop overrides for Fowlpox and Coccidiosis
    if egg_drop or feed_drop:
        # Fowlpox reduces activity, feeding, and egg production
        prob_map["Fowlpox"] = max(prob_map["Fowlpox"], 0.35 if egg_drop and feed_drop else 0.20)
        # Coccidiosis reduces feeding/activity
        if feed_drop:
            prob_map["Coccidiosis"] = max(prob_map["Coccidiosis"], 0.30)

    # Multi-modal Newcastle specific indicator: abnormal posture (image) + coughing/gasping (sound)
    if (image_pred and image_pred.get("prediction") in ["Lethargic", "Huddling"] 
        and sound_pred and sound_pred.get("prediction") == "Sick"):
        # Very high confidence indicator of Newcastle/Avian Influenza
        prob_map["Newcastle"] = max(prob_map["Newcastle"], 0.70)
        prob_map["Avian Influenza"] = max(prob_map["Avian Influenza"], 0.25)

    # Normalize probabilities to sum to 1.0
    total_prob = sum(prob_map.values())
    if total_prob > 0.0:
        prob_map = {k: v / total_prob for k, v in prob_map.items()}
    else:
        prob_map["Healthy"] = 1.0

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
    elif confidence >= 0.70:
        risk_level = "HIGH"
    else:
        risk_level = "MEDIUM"
        
    # Double check ammonia risk overrides
    if ammonia is not None and float(ammonia) >= 25.0:
        risk_level = "HIGH"
        
    # 2. Formulate dynamic recommendations based on fused inputs
    if ammonia is not None and float(ammonia) >= 25.0:
        recommendation = (
            f"CRITICAL AMMONIA ALERT: Air quality is hazardous ({ammonia} ppm). "
            "Exhaust ventilation fans must run at 100% capacity to flush the house and prevent permanent respiratory tract burns."
        )
    elif fused_disease == "Newcastle":
        recommendation = (
            f"NEWCASTLE DISEASE WARNING: High risk of Newcastle infection ({int(confidence * 100)}% confidence). "
            "Acoustic/visual metrics show gasping and abnormal posture. Quarantine affected birds and contact your vet immediately."
        )
    elif fused_disease == "Avian Influenza":
        recommendation = (
            f"AVIAN INFLUENZA WARNING: High risk of Avian Influenza ({int(confidence * 100)}% confidence). "
            "Visual monitors show extreme lethargy and abnormal appearance. Alert biosecurity officers and isolate the flock."
        )
    elif fused_disease == "Coccidiosis":
        recommendation = (
            f"COCCIDIOSIS DETECTED: Elevated risk of digestive infection ({int(confidence * 100)}% confidence). "
            "Visual cues show huddling/lying. Ensure composted litter is dry, feed is dry, and treat with coccidiostats."
        )
    elif fused_disease == "Fowlpox":
        recommendation = (
            f"FOWLPOX ALERT: High risk of Fowlpox infection ({int(confidence * 100)}% confidence). "
            "Visual check reveals possible comb/wattle lesions. Isolate symptomatic birds, control mosquitos, and apply antiseptic."
        )
    elif fused_disease == "Infectious Bronchitis":
        recommendation = (
            f"INFECTIOUS BRONCHITIS ALERT: High risk of IB respiratory infection ({int(confidence * 100)}% confidence). "
            "Detected heavy coughing/gasping. Stabilize shed temperature and mist disinfectant to suppress aerosol transmission."
        )
    else:
        # Healthy
        recommendation = (
            "LOW DISEASE RISK: Environment parameters (Temp, Humidity, Ammonia) and flock behavior "
            "(sound, visual movement) are all within ideal comfort zones."
        )

    # 3. Store result to Supabase database (saving the specific disease string directly)
    db_res = save_prediction(
        device_id=device_id,
        disease=fused_disease,
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

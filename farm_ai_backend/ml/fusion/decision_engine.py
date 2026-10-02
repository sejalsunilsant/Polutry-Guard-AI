import os
from typing import Any, Dict, Optional, Tuple
from data.supabase_client import save_prediction
from ml.fusion.sound_adapter import SoundAdapter

def fuse_decisions(
    temp: Optional[float] = None,
    hum: Optional[float] = None,
    ammonia: Optional[float] = None,
    sensor_pred: Optional[Dict[str, Any]] = None,
    sound_pred: Optional[Dict[str, Any]] = None,
    image_pred: Optional[Dict[str, Any]] = None,
    egg_drop: bool = False,
    feed_drop: bool = False,
    recent_deaths: int = 0,
) -> Tuple[str, float, Dict[str, float]]:
    """
    Weighted decision fusion engine combining Apurva's environmental XGBoost model,
    the existing audio monitoring model (via SoundAdapter), and Apurva's visual ResNet-18 model.
    
    Target Categories:
        - Healthy
        - Fowlpox
        - Infectious Coryza
    
    Returns:
        tuple: (fused_disease, confidence, probabilities)
    """
    classes = ["Healthy", "Fowlpox", "Infectious Coryza"]
    prob_map = {c: 0.0 for c in classes}
    
    # 1. Base weights for active modalities
    w_sensor = 0.35  # Environmental prior (Apurva XGBoost)
    w_sound = 0.35   # Acoustic monitoring (Existing Sound model via Adapter)
    w_image = 0.30   # Visual disease monitoring (Apurva ResNet-18)
    
    # Adapt sound model outputs into fusion feature representation
    sound_adapted = SoundAdapter.adapt_for_fusion(sound_pred)

    # Adjust weights if any modality is missing or failed
    if not sensor_pred or sensor_pred.get("status") == "error":
        w_sensor = 0.0
    if not sound_adapted.get("active"):
        w_sound = 0.0
    if not image_pred or image_pred.get("status") == "error":
        w_image = 0.0
        
    # Re-normalize weights if some modalities are inactive
    total_w = w_sensor + w_sound + w_image
    if total_w > 0.0:
        w_sensor /= total_w
        w_sound /= total_w
        w_image /= total_w
    else:
        # Fallback if all modalities are unavailable
        return "Healthy", 1.0, {"Healthy": 1.0, "Fowlpox": 0.0, "Infectious Coryza": 0.0}

    # 2. Add Sensor (Apurva XGBoost) Environmental Context Contribution (Prior Probability)
    if w_sensor > 0.0:
        sensor_probs = sensor_pred.get("probabilities") or {}
        for key in classes:
            prob_map[key] += float(sensor_probs.get(key, 0.0)) * w_sensor

    # 3. Add Sound Acoustic Symptoms Contribution (via SoundAdapter)
    if w_sound > 0.0:
        sound_priors = sound_adapted.get("disease_priors") or {}
        for key in classes:
            prob_map[key] += float(sound_priors.get(key, 0.0)) * w_sound

    # 4. Add Image Visual Symptoms Contribution (Apurva ResNet-18)
    if w_image > 0.0:
        image_probs = image_pred.get("probabilities") or {}
        for key in classes:
            prob_map[key] += float(image_probs.get(key, 0.0)) * w_image

    # 5. Multi-Modal Synergies & Clinical Overrides
    
    # Ammonia Override (hazardous air quality induces severe respiratory mucosal damage)
    if ammonia is not None:
        nh3 = float(ammonia)
        if nh3 >= 25.0:
            # Toxic ammonia heavily elevates Infectious Coryza respiratory vulnerability
            prob_map["Infectious Coryza"] = max(prob_map["Infectious Coryza"], 0.55)
            prob_map["Healthy"] = max(0.0, prob_map["Healthy"] - 0.50)
        elif nh3 >= 18.0:
            prob_map["Infectious Coryza"] = max(prob_map["Infectious Coryza"], 0.30)
            prob_map["Healthy"] = max(0.0, prob_map["Healthy"] - 0.20)

    # Multi-modal synergy: Visual symptoms + Acoustic coughing/gasping
    img_symptoms = (image_pred.get("symptoms") if image_pred else {}) or {}
    sound_symptoms = sound_adapted.get("symptoms", {})
    resp_sounds = sound_symptoms.get("respiratory_sounds", 0.0)

    # If visual model detects Infectious Coryza signs AND sound model detects coughing/gasping
    if img_symptoms.get("facial_swelling_nasal_discharge", 0.0) > 0.4 and resp_sounds > 0.4:
        prob_map["Infectious Coryza"] = max(prob_map["Infectious Coryza"], 0.80)
        prob_map["Healthy"] = max(0.0, prob_map["Healthy"] - 0.50)

    # If visual model detects Fowlpox scabby lesions strongly
    if img_symptoms.get("scabby_lesions", 0.0) > 0.5:
        prob_map["Fowlpox"] = max(prob_map["Fowlpox"], 0.75)
        prob_map["Healthy"] = max(0.0, prob_map["Healthy"] - 0.40)

    # Production/Feeding drop overrides
    if egg_drop or feed_drop:
        # Fowlpox and Coryza both cause significant anorexia and egg drops
        prob_map["Fowlpox"] = max(prob_map["Fowlpox"], 0.25 if egg_drop and feed_drop else 0.15)
        prob_map["Infectious Coryza"] = max(prob_map["Infectious Coryza"], 0.30 if feed_drop else 0.15)

    # Mortality adjustments
    if recent_deaths > 0:
        prob_map["Healthy"] = max(0.0, prob_map["Healthy"] - (0.15 * recent_deaths))
        if recent_deaths >= 5:
            prob_map["Infectious Coryza"] = max(prob_map["Infectious Coryza"], 0.45)
            prob_map["Fowlpox"] = max(prob_map["Fowlpox"], 0.35)
        else:
            prob_map["Infectious Coryza"] = max(prob_map["Infectious Coryza"], 0.25)

    # Re-normalize probabilities strictly to sum to 1.0
    total_prob = sum(prob_map.values())
    if total_prob > 0.0:
        prob_map = {k: v / total_prob for k, v in prob_map.items()}
    else:
        prob_map["Healthy"] = 1.0

    # 6. Select final decision
    fused_disease = max(prob_map, key=prob_map.get)
    confidence = float(prob_map[fused_disease])
    
    return fused_disease, confidence, prob_map


def fuse_and_store(
    device_id: str,
    telemetry_id: Optional[str],
    temp: Optional[float],
    hum: Optional[float],
    ammonia: Optional[float],
    sound_level: Optional[float],
    sensor_pred: Optional[Dict[str, Any]],
    sound_pred: Optional[Dict[str, Any]],
    image_pred: Optional[Dict[str, Any]],
    farm_id: str = "default_farm",
) -> Dict[str, Any]:
    """
    Fuses predictions from all modalities, calculates risk levels,
    generates biosecurity recommendations, and stores the results to Supabase.
    """
    from data.supabase_client import get_active_batch, get_mortality_records
    active_batch = get_active_batch(farm_id)
    batch_id = active_batch.get("id") if active_batch else None
    
    recent_deaths = 0
    if batch_id:
        mort_records = get_mortality_records(batch_id)
        recent_deaths = sum(int(r.get("death_count", 0) or r.get("deathCount", 0)) for r in mort_records)

    # Run weighted multi-modal fusion
    fused_disease, confidence, prob_map = fuse_decisions(
        temp=temp,
        hum=hum,
        ammonia=ammonia,
        sensor_pred=sensor_pred,
        sound_pred=sound_pred,
        image_pred=image_pred,
        recent_deaths=recent_deaths,
    )
    
    # 1. Determine risk level
    if fused_disease == "Healthy":
        risk_level = "LOW"
    elif confidence >= 0.70:
        risk_level = "HIGH"
    else:
        risk_level = "MEDIUM"
        
    # Ammonia hazard override
    if ammonia is not None and float(ammonia) >= 25.0:
        risk_level = "HIGH"
        
    # 2. Formulate dynamic recommendations based on fused inputs
    if ammonia is not None and float(ammonia) >= 25.0:
        recommendation = (
            f"CRITICAL AMMONIA ALERT: Air quality is hazardous ({ammonia} ppm). "
            "Exhaust ventilation fans must run at 100% capacity to flush the house and prevent permanent respiratory tract burns."
        )
    elif fused_disease == "Infectious Coryza":
        recommendation = (
            f"INFECTIOUS CORYZA ALERT: Elevated risk of acute respiratory bacterial infection ({int(confidence * 100)}% confidence). "
            "Acoustic gasping/rales and facial swelling detected. Consult flock veterinarian for antimicrobial therapy, separate symptomatic birds, and sanitize water lines."
        )
    elif fused_disease == "Fowlpox":
        recommendation = (
            f"FOWLPOX ALERT: High risk of viral Fowlpox infection ({int(confidence * 100)}% confidence). "
            "Visual cues show possible cutaneous/diphtheritic lesions on unfeathered skin. Isolate symptomatic birds, implement mosquito vector control, and apply topical antiseptics."
        )
    else:
        recommendation = (
            "LOW DISEASE RISK: Environment parameters (Temp, Humidity, Ammonia), flock vocalizations, "
            "and visual activity are within stable comfort ranges."
        )

    # 3. Store result to Supabase database
    db_res = save_prediction(
        device_id=device_id,
        disease=fused_disease,
        risk_level=risk_level,
        confidence=confidence,
        recommendation=recommendation,
        telemetry_id=telemetry_id,
        farm_id=farm_id,
    )
    
    return {
        "disease": fused_disease,
        "risk_level": risk_level,
        "confidence": confidence,
        "recommendation": recommendation,
        "db_status": db_res.get("status", "fallback"),
        "probabilities": prob_map,
    }

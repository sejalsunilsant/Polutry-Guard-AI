"""
Live Context Fetcher — fetches real-time structured data from Supabase
for the authenticated farmer's active batch.

This module is the authoritative source for rapidly-changing data that should NOT
be embedded into Qdrant (batch counts, mortality, disease events, alerts, telemetry).
Data is fetched fresh at chat time to ensure the chatbot always has the latest information.
"""


def fetch_live_batch_context(farm_id, batch_id=None):
    """
    Fetch comprehensive live context from Supabase for a farmer's batch.
    
    Returns a dict with all available batch-specific data, or an empty dict
    if the database is unavailable.
    
    Args:
        farm_id: Verified farm_id (server-side authenticated).
        batch_id: Verified batch_id, or None to auto-detect active batch.
    
    Returns:
        dict with keys: batch, mortality_records, mortality_summary,
        disease_events, alerts, latest_telemetry, or empty dict on failure.
    """
    from data.supabase_client import supabase
    
    context = {
        "batch": None,
        "mortality_records": [],
        "mortality_summary": {},
        "disease_events": [],
        "alerts": [],
        "latest_telemetry": None
    }
    
    if not farm_id:
        return context
    
    try:
        # 1. Fetch active batch (or specific batch)
        batch = _fetch_batch(farm_id, batch_id)
        if batch:
            context["batch"] = batch
            resolved_batch_id = batch.get("id")
            
            # 2. Fetch mortality records for this batch
            context["mortality_records"] = _fetch_mortality_records(resolved_batch_id)
            context["mortality_summary"] = _compute_mortality_summary(batch, context["mortality_records"])
            
            # 3. Fetch active disease events for this batch
            context["disease_events"] = _fetch_disease_events(resolved_batch_id)
            
            # 4. Fetch unresolved alerts for this batch
            context["alerts"] = _fetch_alerts(resolved_batch_id)
        
        # 5. Fetch latest telemetry (device-level, linked via farm)
        context["latest_telemetry"] = _fetch_latest_telemetry(farm_id)
        
    except Exception as e:
        print(f"[Live Context] Error fetching live context for farm={farm_id}, batch={batch_id}: {e}")
    
    return context


def _fetch_batch(farm_id, batch_id=None):
    """Fetch batch details — specific batch or active batch for the farm."""
    from data.supabase_client import supabase, get_active_batch
    
    if batch_id:
        # Fetch specific batch (verified to belong to this farm)
        if not supabase:
            return None
        try:
            res = supabase.table("v_batches") \
                .select("*") \
                .eq("id", batch_id) \
                .eq("farm_id", farm_id) \
                .execute()
            if res.data and len(res.data) > 0:
                return res.data[0]
        except Exception as e:
            print(f"[Live Context] Error fetching batch {batch_id}: {e}")
        return None
    else:
        # Fall back to active batch
        return get_active_batch(farm_id)


def _fetch_mortality_records(batch_id, limit=10):
    """Fetch recent mortality records for a batch."""
    from data.supabase_client import supabase
    
    if not supabase or not batch_id:
        return []
    try:
        res = supabase.table("batch_mortality") \
            .select("*") \
            .eq("batch_id", batch_id) \
            .order("recorded_at", desc=True) \
            .limit(limit) \
            .execute()
        return res.data or []
    except Exception as e:
        print(f"[Live Context] Error fetching mortality records for batch {batch_id}: {e}")
        return []


def _compute_mortality_summary(batch, mortality_records):
    """Compute aggregate mortality statistics."""
    if not batch:
        return {}
    
    initial_count = batch.get("initial_count", 0)
    current_count = batch.get("current_count", 0)
    total_deaths = initial_count - current_count if initial_count > 0 else 0
    
    mortality_rate = 0.0
    if initial_count > 0:
        mortality_rate = round((total_deaths / initial_count) * 100, 2)
    
    return {
        "initial_count": initial_count,
        "current_count": current_count,
        "total_deaths": total_deaths,
        "mortality_rate_percent": mortality_rate,
        "recent_record_count": len(mortality_records)
    }


def _fetch_disease_events(batch_id, limit=5):
    """Fetch active/recent disease events for a batch."""
    from data.supabase_client import supabase
    
    if not supabase or not batch_id:
        return []
    try:
        res = supabase.table("disease_events") \
            .select("*") \
            .eq("batch_id", batch_id) \
            .order("created_at", desc=True) \
            .limit(limit) \
            .execute()
        return res.data or []
    except Exception as e:
        print(f"[Live Context] Error fetching disease events for batch {batch_id}: {e}")
        return []


def _fetch_alerts(batch_id, limit=10):
    """Fetch unresolved alerts for a batch."""
    from data.supabase_client import supabase
    
    if not supabase or not batch_id:
        return []
    try:
        res = supabase.table("alerts") \
            .select("*") \
            .eq("batch_id", batch_id) \
            .eq("status", "UNRESOLVED") \
            .order("created_at", desc=True) \
            .limit(limit) \
            .execute()
        return res.data or []
    except Exception as e:
        print(f"[Live Context] Error fetching alerts for batch {batch_id}: {e}")
        return []


def _fetch_latest_telemetry(farm_id):
    """Fetch the latest telemetry reading for the farm's device."""
    from data.supabase_client import supabase
    
    if not supabase or not farm_id:
        return None
    try:
        # Resolve device_id from farm
        device_res = supabase.table("devices") \
            .select("id") \
            .eq("farm_id", farm_id) \
            .limit(1) \
            .execute()
        
        if not device_res.data:
            return None
        
        device_id = device_res.data[0].get("id")
        
        # Fetch latest telemetry
        telemetry_res = supabase.table("sensor_telemetry") \
            .select("*") \
            .eq("device_id", device_id) \
            .order("created_at", desc=True) \
            .limit(1) \
            .execute()
        
        if telemetry_res.data and len(telemetry_res.data) > 0:
            return telemetry_res.data[0]
        return None
    except Exception as e:
        print(f"[Live Context] Error fetching telemetry for farm {farm_id}: {e}")
        return None

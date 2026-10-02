def build_rag_system_prompt(farm_context, retrieved_knowledge, live_context=None):
    """
    Format system instructions incorporating live farm context telemetry, RAG chunks,
    and authoritative live batch/mortality/disease/alert data from the database.
    
    Args:
        farm_context: Dict with real-time sensor telemetry from the client.
        retrieved_knowledge: List of RAG-retrieved knowledge chunks from Qdrant/TF-IDF.
        live_context: Dict from live_context.fetch_live_batch_context() with authoritative
                      batch, mortality, disease, alert data from Supabase. Optional.
    """
    # Extract client telemetry (used as supplementary/real-time signal)
    temp = farm_context.get('currentTemperature', 24.0)
    humid = farm_context.get('currentHumidity', 60.0)
    ammonia = farm_context.get('currentAmmonia', 10.0)
    sound = farm_context.get('currentSoundLevel', 50.0)
    birds = farm_context.get('birdCount', 0)
    deaths = farm_context.get('loggedMortalities', 0)
    shed = farm_context.get('activeShed', 'Unknown')

    # --- Override with authoritative DB data if available ---
    if live_context:
        db_telemetry = live_context.get("latest_telemetry")
        if db_telemetry:
            temp = float(db_telemetry.get("temperature", temp))
            humid = float(db_telemetry.get("humidity", humid))
            ammonia = float(db_telemetry.get("ammonia", ammonia))
            sound = float(db_telemetry.get("sound_level", sound))
        
        batch = live_context.get("batch")
        if batch:
            birds = batch.get("current_count", birds)
            shed = f"{batch.get('id', 'Unknown')} ({batch.get('breed', 'Broiler')})"
        
        mortality_summary = live_context.get("mortality_summary", {})
        if mortality_summary:
            deaths = mortality_summary.get("total_deaths", deaths)

    # --- RAG Knowledge Section ---
    knowledge_text = "\n\n".join([f"• {chunk}" for chunk in retrieved_knowledge])
    if not knowledge_text:
        knowledge_text = "No direct matching knowledge base articles found. Rely on general biosecurity best practices."

    # --- Build the system prompt ---
    system_instructions = (
        f"You are Poultry Guard AI, an expert veterinary and agricultural copilot.\n\n"
        f"CURRENT FARM TELEMETRY IN {shed}:\n"
        f"- Temperature: {temp}°C (Ideal: 21-27°C)\n"
        f"- Humidity: {humid}% (Ideal: 50-70%)\n"
        f"- Ammonia Level: {ammonia} ppm (Safe: <20 ppm)\n"
        f"- Acoustic Panic/Sound: {sound} dB (Safe: 40-65 dB)\n"
        f"- Active Birds: {birds}\n"
        f"- Total Deaths (This Batch): {deaths}\n"
    )

    # --- Batch Details Section ---
    if live_context and live_context.get("batch"):
        batch = live_context["batch"]
        system_instructions += (
            f"\nACTIVE BATCH DETAILS:\n"
            f"- Batch ID: {batch.get('id', 'Unknown')}\n"
            f"- Breed: {batch.get('breed', 'Unknown')}\n"
            f"- Start Date: {batch.get('start_date', 'Unknown')}\n"
            f"- Age (Days): {batch.get('age_days', 'Unknown')}\n"
            f"- Initial Bird Count: {batch.get('initial_count', 'Unknown')}\n"
            f"- Current Bird Count: {batch.get('current_count', 'Unknown')}\n"
            f"- Status: {batch.get('status', 'Unknown')}\n"
        )

    # --- Mortality Summary Section ---
    if live_context and live_context.get("mortality_summary"):
        ms = live_context["mortality_summary"]
        system_instructions += (
            f"\nMORTALITY SUMMARY:\n"
            f"- Total Deaths: {ms.get('total_deaths', 0)}\n"
            f"- Mortality Rate: {ms.get('mortality_rate_percent', 0.0)}%\n"
            f"- Initial Count: {ms.get('initial_count', 0)}\n"
            f"- Current Count: {ms.get('current_count', 0)}\n"
        )
    
    # --- Recent Mortality Records ---
    if live_context and live_context.get("mortality_records"):
        records = live_context["mortality_records"][:5]  # Last 5
        system_instructions += f"\nRECENT MORTALITY RECORDS ({len(records)} most recent):\n"
        for rec in records:
            system_instructions += (
                f"- [{rec.get('recorded_at', 'Unknown')}] "
                f"Deaths: {rec.get('death_count', 0)}, "
                f"Cause: {rec.get('reason', 'Unspecified')}, "
                f"Notes: {rec.get('notes', 'None')}\n"
            )

    # --- Disease Events Section ---
    if live_context and live_context.get("disease_events"):
        events = live_context["disease_events"]
        system_instructions += f"\nDISEASE EVENTS ({len(events)} recent):\n"
        for evt in events:
            system_instructions += (
                f"- {evt.get('disease', 'Unknown')} — "
                f"Risk: {evt.get('risk_level', 'Unknown')}, "
                f"Confidence: {evt.get('max_confidence', 0.0)}, "
                f"Status: {evt.get('status', 'Unknown')}, "
                f"Predictions: {evt.get('prediction_count', 0)}, "
                f"Period: {evt.get('start_time', '?')} to {evt.get('end_time', '?')}\n"
            )

    # --- Active Alerts Section ---
    if live_context and live_context.get("alerts"):
        alerts = live_context["alerts"]
        system_instructions += f"\nACTIVE ALERTS ({len(alerts)} unresolved):\n"
        for alert in alerts:
            system_instructions += (
                f"- [{alert.get('severity', 'MEDIUM')}] {alert.get('title', 'Alert')}: "
                f"{alert.get('description', 'No details')} "
                f"(Created: {alert.get('created_at', 'Unknown')})\n"
            )

    # --- RAG Knowledge Section ---
    system_instructions += (
        f"\nRELEVANT POULTRY GUIDELINES FROM VECTOR KNOWLEDGE BASE:\n"
        f"{knowledge_text}\n\n"
        f"Provide highly specific, practical, biosecurity-compliant advice. "
        f"Synthesize the vector knowledge base facts, active telemetry, batch data, "
        f"and disease/alert information. "
        f"Keep recommendations concise and actionable. Use bullet points. "
        f"Use simple language a farmer can easily understand."
    )
    return system_instructions

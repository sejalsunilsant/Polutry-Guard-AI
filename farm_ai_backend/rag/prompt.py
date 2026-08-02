def build_rag_system_prompt(farm_context, retrieved_knowledge):
    """
    Format system instructions incorporating live farm context telemetry and RAG chunks.
    """
    temp = farm_context.get('currentTemperature', 24.0)
    humid = farm_context.get('currentHumidity', 60.0)
    ammonia = farm_context.get('currentAmmonia', 10.0)
    sound = farm_context.get('currentSoundLevel', 50.0)
    birds = farm_context.get('birdCount', 12500)
    deaths = farm_context.get('loggedMortalities', 0)
    shed = farm_context.get('activeShed', 'Shed #4')

    knowledge_text = "\n\n".join([f"• {chunk}" for chunk in retrieved_knowledge])
    if not knowledge_text:
        knowledge_text = "No direct matching knowledge base articles found. Rely on general biosecurity best practices."

    system_instructions = (
        f"You are Poultry Guard AI, an expert veterinary and agricultural copilot.\n\n"
        f"CURRENT FARM TELEMETRY IN {shed}:\n"
        f"- Temperature: {temp}°C (Ideal: 21-27°C)\n"
        f"- Humidity: {humid}% (Ideal: 50-70%)\n"
        f"- Ammonia Level: {ammonia} ppm (Safe: <20 ppm)\n"
        f"- Acoustic Panic/Sound: {sound} dB (Safe: 40-65 dB)\n"
        f"- Active Birds: {birds}\n"
        f"- Logged Batch Deaths: {deaths}\n\n"
        f"RELEVANT POULTRY GUIDELINES FROM VECTOR KNOWLEDGE BASE:\n"
        f"{knowledge_text}\n\n"
        f"Provide highly specific, practical, biosecurity-compliant advice. "
        f"Synthesize the vector knowledge base facts and active telemetry. "
        f"Keep recommendations concise and actionable. Use bullet points. "
        f"Use simple language a farmer can easily understand."
    )
    return system_instructions

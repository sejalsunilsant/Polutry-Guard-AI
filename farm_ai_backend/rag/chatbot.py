import os
import requests
from .retriever import retrieve_relevant_chunks
from .prompt import build_rag_system_prompt
from .live_context import fetch_live_batch_context

def generate_chatbot_response(user_message, history, farm_context,
                               farmer_id=None, farm_id=None, batch_id=None):
    """
    Generate chatbot response using RAG with farmer/batch isolation.
    
    1. Fetches live structured data from Supabase (batch, mortality, disease, alerts)
    2. Retrieves relevant knowledge chunks from Qdrant with ownership filtering
    3. Queries LLaMA on Groq with enriched context
    4. Falls back to offline RAG engine if the API key is missing
    
    Args:
        user_message: The farmer's question.
        history: Conversation history list.
        farm_context: Real-time telemetry dict from the client.
        farmer_id: Server-verified farmer profile UUID.
        farm_id: Server-verified farm_id (ownership validated).
        batch_id: Server-verified batch_id (ownership validated).
    """
    # 1. Fetch live authoritative data from Supabase
    live_context = {}
    if farm_id:
        try:
            live_context = fetch_live_batch_context(farm_id, batch_id)
        except Exception as e:
            print(f"[Chatbot] Error fetching live context: {e}")
    
    # 2. Retrieve relevant RAG chunks with ownership filtering
    retrieved_chunks = retrieve_relevant_chunks(
        user_message, k=3,
        farmer_id=farmer_id,
        farm_id=farm_id,
        batch_id=batch_id
    )
    
    # 3. Build the enriched system prompt with live context
    system_prompt = build_rag_system_prompt(farm_context, retrieved_chunks, live_context)
    
    groq_api_key = os.environ.get("GROQ_API_KEY")
    
    if groq_api_key and groq_api_key != "YOUR_GROQ_API_KEY_HERE" and groq_api_key.strip():
        # List of models to try in sequence in case of model deprecation or rate limit
        candidate_models = ["groq/compound-mini", "qwen/qwen3.6-27b", "groq/compound", "llama-3.3-70b-versatile"]
        headers = {
            "Authorization": f"Bearer {groq_api_key.strip()}",
            "Content-Type": "application/json"
        }
        messages = [{"role": "system", "content": system_prompt}]
        
        # Map conversation history
        for msg in history:
            role = "user" if msg.get('sender') == "USER" else "assistant"
            messages.append({"role": role, "content": msg.get('text', '')})
            
        messages.append({"role": "user", "content": user_message})

        last_error = None
        for model in candidate_models:
            payload = {
                "model": model,
                "messages": messages,
                "temperature": 0.7,
                "max_tokens": 512
            }

            try:
                response = requests.post(
                    "https://api.groq.com/openai/v1/chat/completions",
                    headers=headers,
                    json=payload,
                    timeout=8
                )

                if response.status_code == 200:
                    result_json = response.json()
                    reply = result_json['choices'][0]['message']['content']
                    return reply
                else:
                    last_error = f"Groq model '{model}' HTTP {response.status_code}: {response.text[:150]}"
                    print(f"[Chatbot] Groq call failed for {model}: {last_error}")
            except Exception as ex:
                last_error = str(ex)
                print(f"[Chatbot] Exception calling Groq model {model}: {ex}")

        return generate_offline_answer(
            user_message, retrieved_chunks, farm_context,
            live_context=live_context, error_msg=last_error
        )
    else:
        # Fallback to local expert with RAG retrieved knowledge
        return generate_offline_answer(
            user_message, retrieved_chunks, farm_context,
            live_context=live_context
        )


def generate_offline_answer(user_message, retrieved_chunks, farm_context, 
                             live_context=None, error_msg=None):
    """
    Format a high-quality offline response incorporating retrieved RAG information
    and live database context.
    """
    # Use authoritative DB values when available, fall back to client-sent context
    temp = farm_context.get('currentTemperature', 24.0)
    ammonia = farm_context.get('currentAmmonia', 10.0)
    deaths = farm_context.get('loggedMortalities', 0)
    birds = farm_context.get('birdCount', 0)
    
    if live_context:
        db_telemetry = live_context.get("latest_telemetry")
        if db_telemetry:
            temp = float(db_telemetry.get("temperature", temp))
            ammonia = float(db_telemetry.get("ammonia", ammonia))
        
        batch = live_context.get("batch")
        if batch:
            birds = batch.get("current_count", birds)
        
        mortality_summary = live_context.get("mortality_summary", {})
        if mortality_summary:
            deaths = mortality_summary.get("total_deaths", deaths)
    
    msg_lower = user_message.lower()
    
    # If we have successfully retrieved RAG chunks, display them directly as primary information source
    if retrieved_chunks:
        kb_summary = "\n\n".join([f"📖 {chunk}" for chunk in retrieved_chunks])
        offline_reply = (
            f"🤖 **PoultryGuard AI (Offline RAG Advisor)**\n\n"
            f"Based on documents retrieved from the farm knowledge base:\n\n"
            f"{kb_summary}\n\n"
            f"*(Live Telemetry: Temp={temp}°C, Ammonia={ammonia} ppm, Deaths={deaths})*"
        )
        
        # Append batch context if available
        if live_context and live_context.get("batch"):
            batch = live_context["batch"]
            ms = live_context.get("mortality_summary", {})
            offline_reply += (
                f"\n\n📊 **Batch Status**: {batch.get('id', '?')} — "
                f"Birds: {batch.get('current_count', '?')}/{batch.get('initial_count', '?')}, "
                f"Age: {batch.get('age_days', '?')} days, "
                f"Mortality Rate: {ms.get('mortality_rate_percent', 0.0)}%"
            )
        
        if error_msg:
            offline_reply += f"\n\n*(Notice: Groq API fallback active due to error: {error_msg})*"
        return offline_reply

    # --- Structured question handling with live context ---
    
    # Mortality/death-related questions
    if any(kw in msg_lower for kw in ["mortality", "death", "died", "dead", "how many died", "mortality rate"]):
        if live_context and live_context.get("mortality_summary"):
            ms = live_context["mortality_summary"]
            batch = live_context.get("batch", {})
            reply = (
                f"📊 **Mortality Report for Batch {batch.get('id', 'Current')}**\n\n"
                f"- Total Deaths: **{ms.get('total_deaths', 0)}**\n"
                f"- Mortality Rate: **{ms.get('mortality_rate_percent', 0.0)}%**\n"
                f"- Current Birds: **{ms.get('current_count', 0)}** / {ms.get('initial_count', 0)}\n"
            )
            records = live_context.get("mortality_records", [])[:3]
            if records:
                reply += "\n**Recent Records:**\n"
                for rec in records:
                    reply += f"- [{rec.get('recorded_at', '?')}] {rec.get('death_count', 0)} deaths — {rec.get('reason', 'Unspecified')}\n"
            return reply
    
    # Disease-related questions
    if any(kw in msg_lower for kw in ["disease", "sick", "infection", "diagnosis", "prediction"]):
        if live_context and live_context.get("disease_events"):
            events = live_context["disease_events"]
            reply = f"🦠 **Disease Events ({len(events)} detected):**\n\n"
            for evt in events:
                reply += (
                    f"- **{evt.get('disease', 'Unknown')}** — "
                    f"Risk: {evt.get('risk_level', '?')}, "
                    f"Confidence: {evt.get('max_confidence', 0.0)}, "
                    f"Status: {evt.get('status', '?')}\n"
                )
            return reply
    
    # Alert-related questions
    if any(kw in msg_lower for kw in ["alert", "warning", "notification"]):
        if live_context and live_context.get("alerts"):
            alerts = live_context["alerts"]
            reply = f"🚨 **Active Alerts ({len(alerts)} unresolved):**\n\n"
            for alert in alerts:
                reply += (
                    f"- [{alert.get('severity', 'MEDIUM')}] **{alert.get('title', 'Alert')}**: "
                    f"{alert.get('description', 'No details')}\n"
                )
            return reply
        else:
            return "✅ **No Active Alerts**: Your farm has no unresolved alerts at this time."
    
    # Batch-related questions
    if any(kw in msg_lower for kw in ["batch", "flock", "birds", "how many birds", "bird count"]):
        if live_context and live_context.get("batch"):
            batch = live_context["batch"]
            ms = live_context.get("mortality_summary", {})
            return (
                f"🐔 **Active Batch: {batch.get('id', '?')}**\n\n"
                f"- Breed: {batch.get('breed', 'Unknown')}\n"
                f"- Start Date: {batch.get('start_date', '?')}\n"
                f"- Age: {batch.get('age_days', '?')} days\n"
                f"- Initial Count: {batch.get('initial_count', '?')}\n"
                f"- Current Count: **{batch.get('current_count', '?')}**\n"
                f"- Deaths: {ms.get('total_deaths', 0)} ({ms.get('mortality_rate_percent', 0.0)}%)\n"
                f"- Status: {batch.get('status', '?')}"
            )

    # Default rule-based responses
    if "risk" in msg_lower or "health" in msg_lower:
        if ammonia > 20 or temp > 29:
            return (
                f"🚨 **Biosecurity Alert**: Live sensors indicate critical readings (Ammonia: {ammonia} ppm, Temp: {temp}°C). "
                f"There is a HIGH disease risk of respiratory snick or infectious bronchitis. "
                f"Action plan:\n1. Increase exhaust fan speed to 100% to purge ammonia.\n2. Enable cooling misters to combat thermal stress.\n3. Log symptoms for veterinarian review."
            )
        else:
            return (
                f"✅ **Flock Health Safe**: Current parameters are ideal (Ammonia: {ammonia} ppm, Temp: {temp}°C). "
                f"Flock mortality rate is normal. Keep cycling litter to maintain pristine conditions."
            )
    elif "ammonia" in msg_lower or "air" in msg_lower or "gas" in msg_lower:
        if ammonia > 18:
            return (
                f"💨 **Ammonia Level Warning ({ammonia} ppm)**: Ammonia levels are elevated. "
                f"Prolonged exposure above 20 ppm causes respiratory damage in broilers and blindness. "
                f"Please treat damp litter immediately and maximize ventilation rates."
            )
        else:
            return f"🍃 **Air Quality OK**: Ammonia is safe at {ammonia} ppm. Keep litter dry to prevent gas release."
    elif "temp" in msg_lower or "heat" in msg_lower or "hot" in msg_lower:
        if temp > 28:
            return (
                f"🔥 **Thermal Stress Warning ({temp}°C)**: Broilers do not have sweat glands and rely on panting. "
                f"Current temperature is too high. Ensure misting pumps are active and water supply is chilled to promote cooling."
            )
        else:
            return f"🌡️ **Thermal Comfort OK**: Shed temperature is cozy at {temp}°C, ideal for broilers at this age stage."
    else:
        batch_info = ""
        if live_context and live_context.get("batch"):
            batch = live_context["batch"]
            ms = live_context.get("mortality_summary", {})
            batch_info = (
                f"\n\n📋 **Your Batch**: {batch.get('id', '?')} — "
                f"{batch.get('current_count', '?')} birds, "
                f"Age: {batch.get('age_days', '?')} days, "
                f"Mortality: {ms.get('mortality_rate_percent', 0.0)}%"
            )
        return (
            f"Hello Farmer! I am your AI Assistant. "
            f"Telemetry shows: Temp={temp}°C, Ammonia={ammonia} ppm, Deaths={deaths}. "
            f"Ask me about biosecurity, air quality, temperature, disease symptoms, "
            f"mortality rate, batch status, or active alerts."
            f"{batch_info}"
        )

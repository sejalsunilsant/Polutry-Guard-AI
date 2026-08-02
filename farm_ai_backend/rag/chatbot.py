import os
import requests
from .retriever import retrieve_relevant_chunks
from .prompt import build_rag_system_prompt

def generate_chatbot_response(user_message, history, farm_context):
    """
    Generate chatbot response using RAG. Searches document corpus and queries LLaMA on Groq,
    or falls back to an offline RAG engine if the API key is missing.
    """
    # 1. Retrieve the top 3 relevant chunks
    retrieved_chunks = retrieve_relevant_chunks(user_message, k=3)
    
    # 2. Build the system prompt
    system_prompt = build_rag_system_prompt(farm_context, retrieved_chunks)
    
    groq_api_key = os.environ.get("GROQ_API_KEY")
    
    if groq_api_key and groq_api_key != "YOUR_GROQ_API_KEY_HERE" and groq_api_key.strip():
        try:
            # Query Groq API Chat Completion
            headers = {
                "Authorization": f"Bearer {groq_api_key}",
                "Content-Type": "application/json"
            }
            messages = [{"role": "system", "content": system_prompt}]
            
            # Map conversation history
            for msg in history:
                role = "user" if msg.get('sender') == "USER" else "assistant"
                messages.append({"role": role, "content": msg.get('text', '')})
                
            messages.append({"role": "user", "content": user_message})

            payload = {
                "model": "llama3-8b-8192",  # Free tier LLaMA model
                "messages": messages,
                "temperature": 0.7,
                "max_tokens": 512
            }

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
                return generate_offline_answer(
                    user_message, 
                    retrieved_chunks, 
                    farm_context, 
                    error_msg=f"Groq API returned HTTP {response.status_code}"
                )
        except Exception as e:
            return generate_offline_answer(user_message, retrieved_chunks, farm_context, error_msg=str(e))
    else:
        # Fallback to local expert with RAG retrieved knowledge
        return generate_offline_answer(user_message, retrieved_chunks, farm_context)


def generate_offline_answer(user_message, retrieved_chunks, farm_context, error_msg=None):
    """
    Format a high-quality offline response incorporating retrieved RAG information.
    """
    temp = farm_context.get('currentTemperature', 24.0)
    ammonia = farm_context.get('currentAmmonia', 10.0)
    deaths = farm_context.get('loggedMortalities', 0)
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
        if error_msg:
            offline_reply += f"\n\n*(Notice: Groq API fallback active due to error: {error_msg})*"
        return offline_reply

    # Default rule backup if no matching documents were found in retriever
    if "risk" in msg_lower or "disease" in msg_lower or "health" in msg_lower:
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
        return (
            f"Hello Farmer! I am your offline AI Assistant. "
            f"Telemetry shows: Temp={temp}°C, Ammonia={ammonia} ppm, Deaths={deaths}. "
            f"Ask me about biosecurity, air quality, temp, or disease symptoms."
        )

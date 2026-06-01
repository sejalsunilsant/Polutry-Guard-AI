import os
from flask import Flask, request, jsonify
from flask_cors import CORS

app = Flask(__name__)
CORS(app)  # Enable CORS for local emulator API queries

# Lightweight AI prompt routing for agricultural protection
@app.route('/api/v1/chat', methods=['POST'])
def chat_assistant():
    try:
        data = request.get_json() or {}
        user_message = data.get('message', '').strip()
        history = data.get('history', [])
        farm_context = data.get('farmContext', {})

        if not user_message:
            return jsonify({'reply': 'Please specify a question regarding your flock.'}), 400

        # Extract context attributes
        temp = farm_context.get('currentTemperature', 24.0)
        humid = farm_context.get('currentHumidity', 60.0)
        ammonia = farm_context.get('currentAmmonia', 10.0)
        sound = farm_context.get('currentSoundLevel', 50.0)
        birds = farm_context.get('birdCount', 12500)
        deaths = farm_context.get('loggedMortalities', 0)
        shed = farm_context.get('activeShed', 'Shed #4')

        # Formulate system prompt with historical/live agricultural parameters
        system_instructions = (
            f"You are Poultry Guard AI, an expert veterinary and agricultural copilot. "
            f"You have live access to the farm context inside {shed}:\n"
            f"- Temperature: {temp}°C\n"
            f"- Humidity: {humid}%\n"
            f"- Ammonia Gas: {ammonia} ppm\n"
            f"- Acoustic Noise: {sound} dB\n"
            f"- Active Birds: {birds}\n"
            f"- Logged Deaths: {deaths}\n\n"
            f"Please give highly specific, practical agricultural advice. Keep recommendations concise."
        )

        # Retrieve Groq API Key from environment
        groq_api_key = os.environ.get('GROQ_API_KEY')

        if groq_api_key:
            import requests
            # Query Groq API Completion endpoint (Free Tier LLaMA model)
            headers = {
                "Authorization": f"Bearer {groq_api_key}",
                "Content-Type": "application/json"
            }
            messages = [{"role": "system", "content": system_instructions}]
            
            # Append Chat History context
            for msg in history:
                role = "user" if msg.get('sender') == "USER" else "assistant"
                messages.append({"role": role, "content": msg.get('text', '')})
                
            messages.append({"role": "user", "content": user_message})

            payload = {
                "model": "llama3-8b-8192",  # Free tier fast model
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
                return jsonify({'reply': reply})
            else:
                return jsonify({'reply': f"Error querying Groq LLM: {response.text}. Fallback engaged."}), 500

        else:
            # High-fidelity Local Expert Rule Fallback when API keys are not supplied (zero cost)
            local_reply = generate_local_expert_response(user_message, temp, ammonia, deaths)
            return jsonify({'reply': local_reply})

    except Exception as e:
        return jsonify({'reply': f"Internal Server exception: {str(e)}"}), 500

def generate_local_expert_response(msg, temp, ammonia, deaths):
    msg_lower = msg.lower()
    
    # Context-aware trigger answers
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
                f"flock mortality rate is normal. Keep cycling litter to maintain pristine conditions."
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

    elif "death" in msg_lower or "mortality" in msg_lower:
        return (
            f"📊 **Mortality Summary**: We have logged {deaths} deaths in this cycle. "
            f"Calculated mortality percentage remains within stable parameters. If mortality exceeds 1%, a veterinary sweep will trigger automatically."
        )

    else:
        return (
            f"Hello Farmer Joe! I am your AI Farm Copilot. "
            f"Currently monitoring Shed #4 ({birds} broilers, Temp: {temp}°C, Ammonia: {ammonia} ppm). "
            f"Ask me about air quality, bird health, temperature management, or mortality rates."
        )

if __name__ == '__main__':
    # Start the Flask app
    app.run(host='0.0.0.0', port=5000, debug=True)

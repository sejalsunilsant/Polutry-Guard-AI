from flask import Blueprint, request, jsonify
from rag.chatbot import generate_chatbot_response

chatbot_bp = Blueprint("chatbot_bp", __name__)

@chatbot_bp.route('/api/v1/chat', methods=['POST'])
def chat_assistant():
    try:
        data = request.get_json() or {}
        user_message = data.get('message', '').strip()
        history = data.get('history', [])
        farm_context = data.get('farmContext', {})

        if not user_message:
            return jsonify({'reply': 'Please specify a question regarding your flock.'}), 400

        reply = generate_chatbot_response(user_message, history, farm_context)
        return jsonify({'reply': reply})

    except Exception as e:
        print(f"[Chatbot API] Error generating chat response: {e}")
        return jsonify({'reply': f"Internal Server exception: {str(e)}"}), 500

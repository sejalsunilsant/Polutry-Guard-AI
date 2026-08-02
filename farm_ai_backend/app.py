import os
import sys

# Load env variables from .env file manually before importing components
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
env_path = os.path.abspath(os.path.join(BASE_DIR, "..", ".env"))
print(f"[Backend Env] Checking for .env at: {env_path}")
if os.path.exists(env_path):
    print(f"[Backend Env] .env found, parsing...")
    with open(env_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, val = line.split("=", 1)
                os.environ[key.strip()] = val.strip()
                print(f"[Backend Env] Loaded: {key.strip()}")
else:
    print(f"[Backend Env] WARNING: .env not found at {env_path}!")

from flask import Flask, jsonify
from flask_cors import CORS
from routes.prediction_routes import prediction_bp
from routes.chatbot_routes import chatbot_bp

app = Flask(__name__)
CORS(app)  # Enable CORS for local emulator API queries

# Register Blueprints for modular routes
app.register_blueprint(prediction_bp)
app.register_blueprint(chatbot_bp)

@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({
        "status": "healthy",
        "service": "PoultryGuard AI Backend API"
    }), 200

if __name__ == '__main__':
    # Retrieve port from environment or default to 5000
    port = int(os.environ.get("PORT", 5000))
    # Start the Flask app
    app.run(host='0.0.0.0', port=port, debug=True)

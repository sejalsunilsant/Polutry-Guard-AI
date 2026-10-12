import os
import sys

# Load environment variables (supports container ENV, local .env in backend, or project root .env)
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
candidate_env_paths = [
    os.path.abspath(os.path.join(BASE_DIR, ".env")),
    os.path.abspath(os.path.join(BASE_DIR, "..", ".env")),
    os.path.abspath(os.path.join(os.getcwd(), ".env")),
]
env_found = False
for env_path in candidate_env_paths:
    if os.path.exists(env_path):
        print(f"[Backend Env] Loading environment from: {env_path}")
        try:
            with open(env_path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith("#") and "=" in line:
                        key, val = line.split("=", 1)
                        clean_key = key.strip()
                        clean_val = val.strip().strip('"').strip("'")
                        if clean_key not in os.environ:
                            os.environ[clean_key] = clean_val
            env_found = True
            break
        except Exception as e:
            print(f"[Backend Env] Error reading {env_path}: {e}")

if not env_found:
    print("[Backend Env] No .env file found; relying on system/container environment variables.")

from flask import Flask, jsonify
from flask_cors import CORS
from routes.prediction_routes import prediction_bp
from routes.chatbot_routes import chatbot_bp
from routes.ingestion_routes import ingestion_bp
from routes.batch_routes import batch_bp
from routes.user_routes import user_bp
from routes.admin_routes import admin_bp
from ml.manager import ModelManager

app = Flask(__name__)
CORS(app)  # Enable CORS for local emulator API queries

# Asynchronously initialize background tasks to prevent blocking WSGI startup and health checks
import threading

def _initialize_async_services():
    try:
        from services.image_pipeline_service import ImagePipelineService
        if os.environ.get("WERKZEUG_RUN_MAIN") == "true" or not app.debug:
            ImagePipelineService.start_background_watcher(interval_seconds=30)
    except Exception as e:
        print(f"[Async Startup] Error starting background watcher: {e}")

    try:
        from data.supabase_client import cleanup_test_data
        cleanup_test_data()
    except Exception as _e:
        pass

_startup_thread = threading.Thread(target=_initialize_async_services, daemon=True, name="AsyncStartupThread")
_startup_thread.start()


# Register Blueprints for modular routes
app.register_blueprint(prediction_bp)
app.register_blueprint(chatbot_bp)
app.register_blueprint(ingestion_bp)
app.register_blueprint(batch_bp)
app.register_blueprint(user_bp)
app.register_blueprint(admin_bp)

@app.route('/', methods=['GET'])
def root():
    return jsonify({
        "status": "online",
        "service": "PoultryGuard AI Backend API Done",
        "health": "/health"
    }), 200

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

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
        # Run background watcher as a singleton with a 15-second startup grace period
        # so Gunicorn and Flask bind to 0.0.0.0:$PORT and pass port checks immediately
        if os.environ.get("WERKZEUG_RUN_MAIN") == "true" or not app.debug:
            interval = int(os.environ.get("IMAGE_WATCHER_INTERVAL", "60"))
            delay = int(os.environ.get("IMAGE_WATCHER_STARTUP_DELAY", "15"))
            ImagePipelineService.start_background_watcher(
                interval_seconds=interval,
                startup_delay_seconds=delay,
                sync_on_startup=False
            )
    except Exception as e:
        print(f"[Async Startup] Note starting background watcher: {e}")

    try:
        if os.environ.get("WERKZEUG_RUN_MAIN") == "true" or not app.debug:
            from scheduler import start_background_scheduler
            poll_interval = int(os.environ.get("TELEMETRY_POLL_INTERVAL", "20"))
            start_background_scheduler(interval_seconds=poll_interval)
    except Exception as e:
        print(f"[Async Startup] Note starting telemetry scheduler: {e}")

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
        "service": "PoultryGuard AI Backend API",
        "health": "/health"
    }), 200

@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({
        "status": "healthy",
        "service": "PoultryGuard AI Backend API"
    }), 200

if __name__ == '__main__':
    # Retrieve port from Render environment ($PORT) or default to 10000 / 5000
    default_port = 10000 if os.environ.get("RENDER") else 5000
    port = int(os.environ.get("PORT", default_port))
    host = os.environ.get("HOST", "0.0.0.0")
    print(f"[Backend API] Starting Flask on {host}:{port}...")
    app.run(host=host, port=port, debug=False)

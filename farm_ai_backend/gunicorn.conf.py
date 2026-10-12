import os

# Render dynamically sets PORT in the environment (defaults to 10000 on Render, 5000 fallback)
port = os.environ.get("PORT", "10000")
bind = f"0.0.0.0:{port}"

# Restrict to 1 worker and 2 threads to adhere to strict 512MB RAM constraints on Render
workers = int(os.environ.get("WEB_CONCURRENCY", "1"))
threads = int(os.environ.get("GUNICORN_THREADS", "2"))

# Worker timeout: 120s ensures slow cold-starts or external API calls don't kill worker
timeout = int(os.environ.get("GUNICORN_TIMEOUT", "120"))
keepalive = 5

# Never preload app so that WSGI initializes inside the worker cleanly
preload_app = False

# Stream stdout and stderr logs to container / Render console
accesslog = "-"
errorlog = "-"
loglevel = "info"

def on_starting(server):
    print(f"[Gunicorn] Initializing server on 0.0.0.0:{port} (workers={workers}, threads={threads}, timeout={timeout}s)")

def when_ready(server):
    print(f"[Gunicorn] Server is LIVE and accepting HTTP connections on 0.0.0.0:{port}")

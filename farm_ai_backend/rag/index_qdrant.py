import os
import sys

# Ensure parent and root directory are in path so we can import modules
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
BACKEND_DIR = os.path.abspath(os.path.join(BASE_DIR, ".."))
if BACKEND_DIR not in sys.path:
    sys.path.append(BACKEND_DIR)

# Load env variables from .env file manually before importing Qdrant client
env_path = os.path.abspath(os.path.join(BACKEND_DIR, "..", ".env"))
if os.path.exists(env_path):
    with open(env_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, val = line.split("=", 1)
                os.environ[key.strip()] = val.strip()

from rag.vector_db import load_knowledge_base
from rag.qdrant_db import recreate_and_index_collection

def run_indexing():
    print("Loading chunks from local knowledge base files...")
    # Load chunks from the local files using the existing vector_db parser
    chunks, _, _ = load_knowledge_base()
    print(f"Extracted {len(chunks)} knowledge base chunks.")
    
    if not chunks:
        print("Error: No chunks extracted. Verify document text files in data/knowledge_base.")
        return

    print("Connecting to Qdrant Cloud and populating collection...")
    success = recreate_and_index_collection(chunks)
    
    if success:
        print("==================================================")
        print("Success! Documents are now fully indexed in Qdrant Cloud.")
        print("==================================================")
    else:
        print("==================================================")
        print("Failed to index documents in Qdrant Cloud.")
        print("Please check your QDRANT_HOST and QDRANT_API_KEY env vars.")
        print("==================================================")

if __name__ == "__main__":
    run_indexing()

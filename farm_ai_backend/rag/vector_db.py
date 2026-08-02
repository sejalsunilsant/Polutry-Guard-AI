import os
from sklearn.feature_extraction.text import TfidfVectorizer

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
KB_DIR = os.path.abspath(os.path.join(BASE_DIR, "..", "data", "knowledge_base"))

chunks = []
vectorizer = None
tfidf_matrix = None

def load_knowledge_base():
    global chunks, vectorizer, tfidf_matrix
    if chunks and vectorizer is not None:
        return chunks, vectorizer, tfidf_matrix
        
    raw_texts = []
    if os.path.exists(KB_DIR):
        print(f"[RAG Vector DB] Scanning knowledge base files in: {KB_DIR}")
        for filename in os.listdir(KB_DIR):
            if filename.endswith(".txt"):
                filepath = os.path.join(KB_DIR, filename)
                try:
                    with open(filepath, "r", encoding="utf-8") as f:
                        content = f.read()
                        # Split by double newlines to segment paragraphs
                        paragraphs = content.split("\n\n")
                        for para in paragraphs:
                            clean_para = para.strip()
                            if clean_para and len(clean_para) > 30:
                                chunks.append(clean_para)
                except Exception as e:
                    print(f"[RAG Vector DB] Error reading {filename}: {e}")
    else:
        print(f"[RAG Vector DB] Warning: Knowledge base directory {KB_DIR} not found.")
        
    if not chunks:
        # Load a default set of fallback chunks to keep the RAG system operational
        chunks = [
            "Ammonia gas should be kept below 20 ppm to prevent respiratory snick and blindness in broilers.",
            "Ideal shed temperature for Day 18 broilers is 24°C - 27°C. Ambient temperature above 29°C causes thermal stress.",
            "Wet litter promotes bacterial growth and coccidiosis. Keep litter moisture between 20% and 25%.",
            "Coccidiosis symptoms include pale combs, bloody droppings, ruffled feathers, and slow weight growth. Treat with Amprolium in drinking water.",
            "Infectious Bronchitis symptoms include sneezing, coughing (snick), gasping for air, nasal discharge, and wet eyes.",
            "If acoustic panic/sound level exceeds 75 dB, check for flock panic, smothering, or predator attack inside the shed."
        ]
        
    try:
        vectorizer = TfidfVectorizer(stop_words="english")
        tfidf_matrix = vectorizer.fit_transform(chunks)
        print(f"[RAG Vector DB] Successfully indexed {len(chunks)} knowledge chunks.")
    except Exception as e:
        print(f"[RAG Vector DB] Error building TF-IDF index: {e}")
        
    return chunks, vectorizer, tfidf_matrix

# Initialize on import
load_knowledge_base()

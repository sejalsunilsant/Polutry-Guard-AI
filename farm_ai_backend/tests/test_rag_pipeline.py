import unittest
import os
import sys

# Ensure backend root is on sys.path
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

from rag.vector_db import load_knowledge_base
from rag.prompt import build_rag_system_prompt
from rag.retriever import retrieve_relevant_chunks

class TestRAGPipeline(unittest.TestCase):
    def test_knowledge_base_loading(self):
        """Verify knowledge base chunks load from files or built-in fallbacks."""
        chunks, vectorizer, tfidf_matrix = load_knowledge_base()
        self.assertGreater(len(chunks), 0)
        self.assertIsNotNone(vectorizer)
        self.assertIsNotNone(tfidf_matrix)

    def test_retriever_query(self):
        """Verify relevant chunk retrieval for standard disease inquiry."""
        query = "What are the common symptoms of Coccidiosis in broilers?"
        results = retrieve_relevant_chunks(query, k=2)
        self.assertIsInstance(results, list)
        self.assertGreater(len(results), 0)

    def test_build_rag_system_prompt(self):
        """Verify system prompt properly synthesizes farm telemetry and retrieved context."""
        farm_context = {
            "currentTemperature": 28.5,
            "currentHumidity": 65.0,
            "currentAmmonia": 15.0,
            "currentSoundLevel": 55.0,
            "birdCount": 1500,
            "loggedMortalities": 2,
            "activeShed": "Shed A"
        }
        retrieved_chunks = ["Ideal shed temperature is 21-27C.", "Ammonia must remain below 20 ppm."]
        prompt = build_rag_system_prompt(farm_context, retrieved_chunks)
        
        self.assertIn("Poultry Guard AI", prompt)
        self.assertIn("28.5", prompt)
        self.assertIn("Shed A", prompt)
        self.assertIn("Ideal shed temperature", prompt)

if __name__ == '__main__':
    unittest.main()

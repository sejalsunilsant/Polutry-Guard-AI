import os
import sys
import unittest
from dotenv import load_dotenv

# Ensure backend root is on sys.path
backend_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if backend_dir not in sys.path:
    sys.path.insert(0, backend_dir)

# Load env vars from backend or project root
for candidate_env in [os.path.abspath(os.path.join(backend_dir, ".env")), os.path.abspath(os.path.join(backend_dir, "..", ".env"))]:
    if os.path.exists(candidate_env):
        load_dotenv(candidate_env)

from data.supabase_client import (
    supabase,
    verify_token_and_get_user,
    supabase_refresh_session,
    get_farm_id_for_user,
    get_active_batch
)
from rag.live_context import fetch_live_batch_context, _fetch_telemetry_status
from rag.retriever import retrieve_relevant_chunks
from rag.qdrant_db import search_knowledge_base, search_knowledge_base_filtered
from rag.prompt import build_rag_system_prompt
from rag.chatbot import generate_chatbot_response, generate_offline_answer
from routes.chatbot_routes import _resolve_ownership_chain, chatbot_bp
from flask import Flask


class TestChickBotEndToEnd(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app = Flask(__name__)
        cls.app.register_blueprint(chatbot_bp)
        cls.client = cls.app.test_client()

    def test_1_auth_token_verification_and_refresh(self):
        print("\n--- Test 1: Authentication & Token Refresh ---")
        # 1. Mock token handling
        user_info, err = verify_token_and_get_user("Bearer mock_token_for_farmer_test_123")
        self.assertIsNone(err)
        self.assertEqual(user_info["uid"], "farmer_test_123")
        self.assertEqual(user_info["role"], "FARMER")

        # 2. Mock refresh test
        refresh_res = supabase_refresh_session("sample_refresh_token_abc")
        self.assertIn("status", refresh_res)
        print("Token refresh verification result:", refresh_res.get("status"))

        # 3. Simulated expired token safe decoding test
        fake_expired_jwt = (
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
            "eyJzdWIiOiIzYTM4YjgzNS1kMDY2LTQ3YjctYmUyMy0wYjAxY2UxM2M2ZWEiLCJleHAiOjE2MDAwMDAwMDB9."
            "invalidsig"
        )
        user_info, err = verify_token_and_get_user(fake_expired_jwt)
        # Even with an expired unverified token, user UUID is safely extracted for graceful fallback
        if user_info:
            self.assertEqual(user_info["uid"], "3a38b835-d066-47b7-be23-0b01ce13c6ea")
            print("Expired token handled safely with user UUID:", user_info["uid"])

    def test_2_farm_ownership_chain_resolution(self):
        print("\n--- Test 2: Farm Ownership Chain Resolution ---")
        # Test known profile with farm membership
        known_profile_id = "3a38b835-d066-47b7-be23-0b01ce13c6ea"
        farmer_id, farm_id, batch_id, err = _resolve_ownership_chain(known_profile_id)
        self.assertEqual(farmer_id, known_profile_id)
        print(f"Ownership Chain: farmer={farmer_id}, farm={farm_id}, batch={batch_id}")
        self.assertIsNotNone(farm_id)

    def test_3_live_context_and_batch_data(self):
        print("\n--- Test 3: Live Batch Context & Authoritative Data ---")
        known_farm_id = "farm_apurvashinde054"
        context = fetch_live_batch_context(known_farm_id)
        self.assertIn("batch", context)
        self.assertIn("mortality_records", context)
        self.assertIn("mortality_summary", context)
        self.assertIn("disease_events", context)
        self.assertIn("alerts", context)
        self.assertIn("telemetry_status", context)
        print("Live context keys retrieved successfully:", list(context.keys()))
        print("Telemetry status:", context.get("telemetry_status"))

    def test_4_telemetry_no_dummy_defaults(self):
        print("\n--- Test 4: Incorrect Live Telemetry Prevention (No 0.0 defaults) ---")
        # Scenario A: Telemetry is completely unavailable / failed
        empty_farm_context = {
            "currentTemperature": 0.0,
            "currentAmmonia": 0.0,
            "birdCount": 1000,
            "loggedMortalities": 5
        }
        empty_live_context = {
            "batch": {"id": "B-001", "breed": "Broiler", "current_count": 1000},
            "latest_telemetry": None,
            "previous_telemetry": None,
            "telemetry_status": "UNAVAILABLE"
        }

        # 1. System Prompt must NEVER report 0.0°C or 0.0 ppm as temperature
        prompt = build_rag_system_prompt(empty_farm_context, [], empty_live_context)
        self.assertNotIn("- Temperature: 0.0", prompt)
        self.assertNotIn("- Ammonia Level: 0.0", prompt)
        self.assertIn("Telemetry previous", prompt)

        # 2. Chatbot offline greeting must use Telemetry previous, never 0.0°C
        offline_reply = generate_offline_answer("hello", [], empty_farm_context, empty_live_context)
        self.assertNotIn("0.0°C", offline_reply)
        self.assertNotIn("0.0 ppm", offline_reply)
        self.assertIn("Telemetry previous", offline_reply)

        # 3. Chatbot must NOT report "Flock Health Safe" with 0.0 values
        health_reply = generate_offline_answer("what is the current disease risk?", [], empty_farm_context, empty_live_context)
        self.assertNotIn("0.0°C", health_reply)
        self.assertNotIn("0.0 ppm", health_reply)
        self.assertIn("Telemetry previous", health_reply)

        temp_reply = generate_offline_answer("check the shed temp", [], empty_farm_context, empty_live_context)
        self.assertNotIn("0.0°C", temp_reply)
        self.assertIn("Telemetry previous", temp_reply)
        print("Successfully verified: No 0.0°C or 0.0 ppm default values used when telemetry fails!")

    def test_5_qdrant_rag_retrieval(self):
        print("\n--- Test 5: Qdrant Cloud RAG Retrieval ---")
        query = "What is the safe ammonia level for chickens?"
        chunks = retrieve_relevant_chunks(query, k=2)
        self.assertGreater(len(chunks), 0)
        print(f"Retrieved {len(chunks)} chunks from Qdrant Cloud:")
        for c in chunks:
            print(" -", c[:60].replace("\n", " "))
        # Check relevance
        ammonia_found = any("ammonia" in c.lower() or "nh3" in c.lower() for c in chunks)
        self.assertTrue(ammonia_found)

    def test_6_groq_models_and_response(self):
        print("\n--- Test 6: Groq Response Generation ---")
        farm_context = {
            "currentTemperature": 24.5,
            "currentAmmonia": 9.2,
            "birdCount": 5000,
            "loggedMortalities": 12,
            "activeShed": "Shed #1"
        }
        reply = generate_chatbot_response(
            user_message="What should I do if ammonia rises in Shed #1?",
            history=[],
            farm_context=farm_context
        )
        self.assertIsNotNone(reply)
        self.assertGreater(len(reply), 20)
        print("Groq response generated successfully! Preview:")
        print(reply[:200].encode('ascii', 'replace').decode().replace("\n", " "))

    def test_7_chat_endpoint(self):
        print("\n--- Test 7: /chat API Endpoint End-to-End ---")
        payload = {
            "message": "Give me a quick status update on my flock.",
            "history": [],
            "farmContext": {
                "farmerId": "3a38b835-d066-47b7-be23-0b01ce13c6ea",
                "farmId": "farm_apurvashinde054",
                "currentTemperature": 0.0,
                "currentAmmonia": 0.0
            }
        }
        response = self.client.post(
            "/chat",
            json=payload,
            headers={
                "Authorization": "Bearer mock_token_for_3a38b835-d066-47b7-be23-0b01ce13c6ea",
                "X-Refresh-Token": "test_refresh_token_123"
            }
        )
        self.assertEqual(response.status_code, 200)
        data = response.get_json()
        self.assertIn("reply", data)
        self.assertNotIn("0.0°C", data["reply"])
        print("/chat endpoint responded with 200 OK! Reply preview:")
        print(data["reply"][:200].encode('ascii', 'replace').decode().replace("\n", " "))


if __name__ == "__main__":
    unittest.main()

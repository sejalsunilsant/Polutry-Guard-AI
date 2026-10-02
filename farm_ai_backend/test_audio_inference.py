import os
import sys
import numpy as np
import soundfile as sf

# Add backend directory to sys.path
backend_dir = os.path.dirname(os.path.abspath(__file__))
if backend_dir not in sys.path:
    sys.path.insert(0, backend_dir)

from services.audio_predictor import get_audio_predictor, predict_flock_sound
from ml.manager import ModelManager

def run_tests():
    print("=" * 60)
    print("Testing Audio Inference Pipeline (Healthy vs. Sick)")
    print("=" * 60)

    # 1. Create a synthetic test audio WAV file (16kHz, 3 seconds)
    sr = 16000
    duration = 3.0
    t = np.linspace(0, duration, int(sr * duration), endpoint=False)
    # Generate a tone mimicking bird vocalization
    audio_signal = 0.5 * np.sin(2 * np.pi * 1200 * t)

    test_wav_path = os.path.join(backend_dir, "test_sample.wav")
    sf.write(test_wav_path, audio_signal, sr)
    print(f"[Test] Generated temporary test audio: {test_wav_path}")

    try:
        # 2. Test direct AudioPredictor
        print("\n--- Testing AudioPredictor.predict() ---")
        predictor = get_audio_predictor()
        res1 = predictor.predict(test_wav_path)
        print("AudioPredictor Result:", res1)
        assert "prediction" in res1, "Missing prediction field"
        assert res1["prediction"] in ["Healthy", "Sick", "Uncertain"], "Invalid prediction label"
        assert "probabilities" in res1, "Missing probabilities field"
        assert res1["scope"] == "binary_respiratory_flock_distress", "Invalid scope"
        print("[PASS] AudioPredictor test passed.")

        # 3. Test ModelManager.predict_sound()
        print("\n--- Testing ModelManager.predict_sound() ---")
        res2 = ModelManager.predict_sound(test_wav_path)
        print("ModelManager Result:", res2)
        assert "prediction" in res2, "Missing prediction field"
        assert "symptoms" in res2, "Missing symptoms field"
        print("[PASS] ModelManager sound integration test passed.")

    finally:
        if os.path.exists(test_wav_path):
            os.remove(test_wav_path)
            print(f"[Test] Cleaned up temporary file: {test_wav_path}")

    print("\n" + "=" * 60)
    print("ALL AUDIO INFERENCE TESTS PASSED!")
    print("=" * 60)

if __name__ == "__main__":
    run_tests()

"""
PoultryGuard AI - Unified Test Runner
Runs all backend unit, integration, ML, and pipeline tests.
"""
import unittest
import os
import sys

def main():
    test_dir = os.path.dirname(os.path.abspath(__file__))
    backend_dir = os.path.dirname(test_dir)
    if backend_dir not in sys.path:
        sys.path.insert(0, backend_dir)

    print("=" * 70)
    print("RUNNING POULTRYGUARD AI PRODUCTION BACKEND TEST SUITE")
    print(f"Test Discovery Directory: {test_dir}")
    print("=" * 70)

    loader = unittest.TestLoader()
    suite = loader.discover(start_dir=test_dir, pattern="test_*.py")

    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)

    print("=" * 70)
    print(f"Tests Run: {result.testsRun}")
    print(f"Failures:  {len(result.failures)}")
    print(f"Errors:    {len(result.errors)}")
    print("=" * 70)

    # Clean up any test artifacts from database after test suite completes
    try:
        from data.supabase_client import cleanup_test_data
        cleanup_test_data()
        print("[CLEANUP] Testing data cleaned up from database successfully.")
    except Exception as e:
        print(f"[CLEANUP] Test cleanup note: {e}")

    if result.wasSuccessful():
        print("[SUCCESS] All backend tests passed successfully!")
        sys.exit(0)
    else:
        print("[FAILURE] Some backend tests failed.")
        sys.exit(1)

if __name__ == '__main__':
    main()

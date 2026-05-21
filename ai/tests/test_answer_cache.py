import os
from pathlib import Path
import tempfile
import threading
import time
import unittest
from concurrent.futures import ThreadPoolExecutor

from app.schemas import AiGenerateRequest
from app.workflow.answer_cache import (
    cache_key_for,
    clear_answer_cache,
    get_cached_answer,
    put_cached_answer,
    run_single_flight,
)


class AnswerCacheTest(unittest.TestCase):
    def setUp(self):
        self._previous_cache_path = os.environ.get("AI_REVIEW_ANSWER_CACHE_PATH")
        self._tmp = tempfile.TemporaryDirectory()
        self.cache_path = Path(self._tmp.name) / "answers.jsonl"
        os.environ["AI_REVIEW_ANSWER_CACHE_PATH"] = str(self.cache_path)
        clear_answer_cache()

    def tearDown(self):
        clear_answer_cache()
        if self._previous_cache_path is None:
            os.environ.pop("AI_REVIEW_ANSWER_CACHE_PATH", None)
        else:
            os.environ["AI_REVIEW_ANSWER_CACHE_PATH"] = self._previous_cache_path
        self._tmp.cleanup()

    def test_cache_survives_memory_clear_when_persistent_path_is_configured(self):
        key = cache_key_for("free-question", AiGenerateRequest(user_answer="Redis cache가 뭐야?"))

        put_cached_answer(key, "Redis cache answer")
        clear_answer_cache()

        self.assertEqual(get_cached_answer(key), "Redis cache answer")
        self.assertTrue(self.cache_path.exists())

    def test_single_flight_shares_uncached_producer_result(self):
        calls = {"count": 0}
        calls_lock = threading.Lock()
        start = threading.Event()

        def producer():
            with calls_lock:
                calls["count"] += 1
            time.sleep(0.1)
            return "shared result"

        def call_single_flight():
            start.wait(timeout=1)
            return run_single_flight("uncached-key", producer)

        with ThreadPoolExecutor(max_workers=2) as executor:
            futures = [executor.submit(call_single_flight) for _ in range(2)]
            start.set()
            results = [future.result(timeout=5) for future in futures]

        self.assertEqual(calls["count"], 1)
        self.assertEqual(results, ["shared result", "shared result"])

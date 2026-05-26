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
    set_redis_answer_cache_client_for_tests,
)


class AnswerCacheTest(unittest.TestCase):
    def setUp(self):
        self._previous_cache_path = os.environ.get("AI_REVIEW_ANSWER_CACHE_PATH")
        self._previous_cache_namespace = os.environ.get("AI_REVIEW_CACHE_NAMESPACE_VERSION")
        self._previous_cache_backend = os.environ.get("AI_REVIEW_ANSWER_CACHE_BACKEND")
        self._previous_cache_ttl = os.environ.get("AI_REVIEW_ANSWER_CACHE_TTL_SECONDS")
        self._previous_distributed_single_flight = os.environ.get("AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT")
        self._previous_distributed_wait = os.environ.get("AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_WAIT_TIMEOUT_SECONDS")
        self._previous_distributed_poll = os.environ.get("AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_POLL_INTERVAL_MS")
        self._tmp = tempfile.TemporaryDirectory()
        self.cache_path = Path(self._tmp.name) / "answers.jsonl"
        os.environ["AI_REVIEW_ANSWER_CACHE_PATH"] = str(self.cache_path)
        os.environ.pop("AI_REVIEW_CACHE_NAMESPACE_VERSION", None)
        os.environ.pop("AI_REVIEW_ANSWER_CACHE_BACKEND", None)
        os.environ.pop("AI_REVIEW_ANSWER_CACHE_TTL_SECONDS", None)
        os.environ.pop("AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT", None)
        os.environ.pop("AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_WAIT_TIMEOUT_SECONDS", None)
        os.environ.pop("AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_POLL_INTERVAL_MS", None)
        set_redis_answer_cache_client_for_tests(None)
        clear_answer_cache()

    def tearDown(self):
        clear_answer_cache()
        set_redis_answer_cache_client_for_tests(None)
        if self._previous_cache_path is None:
            os.environ.pop("AI_REVIEW_ANSWER_CACHE_PATH", None)
        else:
            os.environ["AI_REVIEW_ANSWER_CACHE_PATH"] = self._previous_cache_path
        if self._previous_cache_namespace is None:
            os.environ.pop("AI_REVIEW_CACHE_NAMESPACE_VERSION", None)
        else:
            os.environ["AI_REVIEW_CACHE_NAMESPACE_VERSION"] = self._previous_cache_namespace
        if self._previous_cache_backend is None:
            os.environ.pop("AI_REVIEW_ANSWER_CACHE_BACKEND", None)
        else:
            os.environ["AI_REVIEW_ANSWER_CACHE_BACKEND"] = self._previous_cache_backend
        if self._previous_cache_ttl is None:
            os.environ.pop("AI_REVIEW_ANSWER_CACHE_TTL_SECONDS", None)
        else:
            os.environ["AI_REVIEW_ANSWER_CACHE_TTL_SECONDS"] = self._previous_cache_ttl
        if self._previous_distributed_single_flight is None:
            os.environ.pop("AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT", None)
        else:
            os.environ["AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT"] = self._previous_distributed_single_flight
        if self._previous_distributed_wait is None:
            os.environ.pop("AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_WAIT_TIMEOUT_SECONDS", None)
        else:
            os.environ["AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_WAIT_TIMEOUT_SECONDS"] = self._previous_distributed_wait
        if self._previous_distributed_poll is None:
            os.environ.pop("AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_POLL_INTERVAL_MS", None)
        else:
            os.environ["AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_POLL_INTERVAL_MS"] = self._previous_distributed_poll
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

    def test_cache_key_includes_cache_namespace_version(self):
        request = AiGenerateRequest(user_answer="Redis cache媛 萸먯빞?")

        os.environ["AI_REVIEW_CACHE_NAMESPACE_VERSION"] = "answers-v1"
        first_key = cache_key_for("free-question", request)
        os.environ["AI_REVIEW_CACHE_NAMESPACE_VERSION"] = "answers-v2"
        second_key = cache_key_for("free-question", request)

        self.assertNotEqual(first_key, second_key)
        self.assertTrue(first_key.startswith("answers-v1|"))
        self.assertTrue(second_key.startswith("answers-v2|"))

    def test_redis_backend_hit_returns_cached_answer_and_hydrates_memory(self):
        os.environ["AI_REVIEW_ANSWER_CACHE_BACKEND"] = "redis"
        redis = FakeRedis()
        key = "redis-hit-key"
        redis.setex("ai_review:answer:" + key, 60, "shared answer")
        set_redis_answer_cache_client_for_tests(redis)

        self.assertEqual(get_cached_answer(key), "shared answer")
        set_redis_answer_cache_client_for_tests(FailingRedis())

        self.assertEqual(get_cached_answer(key), "shared answer")

    def test_redis_backend_write_uses_ttl(self):
        os.environ["AI_REVIEW_ANSWER_CACHE_BACKEND"] = "redis"
        os.environ["AI_REVIEW_ANSWER_CACHE_TTL_SECONDS"] = "120"
        redis = FakeRedis()
        set_redis_answer_cache_client_for_tests(redis)

        put_cached_answer("redis-write-key", "answer")

        self.assertEqual(redis.values["ai_review:answer:redis-write-key"], "answer")
        self.assertEqual(redis.ttls["ai_review:answer:redis-write-key"], 120)

    def test_redis_backend_failure_falls_back_to_memory_and_jsonl(self):
        os.environ["AI_REVIEW_ANSWER_CACHE_BACKEND"] = "redis"
        set_redis_answer_cache_client_for_tests(FailingRedis())

        put_cached_answer("redis-failure-key", "answer")

        self.assertEqual(get_cached_answer("redis-failure-key"), "answer")
        self.assertTrue(self.cache_path.exists())

    def test_distributed_single_flight_release_keeps_other_owner_lock(self):
        from app.workflow import redis_answer_cache

        os.environ["AI_REVIEW_ANSWER_CACHE_BACKEND"] = "redis"
        os.environ["AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT"] = "true"
        redis = FakeRedis()
        set_redis_answer_cache_client_for_tests(redis)

        first_owner = redis_answer_cache.acquire_single_flight_lock("shared-key")
        self.assertIsNotNone(first_owner)
        redis.values["ai_review:answer:singleflight:shared-key"] = "other-owner"

        redis_answer_cache.release_single_flight_lock("shared-key", first_owner)

        self.assertEqual(redis.values["ai_review:answer:singleflight:shared-key"], "other-owner")

    def test_distributed_single_flight_waits_for_remote_cached_answer(self):
        from app.workflow import redis_answer_cache

        os.environ["AI_REVIEW_ANSWER_CACHE_BACKEND"] = "redis"
        os.environ["AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT"] = "true"
        os.environ["AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_WAIT_TIMEOUT_SECONDS"] = "1"
        os.environ["AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_POLL_INTERVAL_MS"] = "10"
        redis = FakeRedis()
        set_redis_answer_cache_client_for_tests(redis)
        remote_owner = redis_answer_cache.acquire_single_flight_lock("distributed-key")
        expensive_calls = {"count": 0}

        def publish_remote_answer():
            time.sleep(0.05)
            redis.setex("ai_review:answer:distributed-key", 60, "remote answer")
            redis_answer_cache.release_single_flight_lock("distributed-key", remote_owner)

        def producer():
            cached = get_cached_answer("distributed-key")
            if cached is not None:
                return cached
            expensive_calls["count"] += 1
            return "local answer"

        publisher = threading.Thread(target=publish_remote_answer)
        publisher.start()
        try:
            result = run_single_flight("distributed-key", producer)
        finally:
            publisher.join(timeout=1)

        self.assertEqual(result, "remote answer")
        self.assertEqual(expensive_calls["count"], 0)

    def test_distributed_single_flight_stale_lock_timeout_runs_local_producer(self):
        from app.workflow import redis_answer_cache

        os.environ["AI_REVIEW_ANSWER_CACHE_BACKEND"] = "redis"
        os.environ["AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT"] = "true"
        os.environ["AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_WAIT_TIMEOUT_SECONDS"] = "0.05"
        os.environ["AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_POLL_INTERVAL_MS"] = "5"
        redis = FakeRedis()
        set_redis_answer_cache_client_for_tests(redis)
        redis_answer_cache.acquire_single_flight_lock("stale-key")
        calls = {"count": 0}

        def producer():
            calls["count"] += 1
            return "local answer"

        result = run_single_flight("stale-key", producer)

        self.assertEqual(result, "local answer")
        self.assertEqual(calls["count"], 1)


class FakeRedis:
    def __init__(self):
        self.values = {}
        self.ttls = {}

    def get(self, key):
        return self.values.get(key)

    def setex(self, key, ttl, value):
        self.values[key] = value
        self.ttls[key] = ttl

    def set(self, key, value, nx=False, ex=None):
        if nx and key in self.values:
            return False
        self.values[key] = value
        if ex is not None:
            self.ttls[key] = ex
        return True

    def delete(self, key):
        existed = key in self.values
        self.values.pop(key, None)
        self.ttls.pop(key, None)
        return 1 if existed else 0


class FailingRedis:
    def get(self, key):
        raise OSError("redis unavailable")

    def setex(self, key, ttl, value):
        raise OSError("redis unavailable")

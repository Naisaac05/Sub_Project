from __future__ import annotations

import os
import time
import uuid
from typing import Protocol


DEFAULT_REDIS_PREFIX = "ai_review:answer"
DEFAULT_REDIS_TTL_SECONDS = 86400
DEFAULT_SINGLE_FLIGHT_LOCK_TTL_SECONDS = 60
DEFAULT_SINGLE_FLIGHT_WAIT_TIMEOUT_SECONDS = 30.0
DEFAULT_SINGLE_FLIGHT_POLL_INTERVAL_MS = 100


class RedisAnswerCacheClient(Protocol):
    def get(self, key: str):
        ...

    def setex(self, key: str, ttl: int, value: str):
        ...

    def set(self, key: str, value: str, nx: bool = False, ex: int | None = None):
        ...

    def delete(self, key: str):
        ...


_CLIENT: RedisAnswerCacheClient | None = None
_TEST_CLIENT: RedisAnswerCacheClient | None = None


def set_client_for_tests(client: RedisAnswerCacheClient | None) -> None:
    global _TEST_CLIENT
    _TEST_CLIENT = client


def redis_cache_enabled() -> bool:
    return os.environ.get("AI_REVIEW_ANSWER_CACHE_BACKEND", "memory").strip().lower() == "redis"


def distributed_single_flight_enabled() -> bool:
    raw_value = os.environ.get("AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT", "false").strip().lower()
    return redis_cache_enabled() and raw_value in {"1", "true", "yes", "on"}


def get_answer(key: str) -> str | None:
    if not redis_cache_enabled():
        return None
    client = _client()
    if client is None:
        return None
    try:
        value = client.get(_redis_key(key))
    except Exception:
        return None
    if value is None:
        return None
    if isinstance(value, bytes):
        return value.decode("utf-8")
    return str(value)


def put_answer(key: str, answer: str) -> None:
    if not answer or not redis_cache_enabled():
        return
    client = _client()
    if client is None:
        return
    try:
        client.setex(_redis_key(key), _ttl_seconds(), answer)
    except Exception:
        return


def acquire_single_flight_lock(key: str) -> str | None:
    if not distributed_single_flight_enabled():
        return None
    client = _client()
    if client is None:
        return None
    owner = uuid.uuid4().hex
    try:
        acquired = client.set(_single_flight_lock_key(key), owner, nx=True, ex=_single_flight_lock_ttl_seconds())
    except Exception:
        return None
    return owner if acquired else None


def release_single_flight_lock(key: str, owner: str | None) -> None:
    if not owner or not distributed_single_flight_enabled():
        return
    client = _client()
    if client is None:
        return
    lock_key = _single_flight_lock_key(key)
    try:
        current_owner = client.get(lock_key)
        if isinstance(current_owner, bytes):
            current_owner = current_owner.decode("utf-8")
        if current_owner == owner:
            client.delete(lock_key)
    except Exception:
        return


def wait_for_distributed_answer(key: str) -> bool:
    if not distributed_single_flight_enabled():
        return False
    deadline = time.monotonic() + _single_flight_wait_timeout_seconds()
    interval = _single_flight_poll_interval_seconds()
    while time.monotonic() < deadline:
        if get_answer(key) is not None:
            return True
        time.sleep(interval)
    return get_answer(key) is not None


def _client() -> RedisAnswerCacheClient | None:
    global _CLIENT
    if _TEST_CLIENT is not None:
        return _TEST_CLIENT
    if _CLIENT is not None:
        return _CLIENT
    try:
        import redis
    except ImportError:
        return None
    url = os.environ.get("AI_REVIEW_REDIS_URL", "redis://localhost:6379/0").strip()
    if not url:
        return None
    _CLIENT = redis.Redis.from_url(url, decode_responses=True)
    return _CLIENT


def _redis_key(key: str) -> str:
    prefix = os.environ.get("AI_REVIEW_ANSWER_CACHE_REDIS_PREFIX", DEFAULT_REDIS_PREFIX).strip()
    return f"{prefix or DEFAULT_REDIS_PREFIX}:{key}"


def _single_flight_lock_key(key: str) -> str:
    return f"{_redis_key('singleflight')}:{key}"


def _ttl_seconds() -> int:
    raw_value = os.environ.get("AI_REVIEW_ANSWER_CACHE_TTL_SECONDS", str(DEFAULT_REDIS_TTL_SECONDS)).strip()
    try:
        value = int(raw_value)
    except ValueError:
        return DEFAULT_REDIS_TTL_SECONDS
    return value if value > 0 else DEFAULT_REDIS_TTL_SECONDS


def _single_flight_lock_ttl_seconds() -> int:
    raw_value = os.environ.get(
        "AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_LOCK_TTL_SECONDS",
        str(DEFAULT_SINGLE_FLIGHT_LOCK_TTL_SECONDS),
    ).strip()
    try:
        value = int(raw_value)
    except ValueError:
        return DEFAULT_SINGLE_FLIGHT_LOCK_TTL_SECONDS
    return value if value > 0 else DEFAULT_SINGLE_FLIGHT_LOCK_TTL_SECONDS


def _single_flight_wait_timeout_seconds() -> float:
    raw_value = os.environ.get(
        "AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_WAIT_TIMEOUT_SECONDS",
        str(DEFAULT_SINGLE_FLIGHT_WAIT_TIMEOUT_SECONDS),
    ).strip()
    try:
        value = float(raw_value)
    except ValueError:
        return DEFAULT_SINGLE_FLIGHT_WAIT_TIMEOUT_SECONDS
    return value if value > 0 else DEFAULT_SINGLE_FLIGHT_WAIT_TIMEOUT_SECONDS


def _single_flight_poll_interval_seconds() -> float:
    raw_value = os.environ.get(
        "AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_POLL_INTERVAL_MS",
        str(DEFAULT_SINGLE_FLIGHT_POLL_INTERVAL_MS),
    ).strip()
    try:
        value = int(raw_value)
    except ValueError:
        return DEFAULT_SINGLE_FLIGHT_POLL_INTERVAL_MS / 1000
    return (value if value > 0 else DEFAULT_SINGLE_FLIGHT_POLL_INTERVAL_MS) / 1000

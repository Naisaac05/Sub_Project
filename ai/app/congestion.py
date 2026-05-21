from __future__ import annotations

from contextlib import contextmanager
import os
import threading
from collections.abc import Iterator


DEFAULT_MAX_IN_FLIGHT_REQUESTS = 8
DEFAULT_RETRY_AFTER_SECONDS = 3

_GATE_LOCK = threading.Lock()
_GATE_LIMIT: int | None = None
_GATE: threading.BoundedSemaphore | None = None
_IN_FLIGHT = 0


class AiRequestBusyError(RuntimeError):
    status_code = 429

    def __init__(self, detail: str, retry_after_seconds: int = DEFAULT_RETRY_AFTER_SECONDS):
        super().__init__(detail)
        self.detail = detail
        self.retry_after_seconds = retry_after_seconds


@contextmanager
def ai_request_admission() -> Iterator[None]:
    gate = _admission_gate()
    if gate is None:
        yield
        return

    acquired = gate.acquire(blocking=False)
    if not acquired:
        raise AiRequestBusyError("AI service is busy. Please retry shortly.")
    _increment_in_flight()
    try:
        yield
    finally:
        _decrement_in_flight()
        gate.release()


def reset_admission_gate_for_tests() -> None:
    global _GATE, _GATE_LIMIT, _IN_FLIGHT
    with _GATE_LOCK:
        _GATE = None
        _GATE_LIMIT = None
        _IN_FLIGHT = 0


def admission_snapshot() -> dict[str, int]:
    limit = _configured_limit()
    if limit <= 0:
        return {"limit": 0, "in_flight": 0, "available": 0}
    with _GATE_LOCK:
        in_flight = _IN_FLIGHT
    return {
        "limit": limit,
        "in_flight": in_flight,
        "available": max(limit - in_flight, 0),
    }


def _admission_gate() -> threading.BoundedSemaphore | None:
    global _GATE, _GATE_LIMIT
    limit = _configured_limit()
    if limit <= 0:
        return None
    with _GATE_LOCK:
        if _GATE is None or _GATE_LIMIT != limit:
            _GATE = threading.BoundedSemaphore(limit)
            _GATE_LIMIT = limit
        return _GATE


def _configured_limit() -> int:
    raw = os.environ.get("AI_REVIEW_MAX_IN_FLIGHT_REQUESTS", "").strip()
    if not raw:
        return DEFAULT_MAX_IN_FLIGHT_REQUESTS
    try:
        return int(raw)
    except ValueError:
        return DEFAULT_MAX_IN_FLIGHT_REQUESTS


def _increment_in_flight() -> None:
    global _IN_FLIGHT
    with _GATE_LOCK:
        _IN_FLIGHT += 1


def _decrement_in_flight() -> None:
    global _IN_FLIGHT
    with _GATE_LOCK:
        _IN_FLIGHT = max(_IN_FLIGHT - 1, 0)

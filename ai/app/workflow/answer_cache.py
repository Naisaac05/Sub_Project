from collections import OrderedDict
from collections.abc import Callable
import json
import os
from pathlib import Path
import threading
from typing import Any, TypeVar

from app.schemas import AiGenerateRequest
from app.workflow.intent import normalize_question


MAX_CACHE_ITEMS = 128
_CACHE: OrderedDict[str, str] = OrderedDict()
_CACHE_LOCK = threading.RLock()
_PERSISTENT_CACHE_PATH: Path | None = None
_PERSISTENT_CACHE_LOADED = False
_IN_FLIGHT: dict[str, dict[str, Any]] = {}
_IN_FLIGHT_LOCK = threading.Lock()
T = TypeVar("T")


def cache_key_for(mode: str, request: AiGenerateRequest) -> str:
    parts = (
        mode,
        request.model,
        request.question,
        request.correct_answer,
        request.selected_answer,
        request.user_answer,
        request.evaluation,
    )
    return "|".join(normalize_question(part) for part in parts)


def get_cached_answer(key: str) -> str | None:
    with _CACHE_LOCK:
        _ensure_persistent_cache_loaded()
        answer = _CACHE.get(key)
        if answer is None:
            return None
        _CACHE.move_to_end(key)
        return answer


def put_cached_answer(key: str, answer: str) -> None:
    if not answer:
        return
    with _CACHE_LOCK:
        _CACHE[key] = answer
        _CACHE.move_to_end(key)
        _trim_cache()
        _append_persistent_cache_entry(key, answer)


def clear_answer_cache() -> None:
    global _PERSISTENT_CACHE_LOADED
    with _CACHE_LOCK:
        _CACHE.clear()
        _PERSISTENT_CACHE_LOADED = False


def run_single_flight(key: str, producer: Callable[[], T]) -> T:
    leader = False
    with _IN_FLIGHT_LOCK:
        entry = _IN_FLIGHT.get(key)
        if entry is None:
            entry = {"event": threading.Event(), "result": None, "exception": None}
            _IN_FLIGHT[key] = entry
            leader = True

    if not leader:
        entry["event"].wait()
        if entry["exception"] is not None:
            raise entry["exception"]
        return entry["result"]

    try:
        result = producer()
        entry["result"] = result
        return result
    except Exception as exc:
        entry["exception"] = exc
        raise
    finally:
        with _IN_FLIGHT_LOCK:
            _IN_FLIGHT.pop(key, None)
            entry["event"].set()


def _configured_cache_path() -> Path | None:
    raw_path = os.environ.get("AI_REVIEW_ANSWER_CACHE_PATH", "").strip()
    return Path(raw_path) if raw_path else None


def _ensure_persistent_cache_loaded() -> None:
    global _PERSISTENT_CACHE_LOADED, _PERSISTENT_CACHE_PATH
    path = _configured_cache_path()
    if path != _PERSISTENT_CACHE_PATH:
        _PERSISTENT_CACHE_PATH = path
        _PERSISTENT_CACHE_LOADED = False
    if path is None or _PERSISTENT_CACHE_LOADED:
        return
    if not path.exists() or not path.is_file():
        _PERSISTENT_CACHE_LOADED = True
        return

    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError:
        _PERSISTENT_CACHE_LOADED = True
        return
    for line in lines:
        if not line.strip():
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError:
            continue
        key = str(row.get("key", ""))
        answer = str(row.get("answer", ""))
        if key and answer:
            _CACHE[key] = answer
            _CACHE.move_to_end(key)
    _trim_cache()
    _PERSISTENT_CACHE_LOADED = True


def _append_persistent_cache_entry(key: str, answer: str) -> None:
    path = _configured_cache_path()
    if path is None:
        return
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("a", encoding="utf-8", newline="\n") as handle:
            handle.write(json.dumps({"key": key, "answer": answer}, ensure_ascii=False, sort_keys=True))
            handle.write("\n")
    except OSError:
        return


def _trim_cache() -> None:
    while len(_CACHE) > MAX_CACHE_ITEMS:
        _CACHE.popitem(last=False)

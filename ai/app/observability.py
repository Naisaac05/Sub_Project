from __future__ import annotations

import json
import logging
import uuid
from datetime import date
from pathlib import Path
import threading

from app.schemas import AiGenerateResponse

CORRELATION_ID_HEADER = "X-Correlation-ID"
LOGGER_NAME = "ai_review.observability"
OLLAMA_FALLBACK_LOG_DIR = Path(__file__).resolve().parents[1] / "logs"
_OLLAMA_FALLBACK_LOG_LOCK = threading.Lock()


def correlation_id_from(value: str | None) -> str:
    if value and value.strip():
        return value.strip()
    return f"ai-review-{uuid.uuid4()}"


def emit_observability_events(
    response: AiGenerateResponse,
    correlation_id: str,
    logger=None,
) -> None:
    sink = logger or logging.getLogger(LOGGER_NAME)
    events = response.observability_events or [{"event": "ai_review.workflow_completed"}]
    enriched_events = []
    for event in events:
        enriched = dict(event)
        enriched["correlation_id"] = correlation_id
        enriched["fallback_used"] = bool(response.fallback_used)
        enriched["retrieval_miss"] = not bool(response.retrieved_concept_ids)
        enriched["candidate_captured"] = bool(response.candidate_id)
        enriched["candidate_id"] = response.candidate_id
        quality_flags = set(response.quality_flags or [])
        enriched["candidate_capture_disabled"] = "candidate_capture_disabled" in quality_flags
        enriched["candidate_capture_failed"] = "candidate_capture_failed" in quality_flags
        enriched["route"] = response.route
        enriched["model_used"] = response.model_used
        enriched["cache_hit"] = response.route == "cache"
        enriched["llm_call_avoided"] = response.route in {
            "cache",
            "static_fast_path",
            "generated_card_fast_path",
            "lightweight_only_miss",
        }
        enriched_events.append(enriched)
        sink.info(json.dumps(enriched, ensure_ascii=False, sort_keys=True))
    response.observability_events = enriched_events


def emit_ollama_fallback_log(event: dict[str, object]) -> Path:
    OLLAMA_FALLBACK_LOG_DIR.mkdir(parents=True, exist_ok=True)
    path = OLLAMA_FALLBACK_LOG_DIR / f"ollama_fallback_{date.today().isoformat()}.log"
    payload = {
        "route": event.get("route"),
        "ollama_duration": event.get("ollama_duration", 0),
        "fallback_reason": event.get("fallback_reason"),
        "v2_hit": bool(event.get("v2_hit", False)),
    }
    try:
        with _OLLAMA_FALLBACK_LOG_LOCK:
            with path.open("a", encoding="utf-8") as log_file:
                log_file.write(json.dumps(payload, ensure_ascii=False, sort_keys=True) + "\n")
    except OSError:
        logging.getLogger(LOGGER_NAME).exception("Failed to write Ollama fallback log")
    return path

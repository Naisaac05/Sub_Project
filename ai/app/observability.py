from __future__ import annotations

import json
import logging
import uuid

from app.schemas import AiGenerateResponse

CORRELATION_ID_HEADER = "X-Correlation-ID"
LOGGER_NAME = "ai_review.observability"


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
        enriched["route"] = response.route
        enriched["model_used"] = response.model_used
        enriched["cache_hit"] = response.route == "cache"
        enriched["llm_call_avoided"] = response.route in {
            "cache",
            "static_fast_path",
            "generated_card_fast_path",
        }
        enriched_events.append(enriched)
        sink.info(json.dumps(enriched, ensure_ascii=False, sort_keys=True))
    response.observability_events = enriched_events

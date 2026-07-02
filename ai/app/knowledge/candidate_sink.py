from __future__ import annotations

import json
import logging
import os
from typing import Any
from urllib import error, request
from urllib.parse import urlsplit, urlunsplit


DEFAULT_CAPTURE_URL = "http://localhost:8080/api/internal/ai-review/candidates/capture"
SERVICE_TOKEN_HEADER = "X-AI-Service-Token"
logger = logging.getLogger(__name__)


def candidate_sink_mode() -> str:
    return os.environ.get("AI_REVIEW_CANDIDATE_SINK", "http").strip().lower() or "http"


def save_auto_candidate(candidate: dict[str, Any]) -> bool:
    if candidate_sink_mode() == "off":
        return False
    return _post_candidate(candidate)


def _post_candidate(candidate: dict[str, Any]) -> bool:
    url = os.environ.get("AI_REVIEW_CANDIDATE_CAPTURE_URL", DEFAULT_CAPTURE_URL).strip() or DEFAULT_CAPTURE_URL
    payload = {
        "candidateId": candidate.get("candidate_id", ""),
        "term": candidate.get("term", ""),
        "category": candidate.get("category", "auto-review"),
        "definitionDraft": candidate.get("definition_draft", ""),
        "sourceQuestion": candidate.get("source_question", ""),
        "resolvedQuery": candidate.get("resolved_query", ""),
        "route": candidate.get("route", ""),
        "confidenceScore": candidate.get("confidence_score"),
        "needsReviewReason": candidate.get("needs_review_reason", ""),
    }
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    token = os.environ.get("AI_REVIEW_SERVICE_TOKEN", "").strip()
    if token:
        headers[SERVICE_TOKEN_HEADER] = token
    timeout = _capture_timeout_seconds()

    try:
        req = request.Request(url, data=body, headers=headers, method="POST")
        with request.urlopen(req, timeout=timeout) as response:
            return 200 <= response.status < 300
    except error.HTTPError as exc:
        logger.warning(
            "candidate capture failed url=%s status=%s",
            _sanitized_url(url),
            exc.code,
        )
        return False
    except (OSError, error.URLError, TimeoutError, ValueError) as exc:
        logger.warning(
            "candidate capture failed url=%s error=%s",
            _sanitized_url(url),
            type(exc).__name__,
        )
        return False


def _sanitized_url(url: str) -> str:
    try:
        parsed = urlsplit(url)
        host = parsed.hostname or ""
        if ":" in host:
            host = f"[{host}]"
        port = parsed.port
    except ValueError:
        return "<invalid-url>"
    netloc = f"{host}:{port}" if port is not None else host
    return urlunsplit((parsed.scheme, netloc, parsed.path, "", ""))


def _capture_timeout_seconds() -> float:
    try:
        value = float(os.environ.get("AI_REVIEW_CANDIDATE_CAPTURE_TIMEOUT_SECONDS", "2"))
    except ValueError:
        return 2.0
    return value if value > 0 else 2.0

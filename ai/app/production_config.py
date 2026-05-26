from __future__ import annotations

import os
from collections.abc import Mapping


PRODUCTION_ENV_VALUES = {"prod", "production"}


def is_production_environment(env: Mapping[str, str] | None = None) -> bool:
    source = env or os.environ
    for name in ("ENVIRONMENT", "APP_ENV", "PYTHON_ENV"):
        if source.get(name, "").strip().lower() in PRODUCTION_ENV_VALUES:
            return True
    return False


def validate_production_config(env: Mapping[str, str] | None = None) -> None:
    source = env or os.environ
    if not is_production_environment(source):
        return

    errors: list[str] = []
    if not source.get("AI_REVIEW_SERVICE_TOKEN", "").strip():
        errors.append("AI_REVIEW_SERVICE_TOKEN is required in prod")
    if source.get("AI_REVIEW_CANDIDATE_SINK", "http").strip().lower() == "jsonl":
        errors.append("AI_REVIEW_CANDIDATE_SINK=jsonl is forbidden in prod")

    _require_positive_number(errors, source, "OLLAMA_REQUEST_TIMEOUT_SECONDS")
    _require_positive_number(errors, source, "OLLAMA_QUEUE_WAIT_TIMEOUT_SECONDS")
    _require_positive_number(errors, source, "AI_REVIEW_CANDIDATE_CAPTURE_TIMEOUT_SECONDS")
    _require_positive_number(errors, source, "PYTHON_AI_MAX_TOKENS")
    _require_positive_number(errors, source, "PYTHON_AI_NUM_CTX")
    _require_positive_number(errors, source, "AI_REVIEW_MAX_USER_ANSWER_LENGTH")

    if errors:
        raise RuntimeError("Unsafe AI Review production configuration: " + "; ".join(errors))


def _require_positive_number(errors: list[str], env: Mapping[str, str], name: str) -> None:
    raw_value = env.get(name, "").strip()
    if not raw_value:
        errors.append(f"{name} must be > 0 in prod")
        return
    try:
        value = float(raw_value)
    except ValueError:
        errors.append(f"{name} must be numeric in prod")
        return
    if value <= 0:
        errors.append(f"{name} must be > 0 in prod")

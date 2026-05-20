from __future__ import annotations

import os
import re

DEFAULT_MAX_INPUT_LENGTH = 700

PII_PATTERNS = {
    "phone_kr": re.compile(r"\b01[016789][-\s]?\d{3,4}[-\s]?\d{4}\b"),
    "ssn_kr": re.compile(r"\b\d{6}[-\s]?\d{7}\b"),
    "email": re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"),
    "card": re.compile(r"\b\d{4}[-\s]?\d{4}[-\s]?\d{4}[-\s]?\d{4}\b"),
    "ipv4": re.compile(r"\b(?:\d{1,3}\.){3}\d{1,3}\b"),
}

PROMPT_INJECTION_PATTERNS = (
    re.compile(r"ignore\s+(all\s+)?previous\s+instructions", re.IGNORECASE),
    re.compile(r"reveal\s+(the\s+)?system\s+prompt", re.IGNORECASE),
    re.compile(r"developer\s+message", re.IGNORECASE),
)


def mask_pii(text: str) -> str:
    for name, pattern in PII_PATTERNS.items():
        text = pattern.sub(f"[REDACTED_{name.upper()}]", text)
    return text


def neutralize_prompt_injection(text: str) -> str:
    for pattern in PROMPT_INJECTION_PATTERNS:
        text = pattern.sub("[BLOCKED_PROMPT_INJECTION]", text)
    return text


def sanitize_text(text: str, max_length: int | None = None) -> str:
    guarded = neutralize_prompt_injection(mask_pii(text))
    limit = max_length or _max_input_length()
    return guarded[:limit]


def _max_input_length() -> int:
    try:
        return int(os.environ.get("AI_REVIEW_MAX_INPUT_LENGTH", DEFAULT_MAX_INPUT_LENGTH))
    except ValueError:
        return DEFAULT_MAX_INPUT_LENGTH

from __future__ import annotations

import os
import secrets

from fastapi import HTTPException, Request, status

SERVICE_TOKEN_HEADER = "X-AI-Service-Token"


def verify_service_token(request: Request) -> None:
    expected = os.environ.get("AI_REVIEW_SERVICE_TOKEN", "")
    if not expected:
        return
    actual = request.headers.get(SERVICE_TOKEN_HEADER, "")
    if not secrets.compare_digest(actual, expected):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid AI service token",
        )

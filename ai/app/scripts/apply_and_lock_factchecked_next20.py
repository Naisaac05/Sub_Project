from __future__ import annotations

import copy

from app.scripts.initialize_validation_policy_v212 import validate_lock


def build_locked_candidate(
    original: dict,
    patch: dict,
    *,
    updated_at: str | None = None,
) -> tuple[dict | None, list[str]]:
    if original.get("review", {}).get("card_status") != "approved":
        return None, ["transition_requires_approved"]

    candidate = copy.deepcopy(original)
    candidate["payloads"] = copy.deepcopy(patch["payloads"])
    candidate["review"]["card_status"] = "approved_locked"
    if updated_at is not None:
        candidate["updated_at"] = updated_at

    reasons = validate_lock(original, candidate)
    return (None, reasons) if reasons else (candidate, [])

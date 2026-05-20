from __future__ import annotations

from datetime import datetime, timezone
from typing import Any


VALID_ACTIONS = {"approve", "reject", "hold"}


def apply_human_review(
    candidate: dict[str, Any],
    action: str,
    definition: str | None = None,
    rejected_reason: str = "",
    reviewed_at: str | None = None,
    reviewer: str = "manual-cli",
) -> dict[str, Any]:
    if action not in VALID_ACTIONS:
        raise ValueError(f"Unsupported human review action: {action}")

    reviewed = dict(candidate)
    timestamp = reviewed_at or _now_iso()
    reviewed["reviewed_at"] = timestamp
    reviewed["reviewer"] = reviewer

    if action == "approve":
        approved_definition = (definition or str(reviewed.get("definition_draft", ""))).strip()
        if not approved_definition:
            raise ValueError("Approving a candidate requires a definition or definition_draft")
        reviewed["definition"] = approved_definition
        reviewed["approved"] = True
        reviewed["human_review_status"] = "approved"
        reviewed["definition_status"] = "human_approved"
        reviewed.setdefault("rejected_reason", "")
        return reviewed

    if action == "reject":
        reviewed["approved"] = False
        reviewed["human_review_status"] = "rejected"
        reviewed["definition_status"] = "human_rejected"
        reviewed["rejected_reason"] = rejected_reason.strip() or "No reason provided"
        return reviewed

    reviewed["approved"] = False
    reviewed["human_review_status"] = "hold"
    reviewed["definition_status"] = "human_hold"
    reviewed["rejected_reason"] = rejected_reason.strip()
    return reviewed


def review_candidate_by_term(
    candidates: list[dict[str, Any]],
    term: str,
    category: str | None,
    action: str,
    definition: str | None = None,
    rejected_reason: str = "",
    reviewer: str = "manual-cli",
) -> tuple[list[dict[str, Any]], int]:
    reviewed_rows: list[dict[str, Any]] = []
    changed = 0
    for candidate in candidates:
        if _matches(candidate, term, category):
            reviewed_rows.append(
                apply_human_review(
                    candidate,
                    action=action,
                    definition=definition,
                    rejected_reason=rejected_reason,
                    reviewer=reviewer,
                )
            )
            changed += 1
        else:
            reviewed_rows.append(candidate)
    return reviewed_rows, changed


def _matches(candidate: dict[str, Any], term: str, category: str | None) -> bool:
    if str(candidate.get("term", "")).lower() != term.lower():
        return False
    if category and str(candidate.get("category", "")).lower() != category.lower():
        return False
    return True


def _now_iso() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")

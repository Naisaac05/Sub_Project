from __future__ import annotations

import re
from typing import Any

from app.schemas.rag_card import RagCard


def mark_duplicate_candidates(
    candidates: list[dict[str, Any]],
    cards: list[RagCard],
) -> list[dict[str, Any]]:
    marked: list[dict[str, Any]] = []
    for candidate in candidates:
        duplicate_ids = _duplicate_concept_ids(candidate, cards)
        row = dict(candidate)
        if duplicate_ids:
            row["duplicate_status"] = "duplicate_suspected"
            row["duplicate_concept_ids"] = duplicate_ids
            row["duplicate_reason"] = (
                "Candidate term or alias overlaps existing concept card(s): "
                + ", ".join(duplicate_ids)
            )
        else:
            row.setdefault("duplicate_status", "unique")
            row.setdefault("duplicate_concept_ids", [])
            row.setdefault("duplicate_reason", "")
        marked.append(row)
    return marked


def _duplicate_concept_ids(candidate: dict[str, Any], cards: list[RagCard]) -> list[str]:
    needles = _candidate_needles(candidate)
    duplicates: list[str] = []
    for card in cards:
        haystack = _normalize(card.searchable_text)
        if any(needle and needle in haystack for needle in needles):
            duplicates.append(card.concept_id)
    return sorted(set(duplicates))


def _candidate_needles(candidate: dict[str, Any]) -> list[str]:
    values = [str(candidate.get("term", ""))]
    values.extend(str(alias) for alias in candidate.get("aliases", []))
    needles = []
    for value in values:
        normalized = _normalize(value)
        if normalized and normalized not in {"api", "dto", "orm", "csv", "ttl", "udp", "key", "state", "error"}:
            needles.append(normalized)
    return sorted(set(needles), key=len, reverse=True)


def _normalize(value: str) -> str:
    return re.sub(r"[^a-z0-9가-힣]+", "", value.lower())

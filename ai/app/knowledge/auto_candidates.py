from __future__ import annotations

from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
from typing import Any


def build_auto_candidate(
    source_question: str,
    resolved_query: str,
    route: str | None,
    confidence_score: float | None,
    needs_review_reason: str,
    generated_answer: str = "",
) -> dict[str, Any]:
    normalized_query = _normalize(resolved_query or source_question)
    term = _extract_term(resolved_query or source_question)
    candidate_id = "auto-" + hashlib.sha1(normalized_query.encode("utf-8")).hexdigest()[:12]
    now = datetime.now(timezone.utc).isoformat()
    definition_draft = generated_answer.strip()

    return {
        "candidate_id": candidate_id,
        "term": term,
        "aliases": [term] if term else [],
        "category": "auto-review",
        "definition": "",
        "definition_draft": definition_draft,
        "definition_status": "drafted" if definition_draft else "needs_draft",
        "approved": False,
        "status": "needs_review",
        "source": "ai-review-auto-candidate",
        "source_question": source_question,
        "resolved_query": resolved_query,
        "route": route or "",
        "confidence_score": confidence_score,
        "needs_review_reason": needs_review_reason,
        "created_at": now,
        "sources": ["ai-review-free-question"],
    }


def append_auto_candidate(path: Path, candidate: dict[str, Any]) -> bool:
    if path.exists() and not path.is_file():
        return False
    path.parent.mkdir(parents=True, exist_ok=True)
    candidate_id = str(candidate.get("candidate_id", ""))
    if _update_existing_candidate(path, candidate_id, candidate):
        return False

    try:
        with path.open("a", encoding="utf-8", newline="\n") as handle:
            handle.write(json.dumps(candidate, ensure_ascii=False, sort_keys=True))
            handle.write("\n")
        return True
    except OSError:
        return False


def _update_existing_candidate(path: Path, candidate_id: str, candidate: dict[str, Any]) -> bool:
    if not path.exists() or not path.is_file() or not candidate_id:
        return False

    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError:
        return False

    changed = False
    found = False
    rows: list[dict[str, Any] | str] = []
    for line in lines:
        if not line.strip():
            rows.append(line)
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError:
            rows.append(line)
            continue
        if str(row.get("candidate_id", "")) == candidate_id:
            found = True
            new_draft = str(candidate.get("definition_draft", "")).strip()
            old_draft = str(row.get("definition_draft", "")).strip()
            new_reason = str(candidate.get("needs_review_reason", "")).strip()
            if new_draft and not old_draft:
                row["definition_draft"] = new_draft
                row["definition_status"] = "drafted"
                row["route"] = candidate.get("route", row.get("route", ""))
                row["confidence_score"] = candidate.get("confidence_score", row.get("confidence_score"))
                changed = True
            if new_reason == "static_answer_unapproved" and row.get("needs_review_reason") == "no_match":
                row["needs_review_reason"] = new_reason
                row["route"] = candidate.get("route", row.get("route", ""))
                changed = True
        rows.append(row)

    if found and changed:
        serialized = [
            json.dumps(row, ensure_ascii=False, sort_keys=True) if isinstance(row, dict) else row
            for row in rows
        ]
        path.write_text("\n".join(serialized) + "\n", encoding="utf-8")
    return found


def should_capture_auto_candidate(
    mode: str,
    route: str | None,
    confidence_score: float | None,
    retrieved_concept_ids: list[str],
    fallback_used: bool | None,
) -> str | None:
    if mode != "free-question":
        return None
    if fallback_used:
        return "fallback_used"
    if route == "static_fast_path" and not retrieved_concept_ids:
        return "static_answer_unapproved"
    if route in {"generation", "fallback_template"}:
        return "no_match"
    if confidence_score is not None and confidence_score < 0.6:
        return "low_confidence"
    if not retrieved_concept_ids and route not in {"static_fast_path", "generated_card_fast_path"}:
        return "no_retrieved_context"
    return None


def _existing_candidate_ids(path: Path) -> set[str]:
    if not path.exists() or not path.is_file():
        return set()

    ids: set[str] = set()
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError:
        return set()

    for line in lines:
        if not line.strip():
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError:
            continue
        candidate_id = str(row.get("candidate_id", ""))
        if candidate_id:
            ids.add(candidate_id)
    return ids


def _extract_term(text: str) -> str:
    stripped = text.strip()
    if not stripped:
        return "unknown"

    technical = re.findall(r"[@A-Za-z][@A-Za-z0-9+#_.-]*|[가-힣]{2,}", stripped)
    if not technical:
        return stripped[:30]
    particle_trimmed = re.sub(r"(이|가|은|는|을|를)$", "", stripped)
    if " " in particle_trimmed and len(particle_trimmed) <= 40:
        tokens = particle_trimmed.split()
        if tokens and all(token in technical for token in tokens):
            return particle_trimmed
    first = technical[0]
    return re.sub(r"(이|가|은|는|을|를)$", "", first) or first


def _normalize(text: str) -> str:
    return re.sub(r"[^a-z0-9가-힣@+#]+", "", text.lower())

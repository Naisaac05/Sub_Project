from __future__ import annotations

from datetime import date
import hashlib
import json
from pathlib import Path
import re
from typing import Any


def load_candidate_jsonl(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []

    rows: list[dict[str, Any]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        rows.append(json.loads(line))
    return rows


def promote_approved_candidates(
    candidates: list[dict[str, Any]],
    output_root: Path,
    today: str | None = None,
) -> list[Path]:
    approved = [
        candidate
        for candidate in candidates
        if candidate.get("approved") is True
        and str(candidate.get("definition", "")).strip()
    ]
    output_root.mkdir(parents=True, exist_ok=True)

    written: list[Path] = []
    for candidate in sorted(approved, key=lambda row: _concept_id(row)):
        concept_id = _concept_id(candidate)
        path = output_root / f"{concept_id}.md"
        path.write_text(
            _render_concept_card(candidate, concept_id, today or date.today().isoformat()),
            encoding="utf-8",
            newline="\n",
        )
        written.append(path)
    return written


def _concept_id(candidate: dict[str, Any]) -> str:
    category = _slug(str(candidate.get("category", "course")))
    term = _slug(str(candidate.get("term", "")))
    if not term:
        digest = hashlib.sha1(str(candidate.get("term", "")).encode("utf-8")).hexdigest()[:8]
        term = f"concept-{digest}"
    return f"{category}-{term}"


def _slug(value: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")
    return re.sub(r"-+", "-", slug)


def _render_concept_card(candidate: dict[str, Any], concept_id: str, today: str) -> str:
    term = str(candidate.get("term", "")).strip() or concept_id
    aliases = [str(alias).strip() for alias in candidate.get("aliases", []) if str(alias).strip()]
    category = str(candidate.get("category", "course")).strip() or "course"
    definition = str(candidate.get("definition", "")).strip()
    source_question_ids = [str(item) for item in candidate.get("source_question_ids", [])]
    keywords = _keyword_lines(term, aliases)
    source_note = ", ".join(source_question_ids) if source_question_ids else "course-candidate"

    return f"""---
id: {concept_id}
category: {category}
difficulty: intermediate
version: course-candidate
last_updated: {today}
---

# {term}

## 핵심 설명
{definition}

## 대표 해결
- 면접 답변에서는 {term}의 정의와 실제로 쓰이는 상황을 함께 설명한다.
- 문제 풀이에서는 보기 중 어떤 조건이 {term}과 직접 연결되는지 먼저 확인한다.

## 흔한 오해
- 후보 카드가 승인되기 전에는 정답 지식으로 사용하지 않는다.
- 단어 정의만 외우기보다 관련 문제 맥락과 함께 이해한다.

## 평가 키워드
{keywords}
- source:{source_note}
"""


def _keyword_lines(term: str, aliases: list[str]) -> str:
    keywords = []
    for keyword in [term, *aliases]:
        if keyword and keyword.lower() not in {item.lower() for item in keywords}:
            keywords.append(keyword)
        if len(keywords) >= 4:
            break
    while len(keywords) < 2:
        keywords.append(f"{term} context")
    return "\n".join(f"- {keyword}" for keyword in keywords)

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
import json
from pathlib import Path
from typing import Any, Protocol


DRAFT_VERSION = "candidate-draft-v1"
CRITIC_VERSION = "candidate-critic-v1"

CURATED_REVIEWS: dict[str, dict[str, str]] = {
    "aria-label": {
        "definition": (
            "aria-label은 화면에 보이는 텍스트가 없거나 부족한 요소에 스크린리더가 읽을 수 있는 이름을 제공하는 HTML 접근성 속성입니다. "
            "아이콘 버튼처럼 시각적으로만 의미가 전달되는 요소에 사용하면 보조기술 사용자가 버튼의 목적을 이해할 수 있습니다."
        ),
        "critic": (
            "보이는 텍스트가 이미 충분한 요소에 aria-label을 중복으로 붙이면 보조기술 사용자에게 혼란을 줄 수 있습니다. "
            "아이콘 버튼처럼 접근 가능한 이름이 부족한 인터랙티브 요소인지 확인하세요."
        ),
        "recommendation": "approve",
        "risk": "low",
    },
    "@controlleradvice": {
        "definition": (
            "@ControllerAdvice는 여러 컨트롤러에서 공통으로 적용할 예외 처리나 바인딩 설정을 한곳에 모아두는 Spring MVC 어노테이션입니다. "
            "REST API에서는 전역 예외 응답 형식을 일관되게 만들 때 자주 사용합니다."
        ),
        "critic": (
            "@ControllerAdvice 자체가 예외를 자동으로 해결하는 것은 아니며, 보통 @ExceptionHandler 같은 메서드와 함께 동작합니다. "
            "전역 처리 범위와 응답 형식이 프로젝트 규칙과 맞는지 확인하세요."
        ),
        "recommendation": "approve",
        "risk": "low",
    },
}


class CandidateReviewProvider(Protocol):
    model_name: str

    def draft_definition(self, candidate: dict[str, Any]) -> str:
        ...

    def critic_feedback(self, candidate: dict[str, Any], definition_draft: str) -> dict[str, str]:
        ...


@dataclass(frozen=True)
class TemplateCandidateReviewProvider:
    model_name: str = "template-candidate-review"

    def draft_definition(self, candidate: dict[str, Any]) -> str:
        term = _term(candidate)
        curated = _curated_review(term)
        if curated:
            return curated["definition"]
        category = str(candidate.get("category", "course")).replace("-", " ")
        return (
            f"{term}은 {category} 학습 맥락에서 확인해야 하는 기술 개념입니다. "
            f"정확한 승인을 위해 원본 문제와 선택지에서 {term}이 어떤 조건과 연결되는지 함께 검토해야 합니다."
        )

    def critic_feedback(self, candidate: dict[str, Any], definition_draft: str) -> dict[str, str]:
        term = _term(candidate)
        curated = _curated_review(term)
        if curated:
            return {
                "risk_level": curated["risk"],
                "approval_recommendation": curated["recommendation"],
                "critic_feedback": curated["critic"],
                "suggested_revision": curated["definition"],
            }
        risk_level = "medium"
        recommendation = "edit"
        if _looks_specific(candidate) and len(definition_draft) >= 50:
            risk_level = "low"
            recommendation = "approve"
        if term.lower() in {"api", "state", "key", "error"}:
            risk_level = "high"
            recommendation = "hold"

        return {
            "risk_level": risk_level,
            "approval_recommendation": recommendation,
            "critic_feedback": (
                f"{term} 정의가 초중급자에게 너무 넓거나 문제 맥락과 분리되어 보이지 않는지 확인하세요. "
                "승인 전 원본 문제 ID와 기존 concept card 중복 여부를 함께 점검하는 것이 좋습니다."
            ),
            "suggested_revision": definition_draft,
        }


@dataclass(frozen=True)
class OllamaCandidateReviewProvider:
    model_name: str = "exaone3.5:2.4b"

    def draft_definition(self, candidate: dict[str, Any]) -> str:
        from app.ollama.client import call_ollama

        prompt = (
            "초중급 학습자를 위한 기술 개념 정의를 한국어로 작성하세요.\n"
            "조건: 1~3문장, 과장 금지, 문제 풀이 맥락 포함, JSON 금지.\n\n"
            f"term: {_term(candidate)}\n"
            f"category: {candidate.get('category', '')}\n"
            f"aliases: {candidate.get('aliases', [])}\n"
            f"source_question_ids: {candidate.get('source_question_ids', [])}\n"
        )
        return call_ollama(
            model=self.model_name,
            prompt=prompt,
            temperature=0.1,
            max_tokens=180,
            num_ctx=768,
            num_thread=4,
        )

    def critic_feedback(self, candidate: dict[str, Any], definition_draft: str) -> dict[str, str]:
        from app.ollama.client import call_ollama

        prompt = (
            "너는 초중급 학습자를 위한 기술 개념 정의를 검수하는 Critic AI다.\n"
            "아래 정의가 오해를 만들 수 있는지, 문맥 누락이 있는지, 환각 가능성이 있는지 평가하라.\n"
            "반드시 JSON만 출력하라. 키는 risk_level, approval_recommendation, critic_feedback, suggested_revision.\n"
            "risk_level은 low, medium, high 중 하나. approval_recommendation은 approve, edit, hold, reject 중 하나.\n\n"
            f"term: {_term(candidate)}\n"
            f"category: {candidate.get('category', '')}\n"
            f"source_question_ids: {candidate.get('source_question_ids', [])}\n"
            f"definition_draft: {definition_draft}\n"
        )
        raw = call_ollama(
            model=self.model_name,
            prompt=prompt,
            temperature=0,
            max_tokens=220,
            num_ctx=1024,
            num_thread=4,
        )
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            return {
                "risk_level": "medium",
                "approval_recommendation": "edit",
                "critic_feedback": raw,
                "suggested_revision": definition_draft,
            }
        return {
            "risk_level": str(parsed.get("risk_level", "medium")),
            "approval_recommendation": str(parsed.get("approval_recommendation", "edit")),
            "critic_feedback": str(parsed.get("critic_feedback", "")),
            "suggested_revision": str(parsed.get("suggested_revision", definition_draft)),
        }


def enrich_candidate_with_ai_review(
    candidate: dict[str, Any],
    provider: CandidateReviewProvider,
    drafted_at: str | None = None,
) -> dict[str, Any]:
    enriched = dict(candidate)
    timestamp = drafted_at or _now_iso()
    definition_draft = str(enriched.get("definition_draft") or "").strip()
    if not definition_draft:
        definition_draft = provider.draft_definition(enriched).strip()

    critic = provider.critic_feedback(enriched, definition_draft)
    source_ids = [str(item) for item in enriched.get("source_question_ids", [])]

    enriched.update(
        {
            "definition_draft": definition_draft,
            "definition_status": "critic_reviewed",
            "draft_model": provider.model_name,
            "draft_version": DRAFT_VERSION,
            "drafted_at": timestamp,
            "critic_feedback": critic,
            "critic_risk_level": critic.get("risk_level", "medium"),
            "critic_recommendation": critic.get("approval_recommendation", "edit"),
            "critic_model": provider.model_name,
            "critic_version": CRITIC_VERSION,
            "criticized_at": timestamp,
            "sources": source_ids,
            "rejected_reason": str(enriched.get("rejected_reason", "")),
        }
    )
    enriched.setdefault("approved", False)
    return enriched


def enrich_candidates_with_ai_review(
    candidates: list[dict[str, Any]],
    provider: CandidateReviewProvider,
    limit: int | None = None,
) -> tuple[list[dict[str, Any]], int]:
    enriched_rows: list[dict[str, Any]] = []
    changed = 0

    for candidate in candidates:
        if _should_skip(candidate) or (limit is not None and changed >= limit):
            enriched_rows.append(candidate)
            continue
        enriched_rows.append(enrich_candidate_with_ai_review(candidate, provider))
        changed += 1

    return enriched_rows, changed


def load_candidate_jsonl(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def write_candidate_jsonl(candidates: list[dict[str, Any]], output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    rows = sorted(candidates, key=lambda row: (str(row.get("term", "")).lower(), str(row.get("category", ""))))
    with output_path.open("w", encoding="utf-8", newline="\n") as file:
        for row in rows:
            file.write(json.dumps(row, ensure_ascii=False, sort_keys=True))
            file.write("\n")


def _should_skip(candidate: dict[str, Any]) -> bool:
    if candidate.get("approved") is True:
        return True
    return bool(str(candidate.get("definition_draft", "")).strip() and candidate.get("critic_feedback"))


def _term(candidate: dict[str, Any]) -> str:
    return str(candidate.get("term", "해당 개념")).strip() or "해당 개념"


def _curated_review(term: str) -> dict[str, str] | None:
    return CURATED_REVIEWS.get(term.strip().lower())


def _looks_specific(candidate: dict[str, Any]) -> bool:
    term = _term(candidate)
    return len(term) > 3 and term.lower() not in {"api", "dto", "orm", "csv", "ttl", "udp"}


def _now_iso() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")

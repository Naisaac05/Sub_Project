from __future__ import annotations

import json
import os
from pathlib import Path
import sys
import tempfile
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.rag.retriever import retrieve_context
from app.schemas import AiGenerateRequest
from app.evaluation.semantic import should_cache_answer
from app.workflow.runner import run_review_workflow
from app.workflow.intent import classify_free_question
from app.workflow.nodes import retrieve_context_node
from app.workflow.state import ReviewWorkflowState


DATASET_PATH = ROOT / "evals" / "golden_dataset.jsonl"
DEFAULT_MIN_DATASET_ROWS = 200


def load_dataset(path: Path | None = None) -> list[dict[str, object]]:
    dataset_path = path or DATASET_PATH
    rows: list[dict[str, object]] = []
    for line in dataset_path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            rows.append(json.loads(line))
    if path is None:
        rows = _expanded_default_dataset(rows, DEFAULT_MIN_DATASET_ROWS)
    return rows


def evaluate_dataset(
    rows: list[dict[str, object]],
    workflow_runner=None,
) -> dict[str, float | int]:
    total = len(rows)
    if total == 0:
        return {
            "total": 0,
            "retrieval_hit_rate": 0.0,
            "expected_concept_recall": 0.0,
            "intent_accuracy": 0.0,
            "sub_intent_accuracy": 0.0,
            "rag_policy_accuracy": 0.0,
            "stale_context_absent_rate": 0.0,
            "route_accuracy": 0.0,
            "quality_flag_absent_rate": 0.0,
            "answer_contains_required_keywords": 0.0,
            "forbidden_claims_absent": 0.0,
            "fallback_expectation_accuracy": 0.0,
            "candidate_capture_accuracy": 0.0,
            "observability_event_rate": 0.0,
            "semantic_grounding_pass_rate": 0.0,
            "contradiction_absent_rate": 0.0,
            "hallucination_cache_ban_rate": 0.0,
            "answer_grounding_rate": 0.0,
            "workflow_rows": 0,
        }

    hit_count = 0
    recall_sum = 0.0
    retrieval_rows = 0
    intent_rows = 0
    intent_hits = 0
    sub_intent_rows = 0
    sub_intent_hits = 0
    rag_policy_rows = 0
    rag_policy_hits = 0
    stale_context_rows = 0
    stale_context_absent = 0
    route_rows = 0
    route_hits = 0
    quality_flag_rows = 0
    quality_flag_hits = 0
    keyword_rows = 0
    keyword_hits = 0
    forbidden_rows = 0
    forbidden_hits = 0
    fallback_expectation_rows = 0
    fallback_expectation_hits = 0
    candidate_capture_rows = 0
    candidate_capture_hits = 0
    observability_event_rows = 0
    observability_event_hits = 0
    semantic_grounding_rows = 0
    semantic_grounding_hits = 0
    contradiction_rows = 0
    contradiction_hits = 0
    hallucination_cache_ban_rows = 0
    hallucination_cache_ban_hits = 0
    answer_grounding_rows = 0
    answer_grounding_hits = 0
    context_rows = 0
    context_hits = 0
    workflow_rows = 0
    for row in rows:
        expected = set(str(item) for item in row.get("expected_concepts", []))
        retrieved = retrieve_context(str(row.get("question", "")), limit=3)
        retrieved_ids = {item.concept_id for item in retrieved}
        if expected:
            retrieval_rows += 1
            matched = expected & retrieved_ids
            if matched:
                hit_count += 1
            recall_sum += len(matched) / len(expected)

        intent = classify_free_question(str(row.get("question", "")))
        expected_intent = row.get("expected_intent")
        if expected_intent:
            intent_rows += 1
            if intent.intent == expected_intent:
                intent_hits += 1

        # 세부 의도(sub_intent) 회귀 가드. classifier 의 intent enum 은 4종뿐이라
        # comparison/related/practical 같은 세부 라우팅은 sub_intent 로만 검증할 수 있다.
        expected_sub_intent = row.get("expected_sub_intent")
        if expected_sub_intent:
            sub_intent_rows += 1
            if intent.sub_intent == expected_sub_intent:
                sub_intent_hits += 1

        expected_rag_policy = row.get("expected_rag_policy")
        if expected_rag_policy:
            rag_policy_rows += 1
            if intent.rag_policy == expected_rag_policy:
                rag_policy_hits += 1

        forbidden = set(str(item) for item in row.get("forbidden_concepts", []))
        if forbidden:
            stale_context_rows += 1
            if not (forbidden & retrieved_ids):
                stale_context_absent += 1

        # 게이트 통과 컨텍스트 검사. 지식 베이스 성장(카드 승인)에도 깨지지 않도록
        # 정확 집합 일치가 아니라 의미 기반으로 판정한다:
        #  - expected_context_concepts: 이 카드들은 반드시 포함(subset)
        #  - forbidden_context_concepts: 이 카드들은 절대 미포함(disjoint, 약한 오매칭 회귀 방지)
        expected_context = row.get("expected_context_concepts")
        forbidden_context = row.get("forbidden_context_concepts")
        if expected_context is not None or forbidden_context is not None:
            context_rows += 1
            gated_state = retrieve_context_node(
                ReviewWorkflowState(
                    mode="free-question",
                    request=AiGenerateRequest(
                        question=str(row.get("question", "")),
                        user_answer=str(row.get("question", "")),
                    ),
                )
            )
            gated_ids = {context.concept_id for context in gated_state.contexts}
            expected_ok = expected_context is None or {str(item) for item in expected_context} <= gated_ids
            forbidden_ok = forbidden_context is None or not (
                {str(item) for item in forbidden_context} & gated_ids
            )
            if expected_ok and forbidden_ok:
                context_hits += 1

        workflow_response = _workflow_response_for(row, workflow_runner)
        required_keywords = [str(item) for item in row.get("required_keywords", [])]
        forbidden_claims = [str(item) for item in row.get("forbidden_claims", [])]
        if workflow_response is not None:
            workflow_rows += 1
            flags = set(str(item) for item in getattr(workflow_response, "quality_flags", []))
            answer = str(getattr(workflow_response, "answer", ""))
            semantic_grounding_rows += 1
            if "evidence_missing" not in flags:
                semantic_grounding_hits += 1
            contradiction_rows += 1
            if "contradiction_suspected" not in flags:
                contradiction_hits += 1
            if "hallucination_suspected" in flags:
                hallucination_cache_ban_rows += 1
                if not should_cache_answer(flags):
                    hallucination_cache_ban_hits += 1
            answer_grounding_rows += 1
            if _answer_is_grounded(
                flags=flags,
                answer=answer,
                required_keywords=required_keywords,
                forbidden_claims=forbidden_claims,
            ):
                answer_grounding_hits += 1

        expected_route = row.get("expected_route")
        if expected_route:
            route_rows += 1
            if workflow_response is not None and getattr(workflow_response, "route", None) == expected_route:
                route_hits += 1

        if required_keywords:
            keyword_rows += 1
            answer = str(getattr(workflow_response, "answer", "")) if workflow_response is not None else ""
            if all(keyword in answer for keyword in required_keywords):
                keyword_hits += 1

        if forbidden_claims:
            forbidden_rows += 1
            answer = str(getattr(workflow_response, "answer", "")) if workflow_response is not None else ""
            if not any(claim in answer for claim in forbidden_claims):
                forbidden_hits += 1

        absent_flags = [str(item) for item in row.get("expected_quality_flags_absent", [])]
        if absent_flags:
            quality_flag_rows += 1
            flags = set(str(item) for item in getattr(workflow_response, "quality_flags", [])) if workflow_response is not None else set()
            if not (set(absent_flags) & flags):
                quality_flag_hits += 1

        if "expected_fallback_used" in row:
            fallback_expectation_rows += 1
            fallback_used = bool(getattr(workflow_response, "fallback_used", False)) if workflow_response is not None else False
            if fallback_used == bool(row.get("expected_fallback_used")):
                fallback_expectation_hits += 1

        if "expected_candidate_captured" in row:
            candidate_capture_rows += 1
            candidate_id = str(getattr(workflow_response, "candidate_id", "") or "") if workflow_response is not None else ""
            if bool(candidate_id) == bool(row.get("expected_candidate_captured")):
                candidate_capture_hits += 1

        if "expected_observability_event" in row:
            observability_event_rows += 1
            events = getattr(workflow_response, "observability_events", []) if workflow_response is not None else []
            if bool(events) == bool(row.get("expected_observability_event")):
                observability_event_hits += 1

    return {
        "total": total,
        "retrieval_hit_rate": round(hit_count / retrieval_rows, 4) if retrieval_rows else 1.0,
        "expected_concept_recall": round(recall_sum / retrieval_rows, 4) if retrieval_rows else 1.0,
        "intent_accuracy": round(intent_hits / intent_rows, 4) if intent_rows else 1.0,
        "sub_intent_accuracy": round(sub_intent_hits / sub_intent_rows, 4) if sub_intent_rows else 1.0,
        "rag_policy_accuracy": round(rag_policy_hits / rag_policy_rows, 4) if rag_policy_rows else 1.0,
        "stale_context_absent_rate": round(stale_context_absent / stale_context_rows, 4)
        if stale_context_rows
        else 1.0,
        "route_accuracy": round(route_hits / route_rows, 4) if route_rows else 1.0,
        "quality_flag_absent_rate": round(quality_flag_hits / quality_flag_rows, 4)
        if quality_flag_rows
        else 1.0,
        "answer_contains_required_keywords": round(keyword_hits / keyword_rows, 4)
        if keyword_rows
        else 1.0,
        "forbidden_claims_absent": round(forbidden_hits / forbidden_rows, 4)
        if forbidden_rows
        else 1.0,
        "fallback_expectation_accuracy": round(fallback_expectation_hits / fallback_expectation_rows, 4)
        if fallback_expectation_rows
        else 1.0,
        "candidate_capture_accuracy": round(candidate_capture_hits / candidate_capture_rows, 4)
        if candidate_capture_rows
        else 1.0,
        "observability_event_rate": round(observability_event_hits / observability_event_rows, 4)
        if observability_event_rows
        else 1.0,
        "semantic_grounding_pass_rate": round(semantic_grounding_hits / semantic_grounding_rows, 4)
        if semantic_grounding_rows
        else 1.0,
        "contradiction_absent_rate": round(contradiction_hits / contradiction_rows, 4)
        if contradiction_rows
        else 1.0,
        "hallucination_cache_ban_rate": round(hallucination_cache_ban_hits / hallucination_cache_ban_rows, 4)
        if hallucination_cache_ban_rows
        else 1.0,
        "answer_grounding_rate": round(answer_grounding_hits / answer_grounding_rows, 4)
        if answer_grounding_rows
        else 1.0,
        "workflow_context_accuracy": round(context_hits / context_rows, 4) if context_rows else 1.0,
        "context_rows": context_rows,
        "workflow_rows": workflow_rows,
    }


def _workflow_response_for(row: dict[str, object], workflow_runner: Any):
    if not _needs_workflow(row):
        return None
    if workflow_runner is not None:
        return workflow_runner(row)

    question = str(row.get("question", ""))
    request = AiGenerateRequest(
        question=question,
        user_answer=question,
        correct_answer=str(row.get("correct_answer", "")),
        selected_answer=str(row.get("selected_answer", "")),
    )

    def deterministic_generator(**kwargs):
        return str(row.get("reference_answer") or f"{question}에 대한 설명입니다.")

    previous = os.environ.get("AI_REVIEW_AUTO_CANDIDATES_PATH")
    with tempfile.TemporaryDirectory() as tmp:
        os.environ["AI_REVIEW_AUTO_CANDIDATES_PATH"] = str(Path(tmp) / "auto_candidates.jsonl")
        try:
            return run_review_workflow("free-question", request, generator=deterministic_generator)
        finally:
            if previous is None:
                os.environ.pop("AI_REVIEW_AUTO_CANDIDATES_PATH", None)
            else:
                os.environ["AI_REVIEW_AUTO_CANDIDATES_PATH"] = previous


def _real_workflow_runner(row: dict[str, object]):
    from app.ollama.client import call_ollama

    question = str(row.get("question", ""))
    fields = dict(
        question=question,
        user_answer=question,
        correct_answer=str(row.get("correct_answer", "")),
        selected_answer=str(row.get("selected_answer", "")),
    )
    if row.get("model"):
        fields["model"] = str(row["model"])
    request = AiGenerateRequest(**fields)
    return run_review_workflow("free-question", request, generator=call_ollama)


def _expanded_default_dataset(rows: list[dict[str, object]], minimum_rows: int) -> list[dict[str, object]]:
    if len(rows) >= minimum_rows:
        return rows
    expanded = [dict(row) for row in rows]
    seen_ids = {str(row.get("id", "")) for row in expanded}
    variants = (
        ("explain", "{question} 핵심만 설명해줘"),
        ("compare", "{question} 관련 개념과 헷갈리는 지점은?"),
        ("practice", "{question} 실무에서 언제 조심해야 해?"),
    )
    for source in rows:
        question = str(source.get("question", "")).strip()
        source_id = str(source.get("id", "")).strip()
        if not question or not source_id:
            continue
        for suffix, template in variants:
            candidate_id = f"{source_id}-{suffix}"
            if candidate_id in seen_ids:
                continue
            variant = dict(source)
            variant["id"] = candidate_id
            variant["question"] = template.format(question=question)
            variant["generated_variant"] = True
            # 변형 질문은 원본과 의도/정책이 달라지므로 classifier 출력 기반 단언은 떼어낸다.
            # (검색 타깃 expected_concepts 만 유지 → 변형 행은 순수 recall 가드)
            variant.pop("expected_intent", None)
            variant.pop("expected_sub_intent", None)
            variant.pop("expected_rag_policy", None)
            expanded.append(variant)
            seen_ids.add(candidate_id)
            if len(expanded) >= minimum_rows:
                return expanded
    return expanded


def _answer_is_grounded(
    *,
    flags: set[str],
    answer: str,
    required_keywords: list[str],
    forbidden_claims: list[str],
) -> bool:
    risky_flags = {"evidence_missing", "hallucination_suspected", "contradiction_suspected"}
    if flags & risky_flags:
        return False
    if required_keywords and not all(keyword in answer for keyword in required_keywords):
        return False
    if forbidden_claims and any(claim in answer for claim in forbidden_claims):
        return False
    return True


def _needs_workflow(row: dict[str, object]) -> bool:
    return any(
        row.get(key)
        for key in (
            "expected_route",
            "required_keywords",
            "forbidden_claims",
            "expected_quality_flags_absent",
            "expected_fallback_used",
            "expected_candidate_captured",
            "expected_observability_event",
        )
    )


def main() -> int:
    real = "--real" in sys.argv
    runner = _real_workflow_runner if real else None
    report = evaluate_dataset(load_dataset(), workflow_runner=runner)
    report["generation_mode"] = "ollama" if real else "deterministic"
    print(json.dumps(report, ensure_ascii=False, indent=2))
    passed = (
        report["retrieval_hit_rate"] >= 0.6
        and report["rag_policy_accuracy"] >= 0.8
        and report["workflow_context_accuracy"] >= 1.0
    )
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())


from __future__ import annotations

import json
from pathlib import Path
import sys
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.rag.retriever import retrieve_context
from app.schemas import AiGenerateRequest
from app.workflow.runner import run_review_workflow
from app.workflow.intent import classify_free_question


DATASET_PATH = ROOT / "evals" / "golden_dataset.jsonl"


def load_dataset(path: Path | None = None) -> list[dict[str, object]]:
    dataset_path = path or DATASET_PATH
    rows: list[dict[str, object]] = []
    for line in dataset_path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            rows.append(json.loads(line))
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
            "rag_policy_accuracy": 0.0,
            "stale_context_absent_rate": 0.0,
            "route_accuracy": 0.0,
            "quality_flag_absent_rate": 0.0,
            "answer_contains_required_keywords": 0.0,
            "forbidden_claims_absent": 0.0,
            "fallback_expectation_accuracy": 0.0,
            "candidate_capture_accuracy": 0.0,
            "observability_event_rate": 0.0,
            "workflow_rows": 0,
        }

    hit_count = 0
    recall_sum = 0.0
    retrieval_rows = 0
    intent_rows = 0
    intent_hits = 0
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

        workflow_response = _workflow_response_for(row, workflow_runner)
        if workflow_response is not None:
            workflow_rows += 1

        expected_route = row.get("expected_route")
        if expected_route:
            route_rows += 1
            if workflow_response is not None and getattr(workflow_response, "route", None) == expected_route:
                route_hits += 1

        required_keywords = [str(item) for item in row.get("required_keywords", [])]
        if required_keywords:
            keyword_rows += 1
            answer = str(getattr(workflow_response, "answer", "")) if workflow_response is not None else ""
            if all(keyword in answer for keyword in required_keywords):
                keyword_hits += 1

        forbidden_claims = [str(item) for item in row.get("forbidden_claims", [])]
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

    return run_review_workflow("free-question", request, generator=deterministic_generator)


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
    report = evaluate_dataset(load_dataset())
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["retrieval_hit_rate"] >= 0.6 and report["rag_policy_accuracy"] >= 0.8 else 1


if __name__ == "__main__":
    raise SystemExit(main())


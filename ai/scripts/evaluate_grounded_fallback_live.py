from __future__ import annotations

import json
import os
import sys
from datetime import date
from pathlib import Path
from typing import Callable


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.ollama.client import call_ollama
from app.schemas import AiGenerateRequest, AiGenerateResponse
from app.workflow.answer_cache import clear_answer_cache
from app.workflow.runner import run_review_workflow


CASES = [
    {
        "id": "frontend-react-key-list",
        "question": "React key를 실무 리스트에서 사용할 때 주의점을 설명해줘.",
        "expected_route": "grounded_fallback_generation",
    },
    {
        "id": "frontend-usestate-update",
        "question": "React useState로 상태를 바꿀 때 주의점을 설명해줘.",
        "expected_route": "grounded_fallback_generation",
    },
    {
        "id": "java-equals-compare",
        "question": "Java equals를 비교 로직에서 사용할 때 핵심을 설명해줘.",
        "expected_route": "grounded_fallback_generation",
    },
    {
        "id": "java-arraylist-usage",
        "question": "Java ArrayList를 실무에서 사용할 때 특징을 설명해줘.",
        "expected_route": "grounded_fallback_generation",
    },
    {
        "id": "spring-valid-input",
        "question": "Spring Valid를 입력 검증에 사용할 때 핵심을 설명해줘.",
        "expected_route": "grounded_fallback_generation",
    },
    {
        "id": "python-asyncio-usage",
        "question": "Python asyncio를 비동기 작업에서 사용할 때 핵심을 설명해줘.",
        "expected_route": "grounded_fallback_generation",
    },
    {
        "id": "missing-approved-evidence",
        "question": "Java CopyOnWriteArrayList가 뭐야?",
        "expected_route": "grounded_fallback_safe_response",
    },
    {
        "id": "missing-new-framework",
        "question": "Next.js Server Action 보안 주의점을 설명해줘.",
        "expected_route": "grounded_fallback_safe_response",
    },
]
SAFE_ROUTES = {"grounded_fallback_generation", "grounded_fallback_safe_response"}


def evaluate_cases(
    cases: list[dict],
    *,
    runner: Callable[[str], AiGenerateResponse],
) -> dict[str, object]:
    rows = []
    for case in cases:
        response = runner(case["question"])
        expected_route = case.get("expected_route")
        passed = response.route == expected_route if expected_route else response.route in SAFE_ROUTES
        rows.append({
            "id": case["id"],
            "question": case["question"],
            "expected_route": expected_route,
            "passed": passed,
            "route": response.route,
            "fallback_used": response.fallback_used,
            "model_used": response.model_used,
            "quality_flags": response.quality_flags,
            "answer": response.answer,
        })
    passed_count = sum(row["passed"] for row in rows)
    route_counts = _route_counts(rows)
    approved_rows = [
        row for row in rows
        if row.get("expected_route") == "grounded_fallback_generation"
    ]
    return {
        "case_count": len(rows),
        "passed_count": passed_count,
        "gate_passed": bool(rows) and passed_count == len(rows),
        "route_counts": route_counts,
        "approved_generation_rate": (
            route_counts.get("grounded_fallback_generation", 0) / max(1, len(approved_rows))
        ),
        "safe_response_rate": route_counts.get("grounded_fallback_safe_response", 0) / max(1, len(rows)),
        "unsafe_route_count": sum(row["route"] not in SAFE_ROUTES for row in rows),
        "rows": rows,
    }


def assess_transition_readiness(report: dict[str, object]) -> dict[str, object]:
    blockers: list[str] = []
    if not report.get("gate_passed"):
        blockers.append("grounded_fallback_gate_failed")
    if report.get("unsafe_route_count", 0):
        blockers.append("unsafe_route_detected")
    if not report.get("actual_model_call_verified"):
        blockers.append("approved_evidence_model_call_not_verified")
    if not report.get("missing_evidence_skipped_model"):
        blockers.append("missing_evidence_called_model")
    if float(report.get("approved_generation_rate", 0.0)) < 0.95:
        blockers.append("approved_generation_rate_below_95_percent")

    shadow_ready = not blockers
    serve_blockers = list(blockers)
    if not report.get("production_traffic_validated"):
        serve_blockers.append("production_shadow_not_validated")

    return {
        "shadow_readiness": "READY" if shadow_ready else "NOT_READY",
        "shadow_blockers": blockers,
        "serve_readiness": "READY" if not serve_blockers else "NOT_READY",
        "serve_blockers": serve_blockers,
    }


def _route_counts(rows: list[dict]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for row in rows:
        route = row["route"]
        counts[route] = counts.get(route, 0) + 1
    return counts


def main() -> int:
    os.environ["AI_REVIEW_GROUNDED_FALLBACK_ENABLED"] = "true"
    os.environ["AI_REVIEW_SEMANTIC_JUDGE_ENABLED"] = "false"
    os.environ["AI_REVIEW_GROUNDING_JUDGE_ENABLED"] = "false"
    os.environ["AI_REVIEW_NO_CANDIDATE_CAPTURE"] = "true"
    clear_answer_cache()
    current_case = {"id": ""}
    model_calls: dict[str, int] = {case["id"]: 0 for case in CASES}

    def counting_generator(**kwargs):
        model_calls[current_case["id"]] += 1
        return call_ollama(**kwargs)

    def runner(question: str) -> AiGenerateResponse:
        current_case["id"] = next(case["id"] for case in CASES if case["question"] == question)
        return run_review_workflow(
            "free-question",
            AiGenerateRequest(user_answer=question),
            generator=counting_generator,
        )

    report = evaluate_cases(CASES, runner=runner)
    report["evaluation_date"] = date.today().isoformat()
    report["model_calls_by_case"] = model_calls
    report["model_download_performed"] = False
    approved_case_ids = [
        case["id"] for case in CASES
        if case["expected_route"] == "grounded_fallback_generation"
    ]
    missing_case_ids = [
        case["id"] for case in CASES
        if case["expected_route"] == "grounded_fallback_safe_response"
    ]
    report["actual_model_call_verified"] = all(model_calls[case_id] >= 1 for case_id in approved_case_ids)
    report["missing_evidence_skipped_model"] = all(model_calls[case_id] == 0 for case_id in missing_case_ids)
    report["production_traffic_validated"] = False
    report["transition_readiness"] = assess_transition_readiness(report)
    report["overall_gate_passed"] = bool(
        report["gate_passed"]
        and report["actual_model_call_verified"]
        and report["missing_evidence_skipped_model"]
        and report["transition_readiness"]["shadow_readiness"] == "READY"
    )
    output = ROOT / "reports" / f"grounded_fallback_live_{date.today().isoformat()}.json"
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "output": str(output),
        "model_calls_by_case": model_calls,
        "routes": [row["route"] for row in report["rows"]],
        "overall_gate_passed": report["overall_gate_passed"],
    }, ensure_ascii=False, indent=2))
    return 0 if report["overall_gate_passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())

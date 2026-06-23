from __future__ import annotations

import argparse
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
from scripts.evaluate_grounded_fallback_live import assess_transition_readiness, evaluate_cases


EXPECTED_ROUTE_ALIASES = {
    "approved": "grounded_fallback_generation",
    "generation": "grounded_fallback_generation",
    "grounded": "grounded_fallback_generation",
    "missing": "grounded_fallback_safe_response",
    "safe": "grounded_fallback_safe_response",
    "safe_response": "grounded_fallback_safe_response",
}


def load_cases_from_jsonl(path: Path) -> list[dict[str, str]]:
    cases = []
    with path.open("r", encoding="utf-8-sig") as handle:
        for line_no, line in enumerate(handle, start=1):
            stripped = line.strip()
            if not stripped:
                continue
            raw = json.loads(stripped)
            question = str(raw.get("question") or raw.get("user_answer") or "").strip()
            if not question:
                raise ValueError(f"{path}:{line_no} missing question")
            expected_route = _normalize_expected_route(raw)
            cases.append(
                {
                    "id": str(raw.get("id") or f"case-{line_no}"),
                    "question": question,
                    "expected_route": expected_route,
                }
            )
    if not cases:
        raise ValueError(f"{path} has no cases")
    return cases


def evaluate_operational_shadow_cases(
    cases: list[dict],
    *,
    runner: Callable[[str], AiGenerateResponse],
    model_calls: dict[str, int],
    production_traffic_validated: bool,
) -> dict[str, object]:
    report = evaluate_cases(cases, runner=runner)
    approved_case_ids = [
        case["id"] for case in cases
        if case["expected_route"] == "grounded_fallback_generation"
    ]
    missing_case_ids = [
        case["id"] for case in cases
        if case["expected_route"] == "grounded_fallback_safe_response"
    ]
    report["model_calls_by_case"] = model_calls
    report["actual_model_call_verified"] = all(model_calls[case_id] >= 1 for case_id in approved_case_ids)
    report["missing_evidence_skipped_model"] = all(model_calls[case_id] == 0 for case_id in missing_case_ids)
    report["production_traffic_validated"] = production_traffic_validated
    report["transition_readiness"] = assess_transition_readiness(report)
    report["overall_gate_passed"] = bool(
        report["gate_passed"]
        and report["actual_model_call_verified"]
        and report["missing_evidence_skipped_model"]
        and report["transition_readiness"]["shadow_readiness"] == "READY"
    )
    return report


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Evaluate grounded fallback against operational shadow JSONL cases.")
    parser.add_argument("--input", required=True, help="JSONL file with id, question, and expected/expected_route fields.")
    parser.add_argument("--output", help="Optional report path. Defaults to ai/reports/operational_shadow_<date>.json")
    parser.add_argument(
        "--production-validated",
        action="store_true",
        help="Set only when the input was sampled from real production shadow traffic.",
    )
    args = parser.parse_args(argv)

    os.environ["AI_REVIEW_GROUNDED_FALLBACK_ENABLED"] = "true"
    os.environ["AI_REVIEW_SEMANTIC_JUDGE_ENABLED"] = "false"
    os.environ["AI_REVIEW_GROUNDING_JUDGE_ENABLED"] = "false"
    os.environ["AI_REVIEW_NO_CANDIDATE_CAPTURE"] = "true"
    clear_answer_cache()

    input_path = Path(args.input)
    cases = load_cases_from_jsonl(input_path)
    current_case = {"id": ""}
    model_calls: dict[str, int] = {case["id"]: 0 for case in cases}

    def counting_generator(**kwargs):
        model_calls[current_case["id"]] += 1
        return call_ollama(**kwargs)

    def runner(question: str) -> AiGenerateResponse:
        current_case["id"] = next(case["id"] for case in cases if case["question"] == question)
        return run_review_workflow(
            "free-question",
            AiGenerateRequest(user_answer=question),
            generator=counting_generator,
        )

    report = evaluate_operational_shadow_cases(
        cases,
        runner=runner,
        model_calls=model_calls,
        production_traffic_validated=args.production_validated,
    )
    report["evaluation_date"] = date.today().isoformat()
    report["input_path"] = str(input_path)
    report["model_download_performed"] = False

    output = Path(args.output) if args.output else ROOT / "reports" / f"operational_shadow_{date.today().isoformat()}.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "output": str(output),
        "overall_gate_passed": report["overall_gate_passed"],
        "transition_readiness": report["transition_readiness"],
        "route_counts": report["route_counts"],
    }, ensure_ascii=False, indent=2))
    return 0 if report["overall_gate_passed"] else 1


def _normalize_expected_route(raw: dict) -> str:
    expected_route = raw.get("expected_route")
    if expected_route:
        return str(expected_route)
    expected = str(raw.get("expected") or "").strip().lower()
    if expected in EXPECTED_ROUTE_ALIASES:
        return EXPECTED_ROUTE_ALIASES[expected]
    raise ValueError("case must include expected_route or expected")


if __name__ == "__main__":
    raise SystemExit(main())

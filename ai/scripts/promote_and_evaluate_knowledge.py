from __future__ import annotations

from collections.abc import Callable
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.evaluate_lightweight_rag import evaluate_dataset, load_dataset
from scripts.lint_knowledge_cards import lint_cards
from scripts.promote_concept_candidates import promote_course_concepts
from scripts.reindex_knowledge import reindex_changed_knowledge


def run_promotion_workflow(
    promote: Callable[[], dict[str, object]] = promote_course_concepts,
    lint: Callable[[], list[str]] = lint_cards,
    reindex: Callable[[], dict[str, object]] = reindex_changed_knowledge,
    evaluate: Callable[[], dict[str, float | int]] | None = None,
    evaluate_before: Callable[[], dict[str, float | int]] | None = None,
) -> dict[str, object]:
    evaluator = evaluate or (lambda: evaluate_dataset(load_dataset()))
    before_evaluator = evaluate_before or evaluator
    before_evaluation = before_evaluator()
    promotion_report = promote()
    errors = lint()
    if errors:
        return {
            "status": "failed",
            "stage": "lint",
            "errors": errors,
            "before_evaluation": before_evaluation,
            **_promotion_summary(promotion_report),
        }

    reindex_report = reindex()
    if reindex_report.get("status") == "failed":
        return {
            "status": "failed",
            "stage": "reindex",
            "errors": reindex_report.get("errors", []),
            "before_evaluation": before_evaluation,
            "reindex": reindex_report,
            **_promotion_summary(promotion_report),
        }

    evaluation_report = evaluator()
    evaluation_delta = _evaluation_delta(before_evaluation, evaluation_report)
    if float(evaluation_report.get("retrieval_hit_rate", 0.0)) < 0.6:
        return {
            "status": "failed",
            "stage": "evaluate",
            "before_evaluation": before_evaluation,
            "evaluation": evaluation_report,
            "evaluation_delta": evaluation_delta,
            "reindex": reindex_report,
            **_promotion_summary(promotion_report),
        }

    return {
        "status": "passed",
        "stage": "complete",
        "before_evaluation": before_evaluation,
        "evaluation": evaluation_report,
        "evaluation_delta": evaluation_delta,
        "reindex": reindex_report,
        **_promotion_summary(promotion_report),
    }


def _promotion_summary(report: dict[str, object]) -> dict[str, object]:
    return {
        "written": report.get("written", []),
        "approved_candidates": report.get("approved_candidates", 0),
        "candidates": report.get("candidates", 0),
    }


def _evaluation_delta(
    before: dict[str, float | int],
    after: dict[str, float | int],
) -> dict[str, float]:
    delta: dict[str, float] = {}
    for key, after_value in after.items():
        before_value = before.get(key)
        if isinstance(before_value, (int, float)) and isinstance(after_value, (int, float)):
            delta[key] = round(float(after_value) - float(before_value), 4)
    return delta


def main() -> int:
    report = run_promotion_workflow()
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())

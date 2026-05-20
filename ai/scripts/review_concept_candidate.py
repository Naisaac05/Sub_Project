from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.knowledge.human_review import VALID_ACTIONS, review_candidate_by_term
from app.knowledge.review import load_candidate_jsonl, write_candidate_jsonl


DEFAULT_INPUT = ROOT / "app" / "knowledge" / "candidates" / "course_concepts.jsonl"


def review_concept_candidate(
    term: str,
    action: str,
    category: str | None = None,
    definition: str | None = None,
    rejected_reason: str = "",
    reviewer: str = "manual-cli",
    input_path: Path = DEFAULT_INPUT,
) -> dict[str, int | str]:
    candidates = load_candidate_jsonl(input_path)
    reviewed, changed = review_candidate_by_term(
        candidates,
        term=term,
        category=category,
        action=action,
        definition=definition,
        rejected_reason=rejected_reason,
        reviewer=reviewer,
    )
    write_candidate_jsonl(reviewed, input_path)
    return {
        "input": str(input_path.relative_to(ROOT)),
        "term": term,
        "category": category or "",
        "action": action,
        "matched_candidates": changed,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Approve, reject, or hold one concept candidate.")
    parser.add_argument("--term", required=True)
    parser.add_argument("--category", default=None)
    parser.add_argument("--action", choices=sorted(VALID_ACTIONS), required=True)
    parser.add_argument("--definition", default=None)
    parser.add_argument("--rejected-reason", default="")
    parser.add_argument("--reviewer", default="manual-cli")
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    args = parser.parse_args()

    report = review_concept_candidate(
        term=args.term,
        category=args.category,
        action=args.action,
        definition=args.definition,
        rejected_reason=args.rejected_reason,
        reviewer=args.reviewer,
        input_path=args.input,
    )
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["matched_candidates"] else 1


if __name__ == "__main__":
    raise SystemExit(main())

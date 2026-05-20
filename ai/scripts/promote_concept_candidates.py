from __future__ import annotations

import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.knowledge.approval import load_candidate_jsonl, promote_approved_candidates


DEFAULT_INPUT = ROOT / "app" / "knowledge" / "candidates" / "course_concepts.jsonl"
DEFAULT_OUTPUT = ROOT / "app" / "knowledge" / "concepts" / "generated"


def promote_course_concepts(
    input_path: Path = DEFAULT_INPUT,
    output_root: Path = DEFAULT_OUTPUT,
) -> dict[str, int | str | list[str]]:
    candidates = load_candidate_jsonl(input_path)
    approved_count = sum(
        1
        for candidate in candidates
        if candidate.get("approved") is True
        and str(candidate.get("definition", "")).strip()
    )
    written = promote_approved_candidates(candidates, output_root)
    return {
        "input": str(input_path.relative_to(ROOT)),
        "output": str(output_root.relative_to(ROOT)),
        "candidates": len(candidates),
        "approved_candidates": approved_count,
        "written": [str(path.relative_to(ROOT)) for path in written],
    }


def main() -> int:
    report = promote_course_concepts()
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

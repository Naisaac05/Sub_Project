from __future__ import annotations

import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.knowledge.registry import mark_duplicate_candidates
from app.knowledge.review import load_candidate_jsonl, write_candidate_jsonl
from app.rag.documents import CONCEPT_ROOT, load_concept_cards


DEFAULT_INPUT = ROOT / "app" / "knowledge" / "candidates" / "course_concepts.jsonl"


def check_candidate_duplicates(
    input_path: Path = DEFAULT_INPUT,
    concept_root: Path = CONCEPT_ROOT,
) -> dict[str, int | str]:
    candidates = load_candidate_jsonl(input_path)
    cards = load_concept_cards(concept_root)
    marked = mark_duplicate_candidates(candidates, cards)
    write_candidate_jsonl(marked, input_path)
    duplicate_count = sum(1 for candidate in marked if candidate.get("duplicate_status") == "duplicate_suspected")
    return {
        "input": str(input_path.relative_to(ROOT)),
        "concept_root": str(concept_root.relative_to(ROOT)),
        "candidates": len(candidates),
        "concept_cards": len(cards),
        "duplicate_suspected": duplicate_count,
    }


def main() -> int:
    report = check_candidate_duplicates()
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

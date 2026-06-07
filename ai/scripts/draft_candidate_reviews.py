from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.knowledge.review import (
    OllamaCandidateReviewProvider,
    TemplateCandidateReviewProvider,
    enrich_candidates_with_ai_review,
    load_candidate_jsonl,
    write_candidate_jsonl,
)


DEFAULT_INPUT = ROOT / "app" / "knowledge" / "candidates" / "course_concepts.jsonl"


def draft_candidate_reviews(
    input_path: Path = DEFAULT_INPUT,
    output_path: Path | None = None,
    limit: int | None = 10,
    provider_name: str = "template",
    model: str = "exaone3.5:2.4b",
) -> dict[str, int | str]:
    candidates = load_candidate_jsonl(input_path)
    provider = OllamaCandidateReviewProvider(model_name=model) if provider_name == "ollama" else TemplateCandidateReviewProvider()
    enriched, changed = enrich_candidates_with_ai_review(candidates, provider, limit=limit)
    target = output_path or input_path
    write_candidate_jsonl(enriched, target)
    return {
        "input": str(input_path.relative_to(ROOT)),
        "output": str(target.relative_to(ROOT)),
        "provider": provider.model_name,
        "candidates": len(candidates),
        "review_metadata_added": changed,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Add AI draft and critic metadata to concept candidates.")
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output", type=Path, default=None)
    parser.add_argument("--limit", type=int, default=10)
    parser.add_argument("--provider", choices=["template", "ollama"], default="template")
    parser.add_argument("--model", default="exaone3.5:2.4b")
    args = parser.parse_args()

    report = draft_candidate_reviews(args.input, args.output, args.limit, args.provider, args.model)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

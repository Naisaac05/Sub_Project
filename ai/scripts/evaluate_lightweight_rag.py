from __future__ import annotations

import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.rag.retriever import retrieve_context


DATASET_PATH = ROOT / "evals" / "golden_dataset.jsonl"


def load_dataset(path: Path | None = None) -> list[dict[str, object]]:
    dataset_path = path or DATASET_PATH
    rows: list[dict[str, object]] = []
    for line in dataset_path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            rows.append(json.loads(line))
    return rows


def evaluate_dataset(rows: list[dict[str, object]]) -> dict[str, float | int]:
    total = len(rows)
    if total == 0:
        return {
            "total": 0,
            "retrieval_hit_rate": 0.0,
            "expected_concept_recall": 0.0,
        }

    hit_count = 0
    recall_sum = 0.0
    for row in rows:
        expected = set(str(item) for item in row.get("expected_concepts", []))
        retrieved = retrieve_context(str(row.get("question", "")), limit=3)
        retrieved_ids = {item.concept_id for item in retrieved}
        matched = expected & retrieved_ids
        if matched:
            hit_count += 1
        recall_sum += len(matched) / len(expected) if expected else 1.0

    return {
        "total": total,
        "retrieval_hit_rate": round(hit_count / total, 4),
        "expected_concept_recall": round(recall_sum / total, 4),
    }


def main() -> int:
    report = evaluate_dataset(load_dataset())
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["retrieval_hit_rate"] >= 0.6 else 1


if __name__ == "__main__":
    raise SystemExit(main())


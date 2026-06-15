from __future__ import annotations

import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.rag.retriever import select_retriever_adapter


CASES = [
    ("N+1 문제를 어떻게 해결해?", "spring-n-plus-one"),
    ("aria label 접근성 설명", "frontend-aria-label"),
    ("ControllerAdvice 예외 처리", "java-backend-controlleradvice"),
]


def main() -> int:
    adapter = select_retriever_adapter("bge")
    rows = []
    for query, expected in CASES:
        results = adapter.retrieve(query, limit=3)
        top_id = results[0].concept_id if results else ""
        rows.append(
            {
                "query": query,
                "expected": expected,
                "top_id": top_id,
                "passed": top_id == expected,
                "metadata": results[0].metadata if results else {},
            }
        )
    print(json.dumps(rows, ensure_ascii=False, indent=2))
    return 0 if all(row["passed"] for row in rows) else 1


if __name__ == "__main__":
    raise SystemExit(main())

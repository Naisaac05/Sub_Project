from __future__ import annotations

import json
import statistics
import sys
import time
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.workflow.embedding_intent import intent_from_label
from app.workflow.v2_approved_fast_path import resolve_v2_approved_fast_path


SAMPLES = (
    ("Java에서 equals와 ==는 무엇이 다른가요?", "COMPARISON"),
    ("Java equals와 hashCode를 함께 재정의해야 하는 이유는 무엇인가요?", "ANSWER_REASON"),
    ("Java equals가 무엇인가요?", "CONCEPT_DEFINITION"),
    ("== 대신 equals를 사용해야 하는 이유는 무엇인가요?", "ANSWER_REASON"),
)


def main() -> int:
    rows = []
    for question, label in SAMPLES:
        intent = intent_from_label(label, question, 0.99)
        started = time.perf_counter()
        decision = resolve_v2_approved_fast_path(question, intent)
        latency_ms = (time.perf_counter() - started) * 1000
        rows.append({
            "question": question,
            "intent_label": label,
            "mode": decision.mode,
            "hit": decision.hit,
            "reason": decision.reason,
            "reason_message": decision.reason_message,
            "card_id": decision.card_id,
            "payload_intent": decision.payload_intent,
            "score": decision.score,
            "latency_ms": latency_ms,
            "answer_snippet": (decision.answer or "")[:160],
        })
    report = {
        "sample_count": len(rows),
        "hit_count": sum(row["hit"] for row in rows),
        "hit_rate": sum(row["hit"] for row in rows) / len(rows),
        "average_latency_ms": statistics.mean(row["latency_ms"] for row in rows),
        "fallback_reasons": {
            reason: sum(row["reason"] == reason for row in rows)
            for reason in sorted({row["reason"] for row in rows if not row["hit"]})
        },
        "rows": rows,
    }
    output = ROOT / "reports" / f"v2_streaming_fast_path_shadow_{datetime.now():%Y-%m-%d}.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

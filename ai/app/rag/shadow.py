from datetime import datetime, timezone
import json
from pathlib import Path

from app.rag.retriever import RetrievedContext


def log_shadow_retrieval(
    path: Path,
    *,
    query: str,
    intent: str,
    results: list[RetrievedContext],
    payload_hit: bool,
    fast_path_hit: bool,
    latency_ms: float,
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    row = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "query": query,
        "intent": intent,
        "retrieved_card_id": results[0].concept_id if results else None,
        "payload_hit": payload_hit,
        "fast_path_hit": fast_path_hit,
        "latency_ms": latency_ms,
        "top_3": [
            {"card_id": item.concept_id, "score": item.score}
            for item in results[:3]
        ],
    }
    with path.open("a", encoding="utf-8") as sink:
        sink.write(json.dumps(row, ensure_ascii=False) + "\n")

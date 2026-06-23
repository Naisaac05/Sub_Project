from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

from app.scripts.initialize_validation_policy_v212 import searchable_checksum


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CARD_ROOT = ROOT / "app" / "knowledge" / "concepts_v2"


def build_baseline(card_root: Path) -> dict[str, object]:
    cards = []
    status_counts: Counter[str] = Counter()
    category_counts: Counter[str] = Counter()

    for path in sorted(card_root.rglob("*.json")):
        raw = path.read_bytes()
        card = json.loads(raw.decode("utf-8-sig"))
        review = card.get("review") or {}
        card_status = str(review.get("card_status") or "unknown")
        payload_counts = Counter(str(value) for value in (review.get("payload_status") or {}).values())
        category = str(card.get("category") or "unknown")
        status_counts[card_status] += 1
        category_counts[category] += 1
        cards.append(
            {
                "card_id": card.get("card_id"),
                "path": path.relative_to(card_root).as_posix(),
                "category": category,
                "card_status": card_status,
                "payload_status_counts": dict(sorted(payload_counts.items())),
                "file_sha256": hashlib.sha256(raw).hexdigest(),
                "searchable_checksum": searchable_checksum(card),
            }
        )

    return {
        "captured_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "card_root": str(card_root),
        "total_card_count": len(cards),
        "status_counts": dict(sorted(status_counts.items())),
        "category_counts": dict(sorted(category_counts.items())),
        "cards": cards,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Capture an immutable RAG v2 card baseline")
    parser.add_argument("--root", type=Path, default=DEFAULT_CARD_ROOT)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    report = build_baseline(args.root)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps({key: value for key, value in report.items() if key != "cards"}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

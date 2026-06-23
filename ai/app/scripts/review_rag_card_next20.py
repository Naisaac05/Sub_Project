from __future__ import annotations

import argparse
import copy
import hashlib
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path

from app.schemas.rag_card import RagCard
from app.scripts.initialize_validation_policy_v212 import validate_lock, validate_payload_quality


AI_ROOT = Path(__file__).resolve().parents[2]
CARD_ROOT = AI_ROOT / "app" / "knowledge" / "concepts_v2"
BASELINE_PATH = AI_ROOT / "reports" / "rag_card_expansion_baseline_2026-06-21.json"
CANDIDATE_PATH = AI_ROOT / "reports" / "course_balanced_next20_candidates_2026-06-21.json"
EXECUTION_PATH = AI_ROOT / "reports" / "rag_card_next20_execution_2026-06-21.json"
REPORT_PATH = AI_ROOT / "reports" / "rag_card_next20_approval_dryrun_2026-06-21.json"
BACKUP_ROOT = CARD_ROOT.parent / "concepts_v2_backups" / "rag-card-next20-20260621" / "pre-approval"


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def review_candidate(card: dict, execution: dict) -> dict:
    reasons: list[str] = []
    try:
        RagCard.model_validate(card)
    except Exception as exc:
        reasons.append(f"json_invalid:{exc}")
    if card.get("review", {}).get("card_status") != "draft":
        reasons.append("not_draft")
    quality = validate_payload_quality(card)
    reasons.extend(quality["reasons"])
    if not execution.get("passed"):
        reasons.append("execution_not_verified")
    return {
        "eligible": not reasons,
        "reasons": list(dict.fromkeys(reasons)),
        "quality": quality,
        "execution": execution,
    }


def build_approved_card(card: dict, *, approved_at: str) -> dict:
    candidate = copy.deepcopy(card)
    review = candidate["review"]
    review["card_status"] = "approved"
    for intent, payload in candidate["payloads"].items():
        if payload is not None:
            review["payload_status"][intent] = "approved"
    review["approved_at"] = approved_at
    review["reviewer"] = "codex-assisted-human-review"
    review["rejected_reason"] = None
    return candidate


def _paths() -> dict[str, Path]:
    result = {}
    for path in CARD_ROOT.rglob("*.json"):
        card = json.loads(path.read_text(encoding="utf-8-sig"))
        result[card["card_id"]] = path
    return result


def run(*, write: bool) -> dict:
    baseline = json.loads(BASELINE_PATH.read_text(encoding="utf-8"))
    candidate_report = json.loads(CANDIDATE_PATH.read_text(encoding="utf-8"))
    execution_report = json.loads(EXECUTION_PATH.read_text(encoding="utf-8"))
    candidate_ids = sorted(candidate_report["PREPARATION_BACKLOG"])
    paths = _paths()
    approved_baseline = {
        row["card_id"]: row["file_sha256"]
        for row in baseline["cards"]
        if row["card_status"] == "approved"
    }
    baseline_mismatches = [
        card_id for card_id, checksum in approved_baseline.items()
        if _sha256(paths[card_id]) != checksum
    ]
    if baseline_mismatches:
        raise RuntimeError(f"baseline approved cards changed: {baseline_mismatches}")

    reviews = {}
    approved_ids = []
    approved_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    originals = {}
    for card_id in candidate_ids:
        original = json.loads(paths[card_id].read_text(encoding="utf-8-sig"))
        originals[card_id] = original
        result = review_candidate(original, execution_report["results"].get(card_id, {}))
        if result["eligible"]:
            approved = build_approved_card(original, approved_at=approved_at)
            result["lock_reasons"] = validate_lock(original, approved)
            if result["lock_reasons"]:
                result["eligible"] = False
                result["reasons"].extend(result["lock_reasons"])
        reviews[card_id] = result
        if result["eligible"]:
            approved_ids.append(card_id)

    if write:
        BACKUP_ROOT.mkdir(parents=True, exist_ok=True)
        for card_id in candidate_ids:
            backup = BACKUP_ROOT / paths[card_id].relative_to(CARD_ROOT)
            backup.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(paths[card_id], backup)
        (BACKUP_ROOT / "baseline-approved-manifest.json").write_text(
            json.dumps(approved_baseline, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        for card_id in approved_ids:
            approved = build_approved_card(originals[card_id], approved_at=approved_at)
            RagCard.model_validate(approved)
            paths[card_id].write_text(json.dumps(approved, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    report = {
        "created_at": approved_at,
        "dry_run": not write,
        "candidate_count": len(candidate_ids),
        "eligible_count": len(approved_ids),
        "held_count": len(candidate_ids) - len(approved_ids),
        "approved_card_ids": approved_ids,
        "held_card_ids": [card_id for card_id in candidate_ids if card_id not in approved_ids],
        "baseline_approved_count": len(approved_baseline),
        "baseline_approved_mismatches": baseline_mismatches,
        "reviews": reviews,
        "backup_root": str(BACKUP_ROOT) if write else None,
    }
    REPORT_PATH.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return report


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    report = run(write=args.write)
    print(json.dumps({key: report[key] for key in ("dry_run", "candidate_count", "eligible_count", "held_card_ids")}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

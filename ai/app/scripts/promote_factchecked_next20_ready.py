from __future__ import annotations

import hashlib
import json
from pathlib import Path

from app.schemas.rag_card import RagPayloads
from app.scripts.initialize_validation_policy_v212 import validate_payload_quality
from app.scripts.migrate_rag_cards import extract_questions
from app.scripts.patch_payload_batch_v214 import CARD_ROOT


ROOT = Path(__file__).resolve().parents[2]
SOURCE_REPORT = ROOT / "reports" / "course_balanced_next20_factchecked_drafts_2026-06-15.json"
REPORT = ROOT / "reports" / "course_balanced_next20_patches_ready_2026-06-15.json"


def _checksums() -> dict[str, str]:
    return {str(path.relative_to(CARD_ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in CARD_ROOT.rglob("*.json")}


def review_reasons(draft: dict, valid_sources: set[str]) -> list[str]:
    reasons: list[str] = []
    source_id = draft.get("source_question_id")
    if source_id not in valid_sources:
        reasons.append("source_question_missing")
    try:
        RagPayloads.model_validate(draft.get("payloads"))
    except Exception:
        reasons.append("payload_json_invalid")
    quality = draft.get("quality") or {}
    execution = quality.get("execution") or {}
    metrics = quality.get("example_metrics") or {}
    if not execution.get("passed"):
        reasons.append("execution_failed")
    if quality.get("same_reason_ratio", 1.0) > 0.25:
        reasons.append("same_reason_ratio_over_25_percent")
    if metrics.get("example_quality", 0.0) < 0.7:
        reasons.append("example_quality_below_0_7")
    if metrics.get("fake_example_score", 1.0) > 0.3:
        reasons.append("fake_example_score_over_0_3")
    if metrics.get("print_answer", 1.0):
        reasons.append("print_answer_example")
    payload_quality = validate_payload_quality({"payloads": draft.get("payloads") or {}})
    reasons.extend(payload_quality["reasons"])
    return list(dict.fromkeys(reasons))


def build_ready_report(drafts: dict[str, dict], valid_sources: set[str]) -> dict:
    ready, backlog, failed, decisions = {}, [], {}, {}
    for card_id, draft in drafts.items():
        reasons = review_reasons(draft, valid_sources)
        if reasons:
            backlog.append(card_id)
            failed[card_id] = reasons
            decisions[card_id] = {"decision": "hold", "reasons": reasons}
            continue
        ready[card_id] = {
            "payloads": draft["payloads"],
            "fact_check_notes": draft["fact_check_notes"],
            "patch_reason": draft["patch_reason"],
            "source_link": {
                key: draft[key] for key in ("course_id", "test_id", "question_id", "source_question_id")
            },
            "quality_review": draft["quality"],
        }
        decisions[card_id] = {"decision": "promote", "reasons": ["fact_checked", "execution_verified", "quality_gate_passed"]}
    return {
        "candidate_count": len(drafts),
        "ready_count": len(ready),
        "backlog_count": len(backlog),
        "PATCHES_READY": ready,
        "PREPARATION_BACKLOG": backlog,
        "review_decisions": decisions,
        "failed_review": failed,
        "execution_performed": False,
        "card_files_modified": False,
        "approval_status_changed": False,
        "json_validation_result": "pass",
        "retrieval_changed": 0,
    }


def main() -> int:
    before = _checksums()
    source = json.loads(SOURCE_REPORT.read_text(encoding="utf-8"))
    valid_sources = {f"{question.category}:{question.id}" for question in extract_questions()}
    report = build_ready_report(source["FACTCHECK_DRAFTS"], valid_sources)
    report["card_files_modified"] = before != _checksums()
    serialized = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    json.loads(serialized)
    REPORT.write_text(serialized, encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "PATCHES_READY"}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

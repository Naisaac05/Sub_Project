from __future__ import annotations

import copy
import hashlib
import json
from dataclasses import asdict
from pathlib import Path
from typing import Any

from app.rag.documents import load_concept_cards
from app.schemas.rag_card import RagCard
from app.scripts.initialize_validation_policy_v212 import (
    searchable_checksum,
    validate_lock,
    validate_payload_quality,
)
from app.scripts.migrate_rag_cards import evaluate_retrieval_modes, extract_questions
from app.scripts.patch_payload_batch_v214 import CARD_ROOT


ROOT = Path(__file__).resolve().parents[2]
MANIFEST_PATH = ROOT / "reports" / "course_balanced_next20_patches_ready_2026-06-15.json"
REPORT_PATH = ROOT / "reports" / "course_balanced_next20_approval_dryrun_2026-06-15.json"
METRIC_FIELDS = (
    "exact_hit1",
    "exact_hit3",
    "exact_hit5",
    "loo_candidate_rate",
    "loo_average_score",
    "loo_same_category_rate",
)


def build_candidate(original: dict, patch: dict) -> dict:
    candidate = copy.deepcopy(original)
    candidate["payloads"] = copy.deepcopy(patch["payloads"])
    return candidate


def metric_diff(before: dict, after: dict) -> dict[str, float]:
    result: dict[str, float] = {}
    for mode in ("production_mode", "content_mode"):
        prefix = "production" if mode == "production_mode" else "content"
        for field in METRIC_FIELDS:
            result[f"{prefix}_{field}_diff"] = getattr(after[mode], field) - getattr(before[mode], field)
    result["exact_diff"] = result["production_exact_hit1_diff"]
    return result


def retrieval_diff_reasons(diff: dict[str, float]) -> list[str]:
    return [f"retrieval_changed:{field}" for field, value in diff.items() if value != 0.0]


def approval_decision(current_status: str, gate_reasons: list[str]) -> dict:
    if gate_reasons:
        return {"eligible": False, "result": "retrieval_pending", "reasons": gate_reasons}
    if current_status != "approved":
        return {
            "eligible": False,
            "result": "quality_verified_retrieval_passed",
            "reasons": ["transition_requires_approved"],
        }
    return {"eligible": True, "result": "approved_locked_candidate", "reasons": []}


def quality_gate_passed(card_result: dict) -> bool:
    return not card_result.get("quality_reasons")


def _card_file_checksums() -> dict[str, str]:
    return {
        str(path.relative_to(CARD_ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in sorted(CARD_ROOT.rglob("*.json"))
    }


def _raw_cards() -> tuple[list[dict], dict[str, Path]]:
    cards: list[dict] = []
    paths: dict[str, Path] = {}
    for path in sorted(CARD_ROOT.rglob("*.json")):
        card = json.loads(path.read_text(encoding="utf-8-sig"))
        cards.append(card)
        paths[card["card_id"]] = path
    return cards, paths


def _quality_reasons(candidate: dict, patch: dict) -> tuple[dict, list[str]]:
    result = validate_payload_quality(candidate)
    reasons = list(result["reasons"])
    review = patch.get("quality_review") or {}
    metrics = review.get("example_metrics") or {}
    execution = review.get("execution") or {}
    if not execution.get("passed"):
        reasons.append("example_execution_failed")
    if metrics.get("example_quality", 0.0) < 0.7:
        reasons.append("example_quality_under_0_7")
    if metrics.get("fake_example_score", 1.0) > 0.0:
        reasons.append("fake_example_score_nonzero")
    if metrics.get("print_answer", 1.0) > 0.0:
        reasons.append("print_answer")
    if review.get("same_reason_ratio", 1.0) > 0.25:
        reasons.append("same_reason_ratio_over_25_percent")
    return result, list(dict.fromkeys(reasons))


def _replace_card(cards: list[RagCard], candidate: RagCard) -> list[RagCard]:
    return [candidate if card.card_id == candidate.card_id else card for card in cards]


def run_dryrun() -> dict[str, Any]:
    before_files = _card_file_checksums()
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    patches: dict[str, dict] = manifest["PATCHES_READY"]
    raw_cards, paths = _raw_cards()
    raw_by_id = {card["card_id"]: card for card in raw_cards}
    cards = [card for card in load_concept_cards(CARD_ROOT) if isinstance(card, RagCard)]
    questions = extract_questions()
    baseline_metrics = evaluate_retrieval_modes(questions, cards)

    results: dict[str, dict] = {}
    batch_cards = list(cards)
    for card_id, patch in patches.items():
        original = raw_by_id.get(card_id)
        gate_reasons: list[str] = []
        if original is None:
            results[card_id] = {
                "approval_decision": approval_decision("missing", ["source_card_missing"]),
                "gate_reasons": ["source_card_missing"],
            }
            continue

        candidate = build_candidate(original, patch)
        json_reasons: list[str] = []
        try:
            candidate_model = RagCard.model_validate(candidate)
        except Exception as exc:
            json_reasons.append(f"json_invalid:{exc}")
            candidate_model = None
        lock_reasons = validate_lock(original, candidate)
        quality, quality_reasons = _quality_reasons(candidate, patch)
        retrieval_diff: dict[str, float] = {}
        retrieval_reasons: list[str] = []
        if candidate_model is not None:
            candidate_cards = _replace_card(cards, candidate_model)
            retrieval_diff = metric_diff(
                baseline_metrics,
                evaluate_retrieval_modes(questions, candidate_cards),
            )
            retrieval_reasons = retrieval_diff_reasons(retrieval_diff)
            batch_cards = _replace_card(batch_cards, candidate_model)

        gate_reasons.extend(json_reasons)
        gate_reasons.extend(lock_reasons)
        gate_reasons.extend(quality_reasons)
        gate_reasons.extend(retrieval_reasons)
        gate_reasons = list(dict.fromkeys(gate_reasons))
        current_status = original.get("review", {}).get("card_status", "draft")
        results[card_id] = {
            "path": str(paths[card_id].relative_to(ROOT)),
            "current_status": current_status,
            "json_valid": not json_reasons,
            "searchable_checksum_before": searchable_checksum(original),
            "searchable_checksum_after": searchable_checksum(candidate),
            "searchable_checksum_changed": "searchable_checksum_changed" in lock_reasons,
            "lock_reasons": lock_reasons,
            "semantic_shift": "semantic_shift" in lock_reasons,
            "quality": quality,
            "quality_review": patch.get("quality_review"),
            "quality_reasons": quality_reasons,
            "retrieval_diff": retrieval_diff,
            "gate_reasons": gate_reasons,
            "approval_decision": approval_decision(current_status, gate_reasons),
        }

    full_batch_metrics = evaluate_retrieval_modes(questions, batch_cards)
    full_batch_diff = metric_diff(baseline_metrics, full_batch_metrics)
    after_files = _card_file_checksums()
    changed_files = sorted(
        path for path, checksum in before_files.items() if after_files.get(path) != checksum
    )
    result_values = list(results.values())
    report = {
        "dry_run": True,
        "candidate_count": len(patches),
        "json_passed_count": sum(item.get("json_valid", False) for item in result_values),
        "checksum_passed_count": sum(
            not item.get("searchable_checksum_changed", True) for item in result_values
        ),
        "lock_violation_count": sum(bool(item.get("lock_reasons")) for item in result_values),
        "semantic_shift_failed_count": sum(item.get("semantic_shift", False) for item in result_values),
        "quality_passed_count": sum(
            quality_gate_passed(item) for item in result_values
        ),
        "payload_validator_passed_count": sum(
            not item.get("quality", {}).get("reasons") for item in result_values
        ),
        "retrieval_passed_count": sum(
            not retrieval_diff_reasons(item.get("retrieval_diff", {"missing": 1.0}))
            for item in result_values
        ),
        "approved_locked_eligible_count": sum(
            item.get("approval_decision", {}).get("eligible", False) for item in result_values
        ),
        "approval_distribution": {
            status: sum(item.get("current_status") == status for item in result_values)
            for status in ("draft", "approved", "approved_locked", "rejected")
        },
        "full_batch": {
            "baseline_metrics": {mode: asdict(metrics) for mode, metrics in baseline_metrics.items()},
            "simulated_metrics": {mode: asdict(metrics) for mode, metrics in full_batch_metrics.items()},
            "diff": full_batch_diff,
            "retrieval_reasons": retrieval_diff_reasons(full_batch_diff),
        },
        "cards": results,
        "concepts_v2_modified": bool(changed_files),
        "concepts_v2_changed_files": changed_files,
        "approval_status_changed": False,
        "payload_modified": False,
        "patches_ready_created": False,
    }
    return report


def main() -> int:
    report = run_dryrun()
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    REPORT_PATH.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "report": str(REPORT_PATH),
        "candidate_count": report["candidate_count"],
        "approved_locked_eligible_count": report["approved_locked_eligible_count"],
        "full_batch_retrieval_reasons": report["full_batch"]["retrieval_reasons"],
        "concepts_v2_modified": report["concepts_v2_modified"],
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

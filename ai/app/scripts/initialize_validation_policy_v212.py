from __future__ import annotations

import copy
import hashlib
import json
import re
from itertools import combinations
from pathlib import Path
from typing import Any

from app.rag.documents import load_concept_cards
from app.schemas.rag_card import RagCard
from app.scripts.migrate_rag_cards import evaluate_retrieval_modes, extract_questions
from app.scripts.patch_payload_batch_v214 import CARD_ROOT


ROOT = Path(__file__).resolve().parents[2]
POLICY_PATH = ROOT / "app" / "config" / "batch_validation_policy_v212.json"
REPORT_PATH = ROOT / "reports" / "batch_validation_policy_v212_initialization_2026-06-14.json"
BATCH_SEQUENCE = (10, 20, 40, 80)
LOCKED_FIELDS = (
    "card_id",
    "category",
    "term",
    "source_question_ids",
    "retrieval.embedding_text",
    "retrieval.embedding_hash",
    "retrieval.intent_types",
    "aliases",
    "retrieval.boost_keywords",
    "created_at",
    "related_card_ids",
)
SEMANTIC_FIELDS = (
    "category",
    "term",
    "aliases",
    "retrieval.embedding_text",
    "retrieval.embedding_hash",
    "retrieval.intent_types",
    "retrieval.boost_keywords",
)
GENERIC_PHRASES = (
    "관련 없음",
    "정답이 아님",
    "질문이 요구",
    "조건을 만족",
    "동작을 보장",
    "실행 결과",
    "유사한 선택지",
    "실무에서는",
)
AUTO_DOWNGRADE_REASONS = {"invalid_json", "retrieval_break", "critical_fact_error"}


def _get(card: dict, path: str) -> Any:
    value: Any = card
    for part in path.split("."):
        value = value.get(part) if isinstance(value, dict) else None
    return value


def searchable_checksum(card: dict) -> str:
    searchable = {
        "category": _get(card, "category"),
        "term": _get(card, "term"),
        "aliases": _get(card, "aliases"),
        "retrieval.embedding_text": _get(card, "retrieval.embedding_text"),
        "retrieval.boost_keywords": _get(card, "retrieval.boost_keywords"),
    }
    encoded = json.dumps(searchable, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def validate_lock(before: dict, after: dict) -> list[str]:
    reasons = [
        f"lock_violation:{field}"
        for field in LOCKED_FIELDS
        if _get(before, field) != _get(after, field)
    ]
    if searchable_checksum(before) != searchable_checksum(after):
        reasons.append("searchable_checksum_changed")
    if any(_get(before, field) != _get(after, field) for field in SEMANTIC_FIELDS):
        reasons.append("semantic_shift")
    return reasons


def _strings(value: Any) -> list[str]:
    if isinstance(value, str):
        return [value]
    if isinstance(value, dict):
        return [item for child in value.values() for item in _strings(child)]
    if isinstance(value, list):
        return [item for child in value for item in _strings(child)]
    return []


def _tokens(value: str) -> set[str]:
    return {
        token.lower()
        for token in re.findall(r"[가-힣]+|[A-Za-z][A-Za-z0-9_@().+-]*|\d+", value)
        if len(token) > 1
    }


def _similarity(left: str, right: str) -> float:
    a, b = _tokens(left), _tokens(right)
    return len(a & b) / max(1, len(a | b))


def _same_reason_ratio(reasons: list[str]) -> float:
    pairs = list(combinations(reasons, 2))
    if not pairs:
        return 0.0
    return sum(_similarity(left, right) >= 0.65 for left, right in pairs) / len(pairs)


def _duplicate_strings(payloads: dict) -> list[str]:
    candidate = copy.deepcopy(payloads)
    answer = candidate.get("ANSWER_REASON") or {}
    answer.pop("key_points", None)
    wrong = (candidate.get("WRONG_ANSWER_REASON") or {}).get("per_option", {})
    for option in wrong.values():
        option.pop("text", None)
    return _strings(candidate)


def _example_is_fake(code: str) -> bool:
    lines = [line for line in code.splitlines() if line.strip()]
    print_answer = bool(re.search(r"(print|println|console\.log)\s*\([^)]*(정답|answer|expected)", code, re.I))
    behavior = bool(re.search(r"(assert |==|\.append|\.add|\.put|\.get\(|push|pop|return |yield |raise )", code))
    context = bool(re.search(r"(=|class |def |function |import |from |const |let |new |@[\w]+)", code))
    return len(lines) < 3 or print_answer or not behavior or not context


def validate_payload_quality(card: dict) -> dict:
    payloads = card.get("payloads") or {}
    definition = (payloads.get("CONCEPT_DEFINITION") or {}).get("content", "")
    answer = (payloads.get("ANSWER_REASON") or {}).get("why_correct", "")
    wrong = (payloads.get("WRONG_ANSWER_REASON") or {}).get("per_option", {})
    reasons = [item.get("reason", "") for item in wrong.values()]
    example = (payloads.get("EXAMPLE_REQUEST") or {}).get("code_example", "")
    strings = _strings(payloads)
    text = " ".join(strings)
    answer_overlap = _similarity(answer, definition)
    same_reason = _same_reason_ratio(reasons)
    fake_example = _example_is_fake(example)
    generic_score = sum(text.count(phrase) for phrase in GENERIC_PHRASES) / max(1, len(strings))
    comparable = [item for item in _duplicate_strings(payloads) if len(item) >= 30]
    duplicate_score = max((_similarity(left, right) for left, right in combinations(comparable, 2)), default=0.0)
    payload_length = len(text)
    reasons_failed = []
    option_texts = [item.get("text", "") for item in wrong.values()]
    compares_wrong_answer = bool(re.search(r"(반면|다른|보기|오답|비교|달리|혼동)", answer)) or any(
        option and option in answer for option in option_texts
    ) or any(
        _tokens(option) & _tokens(answer) for option in option_texts
    )
    if answer_overlap > 0.30:
        reasons_failed.append("answer_overlap_over_30_percent")
    if len(_tokens(answer)) < 8:
        reasons_failed.append("answer_conclusion_only")
    if not compares_wrong_answer:
        reasons_failed.append("answer_wrong_comparison_missing")
    if same_reason > 0.25:
        reasons_failed.append("same_reason_ratio_over_25_percent")
    if any(reason.strip() in {"관련 없음", "정답이 아님", "관련 없음. 정답이 아님."} for reason in reasons):
        reasons_failed.append("generic_wrong_answer_reason")
    if fake_example:
        reasons_failed.append("fake_example")
    if generic_score > 0.35:
        reasons_failed.append("generic_score_over_0_35")
    if duplicate_score > 0.25:
        reasons_failed.append("duplicate_score_over_0_25")
    if payload_length < 120:
        reasons_failed.append("payload_length_under_120")
    return {
        "reasons": reasons_failed,
        "answer_overlap": answer_overlap,
        "same_reason_ratio": same_reason,
        "fake_example_score": float(fake_example),
        "generic_score": generic_score,
        "duplicate_score": duplicate_score,
        "payload_length": payload_length,
    }


def auto_downgrade_allowed(reasons: list[str]) -> bool:
    return bool(reasons) and set(reasons).issubset(AUTO_DOWNGRADE_REASONS)


def empty_validation_report() -> dict:
    return {
        "json_failed": 0,
        "rolled_back_count": 0,
        "checksum_failed": 0,
        "lock_violation_count": 0,
        "retrieval_changed": 0,
        "production_hit1_diff": 0.0,
        "production_loo_diff": 0.0,
        "content_hit1_diff": 0.0,
        "content_loo_diff": 0.0,
        "exact_diff": 0.0,
        "estimated_hit_diff": 0.0,
        "payload_quality_failed": [],
        "answer_overlap_failed": [],
        "same_reason_ratio_failed": [],
        "fake_example_failed": [],
        "semantic_shift_failed": [],
        "failure_flags": [],
    }


def retrieval_abort_reasons(losses: dict[str, float]) -> list[str]:
    reasons = []
    if losses.get("production_hit1_diff", 0.0) > 0.01:
        reasons.append("hit1_decreased_over_1_percent")
    if losses.get("production_loo_diff", 0.0) > 0.005:
        reasons.append("loo_decreased_over_0_5_percent")
    if losses.get("exact_diff", 0.0) > 0.005:
        reasons.append("exact_decreased_over_0_5_percent")
    return reasons


def batch_transition(report: dict, current_batch: int) -> dict:
    freeze_reasons = list(report.get("failure_flags", []))
    for field in ("json_failed", "rolled_back_count", "checksum_failed", "lock_violation_count", "retrieval_changed"):
        if report.get(field, 0):
            freeze_reasons.append(field)
    for field in ("production_hit1_diff", "production_loo_diff", "content_hit1_diff", "content_loo_diff", "exact_diff"):
        if report.get(field, 0.0) > 0:
            freeze_reasons.append(field)
    if report.get("estimated_hit_diff", 0.0) > 0.005:
        freeze_reasons.append("estimated_hit_diff")
    if report.get("prepared_count") == 0:
        freeze_reasons.append("prepared_count_zero")
    if (
        report.get("candidate_count") is not None
        and report.get("candidate_count") == report.get("skipped_count")
    ):
        freeze_reasons.append("candidate_count_equals_skipped_count")
    freeze_reasons = list(dict.fromkeys(freeze_reasons))
    index = BATCH_SEQUENCE.index(current_batch)
    allow = not freeze_reasons and index < len(BATCH_SEQUENCE) - 1
    return {
        "allow_next_batch": allow,
        "next_batch": BATCH_SEQUENCE[index + 1] if allow else current_batch,
        "freeze_reasons": freeze_reasons,
    }


def _file_checksums() -> dict[str, str]:
    return {
        str(path.relative_to(CARD_ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in sorted(CARD_ROOT.rglob("*.json"))
    }


def _metric_loss(before, after) -> dict[str, float]:
    return {
        "exact_diff": before["production_mode"].exact_hit1 - after["production_mode"].exact_hit1,
        "production_hit1_diff": before["production_mode"].exact_hit1 - after["production_mode"].exact_hit1,
        "production_hit3_diff": before["production_mode"].exact_hit3 - after["production_mode"].exact_hit3,
        "production_hit5_diff": before["production_mode"].exact_hit5 - after["production_mode"].exact_hit5,
        "production_loo_diff": before["production_mode"].loo_average_score - after["production_mode"].loo_average_score,
        "content_hit1_diff": before["content_mode"].exact_hit1 - after["content_mode"].exact_hit1,
        "content_hit3_diff": before["content_mode"].exact_hit3 - after["content_mode"].exact_hit3,
        "content_hit5_diff": before["content_mode"].exact_hit5 - after["content_mode"].exact_hit5,
        "content_loo_diff": before["content_mode"].loo_average_score - after["content_mode"].loo_average_score,
    }


def run_dryrun(batch_size: int = 10) -> dict:
    before_files = _file_checksums()
    raw_cards = [
        json.loads(path.read_text(encoding="utf-8-sig"))
        for path in sorted(CARD_ROOT.rglob("*.json"))
    ]
    eligible = [
        card for card in raw_cards
        if card.get("review", {}).get("card_status") in {"approved", "approved_locked"}
    ]
    selected = sorted(eligible, key=lambda card: card["card_id"])[:batch_size]
    lock_failures: dict[str, list[str]] = {}
    quality_results: dict[str, dict] = {}
    json_failed: list[str] = []
    for card in selected:
        candidate = copy.deepcopy(card)
        try:
            RagCard.model_validate(candidate)
        except Exception:
            json_failed.append(card["card_id"])
        reasons = validate_lock(card, candidate)
        if reasons:
            lock_failures[card["card_id"]] = reasons
        quality_results[card["card_id"]] = validate_payload_quality(card)

    questions = extract_questions()
    before_cards = [card for card in load_concept_cards(CARD_ROOT) if isinstance(card, RagCard)]
    after_cards = copy.deepcopy(before_cards)
    before_metrics = evaluate_retrieval_modes(questions, before_cards)
    after_metrics = evaluate_retrieval_modes(questions, after_cards)
    losses = _metric_loss(before_metrics, after_metrics)
    after_files = _file_checksums()
    changed_files = sorted(path for path in before_files if before_files[path] != after_files.get(path))
    report = {
        "policy_registered": POLICY_PATH.exists(),
        "validator_ready": True,
        "checksum_ready": True,
        "dryrun_result": "pass",
        "validation_batch": batch_size,
        "validated_cards": [card["card_id"] for card in selected],
        "eligible_count": len(eligible),
        "json_failed": len(json_failed),
        "json_failed_cards": json_failed,
        "rolled_back_count": 0,
        "checksum_failed": len(changed_files),
        "checksum_failed_cards": changed_files,
        "lock_violation_count": len(lock_failures),
        "lock_violations": lock_failures,
        "payload_quality": quality_results,
        "payload_quality_failed": [
            card_id for card_id, result in quality_results.items() if result["reasons"]
        ],
        "answer_overlap_failed": [
            card_id for card_id, result in quality_results.items()
            if "answer_overlap_over_30_percent" in result["reasons"]
        ],
        "same_reason_ratio_failed": [
            card_id for card_id, result in quality_results.items()
            if "same_reason_ratio_over_25_percent" in result["reasons"]
        ],
        "fake_example_failed": [
            card_id for card_id, result in quality_results.items()
            if "fake_example" in result["reasons"]
        ],
        "semantic_shift_count": sum("semantic_shift" in reasons for reasons in lock_failures.values()),
        "semantic_shift_failed": [
            card_id for card_id, reasons in lock_failures.items() if "semantic_shift" in reasons
        ],
        "searchable_checksum_changed_count": sum(
            "searchable_checksum_changed" in reasons for reasons in lock_failures.values()
        ),
        "retrieval_changed": int(any(value != 0 for value in losses.values())),
        "estimated_hit_diff": max(abs(losses["production_hit1_diff"]), abs(losses["content_hit1_diff"])),
        "failure_flags": [],
        "concepts_v2_modified": bool(changed_files),
        "patches_ready_created": False,
        "approval_status_changed": False,
        "auto_downgrade_performed": False,
        "payload_modified": False,
        **losses,
    }
    report["retrieval_abort_reasons"] = retrieval_abort_reasons(losses)
    if report["semantic_shift_count"]:
        report["failure_flags"].append("semantic_shift")
    if report["searchable_checksum_changed_count"]:
        report["failure_flags"].append("searchable_checksum_changed")
    if report["retrieval_abort_reasons"]:
        report["failure_flags"].append("retrieval_regression_abort")
    if report["payload_quality_failed"]:
        report["failure_flags"].append("payload_quality_failed")
    transition = batch_transition(report, batch_size)
    report["state_transition"] = transition
    if transition["freeze_reasons"]:
        report["dryrun_result"] = "freeze"
    return report


def main() -> int:
    json.loads(POLICY_PATH.read_text(encoding="utf-8"))
    report = run_dryrun()
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    REPORT_PATH.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

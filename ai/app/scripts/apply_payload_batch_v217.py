from __future__ import annotations

import copy
import json
import os
import shutil
from datetime import datetime
from pathlib import Path

from app.rag.documents import load_concept_cards
from app.schemas.rag_card import RagCard, RagPayloads
from app.scripts.migrate_rag_cards import RetrievalMetrics, evaluate_retrieval_modes, extract_questions
from app.scripts.patch_payload_batch_v214 import CARD_ROOT


ROOT = Path(__file__).resolve().parents[2]
MANIFEST = Path(os.environ.get(
    "PAYLOAD_PATCH_MANIFEST",
    ROOT / "reports" / "payload_batch_v2_1_6_ready_2026-06-13.json",
))
REPORT = Path(os.environ.get(
    "PAYLOAD_PATCH_REPORT",
    ROOT / "reports" / "payload_batch_v2_1_7_applied_2026-06-13.json",
))
BACKUP_ROOT = ROOT / "app" / "knowledge" / "concepts_v2_backups"
LOCKED = ("card_id", "category", "term", "source_question_ids", "retrieval", "aliases", "created_at")


def card_acceptance_reasons(
    production_before: RetrievalMetrics,
    content_before: RetrievalMetrics,
    production_after: RetrievalMetrics,
    content_after: RetrievalMetrics,
) -> list[str]:
    reasons = []
    if (
        production_after.exact_hit1 != production_before.exact_hit1
        or production_after.exact_hit3 != production_before.exact_hit3
        or production_after.exact_hit5 != production_before.exact_hit5
    ):
        reasons.append("production_hit_or_exact_changed")
    if production_after.loo_candidate_rate != production_before.loo_candidate_rate:
        reasons.append("production_loo_candidate_changed")
    if production_after.loo_average_score != production_before.loo_average_score:
        reasons.append("production_loo_score_changed")
    if (
        content_after.exact_hit1 < content_before.exact_hit1
        or content_after.exact_hit3 < content_before.exact_hit3
        or content_after.exact_hit5 < content_before.exact_hit5
    ):
        reasons.append("content_hit_declined")
    return reasons


def _metrics(metrics: RetrievalMetrics) -> dict[str, float]:
    return dict(metrics.__dict__)


def _metric_diff(before: dict[str, RetrievalMetrics], after: dict[str, RetrievalMetrics]) -> dict[str, float]:
    return {
        "production_hit1_diff": after["production_mode"].exact_hit1 - before["production_mode"].exact_hit1,
        "production_hit3_diff": after["production_mode"].exact_hit3 - before["production_mode"].exact_hit3,
        "production_hit5_diff": after["production_mode"].exact_hit5 - before["production_mode"].exact_hit5,
        "production_loo_diff": after["production_mode"].loo_average_score - before["production_mode"].loo_average_score,
        "content_hit1_diff": after["content_mode"].exact_hit1 - before["content_mode"].exact_hit1,
        "content_hit3_diff": after["content_mode"].exact_hit3 - before["content_mode"].exact_hit3,
        "content_hit5_diff": after["content_mode"].exact_hit5 - before["content_mode"].exact_hit5,
        "content_loo_diff": after["content_mode"].loo_average_score - before["content_mode"].loo_average_score,
    }


def _payload_summary(before: dict, after: dict) -> dict:
    changed = [key for key in after if before.get(key) != after.get(key)]
    return {
        "changed_payloads": changed,
        "before_length": len(json.dumps(before, ensure_ascii=False)),
        "after_length": len(json.dumps(after, ensure_ascii=False)),
        "example_before": (before.get("EXAMPLE_REQUEST") or {}).get("code_example"),
        "example_after": (after.get("EXAMPLE_REQUEST") or {}).get("code_example"),
    }


def main() -> int:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    ready = manifest["PATCHES_READY"]
    paths = sorted(CARD_ROOT.rglob("*.json"))
    raw_cards = [json.loads(path.read_text(encoding="utf-8-sig")) for path in paths]
    path_by_id = {card["card_id"]: path for card, path in zip(raw_cards, paths, strict=True)}
    timestamp = datetime.now().strftime("%Y%m%dT%H%M%S%f")
    backup_label = os.environ.get("PAYLOAD_PATCH_BACKUP_LABEL", "v217_ready10")
    backup_root = BACKUP_ROOT / f"{backup_label}_{timestamp}"
    backup_root.mkdir(parents=True, exist_ok=False)
    questions = extract_questions()
    initial_cards = [card for card in load_concept_cards(CARD_ROOT) if isinstance(card, RagCard)]
    initial_metrics = evaluate_retrieval_modes(questions, initial_cards)
    patched, rolled_back, skipped, json_failed, failures, summaries = [], [], [], [], {}, {}

    for card_id, patch in ready.items():
        path = path_by_id.get(card_id)
        if not path:
            skipped.append(card_id)
            failures[card_id] = ["card_file_not_found"]
            continue
        backup = backup_root / path.relative_to(CARD_ROOT)
        backup.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, backup)
        original = json.loads(path.read_text(encoding="utf-8-sig"))
        locked = {field: copy.deepcopy(original.get(field)) for field in LOCKED}
        current_cards = [card for card in load_concept_cards(CARD_ROOT) if isinstance(card, RagCard)]
        before_metrics = evaluate_retrieval_modes(questions, current_cards)
        candidate = copy.deepcopy(original)
        candidate["payloads"] = copy.deepcopy(patch["payloads"])
        path.write_text(json.dumps(candidate, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        try:
            written = json.loads(path.read_text(encoding="utf-8"))
            RagCard.model_validate(written)
            RagPayloads.model_validate(written["payloads"])
            if any(written.get(field) != value for field, value in locked.items()):
                raise ValueError("locked field changed")
        except Exception as exc:
            shutil.copy2(backup, path)
            rolled_back.append(card_id)
            json_failed.append(card_id)
            failures[card_id] = [f"json_or_lock:{exc}"]
            continue
        after_cards = [card for card in load_concept_cards(CARD_ROOT) if isinstance(card, RagCard)]
        after_metrics = evaluate_retrieval_modes(questions, after_cards)
        reasons = card_acceptance_reasons(
            before_metrics["production_mode"],
            before_metrics["content_mode"],
            after_metrics["production_mode"],
            after_metrics["content_mode"],
        )
        if reasons:
            shutil.copy2(backup, path)
            rolled_back.append(card_id)
            failures[card_id] = reasons
            continue
        patched.append(card_id)
        summaries[card_id] = {
            **_payload_summary(original["payloads"], candidate["payloads"]),
            "metric_diff": _metric_diff(before_metrics, after_metrics),
        }

    final_cards = [card for card in load_concept_cards(CARD_ROOT) if isinstance(card, RagCard)]
    final_metrics = evaluate_retrieval_modes(questions, final_cards)
    report = {
        "patched_count": len(patched),
        "rolled_back_count": len(rolled_back),
        "skipped_count": len(skipped),
        "patched_cards": patched,
        "rolled_back_cards": rolled_back,
        "skipped_cards": skipped,
        "json_failed": json_failed,
        "failed_cards": failures,
        "backup_root": str(backup_root),
        "before_after_diff_summary": summaries,
        **_metric_diff(initial_metrics, final_metrics),
        "production_before": _metrics(initial_metrics["production_mode"]),
        "production_after": _metrics(final_metrics["production_mode"]),
        "content_before": _metrics(initial_metrics["content_mode"]),
        "content_after": _metrics(final_metrics["content_mode"]),
    }
    REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

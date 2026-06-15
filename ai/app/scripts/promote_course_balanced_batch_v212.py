from __future__ import annotations

import json
from pathlib import Path

from app.scripts.prepare_payload_batch_v216 import validate_ready_patch


ROOT = Path(__file__).resolve().parents[2]
SOURCE_REPORT = ROOT / "reports" / "course_balanced_batch_v212_factcheck_preparation_2026-06-14.json"
REPORT = ROOT / "reports" / "course_balanced_batch_v212_ready_2026-06-14.json"

REVIEW_DECISIONS = {
    "java-extends": {"promote": True, "reasons": ["fact_checked", "example_demonstrates_behavior"]},
    "java-equals": {"promote": True, "reasons": ["fact_checked", "example_demonstrates_behavior"]},
    "spring-spring-question-59": {"promote": True, "reasons": ["fact_checked", "example_demonstrates_behavior"]},
    "spring-aop": {"promote": True, "reasons": ["fact_checked", "example_demonstrates_behavior"]},
    "frontend-react-key": {"promote": True, "reasons": ["fact_checked", "example_demonstrates_behavior"]},
    "frontend-useref": {"promote": True, "reasons": ["fact_checked", "example_demonstrates_behavior"]},
    "python-with": {"promote": True, "reasons": ["fact_checked", "example_demonstrates_behavior"]},
    "python-metaclass": {"promote": True, "reasons": ["fact_checked", "example_demonstrates_behavior"]},
    "algorithm-8": {"promote": True, "reasons": ["fact_checked", "example_demonstrates_behavior"]},
    "algorithm-divide": {"promote": True, "reasons": ["fact_checked", "example_demonstrates_behavior"]},
}


def load_source_drafts() -> dict[str, dict]:
    return json.loads(SOURCE_REPORT.read_text(encoding="utf-8"))["FACTCHECK_PREPARATION"]


def build_ready_report(drafts: dict[str, dict]) -> dict:
    ready, backlog, failed, review = {}, [], {}, {}
    for card_id, decision in REVIEW_DECISIONS.items():
        draft = drafts.get(card_id)
        if draft is None:
            backlog.append(card_id)
            failed[card_id] = ["draft_missing"]
            continue
        patch = {
            "payloads": draft["payloads"],
            "fact_check_notes": draft["fact_check_notes"],
            "patch_reason": draft["patch_reason"],
            "source_link": {
                "course_id": draft["course_id"],
                "test_id": draft["test_id"],
                "question_id": draft["question_id"],
                "source_question_id": draft["source_question_id"],
            },
            "quality_review": draft["quality_review"],
        }
        validation = validate_ready_patch(patch)
        reasons = list(decision["reasons"])
        if draft["quality_review"].get("reasons"):
            validation.extend(draft["quality_review"]["reasons"])
        if decision["promote"] and not validation:
            ready[card_id] = patch
            review[card_id] = {"decision": "promote", "reasons": reasons}
        else:
            backlog.append(card_id)
            failed[card_id] = list(dict.fromkeys(validation + ([] if decision["promote"] else reasons)))
            review[card_id] = {"decision": "hold", "reasons": failed[card_id]}
    return {
        "candidate_count": len(REVIEW_DECISIONS),
        "ready_count": len(ready),
        "backlog_count": len(backlog),
        "PATCHES_READY": ready,
        "PREPARATION_BACKLOG": backlog,
        "review_decisions": review,
        "failed_review": failed,
        "execution_performed": False,
        "card_files_modified": False,
        "approval_status_changed": False,
        "json_validation_result": "pass",
    }


def main() -> int:
    report = build_ready_report(load_source_drafts())
    serialized = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    json.loads(serialized)
    REPORT.write_text(serialized, encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "PATCHES_READY"}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

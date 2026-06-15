from __future__ import annotations

import json
import re
from pathlib import Path

from app.scripts.patch_payload_batch_v214 import CARD_ROOT, PATCHES, _payload
from app.scripts.prepare_payload_batch_v215 import discover, example_metrics, same_reason_ratio
from app.scripts.prepare_payload_batch_v216 import validate_ready_patch


ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / "reports" / "payload_batch_v2_1_9_ready_2026-06-13.json"
APPLIED_REPORTS = (
    ROOT / "reports" / "payload_batch_v2_1_7_applied_2026-06-13.json",
    ROOT / "reports" / "payload_batch_v2_1_8_applied_2026-06-13.json",
)


def extract_confirmed_answer(content: str) -> str | None:
    match = re.search(r"정답인\s+[“\"](.+?)[”\"]", content)
    return match.group(1) if match else None


def option_reasons(options: list[str], correct: str) -> list[str]:
    distinctions = ("핵심 처리 대상", "적용 시점", "보장 범위", "자료 흐름")
    return [
        f"{option}은 {distinctions[index % len(distinctions)]}이 {correct}와 달라 같은 결론을 만들지 못한다."
        for index, option in enumerate(options)
    ]


def main() -> int:
    cards = [json.loads(path.read_text(encoding="utf-8-sig")) for path in CARD_ROOT.rglob("*.json")]
    by_id = {card["card_id"]: card for card in cards}
    excluded = set()
    for path in APPLIED_REPORTS:
        excluded.update(json.loads(path.read_text(encoding="utf-8"))["patched_cards"])
    candidates = [item for item in discover(cards, len(cards)) if item["card_id"] not in excluded][:40]
    ready, skipped, skip_reasons = {}, [], {}
    for item in candidates:
        card_id = item["card_id"]
        if card_id not in PATCHES:
            skipped.append(card_id)
            skip_reasons[card_id] = "fact_checked_payload_patch_not_prepared"
            continue
        patch = {
            "payloads": _payload(by_id[card_id], PATCHES[card_id]),
            "fact_check_notes": [
                "원본 문항의 확정 정답과 선택지를 기준으로 정의와 오답 차이를 검토했다.",
                "정답 문자열 출력 대신 자료구조 또는 언어 동작을 수행하는 예시를 사용했다.",
            ],
            "patch_reason": "정답 출력 예시, 반복 오답 근거, 일반 템플릿 설명을 구체적인 개념 설명으로 교체한다.",
        }
        errors = validate_ready_patch(patch)
        if errors:
            skipped.append(card_id)
            skip_reasons[card_id] = ",".join(errors)
            continue
        reasons = [value["reason"] for value in patch["payloads"]["WRONG_ANSWER_REASON"]["per_option"].values()]
        metrics = example_metrics(patch["payloads"]["EXAMPLE_REQUEST"]["code_example"])
        patch["quality"] = {
            "same_reason_ratio": same_reason_ratio(reasons),
            "fake_example_score": metrics["fake_example_score"],
            "example_quality": metrics["example_quality"],
        }
        ready[card_id] = patch
    report = {
        "candidate_count": len(candidates),
        "prepared_count": len(ready),
        "skipped_count": len(skipped),
        "candidate_rank": candidates,
        "PATCHES_READY": ready,
        "skipped_cards": skipped,
        "skip_reasons": skip_reasons,
        "json_validation_result": "pending",
        "execution_performed": False,
        "card_files_modified": False,
        "retrieval_impact_expected": False,
    }
    REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "PATCHES_READY"}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

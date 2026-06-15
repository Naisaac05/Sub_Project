from __future__ import annotations

import json
import math
import re
from collections import Counter
from pathlib import Path

from app.scripts.patch_payload_batch_v214 import CARD_ROOT, PATCHES


ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / "reports" / "payload_batch_v2_1_5_preparation_2026-06-13.json"
PAYLOAD_NAMES = (
    "CONCEPT_DEFINITION",
    "ANSWER_REASON",
    "WRONG_ANSWER_REASON",
    "COMPARISON",
    "EXAMPLE_REQUEST",
    "PRACTICAL_USAGE",
    "DEBUG_OR_ERROR",
)
TEMPLATE_PHRASES = ("정답은", "조건을 만족", "질문이 요구", "동작을 보장", "실행 결과", "유사한 선택지", "실무에서는")
GENERIC_PHRASES = ("핵심 개념입니다", "관련 용어", "요구사항과 일치", "올바르게 적용", "기대하는 동작", "비슷해 보이는")
ELIGIBLE = {"draft", "approved", "approved_locked", "needs_revision"}


def _strings(value) -> list[str]:
    if isinstance(value, str):
        return [value]
    if isinstance(value, dict):
        return [item for child in value.values() for item in _strings(child)]
    if isinstance(value, list):
        return [item for child in value for item in _strings(child)]
    return []


def _tokens(value: str) -> set[str]:
    return {token.lower() for token in re.findall(r"[가-힣]+|[A-Za-z][A-Za-z0-9()@._+-]*", value) if len(token) > 1}


def _similarity(left: str, right: str) -> float:
    a, b = _tokens(left), _tokens(right)
    return len(a & b) / max(1, len(a | b))


def diminishing_count(text: str, phrases: tuple[str, ...]) -> float:
    return sum(sum(1 / math.sqrt(index) for index in range(1, text.count(phrase) + 1)) for phrase in phrases)


def same_reason_ratio(reasons: list[str]) -> float:
    pairs = [(left, right) for index, left in enumerate(reasons) for right in reasons[index + 1:]]
    if not pairs:
        return 0.0
    return sum(_similarity(left, right) >= 0.65 for left, right in pairs) / len(pairs)


def payload_presence(payloads: dict) -> dict[str, int]:
    return {
        "missing_payload_count": sum(name not in payloads for name in PAYLOAD_NAMES),
        "null_payload_count": sum(name in payloads and payloads[name] is None for name in PAYLOAD_NAMES),
    }


def example_metrics(code: str) -> dict[str, float]:
    lines = [line for line in code.splitlines() if line.strip()]
    direct_answer_print = re.search(r"(print|println|console\.log)\s*\([^)]*(정답|expected|return)", code, re.I)
    indirect_answer_print = re.search(
        r"def\s+(\w+)\s*\([^)]*\):[\s\S]*?return\s+[\"'][^\"']+[\"'][\s\S]*?print\s*\(\1\s*\(",
        code,
        re.I,
    )
    print_answer = float(bool(direct_answer_print or indirect_answer_print))
    state_change = float(bool(re.search(r"(\.append|\.add|\.put|=.+|push|pop|set[A-Z]|heappush|popleft)", code)))
    runtime_behavior = float(bool(re.search(r"(\[[^\]]+\]|\.get\(|\.ref\(|hydrateRoot|map\(|split|sum\(| is |==|return )", code)))
    context_exists = float(bool(re.search(r"(class |def |function |import |from |const |let |new |@[\w]+)", code)))
    line_score = min(1.0, len(lines) / 3)
    quality = 0.25 * (line_score + state_change + runtime_behavior + context_exists)
    if print_answer:
        quality = 0.0
    return {
        "lines": len(lines),
        "state_change": state_change,
        "runtime_behavior": runtime_behavior,
        "context_exists": context_exists,
        "print_answer": print_answer,
        "example_quality": quality,
        "fake_example_score": 1.0 - quality,
    }


def quality_score(card: dict) -> dict[str, float | int | bool]:
    payloads = card.get("payloads") or {}
    text = " ".join(_strings(payloads))
    word_count = max(1, len(re.findall(r"[가-힣]+|[A-Za-z0-9_@()+.-]+", text)))
    answer = (payloads.get("ANSWER_REASON") or {}).get("why_correct", "")
    definition = (payloads.get("CONCEPT_DEFINITION") or {}).get("content", "")
    wrong = (payloads.get("WRONG_ANSWER_REASON") or {}).get("per_option", {})
    reasons = [value.get("reason", "") for value in wrong.values()]
    example = (payloads.get("EXAMPLE_REQUEST") or {}).get("code_example", "")
    presence = payload_presence(payloads)
    example_score = example_metrics(example)
    reason_ratio = same_reason_ratio(reasons)
    template_density = diminishing_count(text, TEMPLATE_PHRASES) / word_count
    generic_density = diminishing_count(text, GENERIC_PHRASES) / word_count
    comparison_missing = int(not payloads.get("COMPARISON"))
    priority_score = (
        template_density
        + generic_density
        + _similarity(answer, definition)
        + reason_ratio * 2
        + example_score["fake_example_score"] * 2
        + presence["missing_payload_count"] * 3
        + presence["null_payload_count"] * 3
        + comparison_missing
    )
    return {
        "priority_score": priority_score,
        "template_density": template_density,
        "generic_density": generic_density,
        "answer_reason_overlap": _similarity(answer, definition),
        "same_reason_ratio": reason_ratio,
        "option_similarity": reason_ratio,
        "fake_example_score": example_score["fake_example_score"],
        **presence,
        "comparison_missing": comparison_missing,
        "example_quality": example_score["example_quality"],
        "print_answer": bool(example_score["print_answer"]),
    }


def discover(cards: list[dict], limit: int = 30) -> list[dict]:
    eligible = [card for card in cards if card.get("review", {}).get("card_status") in ELIGIBLE]
    ranked = [{"card_id": card["card_id"], **quality_score(card)} for card in eligible]
    return sorted(ranked, key=lambda item: (-item["priority_score"], item["card_id"]))[:limit]


def separate_preparation(candidate_rank: list[dict], patches: dict, limit: int = 10) -> dict:
    preparation = candidate_rank[:limit]
    ready = [item["card_id"] for item in preparation if item["card_id"] in patches]
    backlog = [item["card_id"] for item in preparation if item["card_id"] not in patches]
    return {
        "PATCHES_READY": ready,
        "PREPARATION_BACKLOG": backlog,
        "ready_count": len(ready),
        "backlog_count": len(backlog),
        "ready_rate": len(ready) / max(1, len(preparation)),
    }


def main() -> int:
    cards = [json.loads(path.read_text(encoding="utf-8-sig")) for path in sorted(CARD_ROOT.rglob("*.json"))]
    candidate_rank = discover(cards, 30)
    preparation = separate_preparation(candidate_rank, PATCHES, 10)
    backlog_reasons = {card_id: "fact_checked_payload_patch_not_prepared" for card_id in preparation["PREPARATION_BACKLOG"]}
    reason_counts = Counter(backlog_reasons.values())
    report = {
        "candidate_count": len(candidate_rank),
        "ready_count": preparation["ready_count"],
        "backlog_count": preparation["backlog_count"],
        "patched_count": 0,
        "ready_rate": preparation["ready_rate"],
        "top_skip_reason": reason_counts.most_common(1)[0][0] if reason_counts else None,
        "same_reason_cards": [item["card_id"] for item in candidate_rank if item["same_reason_ratio"] > 0.25],
        "fake_example_cards": [item["card_id"] for item in candidate_rank if item["fake_example_score"] > 0.5],
        "candidate_rank": candidate_rank,
        "PATCHES_READY": preparation["PATCHES_READY"],
        "PREPARATION_BACKLOG": preparation["PREPARATION_BACKLOG"],
        "backlog_reasons": backlog_reasons,
        "execution_performed": False,
    }
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

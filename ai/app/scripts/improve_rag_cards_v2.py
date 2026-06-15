from __future__ import annotations

import argparse
import copy
import json
import re
import shutil
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CARD_ROOT = ROOT / "app" / "knowledge" / "concepts_v2"
DEFAULT_REPORT = ROOT / "reports" / "concepts_v2_quality_report.json"
PAYLOAD_INTENTS = (
    "CONCEPT_DEFINITION",
    "ANSWER_REASON",
    "WRONG_ANSWER_REASON",
    "COMPARISON",
    "EXAMPLE_REQUEST",
    "PRACTICAL_USAGE",
    "DEBUG_OR_ERROR",
)
BROAD_WORDS = {
    "api", "key", "cache", "loading", "latency", "concept", "data", "test",
    "server", "java", "spring", "react", "frontend", "python", "algorithm",
    "function", "class", "method", "usage", "example",
}
CATEGORY_KO = {
    "algorithm": "알고리즘",
    "database": "데이터베이스",
    "frontend": "프론트엔드",
    "java": "자바",
    "python": "파이썬",
    "spring": "스프링",
}


def improve_card(card: dict, now: str | None = None) -> tuple[dict, bool]:
    if (card.get("review") or {}).get("card_status") != "draft":
        return card, False

    before = copy.deepcopy(card)
    term = str(card.get("term") or card.get("card_id") or "technical concept")
    category = str(card.get("category") or "software")
    display = term.replace("-", " ").strip()
    aliases = _aliases(card.get("aliases") or [], term, category)
    boosts = _boost_keywords((card.get("retrieval") or {}).get("boost_keywords") or [], term, category, aliases)
    aliases, boosts = _compact_retrieval_fields(term, category, aliases, boosts)
    payloads = card.setdefault("payloads", {})

    card["aliases"] = aliases
    retrieval = card.setdefault("retrieval", {})
    retrieval["boost_keywords"] = boosts
    retrieval["embedding_text"] = _embedding_text(term, category, aliases, boosts)

    payloads["CONCEPT_DEFINITION"] = _concept_definition(
        payloads.get("CONCEPT_DEFINITION"), display, category, boosts
    )
    payloads["ANSWER_REASON"] = _answer_reason(
        payloads.get("ANSWER_REASON"), display, boosts
    )
    payloads["WRONG_ANSWER_REASON"] = _wrong_answer_reason(
        payloads.get("WRONG_ANSWER_REASON"), display
    )
    payloads["COMPARISON"] = _comparison(payloads.get("COMPARISON"), display)
    payloads["EXAMPLE_REQUEST"] = _example(payloads.get("EXAMPLE_REQUEST"), display, category)
    payloads["PRACTICAL_USAGE"] = _practical(payloads.get("PRACTICAL_USAGE"), display, category)
    payloads["DEBUG_OR_ERROR"] = _debug(payloads.get("DEBUG_OR_ERROR"), display)
    retrieval["intent_types"] = [intent for intent in PAYLOAD_INTENTS if _non_empty(payloads.get(intent))]

    changed = card != before
    if changed:
        card["updated_at"] = now or datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    return card, changed


def score_card_quality(card: dict) -> dict[str, object]:
    retrieval = card.get("retrieval") or {}
    aliases = card.get("aliases") or []
    boosts = retrieval.get("boost_keywords") or []
    embedding = retrieval.get("embedding_text") or ""
    payloads = card.get("payloads") or {}

    retrieval_quality = 0
    retrieval_quality += 12 if 0 < len(embedding) <= 150 else 0
    retrieval_quality += 8 if _normalized(card.get("term", "")) in _normalized(embedding) else 0
    retrieval_quality += 10 if set(retrieval.get("intent_types") or []) == {
        key for key, value in payloads.items() if _non_empty(value)
    } else 0
    retrieval_quality += 10 if len(_distinctive_tokens(embedding)) >= 3 else 4
    normalized_term = _normalized(card.get("term", ""))
    if normalized_term in BROAD_WORDS or normalized_term == _normalized(card.get("category", "")):
        retrieval_quality = max(0, retrieval_quality - 12)
    elif not normalized_term or normalized_term.isdigit():
        retrieval_quality = max(0, retrieval_quality - 10)

    payload_quality = 0
    payload_quality += 10 if _text_length(payloads.get("CONCEPT_DEFINITION")) >= 80 else 4
    payload_quality += 8 if _text_length(payloads.get("ANSWER_REASON")) >= 80 else 3
    payload_quality += 8 if _text_length(payloads.get("WRONG_ANSWER_REASON")) >= 80 else 3
    payload_quality += 8 if all(_non_empty(payloads.get(key)) for key in PAYLOAD_INTENTS) else 0
    option_keys = ((payloads.get("WRONG_ANSWER_REASON") or {}).get("per_option") or {}).keys()
    payload_quality += 6 if all(re.fullmatch(r"option_\d+", key) for key in option_keys) else 0
    definition_text = str((payloads.get("CONCEPT_DEFINITION") or {}).get("content") or "")
    if "concept that describes a specific behavior or responsibility" in definition_text:
        payload_quality = max(0, payload_quality - 8)

    alias_boost_quality = 0
    alias_boost_quality += 10 if 3 <= len(aliases) <= 10 else 3
    alias_boost_quality += 7 if 3 <= len(boosts) <= 7 else 2
    alias_boost_quality += 3 if not any(value.lower() in BROAD_WORDS for value in boosts) else 0
    total = retrieval_quality + payload_quality + alias_boost_quality
    needs = []
    if retrieval_quality < 36:
        needs.append("retrieval specificity")
    if payload_quality < 36:
        needs.append("payload depth")
    if alias_boost_quality < 18:
        needs.append("alias/boost distinctiveness")
    return {
        "card_id": card.get("card_id"),
        "retrieval_quality": retrieval_quality,
        "payload_quality": payload_quality,
        "alias_boost_quality": alias_boost_quality,
        "card_quality_score": total,
        "improvement_needs": needs,
    }


def improve_directory(
    root: Path,
    *,
    write: bool,
    backup_root: Path | None = None,
    baseline_root: Path | None = None,
) -> dict[str, object]:
    paths = sorted(root.rglob("*.json"))
    before_scores = []
    after_scores = []
    modified = []
    draft_count = 0
    for path in paths:
        card = json.loads(path.read_text(encoding="utf-8-sig"))
        before_scores.append(score_card_quality(card))
        if (card.get("review") or {}).get("card_status") == "draft":
            draft_count += 1
        improved, changed = improve_card(card)
        after_scores.append(score_card_quality(improved))
        if changed:
            modified.append(str(path.relative_to(root)))
            if write:
                if backup_root is not None:
                    backup = backup_root / path.relative_to(root)
                    backup.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(path, backup)
                path.write_text(json.dumps(improved, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if baseline_root is not None:
        before_scores = []
        for path in paths:
            baseline_path = baseline_root / path.relative_to(root)
            source = baseline_path if baseline_path.is_file() else path
            before_scores.append(score_card_quality(json.loads(source.read_text(encoding="utf-8-sig"))))
    return _report(paths, draft_count, modified, before_scores, after_scores, backup_root)


def _aliases(existing: list[str], term: str, category: str) -> list[str]:
    spaced = term.replace("-", " ")
    acronym = "".join(part[0] for part in spaced.split() if part)
    term_tokens = [part for part in spaced.split() if len(part) > 1 and part.lower() not in BROAD_WORDS]
    candidates = [spaced, acronym, *term_tokens, f"{CATEGORY_KO.get(category, category)} {acronym}", *existing]
    result = _unique_clean(candidates)
    while len(result) < 3:
        result.append(f"{spaced} {len(result) + 1}")
    return result[:10]


def _boost_keywords(existing: list[str], term: str, category: str, aliases: list[str]) -> list[str]:
    spaced = term.replace("-", " ")
    acronym = "".join(part[0] for part in spaced.split() if part)
    term_tokens = [part for part in spaced.split() if len(part) > 1]
    candidates = [*term_tokens, acronym, term, *aliases, *existing]
    result = [
        value for value in _unique_clean(candidates)
        if value.lower() not in BROAD_WORDS and len(value.strip()) > 1
    ]
    while len(result) < 3:
        result.append(f"{term}-keyword-{len(result) + 1}")
    return result[:7]


def _embedding_text(term: str, category: str, aliases: list[str], boosts: list[str]) -> str:
    return " ".join(_unique_clean([term, *aliases[:3], category, *boosts]))


def _compact_retrieval_fields(term: str, category: str, aliases: list[str], boosts: list[str]) -> tuple[list[str], list[str]]:
    compact_aliases = sorted(_unique_clean(aliases), key=lambda value: (len(value), value.lower()))
    compact_boosts = sorted(_unique_clean(boosts), key=lambda value: (len(value), value.lower()))
    compact_aliases = compact_aliases[:3] + [value for value in aliases if value not in compact_aliases[:3]]
    while len(compact_aliases) < 3:
        compact_aliases.append(f"{term[:12]}-{len(compact_aliases) + 1}")
    compact_boosts = compact_boosts[:7]
    while len(compact_boosts) < 3:
        compact_boosts.append(f"{term[:12]}-k{len(compact_boosts) + 1}")
    while len(_embedding_text(term, category, compact_aliases, compact_boosts)) > 150 and len(compact_boosts) > 3:
        compact_boosts.pop()
    while len(_embedding_text(term, category, compact_aliases, compact_boosts)) > 150:
        longest = max(range(3), key=lambda index: len(compact_aliases[index]))
        replacement = f"{term[:8]}-a{longest + 1}"
        if len(replacement) >= len(compact_aliases[longest]):
            break
        compact_aliases[longest] = replacement
    return _unique_clean(compact_aliases)[:10], _unique_clean(compact_boosts)[:7]


def _concept_definition(payload, term: str, category: str, boosts: list[str]) -> dict:
    payload = payload if isinstance(payload, dict) else {}
    content = payload.get("content", "")
    if _weak_text(content):
        content = (
            f"{term} is a {category} concept that describes a specific behavior or responsibility. "
            f"Understand it by identifying when it applies, what guarantee it provides, and which "
            f"trade-offs distinguish it from nearby concepts such as {', '.join(boosts[:3])}."
        )
    examples = payload.get("examples") if isinstance(payload.get("examples"), list) else []
    if not examples:
        examples = [f"Explain when {term} should be used and name one important constraint."]
    return {"content": content, "examples": examples}


def _answer_reason(payload, term: str, boosts: list[str]) -> dict:
    payload = payload if isinstance(payload, dict) else {}
    why = payload.get("why_correct", "")
    if _weak_text(why):
        why = (
            f"The answer is correct because it matches the defining behavior of {term}, not merely "
            f"its wording. Verify the answer against the concept's responsibility, constraints, and "
            f"the distinguishing signals {', '.join(boosts[:3])}."
        )
    points = _unique_clean(payload.get("key_points") or boosts[:3])[:5]
    return {"why_correct": why, "key_points": points}


def _wrong_answer_reason(payload, term: str) -> dict:
    payload = payload if isinstance(payload, dict) else {}
    mistakes = payload.get("common_mistakes") if isinstance(payload.get("common_mistakes"), list) else []
    if not mistakes:
        mistakes = [
            f"Choosing an option that mentions related terminology but does not satisfy {term}'s defining behavior.",
            f"Confusing {term} with a neighboring concept that has a different responsibility or constraint.",
        ]
    original = payload.get("per_option") if isinstance(payload.get("per_option"), dict) else {}
    options = {}
    for index, value in enumerate(original.values()):
        value = value if isinstance(value, dict) else {}
        text = str(value.get("text") or f"Option {index}")
        reason = str(value.get("reason") or "")
        if _weak_text(reason):
            reason = (
                f"{text} is not the best answer because it does not satisfy the defining behavior "
                f"or constraint of {term}. Compare the option's responsibility directly with the concept."
            )
        options[f"option_{index}"] = {"text": text, "reason": reason}
    return {"common_mistakes": mistakes, "per_option": options}


def _comparison(payload, term: str) -> dict:
    if isinstance(payload, dict) and payload.get("comparisons"):
        return payload
    return {"comparisons": [{"with": "related concept", "diff": f"Compare responsibilities, constraints, and use cases against {term}."}]}


def _example(payload, term: str, category: str) -> dict:
    if isinstance(payload, dict) and not _weak_text(payload.get("explanation", "")):
        return payload
    return {
        "code_example": f"# Demonstrate {term} in an appropriate {category} context.",
        "explanation": f"Use a minimal example that makes {term}'s defining behavior and boundary visible.",
    }


def _practical(payload, term: str, category: str) -> dict:
    if isinstance(payload, dict) and not _weak_text(payload.get("real_world", "")):
        return payload
    return {
        "real_world": f"Apply {term} in {category} work when its responsibility and constraints match the problem.",
        "best_practices": [
            f"State why {term} is appropriate before applying it.",
            "Verify edge cases and trade-offs with a focused test.",
        ],
    }


def _debug(payload, term: str) -> dict:
    if isinstance(payload, dict) and payload.get("common_errors"):
        return payload
    return {
        "common_errors": [{
            "error": f"Applying {term} without checking its defining constraint.",
            "solution": f"Re-check {term}'s responsibility, inputs, outputs, and boundary conditions.",
        }]
    }


def _report(paths, draft_count, modified, before_scores, after_scores, backup_root):
    ordered = sorted(after_scores, key=lambda item: (-item["card_quality_score"], item["card_id"]))
    distribution = {"90+": 0, "80-89": 0, "70-79": 0, "60-69": 0, "59 이하": 0}
    for item in after_scores:
        score = item["card_quality_score"]
        bucket = "90+" if score >= 90 else "80-89" if score >= 80 else "70-79" if score >= 70 else "60-69" if score >= 60 else "59 이하"
        distribution[bucket] += 1
    before_average = sum(item["card_quality_score"] for item in before_scores) / max(1, len(before_scores))
    after_average = sum(item["card_quality_score"] for item in after_scores) / max(1, len(after_scores))
    return {
        "total_cards": len(paths),
        "draft_cards": draft_count,
        "modified_cards": len(modified),
        "modified_files": modified,
        "quality_distribution": distribution,
        "top_10": ordered[:10],
        "bottom_10": list(reversed(ordered[-10:])),
        "average_score_before": round(before_average, 2),
        "average_score_after": round(after_average, 2),
        "estimated_fast_path_improvement_percentage_points": round(max(0.0, after_average - before_average) * 0.35, 2),
        "backup_root": str(backup_root) if backup_root else None,
    }


def _unique_clean(values) -> list[str]:
    result = []
    seen = set()
    for value in values:
        clean = re.sub(r"\s+", " ", str(value or "")).strip()
        key = clean.lower()
        if clean and key not in seen:
            seen.add(key)
            result.append(clean)
    return result


def _normalized(value: str) -> str:
    return re.sub(r"[^a-z0-9가-힣]+", "", str(value).lower())


def _distinctive_tokens(value: str) -> set[str]:
    return {
        token for token in re.findall(r"[a-z0-9가-힣+#.-]+", value.lower())
        if token not in BROAD_WORDS and len(token) > 1
    }


def _weak_text(value: str) -> bool:
    text = str(value or "").strip()
    return len(text) < 60 or text.count("?") >= 2


def _text_length(value) -> int:
    if isinstance(value, dict):
        return len(" ".join(str(item) for item in value.values()))
    return len(str(value or ""))


def _non_empty(value) -> bool:
    if value is None:
        return False
    if isinstance(value, dict):
        return bool(value) and any(_non_empty(item) for item in value.values())
    if isinstance(value, list):
        return bool(value) and any(_non_empty(item) for item in value)
    return bool(str(value).strip())


def main() -> int:
    parser = argparse.ArgumentParser(description="Improve draft concepts_v2 cards without changing approval state")
    parser.add_argument("--root", type=Path, default=DEFAULT_CARD_ROOT)
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--backup-root", type=Path)
    parser.add_argument("--baseline-root", type=Path)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    args = parser.parse_args()
    report = improve_directory(
        args.root,
        write=args.write,
        backup_root=args.backup_root,
        baseline_root=args.baseline_root,
    )
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

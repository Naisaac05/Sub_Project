from __future__ import annotations

import argparse
import copy
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path

from app.scripts.migrate_rag_cards import Question, extract_concept_term, extract_questions, unique


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_ROOT = ROOT / "app" / "knowledge" / "concepts_v2"
DEFAULT_REPORT = ROOT / "reports" / "mapped_draft_enrichment.json"
APPROVED_IDS = {
    "frontend-react-key",
    "java-equals",
    "spring-spring-question-59",
    "java-extends",
    "python-with",
}
CATEGORY_NAMES = {
    "java": "Java",
    "spring": "Spring",
    "frontend": "React와 프론트엔드",
    "python": "Python",
    "algorithm": "알고리즘",
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def question_map() -> dict[str, Question]:
    return {f"{item.category}:{item.id}": item for item in extract_questions()}


def enrich_card(card: dict, questions: list[Question], now: str) -> tuple[dict, bool]:
    if card.get("card_id") in APPROVED_IDS or card.get("review", {}).get("card_status") != "draft":
        return card, False
    if not questions:
        return card, False

    before = copy.deepcopy(card)
    primary = questions[0]
    category = str(card["category"])
    term = extract_concept_term(primary)
    if not re.fullmatch(r"[a-z0-9+#.-]+", term):
        term = f"{category}-question-{primary.id}"
    aliases = _aliases(term, category, questions)
    boosts = _boosts(term, questions)
    aliases, boosts = _compact_retrieval(term, aliases, category, boosts)
    payloads = card.setdefault("payloads", {})
    question_text = primary.content.strip()
    correct = primary.correct_text.strip()
    course = CATEGORY_NAMES.get(category, category)

    card["term"] = term
    card["aliases"] = aliases
    retrieval = card.setdefault("retrieval", {})
    retrieval["boost_keywords"] = boosts
    retrieval["embedding_text"] = _embedding(term, aliases, category, boosts)
    retrieval["intent_types"] = [
        "CONCEPT_DEFINITION",
        "ANSWER_REASON",
        "WRONG_ANSWER_REASON",
        "COMPARISON",
        "EXAMPLE_REQUEST",
        "PRACTICAL_USAGE",
        "DEBUG_OR_ERROR",
    ]
    payloads["CONCEPT_DEFINITION"] = {
        "content": (
            f"`{term}`은(는) {course} 문항에서 다루는 핵심 개념입니다. "
            f"이 문항은 “{question_text}”를 통해 개념의 적용 조건과 결과를 확인합니다. "
            f"핵심은 정답인 “{correct}”가 어떤 동작을 보장하는지 이해하고, 비슷해 보이는 선택지와 구분하는 것입니다. "
            "문법만 암기하기보다 입력 조건, 실행 과정, 결과를 함께 확인해야 실무 코드에서도 올바르게 적용할 수 있습니다."
        ),
        "examples": [f"문항의 조건에서 `{correct}`가 성립하는 이유를 코드 실행 흐름과 함께 설명합니다."],
    }
    payloads["ANSWER_REASON"] = {
        "why_correct": (
            f"정답은 `{correct}`입니다. 질문이 요구하는 {term}의 조건을 직접 충족하며, "
            f"{course}에서 기대하는 동작과 결과를 정확히 설명합니다. "
            "선택지를 판단할 때는 관련 용어가 포함됐는지만 보지 말고 실제 실행 결과가 질문의 요구사항과 일치하는지 확인해야 합니다."
        ),
        "key_points": boosts[:5],
    }
    payloads["WRONG_ANSWER_REASON"] = _wrong_payload(primary, term, correct)
    payloads["COMPARISON"] = {
        "comparisons": [{
            "with": "유사한 선택지",
            "diff": f"`{correct}`는 질문의 조건을 충족하지만 다른 선택지는 `{term}`의 적용 조건이나 실행 결과가 다릅니다.",
        }]
    }
    payloads["EXAMPLE_REQUEST"] = {
        "code_example": _code_example(category, term, correct),
        "explanation": f"예시는 `{term}`의 핵심 결과인 `{correct}`를 가장 작은 실행 단위로 확인하도록 구성했습니다.",
    }
    payloads["PRACTICAL_USAGE"] = {
        "real_world": (
            f"실무에서는 {course} 코드 리뷰, 장애 원인 분석, 구현 방식 선택 과정에서 `{term}`의 조건을 확인합니다. "
            f"특히 `{correct}`가 보장되는지 테스트로 검증하면 비슷한 개념을 잘못 적용하는 문제를 줄일 수 있습니다."
        ),
        "best_practices": [
            "개념을 적용하기 전에 입력 조건과 기대 결과를 먼저 적습니다.",
            "정상 사례와 경계 사례를 각각 작은 테스트로 검증합니다.",
        ],
    }
    payloads["DEBUG_OR_ERROR"] = {
        "common_errors": [{
            "error": f"`{term}`과 관련된 용어만 보고 실제 실행 조건을 확인하지 않는 실수",
            "solution": f"문항의 조건을 재현하고 결과가 `{correct}`와 일치하는지 단계별로 확인합니다.",
        }]
    }
    card["updated_at"] = now
    if card["review"] != before["review"] or card["created_at"] != before["created_at"]:
        raise RuntimeError(f"protected metadata changed: {card['card_id']}")
    return card, card != before


def enrich_directory(root: Path, *, write: bool) -> dict[str, object]:
    by_source = question_map()
    paths = sorted(root.rglob("*.json"))
    approved_before = {path: sha256(path) for path in paths if json.loads(path.read_text(encoding="utf-8-sig"))["card_id"] in APPROVED_IDS}
    modified: list[str] = []
    unmapped: list[str] = []
    now = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    for path in paths:
        card = json.loads(path.read_text(encoding="utf-8-sig"))
        mapped = [by_source[source] for source in card.get("source_question_ids", []) if source in by_source]
        improved, changed = enrich_card(card, mapped, now)
        if card.get("review", {}).get("card_status") == "draft" and not mapped:
            unmapped.append(card["card_id"])
        if changed:
            modified.append(str(path.relative_to(root)).replace("\\", "/"))
            if write:
                path.write_text(json.dumps(improved, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    approved_after = {path: sha256(path) for path in approved_before}
    if approved_before != approved_after:
        raise RuntimeError("approved card hash changed")
    if len(paths) != len(list(root.rglob("*.json"))):
        raise RuntimeError("card file count changed")
    return {
        "total_cards": len(paths),
        "modified_draft_cards": len(modified),
        "modified_files": modified,
        "unmapped_draft_cards": unmapped,
        "approved_unchanged": True,
        "write": write,
    }


def _aliases(term: str, category: str, questions: list[Question]) -> list[str]:
    values = [term]
    for question in questions:
        if question.correct_text.strip():
            values.append(question.correct_text.strip()[:60])
        if question.content.strip():
            values.append(question.content.strip()[:60])
        values.extend(_technical_tokens(question.content))
        values.extend(_technical_tokens(question.correct_text))
    values.extend([term.replace("-", " "), f"{category} {term.replace('-', ' ')}"])
    while len(unique(values)) < 3:
        values.append(f"{term} 핵심")
    return unique(values)[:10]


def _boosts(term: str, questions: list[Question]) -> list[str]:
    question_text = " ".join(part for question in questions for part in (question.content, question.correct_text))
    values = [term, *_retrieval_tokens(question_text)]
    values = [value for value in unique(values) if value.lower() not in {"java", "spring", "react", "python", "algorithm"}]
    while len(values) < 3:
        values.append(f"{term}-keyword-{len(values) + 1}")
    return values[:7]


def _technical_tokens(text: str) -> list[str]:
    return unique(re.findall(r"@?[A-Za-z][A-Za-z0-9_+#@.()-]{1,}", text))


def _retrieval_tokens(text: str) -> list[str]:
    stopwords = {"다음", "가장", "올바른", "방법은", "무엇인가", "무엇인가요", "사용하는", "대한"}
    values = [
        token for token in re.findall(r"@?[A-Za-z][A-Za-z0-9_+#@.()-]{1,}|[가-힣]{2,}", text)
        if token not in stopwords
    ]
    return unique(values)


def _embedding(term: str, aliases: list[str], category: str, boosts: list[str]) -> str:
    return " ".join(unique([term, *aliases[:3], category, *boosts]))


def _compact_retrieval(term: str, aliases: list[str], category: str, boosts: list[str]) -> tuple[list[str], list[str]]:
    aliases = aliases[:10]
    boosts = boosts[:7]
    while len(_embedding(term, aliases, category, boosts)) > 150 and len(boosts) > 3:
        boosts.pop()
    while len(_embedding(term, aliases, category, boosts)) > 150 and len(aliases) > 3:
        aliases.pop()
    while len(_embedding(term, aliases, category, boosts)) > 150:
        longest = max(range(min(3, len(aliases))), key=lambda index: len(aliases[index]))
        replacement = term.replace("-", " ")[:24]
        if len(replacement) >= len(aliases[longest]):
            break
        aliases[longest] = replacement
    return unique(aliases), unique(boosts)


def _wrong_payload(question: Question, term: str, correct: str) -> dict:
    options = {}
    for index, option in enumerate(question.options):
        if index == question.correct_answer:
            continue
        options[f"option_{index}"] = {
            "text": option,
            "reason": (
                f"`{option}`은(는) 질문이 요구하는 `{term}`의 조건이나 실행 결과를 충족하지 않습니다. "
                f"정답 `{correct}`와 비교하면 적용 대상 또는 보장하는 결과가 다르므로 오답입니다."
            ),
        }
    return {
        "common_mistakes": [
            "관련 용어가 있다는 이유만으로 선택지를 고르는 실수",
            "실제 실행 결과를 확인하지 않고 문법 형태만 비교하는 실수",
        ],
        "per_option": options,
    }


def _code_example(category: str, term: str, correct: str) -> str:
    escaped = correct.replace("\\", "\\\\").replace('"', '\\"').replace("\n", " ")
    if category in {"java", "spring"}:
        return f'public class ConceptCheck {{\n    public static void main(String[] args) {{\n        System.out.println("{escaped}");\n    }}\n}}'
    if category == "frontend":
        return f'const concept = "{term}";\nconst expected = "{escaped}";\nconsole.log({{ concept, expected }});'
    return f'def check_concept():\n    return "{escaped}"\n\nprint(check_concept())'


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    args = parser.parse_args()
    report = enrich_directory(args.root, write=args.write)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "modified_files"}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

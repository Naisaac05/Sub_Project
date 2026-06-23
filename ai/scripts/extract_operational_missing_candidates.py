from __future__ import annotations

import argparse
from collections import defaultdict
from datetime import date
import json
import re
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.knowledge.review import write_candidate_jsonl


DEFAULT_OUTPUT = ROOT / "app" / "knowledge" / "candidates" / "operational_missing_candidates.jsonl"
SAFE_MISSING_ROUTE = "grounded_fallback_safe_response"


def load_operational_shadow_report(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def extract_missing_candidates(
    report: dict[str, Any],
    *,
    min_occurrences: int = 2,
    require_production_validated: bool = True,
) -> list[dict[str, Any]]:
    if require_production_validated and not report.get("production_traffic_validated"):
        return []

    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in report.get("rows", []):
        if not _is_missing_row(row):
            continue
        key = _candidate_key(str(row.get("question", "")))
        if key:
            grouped[key].append(row)

    candidates = []
    for key, rows in sorted(grouped.items()):
        if len(rows) < min_occurrences:
            continue
        term, category = key.split("|", 1)
        questions = [str(row.get("question", "")).strip() for row in rows]
        candidates.append(
            {
                "term": term,
                "category": category,
                "aliases": _aliases_for(term, category, questions),
                "source_question_ids": [str(row.get("id") or f"operational-shadow-{idx}") for idx, row in enumerate(rows, 1)],
                "source": "operational_shadow_missing",
                "source_report_date": str(report.get("evaluation_date") or date.today().isoformat()),
                "occurrences": len(rows),
                "questions": questions,
                "definition_status": "needs_human_review",
                "approved": False,
                "review_status": "pending",
                "rejected_reason": "",
            }
        )
    return candidates


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Extract repeated operational shadow missing questions as RAG card candidates."
    )
    parser.add_argument("--report", required=True, type=Path, help="Operational shadow JSON report.")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--min-occurrences", type=int, default=2)
    parser.add_argument(
        "--allow-local",
        action="store_true",
        help="Allow extraction from non-production-validated reports for dry-runs only.",
    )
    args = parser.parse_args(argv)

    report = load_operational_shadow_report(args.report)
    candidates = extract_missing_candidates(
        report,
        min_occurrences=args.min_occurrences,
        require_production_validated=not args.allow_local,
    )
    write_candidate_jsonl(candidates, args.output)
    result = {
        "input": str(args.report),
        "output": str(args.output),
        "min_occurrences": args.min_occurrences,
        "production_traffic_validated": bool(report.get("production_traffic_validated")),
        "allow_local": args.allow_local,
        "candidate_count": len(candidates),
        "candidate_terms": [candidate["term"] for candidate in candidates],
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


def _is_missing_row(row: dict[str, Any]) -> bool:
    return (
        row.get("route") == SAFE_MISSING_ROUTE
        and row.get("expected_route") == SAFE_MISSING_ROUTE
    )


def _candidate_key(question: str) -> str:
    term = _extract_term(question)
    if not term:
        return ""
    return f"{term}|{_infer_category(question, term)}"


def _extract_term(question: str) -> str:
    technical_phrases = re.findall(
        r"(?:[A-Z][A-Za-z0-9.+#]*(?:\s+[A-Z][A-Za-z0-9.+#]*)*)",
        question,
    )
    cleaned = [
        _strip_language_prefix(phrase.strip())
        for phrase in technical_phrases
        if phrase.strip().lower() not in {"java", "python", "react", "spring", "next.js"}
    ]
    if not cleaned:
        return ""
    return max(cleaned, key=lambda item: (len(item.split()), len(item))).replace(" .", ".")


def _infer_category(question: str, term: str) -> str:
    lowered = f"{question} {term}".lower()
    if "java" in lowered:
        return "java"
    if "python" in lowered:
        return "python"
    if "spring" in lowered:
        return "spring"
    if "react" in lowered or "next.js" in lowered or "javascript" in lowered:
        return "frontend"
    return "general"


def _strip_language_prefix(phrase: str) -> str:
    return re.sub(r"^(?:Java|Python|React|Spring|Next\.js)\s+", "", phrase).strip()


def _aliases_for(term: str, category: str, questions: list[str]) -> list[str]:
    aliases = [term, f"{category} {term}" if category != "general" else term]
    for question in questions:
        if term.lower() in question.lower():
            aliases.append(question.strip().rstrip("?!."))
    seen = set()
    unique = []
    for alias in aliases:
        key = alias.lower()
        if alias and key not in seen:
            seen.add(key)
            unique.append(alias)
    return unique


if __name__ == "__main__":
    raise SystemExit(main())

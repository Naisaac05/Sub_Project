from __future__ import annotations

import json
import os
import sys
from datetime import date
from pathlib import Path
from typing import Callable


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.observability import emit_ollama_fallback_log
from app.workflow.intent import classify_free_question_rule_based
from app.workflow.v2_approved_fast_path import resolve_v2_approved_fast_path


CASES = [
    {"id": "java-equals", "question": "Java equals가 뭐야?", "expected_card_id": "java-equals"},
    {"id": "java-arraylist", "question": "Java ArrayList가 뭐야?", "expected_card_id": "java-arraylist"},
    {"id": "spring-valid", "question": "Spring Valid가 뭐야?", "expected_card_id": "spring-valid"},
    {"id": "spring-aop", "question": "Spring AOP가 뭐야?", "expected_card_id": "spring-aop"},
    {"id": "frontend-key", "question": "React key가 뭐야?", "expected_card_id": "frontend-react-key"},
    {"id": "frontend-usestate", "question": "React useState가 뭐야?", "expected_card_id": "frontend-usestate"},
    {"id": "python-range", "question": "Python range가 뭐야?", "expected_card_id": "python-range"},
    {"id": "python-dictionary", "question": "Python dictionary가 뭐야?", "expected_card_id": "python-dictionary"},
    {"id": "algorithm-stack", "question": "stack이 뭐야?", "expected_card_id": "algorithm-stack"},
    {"id": "approved-miss", "question": "Java CopyOnWriteArrayList가 뭐야?", "expected_card_id": None},
]


def evaluate_shadow_cases(cases: list[dict], *, resolver: Callable[[str], dict]) -> dict[str, object]:
    rows = []
    for case in cases:
        decision = resolver(case["question"])
        expected = case.get("expected_card_id")
        relevant = bool(expected and decision.get("hit") and decision.get("card_id") == expected)
        irrelevant_hit = bool(decision.get("hit") and decision.get("card_id") != expected)
        rows.append({
            "id": case["id"],
            "question": case["question"],
            "expected_card_id": expected,
            "top1_relevant": relevant,
            "irrelevant_hit": irrelevant_hit,
            "fallback_expected": not bool(decision.get("hit")),
            "decision": decision,
        })
    total = len(rows) or 1
    expected_rows = [row for row in rows if row["expected_card_id"]]
    return {
        "case_count": len(rows),
        "shadow_mode_rate": sum(row["decision"].get("mode") == "shadow" for row in rows) / total,
        "top1_relevance_rate": sum(row["top1_relevant"] for row in expected_rows) / max(1, len(expected_rows)),
        "fast_path_hit_rate": sum(bool(row["decision"].get("hit")) for row in rows) / total,
        "fallback_rate": sum(row["fallback_expected"] for row in rows) / total,
        "irrelevant_hit_count": sum(row["irrelevant_hit"] for row in rows),
        "rows": rows,
    }


def _resolve(question: str) -> dict:
    intent = classify_free_question_rule_based(question)
    return resolve_v2_approved_fast_path(question, intent, random_value=0.0).metadata()


def main() -> int:
    os.environ["SHADOW_MODE"] = "true"
    os.environ["AI_REVIEW_V2_APPROVED_FAST_PATH_ENABLED"] = "true"
    report = evaluate_shadow_cases(CASES, resolver=_resolve)
    routing_gate_passed = bool(
        report["shadow_mode_rate"] == 1.0
        and report["top1_relevance_rate"] >= 0.95
        and report["fast_path_hit_rate"] >= 0.90
        and report["fallback_rate"] <= 0.10
        and report["irrelevant_hit_count"] == 0
    )
    live_path = ROOT / "reports" / f"live_ollama_fallback_{date.today().isoformat()}.json"
    live = json.loads(live_path.read_text(encoding="utf-8")) if live_path.exists() else {}
    live_gate_passed = bool(
        live.get("gate_case_count")
        and live.get("gate_case_count") == live.get("gate_passed_count")
    )
    report.update({
        "evaluation_date": date.today().isoformat(),
        "traffic_source": "local_synthetic",
        "routing_gate_passed": routing_gate_passed,
        "live_fallback_gate_passed": live_gate_passed,
        "overall_gate_passed": routing_gate_passed and live_gate_passed,
        "production_traffic_validated": False,
    })
    fallback_rows = [row for row in report["rows"] if row["fallback_expected"]]
    for row in fallback_rows:
        emit_ollama_fallback_log({
            "route": "ollama_fallback",
            "ollama_duration": 0,
            "fallback_reason": row["decision"].get("reason"),
            "v2_hit": False,
        })
    output = ROOT / "reports" / f"synthetic_shadow_traffic_{date.today().isoformat()}.json"
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "output": str(output),
        "routing_gate_passed": routing_gate_passed,
        "live_fallback_gate_passed": live_gate_passed,
        "overall_gate_passed": report["overall_gate_passed"],
        "metrics": {key: report[key] for key in ("shadow_mode_rate", "top1_relevance_rate", "fast_path_hit_rate", "fallback_rate", "irrelevant_hit_count")},
    }, ensure_ascii=False, indent=2))
    return 0 if routing_gate_passed else 1


if __name__ == "__main__":
    raise SystemExit(main())

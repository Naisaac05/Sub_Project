from __future__ import annotations

import json
import re
import sys
import time
from datetime import date
from pathlib import Path
from typing import Callable


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.ollama.client import DEFAULT_MODEL, call_ollama
from app.workflow.intent import classify_free_question_rule_based
from app.workflow.v2_approved_fast_path import resolve_v2_approved_fast_path


CASES = [
    {
        "id": "java-structured-task-scope",
        "question": "Java 21의 StructuredTaskScope는 무엇이며 실패한 하위 작업을 어떻게 처리해? Spring 기능과 구분해서 설명해줘.",
        "expected_terms": ["StructuredTaskScope", "작업", "fork", "join"],
        "forbidden_terms": ["Spring Framework에서 사용", "@Async"],
        "required_for_gate": False,
    },
    {
        "id": "react-use-optimistic",
        "question": "React 19의 useOptimistic은 어떤 상황에서 사용하고 실패하면 UI를 어떻게 복구해?",
        "expected_terms": ["useOptimistic", "상태", "낙관"],
        "forbidden_terms": ["즉시 반영되지 않도록"],
        "required_for_gate": False,
    },
    {
        "id": "java-copy-on-write-array-list",
        "question": "Java CopyOnWriteArrayList의 동작 원리와 적합한 사용 조건을 설명해줘.",
        "expected_terms": ["CopyOnWriteArrayList", "복사", "읽기"],
        "forbidden_terms": [],
        "required_for_gate": True,
    },
    {
        "id": "react-error-boundary",
        "question": "React Error Boundary가 잡는 오류와 잡지 못하는 오류, 구현 방법을 설명해줘.",
        "expected_terms": ["Error Boundary", "오류", "class"],
        "forbidden_terms": ["이벤트 핸들러 오류도 자동으로 처리"],
        "required_for_gate": True,
    },
]


def assess_answer(
    answer: str,
    *,
    expected_terms: list[str],
    forbidden_terms: list[str] | None = None,
) -> dict[str, object]:
    normalized = answer.strip()
    lowered = normalized.lower()
    reasons = []
    if len(normalized) < 80:
        reasons.append("answer_too_short")
    if not re.search(r"[가-힣]", normalized):
        reasons.append("missing_korean")
    if not all(term.lower() in lowered for term in expected_terms):
        reasons.append("missing_topic_anchor")
    if any(term.lower() in lowered for term in (forbidden_terms or [])):
        reasons.append("forbidden_factual_marker")
    if normalized and normalized[-1] not in ".!?다요죠음함됨니다세요":
        reasons.append("incomplete_ending")
    if any(marker in lowered for marker in ("모르겠습니다", "확인할 수 없습니다", "i don't know")):
        reasons.append("non_answer")
    return {
        "passed": not reasons,
        "reasons": reasons,
        "length": len(normalized),
        "has_korean": bool(re.search(r"[가-힣]", normalized)),
    }


def evaluate_cases(
    cases: list[dict],
    *,
    resolver: Callable[[str], dict],
    caller: Callable[[str], str],
) -> dict[str, object]:
    rows = []
    for case in cases:
        decision = resolver(case["question"])
        if decision.get("hit"):
            rows.append({"id": case["id"], "route": "fast_path", "decision": decision})
            continue
        prompt = (
            "다음 개발 질문에 한국어 4~6문장, 500자 이내로 답하세요. 제목과 목록은 쓰지 말고 "
            "핵심 정의, 동작 원리, 사용 조건, 실패 시 주의점을 완결된 문장으로 설명하세요. "
            f"질문의 기술과 다른 프레임워크를 혼동하지 마세요.\n\n질문: {case['question']}"
        )
        started = time.perf_counter()
        try:
            answer = caller(prompt).strip()
            error = None
            quality = assess_answer(
                answer,
                expected_terms=case["expected_terms"],
                forbidden_terms=case.get("forbidden_terms"),
            )
        except Exception as exc:
            answer = ""
            error = f"{type(exc).__name__}: {exc}"
            quality = {"passed": False, "reasons": ["ollama_error"], "length": 0, "has_korean": False}
        rows.append({
            "id": case["id"],
            "question": case["question"],
            "route": "ollama_fallback",
            "decision": decision,
            "latency_ms": round((time.perf_counter() - started) * 1000, 1),
            "answer": answer,
            "error": error,
            "quality": quality,
            "required_for_gate": bool(case.get("required_for_gate", True)),
        })
    fallbacks = [row for row in rows if row["route"] == "ollama_fallback"]
    gate_rows = [row for row in fallbacks if row.get("required_for_gate", True)]
    return {
        "case_count": len(rows),
        "fallback_invocation_count": len(fallbacks),
        "fallback_passed_count": sum(bool(row["quality"]["passed"]) for row in fallbacks),
        "gate_case_count": len(gate_rows),
        "gate_passed_count": sum(bool(row["quality"]["passed"]) for row in gate_rows),
        "rows": rows,
    }


def _resolve(question: str) -> dict:
    intent = classify_free_question_rule_based(question)
    return resolve_v2_approved_fast_path(question, intent, random_value=1.0).metadata()


def _call(prompt: str) -> str:
    return call_ollama(
        model=DEFAULT_MODEL,
        prompt=prompt,
        temperature=0.1,
        max_tokens=256,
        num_ctx=1024,
        num_thread=4,
    )


def main() -> int:
    report = evaluate_cases(CASES, resolver=_resolve, caller=_call)
    report.update({
        "evaluation_date": date.today().isoformat(),
        "model": DEFAULT_MODEL,
        "model_download_performed": False,
        "live_ollama": True,
    })
    output = ROOT / "reports" / f"live_ollama_fallback_{date.today().isoformat()}.json"
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "output": str(output),
        "model": DEFAULT_MODEL,
        "fallback_invocation_count": report["fallback_invocation_count"],
        "fallback_passed_count": report["fallback_passed_count"],
        "gate_case_count": report["gate_case_count"],
        "gate_passed_count": report["gate_passed_count"],
    }, ensure_ascii=False, indent=2))
    return 0 if report["gate_case_count"] and report["gate_case_count"] == report["gate_passed_count"] else 1


if __name__ == "__main__":
    raise SystemExit(main())

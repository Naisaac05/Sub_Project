from __future__ import annotations

import argparse
from contextlib import ExitStack
import json
import os
from pathlib import Path
import sys
import time
from typing import Any, Callable
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.schemas import AiGenerateRequest, AiGenerateResponse
from app.workflow.answer_cache import clear_answer_cache as clear_workflow_answer_cache
from app.workflow.judge import SemanticJudgeResult, clear_answer_cache as clear_judge_cache
from app.workflow.runner import run_review_workflow


DATASET_PATH = ROOT / "evals" / "exaone_live_e2e" / "dataset.jsonl"
REPORT_JSON_PATH = ROOT / "evals" / "exaone_live_e2e" / "REPORT.json"
REPORT_MARKDOWN_PATH = ROOT / "evals" / "exaone_live_e2e" / "REPORT.md"
MODES = ("rag", "no_rag_forced")


def load_dataset(path: Path = DATASET_PATH, limit: int | None = None) -> list[dict[str, Any]]:
    rows = [
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    return rows if limit is None else rows[: max(0, limit)]


def evaluate_response(
    row: dict[str, Any],
    mode: str,
    response: AiGenerateResponse,
) -> dict[str, Any]:
    answer = response.answer or ""
    answer_lower = answer.lower()
    required_keywords = [str(item) for item in row.get("required_keywords", [])]
    forbidden_claims = [str(item) for item in row.get("forbidden_claims", [])]
    judge_event = next(
        (
            event
            for event in response.observability_events
            if event.get("event") == "ai_review.semantic_judge_evaluated"
        ),
        {},
    )
    return {
        "id": str(row.get("id", "")),
        "question": str(row.get("question", "")),
        "mode": mode,
        "answer": answer,
        "model_used": response.model_used or "",
        "route": response.route or "",
        "retrieved_concept_ids": list(response.retrieved_concept_ids),
        "latency_ms": int(response.latency_ms or 0),
        "fallback_used": bool(response.fallback_used),
        "quality_flags": list(response.quality_flags),
        "required_keywords": required_keywords,
        "missing_required_keywords": [
            keyword for keyword in required_keywords if keyword.lower() not in answer_lower
        ],
        "required_keywords_passed": all(
            keyword.lower() in answer_lower for keyword in required_keywords
        ),
        "forbidden_claims": forbidden_claims,
        "present_forbidden_claims": [
            claim for claim in forbidden_claims if claim.lower() in answer_lower
        ],
        "forbidden_claims_absent": all(
            claim.lower() not in answer_lower for claim in forbidden_claims
        ),
        "human_review_required": bool(row.get("human_review_required", True)),
        "intent": str(judge_event.get("intent", "")),
        "sub_intent": str(judge_event.get("sub_intent", "")),
        "rag_policy": str(judge_event.get("rag_policy", "")),
        "judge_event": judge_event,
        "error": "",
    }


def run_case(
    row: dict[str, Any],
    mode: str,
    workflow_runner: Callable[[str, AiGenerateRequest], AiGenerateResponse] = run_review_workflow,
) -> dict[str, Any]:
    clear_workflow_answer_cache()
    clear_judge_cache()
    request = AiGenerateRequest(
        question=str(row.get("question", "")),
        user_answer=str(row.get("question", "")),
        model="exaone3.5:2.4b",
        temperature=0.2,
        max_tokens=256,
        num_ctx=1024,
        num_thread=4,
    )
    env = {
        "AI_REVIEW_RAG_RETRIEVER": "bge",
        "AI_REVIEW_EMBEDDING_MODEL": "bge-m3",
        "AI_REVIEW_BGE_MIN_SCORE": "0.50",
        "AI_REVIEW_ANSWER_CACHE_PATH": "",
    }
    try:
        with patch.dict(os.environ, env, clear=False):
            with ExitStack() as stack:
                stack.enter_context(
                    patch("app.workflow.nodes.resolve_lightweight_answer", return_value=None)
                )
                stack.enter_context(
                    patch("app.workflow.runner.resolve_lightweight_answer", return_value=None)
                )
                stack.enter_context(
                    patch("app.workflow.judge.judge_answer", side_effect=_disabled_semantic_judge)
                )
                if mode == "no_rag_forced":
                    stack.enter_context(patch("app.workflow.nodes.retrieve_context", return_value=[]))
                response = workflow_runner("free-question", request)
        return evaluate_response(row, mode, response)
    except Exception as exc:
        return {
            "id": str(row.get("id", "")),
            "question": str(row.get("question", "")),
            "mode": mode,
            "answer": "",
            "model_used": "",
            "route": "",
            "retrieved_concept_ids": [],
            "latency_ms": 0,
            "fallback_used": False,
            "quality_flags": [],
            "required_keywords_passed": False,
            "forbidden_claims_absent": False,
            "judge_event": {},
            "error": f"{type(exc).__name__}: {exc}",
        }


def _disabled_semantic_judge(*args: Any, **kwargs: Any) -> SemanticJudgeResult:
    return SemanticJudgeResult(
        relevance_score=1.0,
        context_bias_score=0.0,
        hallucination_risk="low",
        should_retry=False,
        reason="Skipped judge: disabled for live E2E evaluation",
    )


def summarize(rows: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    return {mode: _summarize_mode([row for row in rows if row["mode"] == mode]) for mode in MODES}


def _summarize_mode(rows: list[dict[str, Any]]) -> dict[str, Any]:
    total = len(rows)
    if not total:
        return {"total": 0}
    latencies = sorted(int(row.get("latency_ms", 0)) for row in rows if not row.get("error"))
    return {
        "total": total,
        "error_count": sum(bool(row.get("error")) for row in rows),
        "live_exaone_count": sum(_is_live_exaone(row.get("model_used", "")) for row in rows),
        "retrieval_rate": _rate(rows, lambda row: bool(row.get("retrieved_concept_ids"))),
        "generation_route_rate": _rate(rows, lambda row: "generation" in str(row.get("route", ""))),
        "fallback_rate": _rate(rows, lambda row: bool(row.get("fallback_used"))),
        "quality_flag_free_rate": _rate(rows, lambda row: not row.get("quality_flags")),
        "required_keyword_pass_rate": _rate(rows, lambda row: bool(row.get("required_keywords_passed"))),
        "forbidden_claim_absent_rate": _rate(rows, lambda row: bool(row.get("forbidden_claims_absent"))),
        "latency_p50_ms": _percentile(latencies, 0.50),
        "latency_p95_ms": _percentile(latencies, 0.95),
    }


def _rate(rows: list[dict[str, Any]], predicate: Callable[[dict[str, Any]], bool]) -> float:
    return round(sum(bool(predicate(row)) for row in rows) / len(rows), 4) if rows else 0.0


def _percentile(values: list[int], fraction: float) -> int:
    if not values:
        return 0
    index = round((len(values) - 1) * fraction)
    return int(values[index])


def _is_live_exaone(model_used: object) -> bool:
    model = str(model_used)
    return model.startswith("exaone3.5:2.4b") and not model.endswith(":cache")


def format_progress(
    completed: int,
    total: int,
    case_id: str,
    mode: str,
    duration_seconds: float,
    elapsed_seconds: float,
) -> str:
    percent = (completed / total * 100.0) if total else 100.0
    average_seconds = elapsed_seconds / completed if completed else 0.0
    eta_seconds = average_seconds * max(0, total - completed)
    return (
        f"[{completed}/{total} {percent:.1f}%] {case_id} / {mode} "
        f"took {duration_seconds:.1f}s | elapsed {_format_duration(elapsed_seconds)} "
        f"| ETA {_format_duration(eta_seconds)}"
    )


def _format_duration(seconds: float) -> str:
    total_seconds = max(0, round(seconds))
    minutes, remaining_seconds = divmod(total_seconds, 60)
    hours, minutes = divmod(minutes, 60)
    if hours:
        return f"{hours}h {minutes}m {remaining_seconds}s"
    if minutes:
        return f"{minutes}m {remaining_seconds}s"
    return f"{remaining_seconds}s"


def render_markdown(rows: list[dict[str, Any]], summary: dict[str, dict[str, Any]]) -> str:
    lines = [
        "# EXAONE Live E2E Quality Evaluation",
        "",
        "Semantic Judge is disabled for this live evaluation. Human review is required for factual quality.",
        "",
        "## Summary",
        "",
        "| mode | total | errors | live EXAONE | retrieval | keyword pass | forbidden absent | fallback | p50 ms | p95 ms |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for mode in MODES:
        item = summary[mode]
        lines.append(
            f"| {mode} | {item.get('total', 0)} | {item.get('error_count', 0)} | "
            f"{item.get('live_exaone_count', 0)} | {item.get('retrieval_rate', 0):.1%} | "
            f"{item.get('required_keyword_pass_rate', 0):.1%} | "
            f"{item.get('forbidden_claim_absent_rate', 0):.1%} | "
            f"{item.get('fallback_rate', 0):.1%} | {item.get('latency_p50_ms', 0)} | "
            f"{item.get('latency_p95_ms', 0)} |"
        )
    lines.extend(["", "## Human Review", ""])
    for row in rows:
        lines.extend(
            [
                f"### {row['id']} / {row['mode']}",
                "",
                f"- Question: {row['question']}",
                f"- Model: `{row.get('model_used', '')}`",
                f"- Route: `{row.get('route', '')}`",
                f"- Intent: `{row.get('intent', '')}/{row.get('sub_intent', '')}`",
                f"- RAG policy: `{row.get('rag_policy', '')}`",
                f"- Retrieved: `{', '.join(row.get('retrieved_concept_ids', [])) or 'none'}`",
                f"- Latency: `{row.get('latency_ms', 0)} ms`",
                f"- Fallback: `{row.get('fallback_used', False)}`",
                f"- Quality flags: `{', '.join(row.get('quality_flags', [])) or 'none'}`",
                f"- Required keywords passed: `{row.get('required_keywords_passed', False)}`",
                f"- Forbidden claims absent: `{row.get('forbidden_claims_absent', False)}`",
                f"- Error: `{row.get('error', '') or 'none'}`",
                "",
                row.get("answer", "") or "(no answer)",
                "",
            ]
        )
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", type=Path, default=DATASET_PATH)
    parser.add_argument("--limit", type=int)
    parser.add_argument("--json-out", type=Path, default=REPORT_JSON_PATH)
    parser.add_argument("--markdown-out", type=Path, default=REPORT_MARKDOWN_PATH)
    args = parser.parse_args()

    dataset = load_dataset(args.dataset, args.limit)
    results = []
    total_runs = len(dataset) * len(MODES)
    evaluation_started = time.perf_counter()
    completed = 0
    for row in dataset:
        for mode in MODES:
            case_started = time.perf_counter()
            print(
                f"[{completed + 1}/{total_runs}] running {row['id']} / {mode}",
                flush=True,
            )
            results.append(run_case(row, mode))
            completed += 1
            print(
                format_progress(
                    completed=completed,
                    total=total_runs,
                    case_id=str(row["id"]),
                    mode=mode,
                    duration_seconds=time.perf_counter() - case_started,
                    elapsed_seconds=time.perf_counter() - evaluation_started,
                ),
                flush=True,
            )

    summary = summarize(results)
    payload = {"summary": summary, "results": results}
    args.json_out.parent.mkdir(parents=True, exist_ok=True)
    args.json_out.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    args.markdown_out.write_text(render_markdown(results, summary), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print(f"json report -> {args.json_out}")
    print(f"markdown report -> {args.markdown_out}")

    error_count = sum(item.get("error_count", 0) for item in summary.values())
    live_exaone_count = sum(item.get("live_exaone_count", 0) for item in summary.values())
    rag_retrieval_count = sum(
        bool(row.get("retrieved_concept_ids")) for row in results if row["mode"] == "rag"
    )
    return 0 if not error_count and live_exaone_count and rag_retrieval_count else 1


if __name__ == "__main__":
    raise SystemExit(main())

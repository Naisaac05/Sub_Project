from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable


ROOT = Path(__file__).resolve().parents[1]
PROJECT_ROOT = ROOT.parent
DEFAULT_OUTPUT = ROOT / "release-gate-report.json"


@dataclass(frozen=True)
class Check:
    name: str
    command: list[str]
    cwd: Path
    kind: str = "command"
    required: bool = True
    description: str = ""


Runner = Callable[[Check], tuple[int, str, str]]


def default_checks() -> list[Check]:
    python = str(ROOT / ".venv" / "Scripts" / "python.exe")
    gradlew = str(PROJECT_ROOT / "backend" / "gradlew.bat")
    ai_cwd = ROOT
    backend_cwd = PROJECT_ROOT / "backend"
    return [
        Check(
            name="python_workflow",
            command=[
                python,
                "-m",
                "unittest",
                "tests.test_answer_cache",
                "tests.test_workflow_runner",
                "tests.test_workflow_degraded_modes",
            ],
            cwd=ai_cwd,
            description="Answer cache, workflow, and degraded-mode smoke tests.",
        ),
        Check(
            name="streaming_cancellation",
            command=[python, "-m", "unittest", "tests.test_stream", "tests.test_stream_cancellation"],
            cwd=ai_cwd,
            description="FastAPI streaming and cancellation smoke tests.",
        ),
        Check(
            name="gateway_model_pool",
            command=[python, "-m", "unittest", "tests.test_ollama_gateway", "tests.test_ollama_client"],
            cwd=ai_cwd,
            description="Ollama gateway/model-pool routing and capacity smoke tests.",
        ),
        Check(
            name="semantic_and_promotion_eval",
            command=[
                python,
                "-m",
                "unittest",
                "tests.test_semantic_evaluation",
                "tests.test_lightweight_evaluator",
                "tests.test_promotion_workflow",
            ],
            cwd=ai_cwd,
            description="Semantic evaluation and promotion workflow smoke tests.",
        ),
        Check(
            name="rag_evaluator",
            command=[python, "scripts/evaluate_lightweight_rag.py"],
            cwd=ai_cwd,
            kind="rag_eval",
            description="Deterministic 200-row RAG evaluator gate.",
        ),
        Check(
            name="python_production_config",
            command=[python, "-m", "unittest", "tests.test_production_config"],
            cwd=ai_cwd,
            description="Python production config validation smoke test.",
        ),
        Check(
            name="spring_production_config",
            command=[gradlew, "test", "--tests", "*AiReviewProductionConfigValidatorTest"],
            cwd=backend_cwd,
            description="Spring production config validation smoke test.",
        ),
    ]


def run_release_gate(checks: list[Check], runner: Runner | None = None) -> dict[str, object]:
    command_runner = runner or run_command
    results = [_run_check(check, command_runner) for check in checks]
    decision = evaluate_release_gate(results)
    failed_required = sum(1 for result in results if result["required"] and not result["passed"])
    return {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "decision": decision,
        "failed_required_checks": failed_required,
        "failed_optional_checks": sum(1 for result in results if not result["required"] and not result["passed"]),
        "checks": results,
    }


def evaluate_release_gate(checks: list[dict[str, object]]) -> str:
    if any(check.get("required", True) and not check.get("passed", False) for check in checks):
        return "NO-GO"
    if any(not check.get("required", True) and not check.get("passed", False) for check in checks):
        return "CONDITIONAL-GO"
    return "GO"


def run_command(check: Check) -> tuple[int, str, str]:
    completed = subprocess.run(
        check.command,
        cwd=check.cwd,
        capture_output=True,
        text=True,
        check=False,
    )
    return completed.returncode, completed.stdout, completed.stderr


def _run_check(check: Check, runner: Runner) -> dict[str, object]:
    started = time.perf_counter()
    try:
        return_code, stdout, stderr = runner(check)
    except Exception as exc:
        return_code, stdout, stderr = 1, "", f"{type(exc).__name__}: {exc}"
    duration_ms = int((time.perf_counter() - started) * 1000)
    passed, failure_reason, parsed = _check_passed(check, return_code, stdout)
    return {
        "name": check.name,
        "description": check.description,
        "required": check.required,
        "kind": check.kind,
        "cwd": str(check.cwd),
        "command": check.command,
        "return_code": return_code,
        "duration_ms": duration_ms,
        "passed": passed,
        "failure_reason": failure_reason,
        "stdout": stdout,
        "stderr": stderr,
        "parsed": parsed,
    }


def _check_passed(check: Check, return_code: int, stdout: str) -> tuple[bool, str, dict[str, object] | None]:
    if return_code != 0:
        return False, f"command exited with {return_code}", None
    if check.kind != "rag_eval":
        return True, "", None
    try:
        payload = json.loads(stdout)
    except json.JSONDecodeError as exc:
        return False, f"rag evaluator output is not JSON: {exc}", None
    failures = []
    if int(payload.get("total", 0)) < 200:
        failures.append("total >= 200")
    if float(payload.get("retrieval_hit_rate", 0.0)) < 0.6:
        failures.append("retrieval_hit_rate >= 0.6")
    if float(payload.get("rag_policy_accuracy", 0.0)) < 0.8:
        failures.append("rag_policy_accuracy >= 0.8")
    if float(payload.get("workflow_context_accuracy", 0.0)) < 1.0:
        failures.append("workflow_context_accuracy == 1.0")
    if "answer_grounding_rate" not in payload:
        failures.append("answer_grounding_rate present")
    if failures:
        return False, "; ".join(failures), payload
    return True, "", payload


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run AI Review release gate smoke checks.")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT, help="JSON report output path.")
    parser.add_argument("--dry-run", action="store_true", help="Write a GO report without executing commands.")
    args = parser.parse_args(argv)

    checks = default_checks()
    runner = _dry_run_runner if args.dry_run else None
    report = run_release_gate(checks, runner=runner)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"decision": report["decision"], "output": str(args.output)}, ensure_ascii=False))
    return 0 if report["decision"] in {"GO", "CONDITIONAL-GO"} else 1


def _dry_run_runner(check: Check) -> tuple[int, str, str]:
    if check.kind == "rag_eval":
        return (
            0,
            json.dumps(
                {
                    "total": 200,
                    "retrieval_hit_rate": 1.0,
                    "rag_policy_accuracy": 1.0,
                    "workflow_context_accuracy": 1.0,
                    "answer_grounding_rate": 1.0,
                }
            ),
            "",
        )
    return 0, "DRY-RUN OK", ""


if __name__ == "__main__":
    raise SystemExit(main())

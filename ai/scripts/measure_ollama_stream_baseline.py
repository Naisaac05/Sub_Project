from __future__ import annotations

import argparse
import asyncio
import ctypes
import hashlib
import json
import os
import platform
import subprocess
import sys
import tempfile
import time
from datetime import datetime
from pathlib import Path
from typing import Any, Awaitable, Callable
from urllib.request import urlopen

AI_DIR = Path(__file__).resolve().parents[1]
if str(AI_DIR) not in sys.path:
    sys.path.insert(0, str(AI_DIR))

from app.schemas import AiGenerateRequest, AiGenerateResponse
from app.workflow.runner import run_review_workflow_stream


Clock = Callable[[], float]
StreamRunner = Callable[[str, AiGenerateRequest], Any]


DEFAULT_PROMPT = (
    "Explain quorum read and quorum write in Korean, in exactly two short sentences."
)


async def measure_stream_once(
    *,
    mode: str,
    request: AiGenerateRequest,
    run_index: int,
    stream_runner: StreamRunner = run_review_workflow_stream,
    clock: Clock = time.perf_counter,
) -> dict[str, Any]:
    started_at = clock()
    first_token_at: float | None = None
    chunk_count = 0
    response_chars = 0
    done_response: AiGenerateResponse | None = None
    status = "partial_failed"
    error = ""

    try:
        async for event in stream_runner(mode, request):
            event_type = event.get("type")
            if event_type == "chunk":
                if first_token_at is None:
                    first_token_at = clock()
                chunk = str(event.get("chunk", ""))
                chunk_count += 1
                response_chars += len(chunk)
            elif event_type == "done":
                clock()
                done_response = event.get("response")
                status = "completed"
    except Exception as exc:
        error = f"{type(exc).__name__}: {exc}"

    finished_at = clock()
    first_token_latency_ms = (
        int((first_token_at - started_at) * 1000) if first_token_at is not None else None
    )
    stream_duration_ms = int((finished_at - started_at) * 1000)

    return {
        "run": run_index,
        "status": status,
        "first_token_latency_ms": first_token_latency_ms,
        "stream_duration_ms": stream_duration_ms,
        "chunk_count": chunk_count,
        "response_chars": response_chars,
        "model_used": getattr(done_response, "model_used", "") if done_response else "",
        "route": getattr(done_response, "route", "") if done_response else "",
        "fallback_used": bool(getattr(done_response, "fallback_used", False)) if done_response else False,
        "quality_flags": getattr(done_response, "quality_flags", []) if done_response else [],
        "prompt_version": getattr(done_response, "prompt_version", "") if done_response else "",
        "latency_ms": getattr(done_response, "latency_ms", None) if done_response else None,
        "error": error,
    }


def summarize_samples(samples: list[dict[str, Any]]) -> dict[str, Any]:
    completed = [sample for sample in samples if sample["status"] == "completed"]
    first_tokens = [
        sample["first_token_latency_ms"]
        for sample in completed
        if sample["first_token_latency_ms"] is not None
    ]
    durations = [sample["stream_duration_ms"] for sample in samples]
    return {
        "stream_completed": len(completed),
        "stream_disconnected": sum(1 for sample in samples if sample["status"] == "disconnected"),
        "stream_partial_failed": sum(1 for sample in samples if sample["status"] == "partial_failed"),
        "fallback_to_sync_count": 0,
        "first_token_latency_ms": _stats(first_tokens),
        "stream_duration_ms": _stats(durations),
    }


def validate_llm_required(samples: list[dict[str, Any]]) -> None:
    non_llm_samples = [
        sample
        for sample in samples
        if sample.get("status") == "completed"
        and (
            not sample.get("model_used")
            or str(sample.get("model_used")) in {"template", "lightweight-template"}
            or str(sample.get("model_used")).endswith(":cache")
        )
    ]
    if non_llm_samples:
        routes = ", ".join(
            f"run {sample.get('run')} route={sample.get('route')} model={sample.get('model_used')}"
            for sample in non_llm_samples
        )
        raise RuntimeError(f"Baseline did not reach Ollama generation: {routes}")


def render_markdown_report(
    *,
    samples: list[dict[str, Any]],
    environment: dict[str, str],
    command: str,
) -> str:
    summary = summarize_samples(samples)
    lines = [
        "# AI Ollama Streaming Baseline",
        "",
        f"- measured_at: {datetime.now().isoformat(timespec='seconds')}",
        f"- command: `{command}`",
        "",
        "## Environment",
        "",
    ]
    for key, value in environment.items():
        lines.append(f"- {key}: {value}")

    lines.extend(
        [
            "",
            "## Summary",
            "",
            f"- stream_completed: {summary['stream_completed']}",
            f"- stream_disconnected: {summary['stream_disconnected']}",
            f"- stream_partial_failed: {summary['stream_partial_failed']}",
            f"- fallback_to_sync_count: {summary['fallback_to_sync_count']}",
            f"- first_token_latency_ms: {_format_stats(summary['first_token_latency_ms'])}",
            f"- stream_duration_ms: {_format_stats(summary['stream_duration_ms'])}",
            "",
            "## Samples",
            "",
            "| run | status | first_token_latency_ms | stream_duration_ms | chunks | chars | model | route | fallback | quality_flags | error |",
            "| --- | --- | ---: | ---: | ---: | ---: | --- | --- | --- | --- | --- |",
        ]
    )

    for sample in samples:
        lines.append(
            "| {run} | {status} | {first} | {duration} | {chunks} | {chars} | {model} | {route} | {fallback} | {flags} | {error} |".format(
                run=sample["run"],
                status=sample["status"],
                first=_cell(sample["first_token_latency_ms"]),
                duration=_cell(sample["stream_duration_ms"]),
                chunks=sample["chunk_count"],
                chars=sample["response_chars"],
                model=_cell(sample["model_used"]),
                route=_cell(sample["route"]),
                fallback=str(sample["fallback_used"]).lower(),
                flags=", ".join(sample["quality_flags"]) if sample["quality_flags"] else "",
                error=_cell(sample["error"]),
            )
        )

    lines.extend(
        [
            "",
            "## Notes",
            "",
            "- This baseline measures the Python streaming workflow with real Ollama generation.",
            "- `fallback_to_sync_count` is fixed at 0 here because Spring synchronous fallback is outside this script.",
            "- Default runtime cache and candidate outputs are isolated under the system temp directory for this run.",
        ]
    )
    return "\n".join(lines) + "\n"


def collect_environment(*, model: str, prompt: str) -> dict[str, str]:
    manifest_path = Path(__file__).resolve().parents[1] / "app" / "vectorstore" / "index_manifest.json"

    return {
        "os": platform.platform(),
        "cpu": _cpu_name(),
        "ram": _ram_gb(),
        "python": platform.python_version(),
        "ollama_base_url": os.getenv("OLLAMA_BASE_URL", "http://localhost:11434"),
        "ollama_version": _ollama_version(),
        "model": model,
        "prompt": prompt,
        "knowledge_version": knowledge_version_for_manifest(manifest_path),
    }


def knowledge_version_for_manifest(manifest_path: Path) -> str:
    if not manifest_path.exists():
        return "missing"
    try:
        raw = manifest_path.read_bytes()
        manifest = json.loads(raw.decode("utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError):
        return "unreadable"
    digest = hashlib.sha256(raw).hexdigest()[:12]
    entries = manifest.get("entries", {})
    entry_count = len(entries) if isinstance(entries, dict) else 0
    return f"version={manifest.get('version', 'unknown')} entries={entry_count} sha256={digest}"


async def run_baseline(args: argparse.Namespace) -> Path:
    _isolate_runtime_outputs()
    samples = []
    for run_index in range(1, args.runs + 1):
        prompt = args.question
        if args.cache_mode == "miss":
            prompt = f"{args.question} [baseline-run:{run_index}-{int(time.time())}]"
        request = AiGenerateRequest(
            user_answer=prompt,
            model=args.model,
            temperature=args.temperature,
            max_tokens=args.max_tokens,
            num_ctx=args.num_ctx,
            num_thread=args.num_thread,
        )
        samples.append(
            await measure_stream_once(
                mode=args.mode,
                request=request,
                run_index=run_index,
            )
        )
        if args.cooldown_seconds > 0 and run_index < args.runs:
            await asyncio.sleep(args.cooldown_seconds)

    if args.require_llm:
        validate_llm_required(samples)

    environment = collect_environment(model=args.model, prompt=args.question)
    report = render_markdown_report(
        samples=samples,
        environment=environment,
        command=" ".join(args.raw_command),
    )
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(report, encoding="utf-8", newline="\n")
    return output_path


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    root = Path(__file__).resolve().parents[2]
    default_output = root / "docs" / "smoke" / f"{datetime.now().date()}-ai-ollama-streaming-baseline.md"
    parser = argparse.ArgumentParser(description="Measure real Ollama streaming baseline.")
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--mode", default="free-question")
    parser.add_argument("--question", default=DEFAULT_PROMPT)
    parser.add_argument("--model", default=os.getenv("PYTHON_AI_MODEL", "qwen3:1.7b"))
    parser.add_argument("--temperature", type=float, default=0.2)
    parser.add_argument("--max-tokens", type=int, default=128)
    parser.add_argument("--num-ctx", type=int, default=1024)
    parser.add_argument("--num-thread", type=int, default=4)
    parser.add_argument("--cooldown-seconds", type=float, default=0.5)
    parser.add_argument("--cache-mode", choices=("miss", "same"), default="miss")
    parser.add_argument("--require-llm", action=argparse.BooleanOptionalAction, default=True)
    parser.add_argument("--output", default=str(default_output))
    args = parser.parse_args(argv)
    args.raw_command = ["python", "scripts/measure_ollama_stream_baseline.py", *(argv or [])]
    return args


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    output_path = asyncio.run(run_baseline(args))
    print(f"Wrote baseline report: {output_path}")
    return 0


def _stats(values: list[int]) -> dict[str, int | None]:
    if not values:
        return {"min": None, "p50": None, "p95": None, "max": None}
    ordered = sorted(values)
    return {
        "min": ordered[0],
        "p50": _percentile(ordered, 50),
        "p95": _percentile(ordered, 95),
        "max": ordered[-1],
    }


def _percentile(ordered_values: list[int], percentile: int) -> int:
    if len(ordered_values) == 1:
        return ordered_values[0]
    index = round((percentile / 100) * (len(ordered_values) - 1))
    return ordered_values[index]


def _format_stats(stats: dict[str, int | None]) -> str:
    return "min={min}, p50={p50}, p95={p95}, max={max}".format(**stats)


def _cell(value: object) -> str:
    if value is None:
        return ""
    return str(value).replace("|", "\\|").replace("\n", " ")


def _isolate_runtime_outputs() -> None:
    run_dir = Path(tempfile.gettempdir()) / f"devmatch-ai-baseline-{int(time.time())}"
    run_dir.mkdir(parents=True, exist_ok=True)
    os.environ.setdefault("AI_REVIEW_ANSWER_CACHE_PATH", str(run_dir / "answer_cache.jsonl"))
    os.environ.setdefault("AI_REVIEW_AUTO_CANDIDATES_PATH", str(run_dir / "auto_candidates.jsonl"))


def _cpu_name() -> str:
    if platform.system().lower() == "windows":
        try:
            output = subprocess.check_output(
                ["wmic", "cpu", "get", "name"],
                text=True,
                stderr=subprocess.DEVNULL,
                timeout=5,
            )
            lines = [line.strip() for line in output.splitlines() if line.strip() and line.strip() != "Name"]
            if lines:
                return lines[0]
        except (OSError, subprocess.SubprocessError):
            pass
    return platform.processor() or "unknown"


def _ram_gb() -> str:
    if platform.system().lower() == "windows":
        value = _ram_gb_windows_api()
        if value != "unknown":
            return value
    if platform.system().lower() == "windows":
        try:
            output = subprocess.check_output(
                ["wmic", "computersystem", "get", "TotalPhysicalMemory"],
                text=True,
                stderr=subprocess.DEVNULL,
                timeout=5,
            )
            lines = [
                line.strip()
                for line in output.splitlines()
                if line.strip() and line.strip() != "TotalPhysicalMemory"
            ]
            if lines:
                gb = int(lines[0]) / (1024**3)
                return f"{gb:.1f} GB"
        except (OSError, subprocess.SubprocessError, ValueError):
            pass
    return "unknown"


def _ram_gb_windows_api() -> str:
    class MemoryStatus(ctypes.Structure):
        _fields_ = [
            ("dwLength", ctypes.c_ulong),
            ("dwMemoryLoad", ctypes.c_ulong),
            ("ullTotalPhys", ctypes.c_ulonglong),
            ("ullAvailPhys", ctypes.c_ulonglong),
            ("ullTotalPageFile", ctypes.c_ulonglong),
            ("ullAvailPageFile", ctypes.c_ulonglong),
            ("ullTotalVirtual", ctypes.c_ulonglong),
            ("ullAvailVirtual", ctypes.c_ulonglong),
            ("ullAvailExtendedVirtual", ctypes.c_ulonglong),
        ]

    try:
        status = MemoryStatus()
        status.dwLength = ctypes.sizeof(MemoryStatus)
        if ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status)):
            return f"{status.ullTotalPhys / (1024**3):.1f} GB"
    except Exception:
        return "unknown"
    return "unknown"


def _ollama_version() -> str:
    try:
        with urlopen("http://localhost:11434/api/version", timeout=2) as response:
            payload = json.loads(response.read().decode("utf-8"))
            return str(payload.get("version", "unknown"))
    except Exception:
        return "unreachable"


if __name__ == "__main__":
    raise SystemExit(main())

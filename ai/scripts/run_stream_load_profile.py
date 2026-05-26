import argparse
import asyncio
import json
import time
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any

import httpx


@dataclass(frozen=True)
class LoadScenario:
    name: str
    description: str
    question: str
    unique_prompt: bool = False
    concurrency: int = 1
    requests: int = 5
    disconnect_after_chunks: int | None = None
    client_timeout_seconds: float = 45.0


SCENARIOS: dict[str, LoadScenario] = {
    "cache-hit": LoadScenario(
        name="cache-hit",
        description="Repeat the same prompt to exercise answer cache hit behavior.",
        question="REST API에서 201 Created는 언제 쓰나요?",
        unique_prompt=False,
        concurrency=1,
        requests=5,
    ),
    "cache-miss": LoadScenario(
        name="cache-miss",
        description="Send unique prompts to bypass cache and exercise retrieval/generation.",
        question="트랜잭션 격리 수준과 phantom read를 짧게 설명해주세요.",
        unique_prompt=True,
        concurrency=1,
        requests=5,
    ),
    "free-form-storm": LoadScenario(
        name="free-form-storm",
        description="Concurrent unique free-form prompts for local dev-laptop stress.",
        question="Spring에서 N+1 문제를 실무적으로 어떻게 줄이나요?",
        unique_prompt=True,
        concurrency=3,
        requests=9,
    ),
    "stream-disconnect": LoadScenario(
        name="stream-disconnect",
        description="Disconnect after the first chunk to verify stream cancellation behavior.",
        question="HTTP 상태 코드 409 Conflict의 예시를 설명해주세요.",
        unique_prompt=True,
        concurrency=2,
        requests=6,
        disconnect_after_chunks=1,
    ),
    "ollama-timeout": LoadScenario(
        name="ollama-timeout",
        description="Use a very short client timeout to exercise timeout/hang handling.",
        question="분산 시스템에서 quorum read/write를 설명해주세요.",
        unique_prompt=True,
        concurrency=1,
        requests=3,
        client_timeout_seconds=1.0,
    ),
}


def build_payload(scenario: LoadScenario, run_index: int) -> dict[str, Any]:
    answer = scenario.question
    if scenario.unique_prompt:
        answer = f"{answer} [load-run:{run_index}-{int(time.time() * 1000)}]"
    return {
        "answer": answer,
        "user_answer": answer,
        "mode": "FREE_QUESTION",
    }


async def run_one_request(
    *,
    client: httpx.AsyncClient,
    url: str,
    scenario: LoadScenario,
    run_index: int,
) -> dict[str, Any]:
    started_at = time.perf_counter()
    first_token_at: float | None = None
    chunk_count = 0
    response_chars = 0
    status = "partial_failed"
    error = ""
    http_status: int | None = None

    try:
        async with client.stream("POST", url, json=build_payload(scenario, run_index)) as response:
            http_status = response.status_code
            response.raise_for_status()
            async for line in response.aiter_lines():
                event = parse_sse_line(line)
                if not event:
                    continue
                event_type = event.get("type")
                if event_type == "chunk":
                    if first_token_at is None:
                        first_token_at = time.perf_counter()
                    chunk = str(event.get("chunk", ""))
                    chunk_count += 1
                    response_chars += len(chunk)
                    if (
                        scenario.disconnect_after_chunks is not None
                        and chunk_count >= scenario.disconnect_after_chunks
                    ):
                        status = "disconnected"
                        await response.aclose()
                        break
                elif event_type == "done":
                    status = "completed"
                    break
                elif event_type == "error":
                    status = "partial_failed"
                    error = str(event.get("error", "stream error"))
                    break
    except httpx.TimeoutException as exc:
        status = "timeout"
        error = f"{type(exc).__name__}: {exc}"
    except Exception as exc:
        if status != "disconnected":
            status = "partial_failed"
            error = f"{type(exc).__name__}: {exc}"

    finished_at = time.perf_counter()
    return {
        "run": run_index,
        "status": status,
        "http_status": http_status,
        "first_token_latency_ms": (
            int((first_token_at - started_at) * 1000) if first_token_at is not None else None
        ),
        "stream_duration_ms": int((finished_at - started_at) * 1000),
        "chunk_count": chunk_count,
        "response_chars": response_chars,
        "error": error,
    }


async def run_load_profile(args: argparse.Namespace) -> Path:
    scenario = SCENARIOS[args.scenario]
    url = f"{args.base_url.rstrip('/')}/api/ai-review/sessions/{args.session_id}/messages/stream"
    timeout = httpx.Timeout(scenario.client_timeout_seconds)
    limits = httpx.Limits(max_connections=max(args.concurrency, scenario.concurrency))
    semaphore = asyncio.Semaphore(args.concurrency)
    samples: list[dict[str, Any]] = []

    async with httpx.AsyncClient(timeout=timeout, limits=limits, headers=auth_headers(args)) as client:
        async def worker(run_index: int) -> dict[str, Any]:
            async with semaphore:
                return await run_one_request(
                    client=client,
                    url=url,
                    scenario=scenario,
                    run_index=run_index,
                )

        samples = await asyncio.gather(*(worker(index) for index in range(1, args.requests + 1)))

    report = render_markdown_report(
        scenario=scenario,
        samples=samples,
        command=" ".join(args.raw_command),
    )
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(report, encoding="utf-8", newline="\n")
    return output_path


def parse_sse_line(line: str) -> dict[str, Any]:
    stripped = line.strip()
    if not stripped or not stripped.startswith("data:"):
        return {}
    payload = stripped[5:].strip()
    if not payload:
        return {}
    try:
        decoded = json.loads(payload)
    except json.JSONDecodeError:
        return {}
    return decoded if isinstance(decoded, dict) else {}


def summarize_samples(samples: list[dict[str, Any]]) -> dict[str, Any]:
    return {
        "completed": sum(1 for sample in samples if sample["status"] == "completed"),
        "disconnected": sum(1 for sample in samples if sample["status"] == "disconnected"),
        "partial_failed": sum(1 for sample in samples if sample["status"] == "partial_failed"),
        "timeout": sum(1 for sample in samples if sample["status"] == "timeout"),
        "first_token_latency_ms": _stats(
            [
                sample["first_token_latency_ms"]
                for sample in samples
                if sample.get("first_token_latency_ms") is not None
            ]
        ),
        "stream_duration_ms": _stats([sample["stream_duration_ms"] for sample in samples]),
    }


def render_markdown_report(*, scenario: LoadScenario, samples: list[dict[str, Any]], command: str) -> str:
    summary = summarize_samples(samples)
    lines = [
        "# AI Review Streaming Load Profile",
        "",
        f"- measured_at: {datetime.now().isoformat(timespec='seconds')}",
        f"- scenario: {scenario.name}",
        f"- description: {scenario.description}",
        f"- command: `{command}`",
        "",
        "## Summary",
        "",
        f"- completed: {summary['completed']}",
        f"- disconnected: {summary['disconnected']}",
        f"- partial_failed: {summary['partial_failed']}",
        f"- timeout: {summary['timeout']}",
        f"- first_token_latency_ms: {_format_stats(summary['first_token_latency_ms'])}",
        f"- stream_duration_ms: {_format_stats(summary['stream_duration_ms'])}",
        "",
        "## Samples",
        "",
        "| run | status | first_token_latency_ms | stream_duration_ms | chunks | chars | error |",
        "| --- | --- | ---: | ---: | ---: | ---: | --- |",
    ]
    for sample in samples:
        lines.append(
            "| {run} | {status} | {first} | {duration} | {chunks} | {chars} | {error} |".format(
                run=sample["run"],
                status=sample["status"],
                first=_cell(sample.get("first_token_latency_ms")),
                duration=_cell(sample["stream_duration_ms"]),
                chunks=sample["chunk_count"],
                chars=sample["response_chars"],
                error=_cell(sample.get("error", "")),
            )
        )
    return "\n".join(lines) + "\n"


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    root = Path(__file__).resolve().parents[2]
    default_output = root / "docs" / "smoke" / f"{datetime.now().date()}-ai-stream-load-profile.md"
    parser = argparse.ArgumentParser(description="Run AI review streaming load scenarios.")
    parser.add_argument("--scenario", choices=sorted(SCENARIOS), default="cache-hit")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--session-id", required=False, default="20")
    parser.add_argument("--requests", type=int, default=None)
    parser.add_argument("--concurrency", type=int, default=None)
    parser.add_argument("--token", default="")
    parser.add_argument("--output", default=str(default_output))
    args = parser.parse_args(argv)
    scenario = SCENARIOS[args.scenario]
    args.requests = args.requests or scenario.requests
    args.concurrency = args.concurrency or scenario.concurrency
    args.raw_command = ["python", "scripts/run_stream_load_profile.py", *(argv or [])]
    return args


def auth_headers(args: argparse.Namespace) -> dict[str, str]:
    if not args.token:
        return {}
    return {"Authorization": f"Bearer {args.token}"}


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    output_path = asyncio.run(run_load_profile(args))
    print(f"Wrote load profile report: {output_path}")
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


if __name__ == "__main__":
    raise SystemExit(main())

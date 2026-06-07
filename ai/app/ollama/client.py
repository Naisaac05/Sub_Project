import json
import logging
import os
import threading
import time
import urllib.error
import urllib.request
import asyncio
import httpx

from app.observability import LOGGER_NAME
from app.ollama.gateway import AcquireResult, ModelPoolGateway
from app.validation.text import strip_thinking


OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
OLLAMA_REQUEST_TIMEOUT_SECONDS = int(os.getenv("OLLAMA_REQUEST_TIMEOUT_SECONDS", "30"))
OLLAMA_QUEUE_WAIT_TIMEOUT_SECONDS = int(os.getenv("OLLAMA_QUEUE_WAIT_TIMEOUT_SECONDS", "3"))
DEFAULT_MODEL = os.getenv("PYTHON_AI_MODEL") or os.getenv("OLLAMA_SMALL_MODEL") or "exaone3.5:2.4b"
FALLBACK_MODEL = os.getenv("PYTHON_AI_FALLBACK_MODEL", "exaone3.5:2.4b")
OLLAMA_SMALL_KEEP_ALIVE = os.getenv("OLLAMA_SMALL_KEEP_ALIVE", "-1")
OLLAMA_FALLBACK_KEEP_ALIVE = os.getenv("OLLAMA_FALLBACK_KEEP_ALIVE", "30m")
OLLAMA_MAX_CONCURRENT_GENERATIONS = int(os.getenv("OLLAMA_MAX_CONCURRENT_GENERATIONS", "1"))
OLLAMA_WARMUP_ENABLED = os.getenv("OLLAMA_WARMUP_ENABLED", "true").lower() == "true"
OLLAMA_WARMUP_MODEL = os.getenv("OLLAMA_WARMUP_MODEL", DEFAULT_MODEL)
OLLAMA_WARMUP_TIMEOUT_SECONDS = int(os.getenv("OLLAMA_WARMUP_TIMEOUT_SECONDS", "45"))
_GENERATION_SEMAPHORE = threading.BoundedSemaphore(max(1, OLLAMA_MAX_CONCURRENT_GENERATIONS))
_METRIC_LOGGER = logging.getLogger(LOGGER_NAME)
_GATEWAY: ModelPoolGateway | None = None


def bounded_ollama_request_timeout_seconds(value: int) -> int:
    return value if value > 0 else 30


def bounded_ollama_queue_wait_timeout_seconds(value: int) -> int:
    return value if value > 0 else 3


def call_ollama(model: str, prompt: str, temperature: float, max_tokens: int, num_ctx: int, num_thread: int) -> str:
    gateway = _ollama_gateway()
    route = gateway.route_for(model)
    body = {
        "model": model,
        "prompt": prompt,
        "stream": False,
        "think": False,
        "keep_alive": keep_alive_for_model(model),
        "options": {
            "temperature": temperature,
            "num_predict": max_tokens,
            "num_ctx": num_ctx,
            "num_thread": num_thread,
            "repeat_penalty": 1.25,
            "repeat_last_n": 128,
            "stop": stop_sequences(),
        },
    }
    data = json.dumps(body).encode("utf-8")
    request = urllib.request.Request(
        f"{route.endpoint.base_url}/api/generate",
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    acquisition: AcquireResult | None = None
    status = "completed"
    started_at = time.perf_counter()
    try:
        timeout = bounded_ollama_request_timeout_seconds(OLLAMA_REQUEST_TIMEOUT_SECONDS)
        queue_wait_timeout = bounded_ollama_queue_wait_timeout_seconds(OLLAMA_QUEUE_WAIT_TIMEOUT_SECONDS)
        acquisition = gateway.acquire(model, queue_wait_timeout)
        if not acquisition.acquired:
            status = "queue_timeout"
            raise TimeoutError("Ollama generation queue wait timed out")
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                payload = json.loads(response.read().decode("utf-8"))
        finally:
            gateway.release(acquisition)
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
        if status != "queue_timeout":
            status = "error"
        raise RuntimeError("Ollama request failed") from exc
    finally:
        _emit_ollama_generation_finished_metric(
            model=model,
            endpoint=route.endpoint.base_url,
            capacity=route.capacity,
            in_flight=gateway.in_flight_for(model),
            status=status,
            queue_wait_ms=acquisition.queue_wait_ms if acquisition is not None else 0,
            elapsed_ms=int((time.perf_counter() - started_at) * 1000),
        )

    answer = str(payload.get("response", "")).strip()
    if not answer:
        raise RuntimeError("Ollama returned an empty response")
    return strip_thinking(answer)


def keep_alive_for_model(model: str) -> str | int:
    value = OLLAMA_FALLBACK_KEEP_ALIVE if model == FALLBACK_MODEL else OLLAMA_SMALL_KEEP_ALIVE
    stripped = value.strip()
    if stripped.lstrip("-").isdigit():
        return int(stripped)
    return value


def stop_sequences() -> list[str]:
    return ["\n\n\n"]


def warm_up_ollama() -> None:
    if not OLLAMA_WARMUP_ENABLED:
        return

    thread = threading.Thread(target=_warm_up_ollama, daemon=True)
    thread.start()


def _warm_up_ollama() -> None:
    route = _ollama_gateway().route_for(OLLAMA_WARMUP_MODEL)
    body = {
        "model": OLLAMA_WARMUP_MODEL,
        "prompt": "준비",
        "stream": False,
        "think": False,
        "keep_alive": keep_alive_for_model(OLLAMA_WARMUP_MODEL),
        "options": {
            "temperature": 0,
            "num_predict": 1,
            "num_ctx": 128,
        },
    }
    data = json.dumps(body).encode("utf-8")
    request = urllib.request.Request(
        f"{route.endpoint.base_url}/api/generate",
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=OLLAMA_WARMUP_TIMEOUT_SECONDS) as response:
            response.read()
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError):
        pass


async def call_ollama_stream_async(
    model: str,
    prompt: str,
    temperature: float,
    max_tokens: int,
    num_ctx: int,
    num_thread: int,
):
    gateway = _ollama_gateway()
    route = gateway.route_for(model)
    body = {
        "model": model,
        "prompt": prompt,
        "stream": True,
        "think": False,
        "keep_alive": keep_alive_for_model(model),
        "options": {
            "temperature": temperature,
            "num_predict": max_tokens,
            "num_ctx": num_ctx,
            "num_thread": num_thread,
            "repeat_penalty": 1.25,
            "repeat_last_n": 128,
            "stop": stop_sequences(),
        },
    }

    loop = asyncio.get_running_loop()
    started_at = time.perf_counter()
    acquire_started_at = time.perf_counter()
    queue_wait_ms = 0
    acquired = False
    acquisition: AcquireResult | None = None
    status = "completed"

    try:
        queue_wait_timeout = bounded_ollama_queue_wait_timeout_seconds(OLLAMA_QUEUE_WAIT_TIMEOUT_SECONDS)
        acquisition = await loop.run_in_executor(None, gateway.acquire, model, queue_wait_timeout)
        acquired = acquisition.acquired
        queue_wait_ms = acquisition.queue_wait_ms
        if not acquired:
            status = "queue_timeout"
            raise TimeoutError("Ollama generation queue wait timed out")

        timeout = bounded_ollama_request_timeout_seconds(OLLAMA_REQUEST_TIMEOUT_SECONDS)
        async with httpx.AsyncClient(timeout=timeout) as client:
            async with client.stream(
                "POST",
                f"{acquisition.endpoint.base_url}/api/generate",
                json=body,
                headers={"Content-Type": "application/json"},
            ) as response:
                if response.status_code != 200:
                    status = "http_error"
                    raise RuntimeError(f"Ollama returned status code {response.status_code}")

                async for line in response.aiter_lines():
                    if not line:
                        continue
                    payload = json.loads(line)
                    chunk = payload.get("response", "")
                    if chunk:
                        yield chunk
                    if payload.get("done", False):
                        break
    except asyncio.CancelledError:
        status = "cancelled"
        raise
    except (httpx.TimeoutException, TimeoutError) as exc:
        if status != "queue_timeout":
            status = "timeout"
        raise RuntimeError("Ollama streaming request failed") from exc
    except Exception as exc:
        if status == "completed":
            status = "error"
        raise RuntimeError("Ollama streaming request failed") from exc
    finally:
        semaphore_released = False
        if acquired:
            gateway.release(acquisition)
            semaphore_released = True
        _emit_ollama_stream_finished_metric(
            model=model,
            endpoint=(acquisition.endpoint.base_url if acquisition is not None else route.endpoint.base_url),
            capacity=(acquisition.capacity if acquisition is not None else route.capacity),
            in_flight=gateway.in_flight_for(model),
            status=status,
            queue_wait_ms=queue_wait_ms,
            elapsed_ms=int((time.perf_counter() - started_at) * 1000),
            semaphore_released=semaphore_released,
        )


def _emit_ollama_stream_finished_metric(
    *,
    model: str,
    endpoint: str,
    capacity: int,
    in_flight: int,
    status: str,
    queue_wait_ms: int,
    elapsed_ms: int,
    semaphore_released: bool,
) -> None:
    _METRIC_LOGGER.info(
        json.dumps(
            {
                "event": "ai_review.ollama_stream_finished",
                "model": model,
                "endpoint": endpoint,
                "capacity": capacity,
                "in_flight": in_flight,
                "status": status,
                "queue_wait_ms": queue_wait_ms,
                "elapsed_ms": elapsed_ms,
                "semaphore_released": semaphore_released,
            },
            ensure_ascii=False,
            sort_keys=True,
        )
    )


def _emit_ollama_generation_finished_metric(
    *,
    model: str,
    endpoint: str,
    capacity: int,
    in_flight: int,
    status: str,
    queue_wait_ms: int,
    elapsed_ms: int,
) -> None:
    _METRIC_LOGGER.info(
        json.dumps(
            {
                "event": "ai_review.ollama_generation_finished",
                "model": model,
                "endpoint": endpoint,
                "capacity": capacity,
                "in_flight": in_flight,
                "status": status,
                "queue_wait_ms": queue_wait_ms,
                "elapsed_ms": elapsed_ms,
            },
            ensure_ascii=False,
            sort_keys=True,
        )
    )


def _ollama_gateway() -> ModelPoolGateway:
    global _GATEWAY
    if _GATEWAY is None:
        _GATEWAY = ModelPoolGateway.from_env(
            default_base_url=OLLAMA_BASE_URL,
            default_model=DEFAULT_MODEL,
            default_capacity=max(1, OLLAMA_MAX_CONCURRENT_GENERATIONS),
        )
    return _GATEWAY


def reset_ollama_gateway_for_tests() -> None:
    global _GATEWAY
    _GATEWAY = None



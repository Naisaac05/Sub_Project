import json
import os
import threading
import urllib.error
import urllib.request

from app.validation.text import strip_thinking


OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
OLLAMA_REQUEST_TIMEOUT_SECONDS = int(os.getenv("OLLAMA_REQUEST_TIMEOUT_SECONDS", "0"))
OLLAMA_SMALL_KEEP_ALIVE = os.getenv("OLLAMA_SMALL_KEEP_ALIVE", "-1")
OLLAMA_FALLBACK_KEEP_ALIVE = os.getenv("OLLAMA_FALLBACK_KEEP_ALIVE", "30m")
OLLAMA_WARMUP_ENABLED = os.getenv("OLLAMA_WARMUP_ENABLED", "true").lower() == "true"
OLLAMA_WARMUP_MODEL = os.getenv("PYTHON_AI_MODEL") or os.getenv("OLLAMA_MODEL") or "qwen3:1.7b"
OLLAMA_WARMUP_TIMEOUT_SECONDS = int(os.getenv("OLLAMA_WARMUP_TIMEOUT_SECONDS", "45"))
FALLBACK_MODEL = os.getenv("PYTHON_AI_FALLBACK_MODEL", "qwen3:4b-q4_K_M")


def call_ollama(model: str, prompt: str, temperature: float, max_tokens: int, num_ctx: int, num_thread: int) -> str:
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
            "stop": ["\n\n\n", "[Question]", "[Original Question]", "[Learner Free Question]"],
        },
    }
    data = json.dumps(body).encode("utf-8")
    request = urllib.request.Request(
        f"{OLLAMA_BASE_URL}/api/generate",
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        timeout = None if OLLAMA_REQUEST_TIMEOUT_SECONDS <= 0 else OLLAMA_REQUEST_TIMEOUT_SECONDS
        with urllib.request.urlopen(request, timeout=timeout) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
        raise RuntimeError("Ollama request failed") from exc

    answer = str(payload.get("response", "")).strip()
    if not answer:
        raise RuntimeError("Ollama returned an empty response")
    return strip_thinking(answer)


def keep_alive_for_model(model: str) -> str:
    if model == FALLBACK_MODEL:
        return OLLAMA_FALLBACK_KEEP_ALIVE
    return OLLAMA_SMALL_KEEP_ALIVE


def warm_up_ollama() -> None:
    if not OLLAMA_WARMUP_ENABLED:
        return

    thread = threading.Thread(target=_warm_up_ollama, daemon=True)
    thread.start()


def _warm_up_ollama() -> None:
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
        f"{OLLAMA_BASE_URL}/api/generate",
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=OLLAMA_WARMUP_TIMEOUT_SECONDS) as response:
            response.read()
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError):
        pass


import json
import math
import os
import urllib.request

from app.ollama.client import OLLAMA_BASE_URL


class EmbeddingError(RuntimeError):
    pass


def normalize_vector(values) -> list[float]:
    try:
        vector = [float(value) for value in values]
    except (TypeError, ValueError, OverflowError) as exc:
        raise EmbeddingError("Embedding vector contains invalid values") from exc
    if not vector or not all(math.isfinite(value) for value in vector):
        raise EmbeddingError("Embedding vector contains invalid values")
    norm = math.hypot(*vector)
    if not math.isfinite(norm) or norm <= 0:
        raise EmbeddingError("Embedding vector has an invalid norm")
    return [value / norm for value in vector]


def cosine_similarity(left, right) -> float:
    try:
        left_values = list(left)
        right_values = list(right)
    except TypeError as exc:
        raise EmbeddingError("Embedding vectors must be iterable") from exc
    if len(left_values) != len(right_values):
        raise EmbeddingError("Embedding vector dimensions do not match")
    normalized_left = normalize_vector(left_values)
    normalized_right = normalize_vector(right_values)
    return sum(a * b for a, b in zip(normalized_left, normalized_right))


class OllamaEmbeddingClient:
    def __init__(
        self,
        base_url: str | None = None,
        model: str | None = None,
        timeout_seconds: int | None = None,
    ):
        configured_base_url = OLLAMA_BASE_URL if base_url is None else base_url
        if not isinstance(configured_base_url, str) or not configured_base_url.strip():
            raise EmbeddingError("Embedding base URL must be a nonblank string")
        self.base_url = configured_base_url.strip().rstrip("/")
        if model is not None:
            if not isinstance(model, str):
                raise EmbeddingError("Embedding model must be a string")
            if not model.strip():
                raise EmbeddingError("Embedding model must not be blank")
            self.model = model.strip()
        else:
            self.model = os.getenv("AI_REVIEW_EMBEDDING_MODEL", "").strip() or "bge-m3"
        configured_timeout = timeout_seconds
        if configured_timeout is None:
            try:
                configured_timeout = int(os.getenv("AI_REVIEW_EMBEDDING_TIMEOUT_SECONDS", "10"))
            except (TypeError, ValueError) as exc:
                raise EmbeddingError(
                    "Embedding timeout must be an integer number of seconds"
                ) from exc
        if not isinstance(configured_timeout, int):
            raise EmbeddingError("Embedding timeout must be an integer number of seconds")
        self.timeout_seconds = max(1, configured_timeout)

    def embed(self, prompt: str) -> list[float]:
        if not isinstance(prompt, str) or not prompt.strip():
            raise EmbeddingError("Embedding prompt must not be blank")
        body = json.dumps({"model": self.model, "prompt": prompt}).encode("utf-8")
        try:
            request = urllib.request.Request(
                f"{self.base_url}/api/embeddings",
                data=body,
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            with urllib.request.urlopen(request, timeout=self.timeout_seconds) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except (OSError, ValueError, json.JSONDecodeError, UnicodeDecodeError) as exc:
            raise EmbeddingError("Ollama embedding request failed") from exc

        if not isinstance(payload, dict) or not isinstance(payload.get("embedding"), list):
            raise EmbeddingError("Ollama returned a malformed embedding payload")
        return normalize_vector(payload["embedding"])

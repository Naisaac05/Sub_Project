from __future__ import annotations

from dataclasses import dataclass
import os
import threading
import time


DEFAULT_CAPACITY = 1


@dataclass(frozen=True)
class OllamaEndpoint:
    model: str
    base_url: str


@dataclass(frozen=True)
class RouteDecision:
    model: str
    endpoint: OllamaEndpoint
    capacity: int
    in_flight: int
    all_draining: bool = False


@dataclass(frozen=True)
class AcquireResult:
    model: str
    endpoint: OllamaEndpoint
    capacity: int
    in_flight: int
    queue_wait_ms: int
    acquired: bool
    all_draining: bool = False


class ModelPoolGateway:
    def __init__(
        self,
        *,
        model_pool: dict[str, list[OllamaEndpoint]] | None = None,
        capacities: dict[str, int] | None = None,
        draining_endpoints: set[str] | None = None,
        default_base_url: str = "http://localhost:11434",
        default_capacity: int = DEFAULT_CAPACITY,
    ):
        self.model_pool = model_pool or {}
        self.capacities = capacities or {}
        self.draining_endpoints = draining_endpoints or set()
        self.default_base_url = default_base_url
        self.default_capacity = max(1, default_capacity)
        self._lock = threading.Lock()
        self._semaphores: dict[str, threading.BoundedSemaphore] = {}
        self._in_flight: dict[str, int] = {}

    @classmethod
    def from_env(
        cls,
        *,
        default_base_url: str,
        default_model: str,
        default_capacity: int,
    ) -> "ModelPoolGateway":
        return cls(
            model_pool=parse_model_pool(
                os.environ.get("OLLAMA_MODEL_POOL", ""),
                default_base_url=default_base_url,
                default_model=default_model,
            ),
            capacities=parse_model_capacities(os.environ.get("OLLAMA_MODEL_CAPACITY", "")),
            draining_endpoints=parse_draining_endpoints(os.environ.get("OLLAMA_DRAINING_ENDPOINTS", "")),
            default_base_url=default_base_url,
            default_capacity=default_capacity,
        )

    def route_for(self, model: str) -> RouteDecision:
        endpoints = self.model_pool.get(model) or [OllamaEndpoint(model=model, base_url=self.default_base_url)]
        active = [endpoint for endpoint in endpoints if endpoint.base_url not in self.draining_endpoints]
        all_draining = not active and bool(endpoints)
        endpoint = (active or endpoints)[0]
        capacity = self.capacity_for(model)
        return RouteDecision(
            model=model,
            endpoint=endpoint,
            capacity=capacity,
            in_flight=self.in_flight_for(model),
            all_draining=all_draining,
        )

    def acquire(self, model: str, timeout_seconds: float) -> AcquireResult:
        route = self.route_for(model)
        semaphore = self._semaphore_for(model, route.capacity)
        started_at = time.perf_counter()
        acquired = semaphore.acquire(timeout=max(timeout_seconds, 0))
        queue_wait_ms = int((time.perf_counter() - started_at) * 1000)
        if acquired:
            with self._lock:
                self._in_flight[model] = self._in_flight.get(model, 0) + 1
        return AcquireResult(
            model=model,
            endpoint=route.endpoint,
            capacity=route.capacity,
            in_flight=self.in_flight_for(model),
            queue_wait_ms=queue_wait_ms,
            acquired=acquired,
            all_draining=route.all_draining,
        )

    def release(self, acquisition: AcquireResult | None) -> None:
        if acquisition is None or not acquisition.acquired:
            return
        model = acquisition.model
        with self._lock:
            self._in_flight[model] = max(self._in_flight.get(model, 0) - 1, 0)
            semaphore = self._semaphores.get(model)
        if semaphore is not None:
            semaphore.release()

    def capacity_for(self, model: str) -> int:
        return max(1, self.capacities.get(model, self.default_capacity))

    def in_flight_for(self, model: str) -> int:
        with self._lock:
            return self._in_flight.get(model, 0)

    def _semaphore_for(self, model: str, capacity: int) -> threading.BoundedSemaphore:
        with self._lock:
            semaphore = self._semaphores.get(model)
            if semaphore is None:
                semaphore = threading.BoundedSemaphore(capacity)
                self._semaphores[model] = semaphore
            return semaphore


def parse_model_pool(
    value: str,
    *,
    default_base_url: str,
    default_model: str | None = None,
) -> dict[str, list[OllamaEndpoint]]:
    pool: dict[str, list[OllamaEndpoint]] = {}
    for part in value.split(","):
        if "=" not in part:
            continue
        model, base_url = part.split("=", 1)
        model = model.strip()
        base_url = base_url.strip().rstrip("/")
        if not model or not base_url:
            continue
        pool.setdefault(model, []).append(OllamaEndpoint(model=model, base_url=base_url))
    if not pool and default_model:
        pool[default_model] = [OllamaEndpoint(model=default_model, base_url=default_base_url.rstrip("/"))]
    return pool


def parse_model_capacities(value: str) -> dict[str, int]:
    capacities: dict[str, int] = {}
    for part in value.split(","):
        if "=" not in part:
            continue
        model, raw_capacity = part.split("=", 1)
        model = model.strip()
        try:
            capacity = int(raw_capacity.strip())
        except ValueError:
            continue
        if model and capacity > 0:
            capacities[model] = capacity
    return capacities


def parse_draining_endpoints(value: str) -> set[str]:
    return {part.strip().rstrip("/") for part in value.split(",") if part.strip()}

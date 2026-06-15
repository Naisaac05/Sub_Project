from dataclasses import dataclass
import json
from pathlib import Path


DEFAULT_CONFIG_PATH = Path(__file__).resolve().parents[1] / "config" / "rag_parallel.json"


@dataclass(frozen=True)
class ParallelRagConfig:
    enabled: bool = False
    shadow_mode: bool = True
    v2_percentage: int = 10


def load_parallel_rag_config(path: Path = DEFAULT_CONFIG_PATH) -> ParallelRagConfig:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError, TypeError):
        return ParallelRagConfig()
    return ParallelRagConfig(
        enabled=bool(data.get("AI_REVIEW_V2_APPROVED_FAST_PATH_ENABLED", False)),
        shadow_mode=bool(data.get("SHADOW_MODE", True)),
        v2_percentage=max(0, min(100, int(data.get("V2_PERCENTAGE", 10)))),
    )


def should_serve_v2(config: ParallelRagConfig, random_value: float) -> bool:
    if config.shadow_mode:
        return False
    return random_value < config.v2_percentage / 100

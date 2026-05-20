from dataclasses import dataclass


@dataclass(frozen=True)
class ConfidenceInputs:
    retrieval_score: float
    rule_match_score: float
    answer_validation_score: float
    model_self_check_score: float = 1.0


@dataclass(frozen=True)
class ConfidenceResult:
    score: float
    band: str
    should_fallback: bool
    should_save_candidate: bool


def calculate_confidence(inputs: ConfidenceInputs) -> ConfidenceResult:
    score = (
        0.4 * _clamp(inputs.retrieval_score)
        + 0.2 * _clamp(inputs.rule_match_score)
        + 0.3 * _clamp(inputs.answer_validation_score)
        + 0.1 * _clamp(inputs.model_self_check_score)
    )
    score = round(_clamp(score), 4)

    if score >= 0.8:
        band = "high"
    elif score >= 0.6:
        band = "medium"
    else:
        band = "low"

    return ConfidenceResult(
        score=score,
        band=band,
        should_fallback=score < 0.6,
        should_save_candidate=score < 0.8,
    )


def _clamp(value: float) -> float:
    return max(0.0, min(1.0, value))


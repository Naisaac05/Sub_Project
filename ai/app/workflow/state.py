from dataclasses import dataclass, field

from app.rag.retriever import RetrievedContext
from app.schemas import AiGenerateRequest
from app.scoring import ConfidenceResult


@dataclass
class ValidationResult:
    korean_ok: bool
    required_keywords_ok: bool
    forbidden_claims_ok: bool
    score: float
    reasons: list[str] = field(default_factory=list)


@dataclass
class ReviewWorkflowState:
    mode: str
    request: AiGenerateRequest
    contexts: list[RetrievedContext] = field(default_factory=list)
    answer: str = ""
    model_used: str | None = None
    fallback_used: bool = False
    prompt_version: str | None = None
    validation: ValidationResult | None = None
    confidence: ConfidenceResult | None = None
    error: str | None = None


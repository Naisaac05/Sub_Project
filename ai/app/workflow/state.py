from dataclasses import dataclass, field

from app.rag.retriever import RetrievedContext
from app.schemas import AiGenerateRequest
from app.scoring import ConfidenceResult
from app.workflow.intent import FreeQuestionIntent
from app.workflow.query_resolver import ResolvedQuery


@dataclass
class ValidationResult:
    korean_ok: bool
    required_keywords_ok: bool
    forbidden_claims_ok: bool
    relevance_ok: bool
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
    free_question_intent: FreeQuestionIntent | None = None
    resolved_query: ResolvedQuery | None = None
    validation: ValidationResult | None = None
    confidence: ConfidenceResult | None = None
    error: str | None = None
    route: str | None = None
    answer_style: str | None = None
    quality_flags: list[str] = field(default_factory=list)
    candidate_id: str | None = None
    graph_status: str = "pending"


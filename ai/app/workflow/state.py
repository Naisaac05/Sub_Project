from dataclasses import dataclass, field

from app.rag.retriever import RetrievedContext
from app.schemas import AiGenerateRequest
from app.scoring import ConfidenceResult
from app.workflow.intent import FreeQuestionIntent
from app.workflow.query_resolver import ResolvedQuery
from app.workflow.judge import SemanticJudgeResult
from app.workflow.grounding import GroundingResult


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
    prompt_hash: str | None = None
    prompt_strategy: str | None = None
    retry_prompt_version: str | None = None
    retry_prompt_hash: str | None = None
    semantic_judge_prompt_version: str | None = None
    semantic_judge_prompt_hash: str | None = None
    grounding_prompt_version: str | None = None
    grounding_prompt_hash: str | None = None
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
    judge_result: SemanticJudgeResult | None = None
    retry_count: int = 0
    grounding_result: GroundingResult | None = None
    judge_tier: str = "tier0"  # "tier0" | "tier1" | "tier2"
    semantic_judge_skipped: bool = True
    grounding_judge_skipped: bool = True
    grounding_async_executed: bool = False
    estimated_latency_saved_ms: float = 4000.0
    grounding_thread: object = None  # unit test join 용도 스레드 홀더


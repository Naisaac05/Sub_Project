from pydantic import BaseModel, Field, field_validator

from app.guardrails import sanitize_text


DEFAULT_MODEL = "exaone3.5:2.4b"
DEFAULT_MAX_TOKENS = 256
DEFAULT_NUM_CTX = 1024


class AiGenerateRequest(BaseModel):
    question: str = ""
    options: list[str] | None = Field(default_factory=list)
    correct_answer: str = ""
    selected_answer: str = ""
    user_answer: str = ""
    evaluation: str = ""
    follow_up_type: str = ""
    previous_ai_question: str = ""
    active_concept: str = ""
    course_id: str = ""
    test_id: str = ""
    question_id: str = ""
    source_question_id: str = ""
    step: int = 1
    model: str = DEFAULT_MODEL
    temperature: float = 0.2
    max_tokens: int = DEFAULT_MAX_TOKENS
    num_ctx: int = DEFAULT_NUM_CTX
    num_thread: int = 4
    stream: bool = False

    @field_validator(
        "question",
        "correct_answer",
        "selected_answer",
        "user_answer",
        "evaluation",
        "follow_up_type",
        "previous_ai_question",
        "active_concept",
        "course_id",
        "test_id",
        "question_id",
        "source_question_id",
        mode="before",
    )
    @classmethod
    def none_to_empty_string(cls, value: object) -> str:
        return "" if value is None else sanitize_text(str(value))

    @field_validator("model", mode="before")
    @classmethod
    def none_to_default_model(cls, value: object) -> str:
        model = "" if value is None else str(value).strip()
        return model or DEFAULT_MODEL

    @field_validator("options", mode="before")
    @classmethod
    def normalize_options(cls, value: object) -> list[str]:
        if value is None:
            return []
        if not isinstance(value, list):
            return [str(value)]
        return [str(option) for option in value if option is not None]

    @field_validator("step", mode="before")
    @classmethod
    def none_to_default_step(cls, value: object) -> int:
        try:
            return 1 if value in (None, "") else int(value)
        except (TypeError, ValueError):
            return 1

    @field_validator("temperature", mode="before")
    @classmethod
    def none_to_default_temperature(cls, value: object) -> float:
        try:
            return 0.2 if value in (None, "") else float(value)
        except (TypeError, ValueError):
            return 0.2

    @field_validator("max_tokens", mode="before")
    @classmethod
    def none_to_default_max_tokens(cls, value: object) -> int:
        try:
            return DEFAULT_MAX_TOKENS if value in (None, "") else int(value)
        except (TypeError, ValueError):
            return DEFAULT_MAX_TOKENS

    @field_validator("num_ctx", mode="before")
    @classmethod
    def none_to_default_num_ctx(cls, value: object) -> int:
        try:
            return DEFAULT_NUM_CTX if value in (None, "") else int(value)
        except (TypeError, ValueError):
            return DEFAULT_NUM_CTX

    @field_validator("num_thread", mode="before")
    @classmethod
    def none_to_default_num_thread(cls, value: object) -> int:
        try:
            return 4 if value in (None, "") else int(value)
        except (TypeError, ValueError):
            return 4


class AiGenerateResponse(BaseModel):
    answer: str
    provider: str = "python-ollama"
    confidence_score: float | None = None
    model_used: str | None = None
    fallback_used: bool | None = None
    retrieved_concept_ids: list[str] = Field(default_factory=list)
    candidate_id: str | None = None
    prompt_version: str | None = None
    latency_ms: int | None = None
    route: str | None = None
    resolved_query: str | None = None
    correction_type: str | None = None
    matched_concept_id: str | None = None
    answer_style: str | None = None
    quality_flags: list[str] = Field(default_factory=list)
    observability_events: list[dict[str, object]] = Field(default_factory=list)


def normalize_ai_request(payload: object) -> AiGenerateRequest:
    if isinstance(payload, dict):
        data = payload
    else:
        data = {"user_answer": "" if payload is None else str(payload)}
    return AiGenerateRequest.model_validate(data)


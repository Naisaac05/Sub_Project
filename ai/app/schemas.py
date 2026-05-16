from pydantic import BaseModel, Field, field_validator


class AiGenerateRequest(BaseModel):
    question: str = ""
    options: list[str] | None = Field(default_factory=list)
    correct_answer: str = ""
    selected_answer: str = ""
    user_answer: str = ""
    evaluation: str = ""
    step: int = 1
    model: str = "qwen3:1.7b"
    temperature: float = 0.2
    max_tokens: int = 120
    num_ctx: int = 512
    num_thread: int = 4

    @field_validator(
        "question",
        "correct_answer",
        "selected_answer",
        "user_answer",
        "evaluation",
        mode="before",
    )
    @classmethod
    def none_to_empty_string(cls, value: object) -> str:
        return "" if value is None else str(value)

    @field_validator("model", mode="before")
    @classmethod
    def none_to_default_model(cls, value: object) -> str:
        model = "" if value is None else str(value).strip()
        return model or "qwen3:1.7b"

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
            return 120 if value in (None, "") else int(value)
        except (TypeError, ValueError):
            return 120

    @field_validator("num_ctx", mode="before")
    @classmethod
    def none_to_default_num_ctx(cls, value: object) -> int:
        try:
            return 512 if value in (None, "") else int(value)
        except (TypeError, ValueError):
            return 512

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


def normalize_ai_request(payload: object) -> AiGenerateRequest:
    if isinstance(payload, dict):
        data = payload
    else:
        data = {"user_answer": "" if payload is None else str(payload)}
    return AiGenerateRequest.model_validate(data)


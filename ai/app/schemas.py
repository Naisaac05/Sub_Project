from pydantic import BaseModel, Field


class AiGenerateRequest(BaseModel):
    question: str = ""
    options: list[str] = Field(default_factory=list)
    correct_answer: str = ""
    selected_answer: str = ""
    user_answer: str = ""
    evaluation: str = ""
    step: int = 1
    model: str = "qwen2.5:1.5b"
    temperature: float = 0.2
    max_tokens: int = 200


class AiGenerateResponse(BaseModel):
    answer: str
    provider: str = "python-ollama"


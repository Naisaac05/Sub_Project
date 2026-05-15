from typing import Any

from fastapi import Body, FastAPI

from app.schemas import AiGenerateResponse, normalize_ai_request
from app.service import generate_review_answer

app = FastAPI(title="DevMatch AI Service")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/review/first-question", response_model=AiGenerateResponse)
def first_question(payload: Any = Body(default_factory=dict)) -> AiGenerateResponse:
    return generate_review_answer("first-question", normalize_ai_request(payload))


@app.post("/api/review/follow-up", response_model=AiGenerateResponse)
def follow_up(payload: Any = Body(default_factory=dict)) -> AiGenerateResponse:
    return generate_review_answer("follow-up", normalize_ai_request(payload))


@app.post("/api/review/free-question", response_model=AiGenerateResponse)
def free_question(payload: Any = Body(default_factory=dict)) -> AiGenerateResponse:
    return generate_review_answer("free-question", normalize_ai_request(payload))


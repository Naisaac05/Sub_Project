from fastapi import FastAPI

from app.schemas import AiGenerateRequest, AiGenerateResponse
from app.service import generate_review_answer

app = FastAPI(title="DevMatch AI Service")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/review/first-question", response_model=AiGenerateResponse)
def first_question(request: AiGenerateRequest) -> AiGenerateResponse:
    return generate_review_answer("first-question", request)


@app.post("/api/review/follow-up", response_model=AiGenerateResponse)
def follow_up(request: AiGenerateRequest) -> AiGenerateResponse:
    return generate_review_answer("follow-up", request)


@app.post("/api/review/free-question", response_model=AiGenerateResponse)
def free_question(request: AiGenerateRequest) -> AiGenerateResponse:
    return generate_review_answer("free-question", request)


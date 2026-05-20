from typing import Any

from fastapi import Body, FastAPI, Request, Response

from app.observability import CORRELATION_ID_HEADER, correlation_id_from, emit_observability_events
from app.schemas import AiGenerateResponse, normalize_ai_request
from app.security import verify_service_token
from app.ollama.client import warm_up_ollama
from app.service import generate_review_answer

app = FastAPI(title="DevMatch AI Service")


@app.on_event("startup")
def startup() -> None:
    warm_up_ollama()


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/review/first-question", response_model=AiGenerateResponse)
def first_question(
    request: Request,
    response: Response,
    payload: Any = Body(default_factory=dict),
) -> AiGenerateResponse:
    return _generate("first-question", payload, request, response)


@app.post("/api/review/follow-up", response_model=AiGenerateResponse)
def follow_up(
    request: Request,
    response: Response,
    payload: Any = Body(default_factory=dict),
) -> AiGenerateResponse:
    return _generate("follow-up", payload, request, response)


@app.post("/api/review/free-question", response_model=AiGenerateResponse)
def free_question(
    request: Request,
    response: Response,
    payload: Any = Body(default_factory=dict),
) -> AiGenerateResponse:
    return _generate("free-question", payload, request, response)


def _generate(mode: str, payload: Any, request: Request, response: Response) -> AiGenerateResponse:
    verify_service_token(request)
    correlation_id = correlation_id_from(request.headers.get(CORRELATION_ID_HEADER))
    result = generate_review_answer(mode, normalize_ai_request(payload))
    emit_observability_events(result, correlation_id)
    response.headers[CORRELATION_ID_HEADER] = correlation_id
    return result


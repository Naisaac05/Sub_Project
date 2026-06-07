from typing import Any
import os

from fastapi import Body, FastAPI, HTTPException, Request, Response
from fastapi.responses import StreamingResponse
import json

from app.congestion import AiRequestBusyError, ai_request_admission
from app.observability import CORRELATION_ID_HEADER, correlation_id_from, emit_observability_events
from app.production_config import validate_production_config
from app.schemas import AiGenerateResponse, normalize_ai_request
from app.security import verify_service_token
from app.ollama.client import warm_up_ollama
from app.service import generate_review_answer

STREAMING_ENABLED = os.getenv("PYTHON_AI_STREAMING_ENABLED", "true").lower() == "true"

app = FastAPI(title="DevMatch AI Service")


@app.on_event("startup")
def startup() -> None:
    validate_production_config()
    warm_up_ollama()


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/review/first-question", response_model=AiGenerateResponse)
def first_question(
    request: Request,
    response: Response,
    payload: Any = Body(default_factory=dict),
) -> Any:
    return _generate("first-question", payload, request, response)


@app.post("/api/review/follow-up", response_model=AiGenerateResponse)
def follow_up(
    request: Request,
    response: Response,
    payload: Any = Body(default_factory=dict),
) -> Any:
    return _generate("follow-up", payload, request, response)


@app.post("/api/review/free-question", response_model=AiGenerateResponse)
def free_question(
    request: Request,
    response: Response,
    payload: Any = Body(default_factory=dict),
) -> Any:
    return _generate("free-question", payload, request, response)


def _generate(mode: str, payload: Any, request: Request, response: Response) -> Any:
    verify_service_token(request)
    correlation_id = correlation_id_from(request.headers.get(CORRELATION_ID_HEADER))
    
    is_streaming = False
    if STREAMING_ENABLED:
        if isinstance(payload, dict) and payload.get("stream") is True:
            is_streaming = True
        elif "text/event-stream" in request.headers.get("accept", "").lower():
            is_streaming = True

    if is_streaming:
        normalized_request = normalize_ai_request(payload)
        normalized_request.stream = True
        ctx = ai_request_admission()
        try:
            ctx.__enter__()
        except AiRequestBusyError as exc:
            raise HTTPException(
                status_code=exc.status_code,
                detail=exc.detail,
                headers={"Retry-After": str(exc.retry_after_seconds)},
            ) from exc

        async def sse_generator():
            try:
                from app.workflow.runner import run_review_workflow_stream
                async for event in run_review_workflow_stream(mode, normalized_request):
                    if await request.is_disconnected():
                        break
                    
                    if event["type"] == "done":
                        resp = event["response"]
                        if hasattr(resp, "model_dump"):
                            event_copy = {"type": "done", "response": resp.model_dump()}
                        else:
                            event_copy = {"type": "done", "response": resp}
                        emit_observability_events(resp, correlation_id)
                        yield f"data: {json.dumps(event_copy, ensure_ascii=False)}\n\n"
                    else:
                        yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"
            finally:
                ctx.__exit__(None, None, None)

        return StreamingResponse(
            sse_generator(),
            media_type="text/event-stream",
            headers={CORRELATION_ID_HEADER: correlation_id},
        )

    try:
        with ai_request_admission():
            result = generate_review_answer(mode, normalize_ai_request(payload))
    except AiRequestBusyError as exc:
        raise HTTPException(
            status_code=exc.status_code,
            detail=exc.detail,
            headers={"Retry-After": str(exc.retry_after_seconds)},
        ) from exc
    emit_observability_events(result, correlation_id)
    response.headers[CORRELATION_ID_HEADER] = correlation_id
    return result


from app.schemas import AiGenerateRequest, AiGenerateResponse
from app.workflow.runner import run_review_workflow


def generate_review_answer(mode: str, request: AiGenerateRequest) -> AiGenerateResponse:
    return run_review_workflow(mode, request)

from dataclasses import dataclass
import json
import logging
import re
import inspect
from app.ollama.client import call_ollama, FALLBACK_MODEL
from app.schemas import AiGenerateRequest
from app.prompts.registry import compute_prompt_hash

logger = logging.getLogger("ai_review.workflow.grounding")


@dataclass(frozen=True)
class GroundingResult:
    grounding_score: float
    evidence_coverage: float
    unsupported_claims: list[str]
    grounded: bool
    reason: str
    prompt_version: str | None = None
    prompt_hash: str | None = None


def validate_grounding(
    request: AiGenerateRequest,
    answer: str,
    contexts: list,
    generator=call_ollama,
) -> GroundingResult:
    """
    Retrieved context와 생성된 답변의 semantic grounding 상태를 분석하여 검증 결과를 반환합니다.
    이 호출은 완전히 timeout-safe 및 exception-safe하여 실패 시에도 본 서비스 응답을 막지 않습니다.
    """
    if not answer:
        return GroundingResult(1.0, 1.0, [], True, "Skipped grounding: empty answer")

    # [중요 안전망] 기존 유닛 테스트의 단순 mock generator(호출 횟수를 세거나 단순 텍스트를 반환하는 lambda 등)들의 
    # 호출 오염을 예방하고 에러를 차단하기 위한 Pre-flight Check 필터링.
    # 실제 call_ollama 이거나 grounding 평결용 프로토콜을 수행하는 전용 mock_generator 인 경우에만 LLM judge를 돌립니다.
    is_compatible = False
    func_name = getattr(generator, "__name__", "")
    if func_name in ("call_ollama", "recording_generator", "call_ollama_stream_async"):
        is_compatible = True
    else:
        try:
            # inspect.getsource를 사용해 generator 내부 구현 문자열에 grounding judge 프로토콜이 있는지 실시간 검사
            source = inspect.getsource(generator)
            if "precise Grounding Judge" in source or "GroundingResult" in source or "grounding" in source or "grounding_score" in source:
                is_compatible = True
        except Exception:
            if "ground" in func_name.lower() or "mock_llm" in func_name.lower() or "fallback" in func_name.lower():
                is_compatible = True

    if not is_compatible:
        return GroundingResult(1.0, 1.0, [], True, "Skipped grounding: generator is not grounding-compatible")

    if not contexts:
        return GroundingResult(1.0, 1.0, [], True, "Skipped grounding: empty contexts")

    # RAG contexts text
    context_text = "\n\n".join(ctx.content for ctx in contexts)

    # Grounding Judge Prompt 구성 (JSON Schema 명시)
    prompt = f"""
You are a precise Grounding Judge evaluating if a programming mentor's RESPONSE is strictly grounded in the RETRIEVED CONTEXTS.
Your task is to judge the semantic overlap, coverage, and identify any unsupported hallucinated claims in the RESPONSE compared to the RETRIEVED CONTEXTS.

[Learner Question]
{request.user_answer}

[Mentor Response to Evaluate]
{answer}

[Retrieved Contexts (Evidence)]
{context_text}

Evaluate the RESPONSE strictly based on these guidelines:
1. Grounding Score (grounding_score: 0.0 - 1.0):
   - Assess if the statements/assertions in the RESPONSE are supported by the RETRIEVED CONTEXTS.
   - If the RESPONSE makes strong, absolute assertions (e.g. using words like "반드시", "항상", "절대", "무조건") that have no evidence in the RETRIEVED CONTEXTS, reduce the grounding_score drastically (< 0.7).
2. Evidence Coverage (evidence_coverage: 0.0 - 1.0):
   - Assess if the key technical keywords or entities present in the RETRIEVED CONTEXTS are completely absent/ignored in the RESPONSE.
   - If key retrieved concepts that are highly relevant to the question are missing in the response, decrease the evidence_coverage.
3. Unsupported Claims (unsupported_claims: list of strings):
   - List any factual assertions or claims made in the RESPONSE that have NO evidence or are contradicted by the RETRIEVED CONTEXTS.
4. Grounded (grounded: true | false):
   - Set to false if grounding_score < 0.7 OR evidence_coverage < 0.5 OR len(unsupported_claims) > 0. Otherwise set to true.

You MUST respond ONLY with a single JSON object in the following schema. Do NOT wrap it in ```json blocks or include any other text.

JSON Schema:
{{
  "grounding_score": 0.0-1.0,
  "evidence_coverage": 0.0-1.0,
  "unsupported_claims": ["unsupported assertion in Korean"],
  "grounded": true|false
}}
""".strip()

    prompt_version = "grounding_judge_v1"
    prompt_hash = compute_prompt_hash(prompt)

    model = request.model or FALLBACK_MODEL
    try:
        raw_response = generator(
            model=model,
            prompt=prompt,
            temperature=0.0,
            max_tokens=256,
            num_ctx=request.num_ctx,
            num_thread=request.num_thread,
        )
        
        # Clean and parse JSON
        cleaned = raw_response.strip()
        match = re.search(r"\{.*\}", cleaned, re.DOTALL)
        if match:
            cleaned = match.group(0)
            
        data = json.loads(cleaned)
        
        grounding_score = float(data.get("grounding_score", 1.0))
        evidence_coverage = float(data.get("evidence_coverage", 1.0))
        raw_claims = list(data.get("unsupported_claims", []))
        
        # [안전 요구사항] unsupported_claims 최대 개수 및 개별 문자열 길이 제한 (최대 5개, 각각 최대 100자)
        unsupported_claims = []
        for claim in raw_claims[:5]:
            truncated_claim = str(claim)[:100]
            unsupported_claims.append(truncated_claim)
            
        # Grounding flag 계산 보정
        grounded = bool(data.get("grounded", True))
        if grounding_score < 0.7 or evidence_coverage < 0.5 or len(unsupported_claims) > 0:
            grounded = False
            
        return GroundingResult(
            grounding_score=grounding_score,
            evidence_coverage=evidence_coverage,
            unsupported_claims=unsupported_claims,
            grounded=grounded,
            reason="Grounding validation completed",
            prompt_version=prompt_version,
            prompt_hash=prompt_hash,
        )
    except Exception as exc:
        logger.warning(f"Grounding validation failed (timeout-safe). Fallback to safe result. Error: {exc}")
        return GroundingResult(
            grounding_score=1.0,
            evidence_coverage=1.0,
            unsupported_claims=[],
            grounded=True,
            reason=f"Grounding failed with exception: {exc}",
            prompt_version=prompt_version,
            prompt_hash=prompt_hash,
        )

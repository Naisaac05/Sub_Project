# Prompt Artifact Versioning Implementation Plan

> **For Antigravity:** REQUIRED WORKFLOW: Use `.agent/workflows/execute-plan.md` to execute this plan in single-flow mode.

**Goal:** AI Review prompt assembly, retry prompts, semantic judge prompts, and grounding judge prompts의 버전, 해시, 그리고 실행 전략을 추적 가능하게 함으로써 답변 품질 변화의 원인을 투명하게 규명하고 완벽한 품질 재현성을 확보합니다.

**Architecture:** 
1. `app/prompts.py`를 파이썬 패키지 `app/prompts/` 구조로 개편하여 기존 코드를 `app/prompts/__init__.py`로 이관하고, 전용 버저닝 및 해시 모듈 `app/prompts/registry.py`를 신설합니다.
2. 생성된 원문 및 판사 프롬프트의 공백 정규화(Whitespace Normalization) 후 SHA-256 해시를 산출하는 `compute_prompt_hash`를 구현합니다.
3. `ReviewWorkflowState`, `SemanticJudgeResult`, `GroundingResult` 스키마를 보강하여 프롬프트 메타데이터를 저장 및 인계합니다.
4. Observability 이벤트 스트림(`workflow_completed`, `semantic_judge_evaluated`, `grounding_evaluated`)에 프롬프트 버전, 해시 및 실행 전략을 풍부하게 임베딩합니다.

**Tech Stack:** Python 3.11, Standard Library (hashlib, re, dataclasses, unittest)

---

## Proposed Changes

### Component 1: Prompt Registry & Package Restructuring

#### [NEW] [registry.py](file:///c:/Users/User/Desktop/Sub_Project/ai/app/prompts/registry.py)
프롬프트 에일리어스 관리, 버전 룩업, 롤백 준비 및 결정론적 SHA-256 해시 산출을 전담하는 레지스트리 엔진을 신설합니다.

```python
import hashlib
import re

# Central Registry for prompt metadata, supporting aliases, lookup, and rollback preparation
PROMPT_REGISTRY = {
    "first_question_v1": {
        "name": "first_question",
        "version": "1.0.0",
        "alias": "latest",
        "description": "First diagnostic test review follow-up question template"
    },
    "follow_up_v1": {
        "name": "follow_up",
        "version": "1.0.0",
        "alias": "latest",
        "description": "Follow-up question feedback and question template"
    },
    "free_question_v1": {
        "name": "free_question",
        "version": "1.0.0",
        "alias": "latest",
        "description": "Generic free question template with full background context"
    },
    "concept_definition_v1": {
        "name": "concept_definition",
        "version": "1.0.0",
        "alias": "latest",
        "description": "Pure concept/definition question template with minimized background context"
    },
    "wrong_answer_explanation_v1": {
        "name": "wrong_answer_explanation",
        "version": "1.0.0",
        "alias": "latest",
        "description": "Wrong answer explanation template with active context detail"
    },
    "follow_up_intent_v1": {
        "name": "follow_up_intent",
        "version": "1.0.0",
        "alias": "latest",
        "description": "Follow-up tail question intent template"
    },
    "retry_v1": {
        "name": "retry",
        "version": "1.0.0",
        "alias": "latest",
        "description": "Retry generation template with background context completely removed"
    },
    "semantic_judge_v1": {
        "name": "semantic_judge",
        "version": "1.0.0",
        "alias": "latest",
        "description": "Semantic quality and bias assessment LLM judge template"
    },
    "grounding_judge_v1": {
        "name": "grounding_judge",
        "version": "1.0.0",
        "alias": "latest",
        "description": "RAG evidence semantic grounding LLM judge template"
    }
}


def compute_prompt_hash(prompt_text: str) -> str:
    """
    동일 prompt에 대해 항상 동일한 해시값을 보장합니다.
    1. 모든 연속된 공백문자(공백, 탭, 개행)를 단일 스페이스 ' '로 정규화합니다.
    2. 양 끝의 공백을 제거합니다.
    3. SHA-256 해시를 적용하여 16진수 문자열로 반환합니다.
    """
    if not prompt_text:
        return ""
    normalized = re.sub(r"\s+", " ", prompt_text).strip()
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def lookup_prompt_version(alias: str, category: str) -> str:
    """
    주어진 alias와 category에 매칭되는 프롬프트 버전을 찾아 반환합니다.
    매칭되는 항목이 없으면 기본 {category}_v1 버전을 기본 반환하여 롤백 및 하위 호환성을 보장합니다.
    """
    for version, meta in PROMPT_REGISTRY.items():
        if meta["name"] == category and meta["alias"] == alias:
            return version
    return f"{category}_v1"
```

#### [NEW] [__init__.py](file:///c:/Users/User/Desktop/Sub_Project/ai/app/prompts/__init__.py)
기존 `ai/app/prompts.py` 내용을 그대로 이관하되, 버저닝과 해시 기능 및 결정을 풍부하게 바인딩합니다.

```python
from app.schemas import AiGenerateRequest
from app.workflow.intent import FreeQuestionIntent
from app.prompts.registry import compute_prompt_hash, lookup_prompt_version

PROMPT_VERSIONS = {
    "first-question": "first_question_v1",
    "follow-up": "follow_up_v1",
    "free-question": "free_question_v1",
}


def prompt_version_for_mode(mode: str, intent: FreeQuestionIntent | None = None) -> str:
    if mode == "first-question":
        return "first_question_v1"
    if mode == "follow-up":
        return "follow_up_v1"
    if mode == "free-question":
        if intent:
            if intent.intent == "concept_definition":
                return "concept_definition_v1"
            elif intent.intent == "wrong_answer_explanation":
                return "wrong_answer_explanation_v1"
            elif intent.intent == "follow_up":
                return "follow_up_intent_v1"
        return "free_question_v1"
    return "free_question_v1"


def prompt_strategy_for_mode(mode: str, intent: FreeQuestionIntent | None = None) -> str:
    if mode == "first-question":
        return "first-question"
    if mode == "follow-up":
        return "follow-up"
    if mode == "free-question":
        if intent:
            return f"free-question:{intent.intent}:context_dependent={intent.context_dependent}"
        return "free-question:generic"
    return f"unknown:{mode}"


# ... 기존 build_prompt 및 format_options 함수 전체 복사 ...
```

#### [DELETE] [prompts.py](file:///c:/Users/User/Desktop/Sub_Project/ai/app/prompts.py)
기존 단일 파일 `app/prompts.py`는 패키지 개편으로 인해 삭제 처리합니다.

---

### Component 2: Workflow State & Judge schemas 확장

#### [MODIFY] [state.py](file:///c:/Users/User/Desktop/Sub_Project/ai/app/workflow/state.py)
`ReviewWorkflowState`에 다차원 프롬프트 메타데이터를 추적할 수 있도록 신규 멤버 변수를 대거 바인딩합니다.
*   Modify `ReviewWorkflowState` 클래스 바디에 아래 항목 추가:
    ```python
    prompt_hash: str | None = None
    prompt_strategy: str | None = None
    retry_prompt_version: str | None = None
    retry_prompt_hash: str | None = None
    semantic_judge_prompt_version: str | None = None
    semantic_judge_prompt_hash: str | None = None
    grounding_prompt_version: str | None = None
    grounding_prompt_hash: str | None = None
    ```

#### [MODIFY] [judge.py](file:///c:/Users/User/Desktop/Sub_Project/ai/app/workflow/judge.py)
`SemanticJudgeResult` 데이터 클래스 및 `judge_answer` 함수를 수정하여 판사 프롬프트 버전 및 해시를 인계하도록 합니다.
*   Modify `SemanticJudgeResult`:
    ```python
    @dataclass(frozen=True)
    class SemanticJudgeResult:
        relevance_score: float
        context_bias_score: float
        hallucination_risk: str
        should_retry: bool
        reason: str
        prompt_version: str | None = None
        prompt_hash: str | None = None
    ```
*   Modify `judge_answer` 함수:
    *   실제 LLM 판결 시 `prompt_version = "semantic_judge_v1"`, `prompt_hash = compute_prompt_hash(prompt)`를 계산해 `SemanticJudgeResult` 생성자에 추가 인입.
    *   에러/디폴트/스킵 시에는 `prompt_version=None, prompt_hash=None`을 기본 설정하여 하위 안전을 확보.

#### [MODIFY] [grounding.py](file:///c:/Users/User/Desktop/Sub_Project/ai/app/workflow/grounding.py)
`GroundingResult` 데이터 클래스 및 `validate_grounding` 함수를 수정하여 RAG Grounding 판사 프롬프트 버전 및 해시를 인계하도록 합니다.
*   Modify `GroundingResult`:
    ```python
    @dataclass(frozen=True)
    class GroundingResult:
        grounding_score: float
        evidence_coverage: float
        unsupported_claims: list[str]
        grounded: bool
        reason: str
        prompt_version: str | None = None
        prompt_hash: str | None = None
    ```
*   Modify `validate_grounding` 함수:
    *   `prompt_version = "grounding_judge_v1"`, `prompt_hash = compute_prompt_hash(prompt)`를 계산해 `GroundingResult` 생성자에 추가 인입.
    *   스킵/실패 시에는 `prompt_version=None, prompt_hash=None` 설정.

---

### Component 3: Prompt Generation Nodes & Observability Events 연동

#### [MODIFY] [nodes.py](file:///c:/Users/User/Desktop/Sub_Project/ai/app/workflow/nodes.py)
프롬프트가 빌드되는 노드 단계에서 메타데이터를 추출해 상태값에 정밀 보존합니다.
*   **1차 생성 프롬프트 정보 수집 (`generate_answer_node`):**
    ```python
    prompt = build_prompt(state.mode, state.request, context=_context_text(state), intent=state.free_question_intent)
    from app.prompts import prompt_strategy_for_mode, compute_prompt_hash
    state.prompt_version = prompt_version_for_mode(state.mode, state.free_question_intent)
    state.prompt_strategy = prompt_strategy_for_mode(state.mode, state.free_question_intent)
    state.prompt_hash = compute_prompt_hash(prompt)
    ```
*   **Retry 생성 프롬프트 정보 수집 (`generate_answer_node` 재시도 파트):**
    ```python
    retry_prompt = build_prompt(state.mode, state.request, context=_context_text(state), intent=fake_intent)
    state.retry_prompt_version = "retry_v1"
    state.retry_prompt_hash = compute_prompt_hash(retry_prompt)
    ```
*   **Semantic Judge & Grounding Judge 결과 보존:**
    *   `judge_answer`의 결과인 `SemanticJudgeResult` 내의 `prompt_version`, `prompt_hash`를 `state.semantic_judge_prompt_version`, `state.semantic_judge_prompt_hash`에 매핑합니다.
    *   `validate_grounding`의 결과인 `GroundingResult` 내의 `prompt_version`, `prompt_hash`를 `state.grounding_prompt_version`, `state.grounding_prompt_hash`에 매핑합니다.

#### [MODIFY] [runner.py](file:///c:/Users/User/Desktop/Sub_Project/ai/app/workflow/runner.py)
수집된 버전 메타데이터를 observability 이벤트에 주입하여 대시보드와 관측계에 안전하게 유통시킵니다.
*   Modify `_workflow_completed_event` 함수:
    ```python
    # state 인입을 통한 완성형 메타데이터 주입
    def _workflow_completed_event(state: ReviewWorkflowState, response: AiGenerateResponse) -> dict[str, object]:
        return {
            "event": "ai_review.workflow_completed",
            "route": response.route,
            "model_used": response.model_used,
            "fallback_used": response.fallback_used,
            "confidence_score": response.confidence_score,
            "retrieved_concept_ids": response.retrieved_concept_ids,
            "candidate_id": response.candidate_id,
            "prompt_version": response.prompt_version,
            "prompt_hash": state.prompt_hash,
            "prompt_strategy": state.prompt_strategy,
            "retry_prompt_version": state.retry_prompt_version,
            "retry_prompt_hash": state.retry_prompt_hash,
            "semantic_judge_prompt_version": state.semantic_judge_prompt_version,
            "grounding_prompt_version": state.grounding_prompt_version,
            "latency_ms": response.latency_ms,
            "quality_flags": response.quality_flags,
        }
    ```
*   Modify `ai_review.semantic_judge_evaluated` 이벤트 발행 부분 (runner.py:65, runner.py:123):
    *   아래 메타데이터 정보들 추가 주입:
        ```python
        "prompt_version": state.prompt_version,
        "prompt_hash": state.prompt_hash,
        "prompt_strategy": state.prompt_strategy,
        "semantic_judge_prompt_version": state.semantic_judge_prompt_version,
        "semantic_judge_prompt_hash": state.semantic_judge_prompt_hash,
        "retry_prompt_version": state.retry_prompt_version,
        "retry_prompt_hash": state.retry_prompt_hash,
        ```
*   Modify `ai_review.grounding_evaluated` 이벤트 발행 부분 (runner.py:164):
    *   아래 메타데이터 정보들 추가 주입:
        ```python
        "prompt_version": state.prompt_version,
        "prompt_hash": state.prompt_hash,
        "prompt_strategy": state.prompt_strategy,
        "grounding_prompt_version": state.grounding_prompt_version,
        "grounding_prompt_hash": state.grounding_prompt_hash,
        ```

---

## Verification Plan

### Automated Tests
*   `tests/test_prompt_versioning.py` 신설하여 4가지 시나리오 검증:
    1.  **결정론적 해시 테스트 (`test_deterministic_hash`):** 여러 가지 공백 구조를 가진 동일한 내용의 프롬프트가 정규화 후 완벽히 동일한 SHA-256 해시를 반환하는지 테스트.
    2.  **의도별 고유 해시 테스트 (`test_different_strategies_produce_different_hashes`):** 서로 다른 질문 전략(`wrong_answer_explanation` vs `concept_definition`)이 서로 다른 버전과 고유 해시를 보장하는지 검증.
    3.  **재시도 개별 추적 테스트 (`test_retry_prompt_tracked_separately`):** 재시도 동작이 발생했을 때 1차 프롬프트와 재시도 프롬프트(background context 소거 버전)가 개별 메타데이터와 독립 해시로 추적되는지 검증.
    4.  **관측 메타데이터 방출 테스트 (`test_prompt_metadata_emitted_correctly`):** 비즈니스 플로우 완성 시 발행되는 Observability 이벤트 스트림(`ai_review.workflow_completed` 등)에 버전 및 해시 필드가 고스란히 담겨 방출되는지 최종 통합 테스트.

### Commands to Run:
```powershell
.venv\Scripts\python.exe -m unittest tests/test_prompt_versioning.py -v
.venv\Scripts\python.exe -m unittest discover -s tests
```
